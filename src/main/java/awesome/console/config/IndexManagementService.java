package awesome.console.config;

import awesome.console.AwesomeLinkFilter;
import awesome.console.AwesomeLinkFilterProvider;
import awesome.console.util.ExceptionHandling;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

import java.awt.Component;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 索引管理服务类
 * 负责处理文件索引的重建、清除等业务逻辑
 * UI 更新通过回调接口通知调用方
 */
public class IndexManagementService {
    private static final Logger logger = Logger.getInstance(IndexManagementService.class);

    private static final long REBUILD_INTERVAL_MS = 5000; // 5秒间隔

    /**
     * 操作互斥标志。
     * <p>
     * 必须是静态的：本服务的实例被设置面板持有并随对话框创建/销毁
     * （{@code AwesomeConsoleConfigForm} 的字段），若状态存放在实例上，
     * 互斥与防抖只在单次对话框会话内有效——关闭再打开设置页即可绕过，
     * 与在飞的后台重建任务并发冲突。
     * <p>
     * 用 CAS 获取而非"先查后置"，避免两次点击之间的竞态窗口。
     */
    private static final AtomicBoolean OPERATION_IN_PROGRESS = new AtomicBoolean(false);

    /**
     * 各项目最近一次<b>成功受理</b>的重建时刻，用于防抖。
     * key 为 {@link Project#getLocationHash()}，不持有 Project 引用以免妨碍回收。
     */
    private static final Map<String, Long> LAST_REBUILD_TIME = new ConcurrentHashMap<>();

    // 重建进度跟踪
    private long rebuildStartTime = 0;
    private int estimatedTotalFiles = 1000;

    // 通知组
    private static final NotificationGroup NOTIFICATION_GROUP =
            NotificationGroupManager.getInstance().getNotificationGroup("Awesome Console X");

    /**
     * 索引操作类型常量
     */
    public static final String OPERATION_REBUILD = "rebuild";
    public static final String OPERATION_CLEAR = "clear";

    /**
     * 索引操作进度回调接口
     */
    public interface ProgressCallback {
        /**
         * 操作开始
         * @param operationType 操作类型（OPERATION_REBUILD 或 OPERATION_CLEAR）
         */
        void onStart(String operationType);

        /**
         * 进度更新（来自正在构建的临时缓存，与自动初始化共用 {@link AwesomeLinkFilter.ReloadProgress}）
         * 清除操作无需实现
         */
        default void onProgress(AwesomeLinkFilter.ReloadProgress progress) {
        }

        /**
         * 操作完成
         * @param operationType 操作类型（OPERATION_REBUILD 或 OPERATION_CLEAR）
         * @param stats 最终统计信息
         * @param duration 耗时（毫秒）
         */
        void onComplete(String operationType, AwesomeLinkFilter.IndexStatistics stats, long duration);

        /**
         * 操作失败
         * @param operationType 操作类型（OPERATION_REBUILD 或 OPERATION_CLEAR）
         * @param error 错误信息
         */
        void onError(String operationType, String error);

        /**
         * 操作未被受理（前置检查未通过，没有任何后台任务被启动）。
         * <p>
         * 必须存在这个回调：早退路径若不通知调用方，UI 无从区分
         * "操作已开始"与"操作被拒绝"，只能把按钮永久留在禁用+"Rebuilding..."状态。
         *
         * @param operationType 操作类型
         * @param reason 拒绝原因（已经以通知形式展示给用户）
         */
        default void onRejected(String operationType, String reason) {
        }
    }

    /**
     * 检查是否可以执行操作
     * @param project 项目
     * @return 如果可以执行返回 true
     */
    public boolean canExecuteOperation(Project project) {
        if (project == null) {
            showNotification(null, "No project is currently opened.", NotificationType.ERROR);
            return false;
        }

        if (OPERATION_IN_PROGRESS.get()) {
            showNotification(project, "Another index operation is already in progress.", NotificationType.WARNING);
            return false;
        }

        return true;
    }

    /**
     * 通知调用方操作未被受理。
     * <p>
     * 与 onStart/onComplete 一样投递到 EDT，保证 UI 层的状态转移全部发生在同一线程，
     * 不会出现"拒绝先到、开始后到"的乱序。
     */
    private void notifyRejected(Component component, ProgressCallback callback,
                                String operationType, String reason) {
        if (callback == null) {
            return;
        }
        ModalityState modality = component == null
                ? ModalityState.defaultModalityState()
                : ModalityState.stateForComponent(component);
        ApplicationManager.getApplication().invokeLater(
                () -> callback.onRejected(operationType, reason), modality);
    }

    /**
     * 只读判断重建防抖是否已过冷却期，<b>不</b>推进时间戳。
     *
     * @return 冷却中返回剩余秒数（&gt;=0），可以重建返回 -1
     */
    private static long remainingRebuildCooldownSeconds(@NotNull Project project) {
        Long last = LAST_REBUILD_TIME.get(project.getLocationHash());
        if (last == null) {
            return -1;
        }
        long elapsed = System.currentTimeMillis() - last;
        if (elapsed >= REBUILD_INTERVAL_MS) {
            return -1;
        }
        // 向上取整：1–999ms 剩余不得报 “Please wait 0 seconds” 却仍拒绝。
        long remainingMs = REBUILD_INTERVAL_MS - elapsed;
        return (remainingMs + 999) / 1000;
    }

    /**
     * 检查重建间隔（防抖机制），通过检查时推进时间戳。
     * <p>
     * 保留此公开方法以兼容既有调用方；{@link #rebuildIndex} 内部改用
     * {@link #remainingRebuildCooldownSeconds} 做只读判断，仅在操作真正受理后
     * 才推进时间戳，避免"被拒绝"本身不断为冷却期续期。
     *
     * @param project 项目
     * @return 如果可以继续重建返回 true
     */
    public boolean checkRebuildInterval(Project project) {
        if (project == null) {
            return false;
        }
        long remaining = remainingRebuildCooldownSeconds(project);
        if (remaining >= 0) {
            showNotification(project,
                    String.format("Please wait %d seconds before rebuilding again.", remaining),
                    NotificationType.WARNING);
            return false;
        }
        LAST_REBUILD_TIME.put(project.getLocationHash(), System.currentTimeMillis());
        return true;
    }

    /**
     * 重建索引
     * 
     * @param project 当前项目实例，不能为 null
     * @param component UI 组件，用于确定模态状态上下文
     * @param callback 进度回调接口，用于接收操作状态更新（开始、进度、完成、错误、拒绝）
     */
    public void rebuildIndex(Project project, Component component, ProgressCallback callback) {
        // ========== 第一阶段：前置检查 ==========
        // 每条早退路径都必须回调 onRejected，否则 UI 无从区分"已开始"与"被拒绝"
        if (project == null) {
            String reason = "No project is currently opened.";
            showNotification(null, reason, NotificationType.ERROR);
            notifyRejected(component, callback, OPERATION_REBUILD, reason);
            return;
        }

        // 防抖：只读判断，不推进时间戳
        long remaining = remainingRebuildCooldownSeconds(project);
        if (remaining >= 0) {
            String reason = String.format("Please wait %d seconds before rebuilding again.", remaining);
            showNotification(project, reason, NotificationType.WARNING);
            notifyRejected(component, callback, OPERATION_REBUILD, reason);
            return;
        }

        // 互斥：CAS 获取。用"先查后置"会在两次快速点击之间留下竞态窗口
        if (!OPERATION_IN_PROGRESS.compareAndSet(false, true)) {
            String reason = "Another index operation is already in progress.";
            showNotification(project, reason, NotificationType.WARNING);
            notifyRejected(component, callback, OPERATION_REBUILD, reason);
            return;
        }

        // ========== 第二阶段：初始化（此后操作已确认受理）==========
        
        // 只有真正受理才推进防抖时间戳
        LAST_REBUILD_TIME.put(project.getLocationHash(), System.currentTimeMillis());
        
        // 记录重建操作的开始时间戳（毫秒）
        // 这个时间戳将在操作完成时用于计算总耗时（duration = 完成时间 - 开始时间）
        rebuildStartTime = System.currentTimeMillis();
        
        // 重置预估的文件总数为 1000
        // 这是一个初始预估值，在实际扫描过程中会根据实际情况动态调整
        // 用于进度条显示和进度百分比计算
        estimatedTotalFiles = 1000;
        
        // 调用回调接口的 onStart 方法，通知调用方（通常是 UI 层）操作已开始
        // 传入操作类型常量 OPERATION_REBUILD（值为 "rebuild"），用于区分不同类型的操作
        // UI 层收到此回调后可以显示进度条、禁用按钮等
        // 使用 invokeLater 和 ModalityState.stateForComponent() 确保在正确的模态上下文中更新 UI
        ApplicationManager.getApplication().invokeLater(() -> {
            callback.onStart(OPERATION_REBUILD);
        }, ModalityState.stateForComponent(component));

        // ========== 第三阶段：异步执行 ==========
        
        // 获取 IntelliJ IDEA 的 Application 实例，并在其管理的线程池中执行任务
        // executeOnPooledThread 方法会将任务提交到后台线程池，避免阻塞 EDT（事件分发线程）
        // 这样可以确保 UI 保持响应，不会因为长时间的索引重建操作而卡死
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean releasedOnEdt = false;
            // 使用 try-catch 块包裹整个重建逻辑，确保任何异常都能被捕获和处理
            try {
                // 调用 getFilterOrThrow 方法获取当前项目的 AwesomeLinkFilter 实例
                // 该方法内部会调用 AwesomeLinkFilterProvider.getFilter(project)
                // 如果返回 null（filter 不可用），会抛出 IllegalStateException 异常
                // 这个 filter 对象是实际执行索引操作的核心组件
                AwesomeLinkFilter filter = getFilterOrThrow(project);

                // 与设置页自动初始化共用 ReloadProgress：读正在构建的临时缓存，
                // 而不是主缓存（主缓存在原子替换前仍是旧数据或空的）
                Consumer<AwesomeLinkFilter.ReloadProgress> progressListener = callback::onProgress;
                filter.addReloadProgressListener(progressListener);
                try {
                    filter.manualRebuild();
                } finally {
                    filter.removeReloadProgressListener(progressListener);
                }

                // ========== 第四阶段：完成处理 ==========
                
                // 计算总耗时（毫秒）
                // 用当前时间戳减去开始时间戳（rebuildStartTime），得到操作的总耗时
                long duration = System.currentTimeMillis() - rebuildStartTime;
                
                // 获取最终的索引统计信息
                // 重建完成后再次调用 getIndexStatistics 获取最终的统计数据
                // 这个数据包含了完整的索引结果：总文件数、匹配数、忽略数等
                AwesomeLinkFilter.IndexStatistics finalStats = filter.getIndexStatistics();
                
                deliverUiThenRelease(component, () -> {
                    callback.onComplete(OPERATION_REBUILD, finalStats, duration);
                    String message = String.format("File index rebuilt successfully! %d files indexed", 
                            finalStats.getTotalCachedFiles());
                    if (finalStats.hasIgnoreStatistics()) {
                        message += String.format(" (Matched: %d, Ignored: %d)", 
                                finalStats.getMatchedFiles(), finalStats.getIgnoredFiles());
                    }
                    showNotification(project, message, NotificationType.INFORMATION);
                });
                releasedOnEdt = true;

            } catch (Exception e) {
                ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
                // ========== 第五阶段：异常处理 ==========
                
                // 记录错误日志
                // 使用 logger.error 方法记录错误信息和完整的异常堆栈
                // 第一个参数是错误消息，第二个参数是异常对象（会自动记录堆栈信息）
                // 这对于调试和问题排查非常重要
                logger.error("Failed to rebuild index: " + e.getMessage(), e);
                deliverUiThenRelease(component, () -> {
                    callback.onError(OPERATION_REBUILD, e.getMessage());
                    showNotification(project, "Failed to rebuild index: " + e.getMessage(), 
                            NotificationType.ERROR);
                });
                releasedOnEdt = true;
            } finally {
                // 未投递 EDT（控制流异常）时必须在此复位，否则按钮永久卡死
                if (!releasedOnEdt) {
                    OPERATION_IN_PROGRESS.set(false);
                }
            }
        });
    }

    /**
     * 清除索引
     * 
     * @param project 当前项目实例，不能为 null
     * @param component UI 组件，用于确定模态状态上下文
     * @param callback 进度回调接口，用于接收操作状态更新（开始、完成、错误）
     */
    public void clearIndex(Project project, Component component, ProgressCallback callback) {
        // ========== 第一阶段：前置检查 ==========
        // 与 rebuildIndex 一致：每条早退路径都必须回调 onRejected
        if (project == null) {
            String reason = "No project is currently opened.";
            showNotification(null, reason, NotificationType.ERROR);
            notifyRejected(component, callback, OPERATION_CLEAR, reason);
            return;
        }

        // ========== 第二阶段：初始化 ==========
        
        // 互斥：CAS 获取，失败即未受理
        if (!OPERATION_IN_PROGRESS.compareAndSet(false, true)) {
            String reason = "Another index operation is already in progress.";
            showNotification(project, reason, NotificationType.WARNING);
            notifyRejected(component, callback, OPERATION_CLEAR, reason);
            return;
        }
        
        // 调用回调接口的 onStart 方法，通知调用方（通常是 UI 层）操作已开始
        // 传入操作类型常量 OPERATION_CLEAR（值为 "clear"），用于区分不同类型的操作
        // UI 层收到此回调后可以显示进度提示、禁用按钮等
        // 使用 invokeLater 和 ModalityState.stateForComponent() 确保在正确的模态上下文中更新 UI
        ApplicationManager.getApplication().invokeLater(() -> {
            callback.onStart(OPERATION_CLEAR);
        }, ModalityState.stateForComponent(component));

        // ========== 第三阶段：异步执行 ==========
        
        // 获取 IntelliJ IDEA 的 Application 实例，并在其管理的线程池中执行任务
        // executeOnPooledThread 方法会将任务提交到后台线程池，避免阻塞 EDT（事件分发线程）
        // 这样可以确保 UI 保持响应，不会因为清除索引操作而卡死
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean releasedOnEdt = false;
            // 使用 try-catch 块包裹整个清除逻辑，确保任何异常都能被捕获和处理
            try {
                // 调用 getFilterOrThrow 方法获取当前项目的 AwesomeLinkFilter 实例
                // 该方法内部会调用 AwesomeLinkFilterProvider.getFilter(project)
                // 如果返回 null（filter 不可用），会抛出 IllegalStateException 异常
                // 这个 filter 对象是实际执行索引操作的核心组件
                AwesomeLinkFilter filter = getFilterOrThrow(project);
                
                // 记录清除操作的开始时间戳（毫秒）
                // 这个时间戳将在操作完成时用于计算总耗时（duration = 完成时间 - 开始时间）
                long startTime = System.currentTimeMillis();

                // 获取清除前的统计信息
                // 在执行清除操作之前，先调用 getIndexStatistics 方法获取当前的索引统计信息
                // 这些信息包括：总文件数、文件名缓存大小、基础文件名缓存大小等
                // 保存这些信息是为了在清除完成后能够告知用户清除了多少数据
                AwesomeLinkFilter.IndexStatistics beforeStats = filter.getIndexStatistics();
                
                // 执行清除操作
                // 调用 filter 的 clearCache 方法清除所有索引缓存
                // 该方法会清空内部的文件索引、文件名缓存、基础文件名缓存等所有缓存数据
                // 清除后，索引会在下次需要时自动重建
                filter.clearCache();
                
                // ========== 第四阶段：完成处理 ==========
                
                // 计算总耗时（毫秒）
                // 用当前时间戳减去开始时间戳（startTime），得到清除操作的总耗时
                long duration = System.currentTimeMillis() - startTime;
                
                // 在 EDT 中执行完成后的处理
                // 使用 invokeLater 确保所有 UI 更新和回调通知都在 EDT 中执行
                // 这是 Swing/IntelliJ UI 的线程安全要求
                // 使用 ModalityState.stateForComponent() 确保在正确的模态上下文中更新 UI
                deliverUiThenRelease(component, () -> {
                    callback.onComplete(OPERATION_CLEAR, beforeStats, duration);
                    String message = String.format(
                            "File index cleared successfully! Cleared %d files (%d filenames, %d basenames) in %s. Index will be rebuilt automatically when needed.",
                            beforeStats.getTotalCachedFiles(), 
                            beforeStats.getFileCacheSize(), 
                            beforeStats.getFileBaseCacheSize(), 
                            formatDuration(duration));
                    showNotification(project, message, NotificationType.INFORMATION);
                });
                releasedOnEdt = true;

            } catch (Exception e) {
                ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
                // ========== 第五阶段：异常处理 ==========
                
                // 记录错误日志
                // 使用 logger.error 方法记录错误信息和完整的异常堆栈
                // 第一个参数是错误消息，第二个参数是异常对象（会自动记录堆栈信息）
                // 这对于调试和问题排查非常重要
                logger.error("Failed to clear index: " + e.getMessage(), e);
                deliverUiThenRelease(component, () -> {
                    callback.onError(OPERATION_CLEAR, e.getMessage());
                    showNotification(project, "Failed to clear index: " + e.getMessage(), 
                            NotificationType.ERROR);
                });
                releasedOnEdt = true;
            } finally {
                if (!releasedOnEdt) {
                    OPERATION_IN_PROGRESS.set(false);
                }
            }
        });
    }

    /**
     * 获取索引统计信息
     * @param project 项目
     * @return 索引统计信息，如果获取失败返回 null
     */
    public AwesomeLinkFilter.IndexStatistics getIndexStatistics(Project project) {
        AwesomeLinkFilter filter = filterForIndexing(project);
        if (filter == null) {
            return null;
        }
        try {
            return filter.getIndexStatistics();
        } catch (Exception e) {
            ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
            logger.error("Failed to get index statistics: " + e.getMessage(), e);
        }
        return null;
    }

    public boolean isCacheInitialized(Project project) {
        AwesomeLinkFilter filter = filterForQuery(project);
        return filter != null && filter.isCacheInitialized();
    }

    /**
     * 是否正在进行首次缓存构建
     */
    public boolean isCacheBuilding(Project project) {
        AwesomeLinkFilter filter = filterForQuery(project);
        return filter != null && filter.isCacheBuilding();
    }

    /**
     * 是否已调度或正在执行缓存重建（含首次初始化和后续 rebuild）
     */
    public boolean isReloadScheduledOrRunning(Project project) {
        AwesomeLinkFilter filter = filterForQuery(project);
        return filter != null && filter.isReloadScheduledOrRunning();
    }

    /**
     * 等待当前已调度或正在执行的缓存重建完成。
     * 若没有进行中的重建，则立即完成，不会额外触发重建。
     */
    public CompletableFuture<Void> whenCacheReady(Project project) {
        // 这里必须允许创建 Filter：设置页要展示的是"索引就绪后的统计"。
        // 若沿用只读查询（不创建），Filter 尚未创建时 future 会立刻完成，
        // UI 就永久停在打开瞬间的全 0 快照上。
        AwesomeLinkFilter filter = filterForIndexing(project);
        if (filter == null) {
            return CompletableFuture.completedFuture(null);
        }
        return filter.whenCacheReady();
    }

    /**
     * 订阅缓存重建实时进度（重建已开始后也能挂接）
     */
    public void addReloadProgressListener(Project project, Consumer<AwesomeLinkFilter.ReloadProgress> listener) {
        if (listener == null) {
            return;
        }
        AwesomeLinkFilter filter = filterForQuery(project);
        if (filter != null) {
            filter.addReloadProgressListener(listener);
        }
    }

    /**
     * 取消订阅缓存重建进度
     */
    public void removeReloadProgressListener(Project project, Consumer<AwesomeLinkFilter.ReloadProgress> listener) {
        if (listener == null) {
            return;
        }
        AwesomeLinkFilter filter = filterForQuery(project);
        if (filter != null) {
            filter.removeReloadProgressListener(listener);
        }
    }

    /**
     * 获取用于<b>只读状态查询</b>的 Filter，不存在时返回 null 且不创建。
     * <p>
     * 只做 {@code cache.get}（{@link AwesomeLinkFilterProvider#getFilterIfExists}），
     * 不会走 computeIfAbsent，也不会在已 dispose 的项目上创建 Filter。
     *
     * @param project 项目，可为 null
     * @return 已存在的 Filter 或 null
     */
    private static AwesomeLinkFilter filterForQuery(Project project) {
        if (project == null || project.isDisposed()) {
            return null;
        }
        return AwesomeLinkFilterProvider.getFilterIfExists(project);
    }

    /**
     * 获取用于<b>需要索引真实就绪</b>场景的 Filter，必要时创建。
     * <p>
     * 与 {@link #filterForQuery} 的区别在于允许创建：展示索引统计、等待索引完成
     * 这类需求本身就要求索引存在。仅对已 dispose 的项目短路，避免
     * {@code AlreadyDisposedException} 传播到 EDT。
     *
     * @param project 项目，可为 null
     * @return Filter 或 null
     */
    private static AwesomeLinkFilter filterForIndexing(Project project) {
        if (project == null || project.isDisposed()) {
            return null;
        }
        try {
            return AwesomeLinkFilterProvider.getFilter(project);
        } catch (Exception e) {
            ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
            logger.warn("Failed to obtain AwesomeLinkFilter: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 检查操作是否正在进行
     */
    public boolean isOperationInProgress() {
        return OPERATION_IN_PROGRESS.get();
    }

    /**
     * 获取重建开始时间
     */
    public long getRebuildStartTime() {
        return rebuildStartTime;
    }

    /**
     * 获取预估文件总数
     */
    public int getEstimatedTotalFiles() {
        return estimatedTotalFiles;
    }

    /**
     * 获取项目的 AwesomeLinkFilter，如果不存在则抛出异常
     */
    private AwesomeLinkFilter getFilterOrThrow(Project project) {
        AwesomeLinkFilter filter = AwesomeLinkFilterProvider.getFilter(project);
        if (filter == null) {
            throw new IllegalStateException("AwesomeLinkFilter is not available for project: " + project.getName());
        }
        return filter;
    }

    /**
     * 格式化时间间隔
     */
    public String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return (millis / 1000) + "s";
        } else if (millis < 3600000) {
            return (millis / 60000) + "m";
        } else {
            return (millis / 3600000) + "h";
        }
    }

    /**
     * 显示通知
     */
    /**
     * 完成/错误回调投到 EDT，并在回调结束后才释放互斥。
     */
    private void deliverUiThenRelease(Component component, Runnable uiWork) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                uiWork.run();
            } finally {
                OPERATION_IN_PROGRESS.set(false);
            }
        }, ModalityState.stateForComponent(component));
    }

    private void showNotification(Project project, String content, NotificationType type) {
        // 检查通知开关
        if (!AwesomeConsoleStorage.getInstance().showNotifications) {
            return;
        }
        
        Notification notification = NOTIFICATION_GROUP.createNotification(
                "Awesome Console - Index Management",
                content,
                type
        );
        notification.notify(project);
    }
}