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

import java.awt.Component;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 索引管理服务类
 * 负责处理文件索引的重建、清除等业务逻辑
 * UI 更新通过回调接口通知调用方
 */
public class IndexManagementService {
    private static final Logger logger = Logger.getInstance(IndexManagementService.class);

    // 防抖机制
    private long lastRebuildTime = 0;
    private static final long REBUILD_INTERVAL_MS = 5000; // 5秒间隔

    // 操作互斥标志
    private volatile boolean isOperationInProgress = false;

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

        if (isOperationInProgress) {
            showNotification(project, "Another index operation is already in progress.", NotificationType.WARNING);
            return false;
        }

        return true;
    }

    /**
     * 检查重建间隔（防抖机制）
     * @param project 项目
     * @return 如果可以继续重建返回 true
     */
    public boolean checkRebuildInterval(Project project) {
        long now = System.currentTimeMillis();
        if (now - lastRebuildTime < REBUILD_INTERVAL_MS) {
            long remaining = (REBUILD_INTERVAL_MS - (now - lastRebuildTime)) / 1000;
            showNotification(project,
                    String.format("Please wait %d seconds before rebuilding again.", remaining),
                    NotificationType.WARNING);
            return false;
        }
        lastRebuildTime = now;
        return true;
    }

    /**
     * 重建索引
     * 
     * @param project 当前项目实例，不能为 null
     * @param component UI 组件，用于确定模态状态上下文
     * @param callback 进度回调接口，用于接收操作状态更新（开始、进度、完成、错误）
     */
    public void rebuildIndex(Project project, Component component, ProgressCallback callback) {
        // ========== 第一阶段：前置检查 ==========
        
        // 该方法会检查：1) project 是否为 null  2) 是否有其他操作正在进行（isOperationInProgress 标志）
        // 如果检查失败，方法内部会显示相应的错误或警告通知，并返回 false
        if (!canExecuteOperation(project)) {
            // 检查失败，直接返回，不执行后续操作
            return;
        }

        // 该方法会检查当前时间与上次重建时间（lastRebuildTime）的间隔是否大于 5 秒（REBUILD_INTERVAL_MS）
        // 如果间隔不足，会显示警告通知告知用户需要等待的秒数，并返回 false
        // 如果间隔足够，会更新 lastRebuildTime 为当前时间，并返回 true
        if (!checkRebuildInterval(project)) {
            // 防抖检查失败（操作过于频繁），直接返回，不执行后续操作
            return;
        }

        // ========== 第二阶段：初始化 ==========
        
        // 将 volatile 标志位 isOperationInProgress 设置为 true
        // 这个标志用于防止并发操作：当有操作正在进行时，其他操作会被 canExecuteOperation 方法拦截
        // 使用 volatile 关键字确保多线程环境下的可见性
        isOperationInProgress = true;
        
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
                
                // 在 EDT 中执行完成后的处理
                // 使用 invokeLater 确保所有 UI 更新和回调通知都在 EDT 中执行
                // 使用 ModalityState.stateForComponent() 确保在正确的模态上下文中更新 UI
                ApplicationManager.getApplication().invokeLater(() -> {
                    // 重置操作进行中标志为 false
                    // 这样其他操作就可以继续执行了（canExecuteOperation 会返回 true）
                    isOperationInProgress = false;
                    
                    // 调用回调接口的 onComplete 方法，通知调用方操作已完成
                    // 传入三个参数：
                    // 1. OPERATION_REBUILD - 操作类型常量
                    // 2. finalStats - 最终的索引统计信息
                    // 3. duration - 操作总耗时（毫秒）
                    // UI 层收到此回调后可以隐藏进度条、启用按钮、显示完成消息等
                    callback.onComplete(OPERATION_REBUILD, finalStats, duration);
                    
                    // 构建成功通知消息
                    // 使用 String.format 格式化消息，显示已索引的文件总数
                    String message = String.format("File index rebuilt successfully! %d files indexed", 
                            finalStats.getTotalCachedFiles());
                    
                    // 检查统计信息中是否包含忽略统计数据
                    // hasIgnoreStatistics 方法会检查是否有匹配/忽略文件的统计信息
                    if (finalStats.hasIgnoreStatistics()) {
                        // 如果有忽略统计信息，则追加到消息中
                        // 显示匹配的文件数和被忽略的文件数
                        message += String.format(" (Matched: %d, Ignored: %d)", 
                                finalStats.getMatchedFiles(), finalStats.getIgnoredFiles());
                    }
                    
                    // 调用 showNotification 方法显示成功通知
                    // 传入三个参数：
                    // 1. project - 项目实例，用于确定通知显示的范围
                    // 2. message - 通知消息内容
                    // 3. NotificationType.INFORMATION - 通知类型（信息类型，通常显示为蓝色图标）
                    showNotification(project, message, NotificationType.INFORMATION);
                }, ModalityState.stateForComponent(component));

            } catch (Exception e) {
                ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
                // ========== 第五阶段：异常处理 ==========
                
                // 记录错误日志
                // 使用 logger.error 方法记录错误信息和完整的异常堆栈
                // 第一个参数是错误消息，第二个参数是异常对象（会自动记录堆栈信息）
                // 这对于调试和问题排查非常重要
                logger.error("Failed to rebuild index: " + e.getMessage(), e);
                
                // 在 EDT 中执行错误处理
                // 使用 invokeLater 确保 UI 更新和回调通知在正确的线程中执行
                // 使用 ModalityState.stateForComponent() 确保在正确的模态上下文中更新 UI
                ApplicationManager.getApplication().invokeLater(() -> {
                    // 重置操作进行中标志为 false
                    // 即使操作失败，也要重置标志，否则后续操作将永远无法执行
                    isOperationInProgress = false;
                    
                    // 调用回调接口的 onError 方法，通知调用方操作失败
                    // 传入两个参数：
                    // 1. OPERATION_REBUILD - 操作类型常量
                    // 2. e.getMessage() - 异常的错误消息
                    // UI 层收到此回调后可以隐藏进度条、启用按钮、显示错误消息等
                    callback.onError(OPERATION_REBUILD, e.getMessage());
                    
                    // 调用 showNotification 方法显示错误通知
                    // 传入三个参数：
                    // 1. project - 项目实例
                    // 2. 错误消息 - 包含异常的详细信息
                    // 3. NotificationType.ERROR - 通知类型（错误类型，通常显示为红色图标）
                    showNotification(project, "Failed to rebuild index: " + e.getMessage(), 
                            NotificationType.ERROR);
                }, ModalityState.stateForComponent(component));
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
        
        // 该方法会检查：1) project 是否为 null  2) 是否有其他操作正在进行（isOperationInProgress 标志）
        // 如果检查失败，方法内部会显示相应的错误或警告通知，并返回 false
        if (!canExecuteOperation(project)) {
            // 检查失败，直接返回，不执行后续操作
            return;
        }

        // ========== 第二阶段：初始化 ==========
        
        // 将 volatile 标志位 isOperationInProgress 设置为 true
        // 这个标志用于防止并发操作：当有操作正在进行时，其他操作会被 canExecuteOperation 方法拦截
        // 使用 volatile 关键字确保多线程环境下的可见性
        isOperationInProgress = true;
        
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
                ApplicationManager.getApplication().invokeLater(() -> {
                    // 重置操作进行中标志为 false
                    // 这样其他操作就可以继续执行了（canExecuteOperation 会返回 true）
                    // 即使清除操作很快完成，也要确保在回调之前重置标志
                    isOperationInProgress = false;
                    
                    // 调用回调接口的 onComplete 方法，通知调用方操作已完成
                    // 传入三个参数：
                    // 1. OPERATION_CLEAR - 操作类型常量（值为 "clear"）
                    // 2. beforeStats - 清除前的索引统计信息（用于显示清除了多少数据）
                    // 3. duration - 操作总耗时（毫秒）
                    // UI 层收到此回调后可以隐藏进度提示、启用按钮、显示完成消息等
                    callback.onComplete(OPERATION_CLEAR, beforeStats, duration);
                    
                    // 构建成功通知消息
                    // 使用 String.format 格式化消息，显示清除的详细信息：
                    // - beforeStats.getTotalCachedFiles() - 清除的缓存文件数
                    // - beforeStats.getFileCacheSize() - 清除的文件名缓存条目数
                    // - beforeStats.getFileBaseCacheSize() - 清除的基础文件名缓存条目数
                    // - formatDuration(duration) - 格式化后的耗时（如 "123ms"、"2s"、"1m" 等）
                    // 同时提示用户索引会在需要时自动重建
                    String message = String.format(
                            "File index cleared successfully! Cleared %d files (%d filenames, %d basenames) in %s. Index will be rebuilt automatically when needed.",
                            beforeStats.getTotalCachedFiles(), 
                            beforeStats.getFileCacheSize(), 
                            beforeStats.getFileBaseCacheSize(), 
                            formatDuration(duration));
                    
                    // 调用 showNotification 方法显示成功通知
                    // 传入三个参数：
                    // 1. project - 项目实例，用于确定通知显示的范围
                    // 2. message - 通知消息内容
                    // 3. NotificationType.INFORMATION - 通知类型（信息类型，通常显示为蓝色图标）
                    showNotification(project, message, NotificationType.INFORMATION);
                }, ModalityState.stateForComponent(component));

            } catch (Exception e) {
                ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
                // ========== 第五阶段：异常处理 ==========
                
                // 记录错误日志
                // 使用 logger.error 方法记录错误信息和完整的异常堆栈
                // 第一个参数是错误消息，第二个参数是异常对象（会自动记录堆栈信息）
                // 这对于调试和问题排查非常重要
                logger.error("Failed to clear index: " + e.getMessage(), e);
                
                // 在 EDT 中执行错误处理
                // 使用 invokeLater 确保 UI 更新和回调通知在正确的线程中执行
                // 使用 ModalityState.stateForComponent() 确保在正确的模态上下文中更新 UI
                ApplicationManager.getApplication().invokeLater(() -> {
                    // 重置操作进行中标志为 false
                    // 即使操作失败，也要重置标志，否则后续操作将永远无法执行
                    // 这是确保系统能够从错误状态恢复的关键步骤
                    isOperationInProgress = false;
                    
                    // 调用回调接口的 onError 方法，通知调用方操作失败
                    // 传入两个参数：
                    // 1. OPERATION_CLEAR - 操作类型常量（值为 "clear"）
                    // 2. e.getMessage() - 异常的错误消息
                    // UI 层收到此回调后可以隐藏进度提示、启用按钮、显示错误消息等
                    callback.onError(OPERATION_CLEAR, e.getMessage());
                    
                    // 调用 showNotification 方法显示错误通知
                    // 传入三个参数：
                    // 1. project - 项目实例
                    // 2. 错误消息 - 包含异常的详细信息
                    // 3. NotificationType.ERROR - 通知类型（错误类型，通常显示为红色图标）
                    showNotification(project, "Failed to clear index: " + e.getMessage(), 
                            NotificationType.ERROR);
                }, ModalityState.stateForComponent(component));
            }
        });
    }

    /**
     * 获取索引统计信息
     * @param project 项目
     * @return 索引统计信息，如果获取失败返回 null
     */
    public AwesomeLinkFilter.IndexStatistics getIndexStatistics(Project project) {
        if (project == null) {
            return null;
        }

        try {
            AwesomeLinkFilter filter = AwesomeLinkFilterProvider.getFilter(project);
            if (filter != null) {
                return filter.getIndexStatistics();
            }
        } catch (Exception e) {
            ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
            logger.error("Failed to get index statistics: " + e.getMessage(), e);
        }
        return null;
    }

    public boolean isCacheInitialized(Project project) {
        if (project == null) {
            return false;
        }
        return AwesomeLinkFilterProvider.getFilter(project).isCacheInitialized();
    }

    /**
     * 是否正在进行首次缓存构建
     */
    public boolean isCacheBuilding(Project project) {
        if (project == null) {
            return false;
        }
        return AwesomeLinkFilterProvider.getFilter(project).isCacheBuilding();
    }

    /**
     * 是否已调度或正在执行缓存重建（含首次初始化和后续 rebuild）
     */
    public boolean isReloadScheduledOrRunning(Project project) {
        if (project == null) {
            return false;
        }
        return AwesomeLinkFilterProvider.getFilter(project).isReloadScheduledOrRunning();
    }

    /**
     * 等待当前已调度或正在执行的缓存重建完成。
     * 若没有进行中的重建，则立即完成，不会额外触发重建。
     */
    public CompletableFuture<Void> whenCacheReady(Project project) {
        if (project == null) {
            return CompletableFuture.completedFuture(null);
        }
        return AwesomeLinkFilterProvider.getFilter(project).whenCacheReady();
    }

    /**
     * 订阅缓存重建实时进度（重建已开始后也能挂接）
     */
    public void addReloadProgressListener(Project project, Consumer<AwesomeLinkFilter.ReloadProgress> listener) {
        if (project == null || listener == null) {
            return;
        }
        AwesomeLinkFilterProvider.getFilter(project).addReloadProgressListener(listener);
    }

    /**
     * 取消订阅缓存重建进度
     */
    public void removeReloadProgressListener(Project project, Consumer<AwesomeLinkFilter.ReloadProgress> listener) {
        if (project == null || listener == null) {
            return;
        }
        AwesomeLinkFilterProvider.getFilter(project).removeReloadProgressListener(listener);
    }

    /**
     * 检查操作是否正在进行
     */
    public boolean isOperationInProgress() {
        return isOperationInProgress;
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