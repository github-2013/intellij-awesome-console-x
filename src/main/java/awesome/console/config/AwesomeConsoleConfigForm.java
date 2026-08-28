package awesome.console.config;

import awesome.console.AwesomeLinkFilter;
import awesome.console.util.ExceptionHandling;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.util.*;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("SameParameterValue")
public class AwesomeConsoleConfigForm implements AwesomeConsoleDefaults {
    private static final Logger logger = Logger.getInstance(AwesomeConsoleConfigForm.class);

    public JPanel mainPanel;
    public JCheckBox limitLineMatchingByCheckBox;
    public JSpinner maxLengthSpinner;
    public JCheckBox matchLinesLongerThanCheckBox;
    public JCheckBox searchForURLsCheckBox;
    public JCheckBox searchForFilesCheckBox;
    public JCheckBox searchForClassesCheckBox;
	public JCheckBox limitResultCheckBox;
	public JSpinner limitResultSpinner;
	public JCheckBox ignorePatternCheckBox;
    public JTextField ignorePatternTextField;
    public JLabel ignorePatternLabel;
    public JCheckBox ignoreStyleCheckBox;
    public JCheckBox fixChooseTargetFileCheckBox;
    public JCheckBox fileTypesCheckBox;
    public JTextField fileTypesTextField;
    public JCheckBox resolveSymlinkCheckBox;
    public JCheckBox preserveAnsiColorsCheckBox;
    public JCheckBox showNotificationsCheckBox;
    public JCheckBox underlineOnlyCheckBox;

    // 索引管理相关字段
    public JLabel indexStatusLabel;
    public JProgressBar indexProgressBar;
    public JButton rebuildIndexButton;
    public JButton clearIndexButton;

    private Map<JCheckBox, Set<JComponent>> bindMap;
    private Map<JComponent, Set<JCheckBox>> bindMap2;

    // 索引管理服务
    private IndexManagementService indexManagementService;
    // 自定义双色进度条UI
    private DualColorProgressBarUI dualColorProgressBarUI;
    // 表单是否已销毁，用于忽略过期的异步状态刷新
    private volatile boolean disposed;
    // 自动初始化与手动 Rebuild 共用的进度监听
    private Consumer<AwesomeLinkFilter.ReloadProgress> buildingProgressListener;
    private Project buildingProgressProject;
    private volatile AwesomeLinkFilter.ReloadProgress pendingIndexingProgress;
    private volatile String pendingIndexingAction;
    private final AtomicBoolean indexingProgressUiScheduled = new AtomicBoolean(false);
    private int indexingEstimatedTotal = 1000;

    private static final String ACTION_BUILDING = "Building file index";
    private static final String ACTION_REBUILDING = "Rebuilding index";

    private void createUIComponents() {
        bindMap = new HashMap<>();
        bindMap2 = new HashMap<>();
        setupLineLimit();
        setupSplitLineIntoChunk();
        setupMatchURLs();
        setupMatchFiles();
        setupIgnorePattern();
        setupFixChooseTargetFileCheckBox();
        setupFileTypes();
        setupResolveSymlink();
        setupPreserveAnsiColors();
        setupShowNotifications();
        setupUnderlineOnly();
        setupIndexManagement();
    }

    private void setupRestore(@NotNull JComponent component, ActionListener listener) {
        final JPopupMenu popup = new JPopupMenu("Defaults");
        final JMenuItem item = popup.add("Restore defaults");
        item.setMnemonic(KeyEvent.VK_R);
        item.addActionListener(listener);
        component.setComponentPopupMenu(popup);
    }

    private void setupRestoreCheckBox(@NotNull JCheckBox checkBox, boolean defaultSelected) {
        setupRestore(checkBox, e -> setupCheckBox(checkBox, defaultSelected));
    }

    private void setupRestoreText(@NotNull JTextComponent textComponent, String defaultText) {
        setupRestore(textComponent, e -> textComponent.setText(defaultText));
    }

    private void bindCheckBoxAndComponents(@NotNull JCheckBox checkBox, @NotNull JComponent... components) {
        if (components.length > 0) {
            Stream.of(components).forEach(it -> bind(checkBox, it));
        }
    }

    private void bindComponentToCheckBoxes(@NotNull JComponent component, @NotNull JCheckBox... checkBoxes) {
        if (checkBoxes.length > 0) {
            Stream.of(checkBoxes).forEach(it -> bind(it, component));
        }
    }

    private void bind(@NotNull JCheckBox checkBox, @NotNull JComponent component) {
        getBindings(checkBox).add(component);
        getBindings(component).add(checkBox);
    }

    private Set<JComponent> getBindings(@NotNull JCheckBox checkBox) {
        return bindMap.computeIfAbsent(checkBox, __ -> new HashSet<>());
    }

    private Set<JCheckBox> getBindings(@NotNull JComponent component) {
        return bindMap2.computeIfAbsent(component, __ -> new HashSet<>());
    }

    private void onCheckBoxChange(@NotNull JCheckBox checkBox) {
        getBindings(checkBox).forEach(component -> {
            final boolean enabled = getBindings(component).stream().allMatch(JCheckBox::isSelected);
            setComponentEnabled(component, enabled);
        });
    }

    private void setComponentEnabled(@NotNull JComponent component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof JTextComponent) {
            ((JTextComponent) component).setEditable(enabled);
        }
    }

    private void setupCheckBoxAndText(@NotNull JCheckBox checkBox, boolean selected, @NotNull JTextComponent textComponent, String text) {
        setupCheckBox(checkBox, selected);
        textComponent.setText(text);
    }

    private void setupCheckBox(@NotNull JCheckBox checkBox, boolean selected) {
        checkBox.setSelected(selected);
        onCheckBoxChange(checkBox);
    }

    private JCheckBox initCheckBox(boolean defaultSelected) {
        final JCheckBox checkBox = new JCheckBox();
        checkBox.addActionListener(e -> onCheckBoxChange(checkBox));
        setupRestoreCheckBox(checkBox, defaultSelected);
        return checkBox;
    }

    private JTextField initTextField(String defaultText) {
        final JTextField textField = new JTextField();
        setupRestoreText(textField, defaultText);
        return textField;
    }

    private JTextArea initTextArea(String defaultText) {
        final JTextArea textArea = new JTextArea();
        setupRestoreText(textArea, defaultText);
        return textArea;
    }

    private JSpinner initSpinner(int defaultValue) {
        final JSpinner spinner = new JSpinner();
        setupRestore(spinner, e -> spinner.setValue(defaultValue));
        return spinner;
    }

    private void setupLineLimit() {
        limitLineMatchingByCheckBox = new JCheckBox("limitLineMatchingByCheckBox");
        limitLineMatchingByCheckBox.setToolTipText("Limit the maximum length of lines to be matched. Useful for performance optimization with very long lines.");
        limitLineMatchingByCheckBox.addActionListener(e -> {
            final boolean selected = limitLineMatchingByCheckBox.isSelected();
            maxLengthSpinner.setEnabled(selected);
            matchLinesLongerThanCheckBox.setEnabled(selected);
        });

        maxLengthSpinner = initSpinner(DEFAULT_LINE_MAX_LENGTH);
        maxLengthSpinner.setModel(new SpinnerNumberModel(DEFAULT_LINE_MAX_LENGTH, 1, Integer.MAX_VALUE, 10));
        maxLengthSpinner.setToolTipText("Maximum number of characters per line to process. Lines exceeding this limit will be handled based on the 'Match lines longer than the limit' setting.");

        JPopupMenu popup = new JPopupMenu("Defaults");
        maxLengthSpinner.setComponentPopupMenu(popup);

        final JMenuItem itm = popup.add("Restore defaults");
        itm.setMnemonic(KeyEvent.VK_R);
        itm.addActionListener(e -> {
            maxLengthSpinner.setValue(DEFAULT_LINE_MAX_LENGTH);
            maxLengthSpinner.setEnabled(true);
            limitLineMatchingByCheckBox.setSelected(DEFAULT_LIMIT_LINE_LENGTH);
            matchLinesLongerThanCheckBox.setEnabled(true);
        });
    }

    private void setupSplitLineIntoChunk() {
        matchLinesLongerThanCheckBox = new JCheckBox("matchLinesLongerThanCheckBox");
        matchLinesLongerThanCheckBox.setToolTipText("Check this to keep on matching the text of a line longer than the defined limit. Keep in mind: The text will be matched chunk by chunk, so it might miss some links.");
        JPopupMenu popup = new JPopupMenu("Defaults");
        matchLinesLongerThanCheckBox.setComponentPopupMenu(popup);

        final JMenuItem itm = popup.add("Restore defaults");
        itm.setMnemonic(KeyEvent.VK_R);
        itm.addActionListener(e -> matchLinesLongerThanCheckBox.setSelected(DEFAULT_SPLIT_ON_LIMIT));
    }

    private void setupMatchURLs() {
        searchForURLsCheckBox = initCheckBox(DEFAULT_SEARCH_URLS);
        searchForURLsCheckBox.setToolTipText("Uncheck if you do not want URLs parsed from the console.");
    }

    private void setupMatchFiles() {
        searchForFilesCheckBox = initCheckBox(DEFAULT_SEARCH_FILES);
        searchForFilesCheckBox.setToolTipText("Uncheck if you do not want file paths parsed from the console.");
        searchForClassesCheckBox = initCheckBox(DEFAULT_SEARCH_CLASSES);
        searchForClassesCheckBox.setToolTipText("Uncheck if you do not want classes parsed from the console.");

        limitResultCheckBox = initCheckBox(DEFAULT_USE_RESULT_LIMIT);
		limitResultCheckBox.setToolTipText("Limit the maximum number of search results to improve performance when multiple files match.");
		limitResultSpinner = initSpinner(DEFAULT_RESULT_LIMIT);
		limitResultSpinner.setModel(new SpinnerNumberModel(DEFAULT_RESULT_LIMIT, DEFAULT_MIN_RESULT_LIMIT, Integer.MAX_VALUE, 10));
		limitResultSpinner.setToolTipText("Maximum number of matching files to return for each hyperlink.");

		bindCheckBoxAndComponents(searchForFilesCheckBox, searchForClassesCheckBox, limitResultCheckBox);
		bindComponentToCheckBoxes(limitResultSpinner, searchForFilesCheckBox, limitResultCheckBox);
    }

	public void initMatchFiles(boolean enableFiles, boolean enableClasses) {
		setupCheckBox(searchForFilesCheckBox, enableFiles);
		setupCheckBox(searchForClassesCheckBox, enableClasses);
	}

    public void initLimitResult(boolean enabled, int value) {
        setupCheckBox(limitResultCheckBox, enabled);
        limitResultSpinner.setValue(value);
    }

    private void setupIgnorePattern() {
        ignorePatternCheckBox = initCheckBox(DEFAULT_USE_IGNORE_PATTERN);
        ignorePatternCheckBox.setToolTipText("Use regex pattern to ignore specific file paths or URLs from being matched.");
        ignorePatternTextField = initTextField(DEFAULT_IGNORE_PATTERN_TEXT);
        ignorePatternTextField.setToolTipText("Regular expression pattern. Matches will be excluded from hyperlinks. Example: node_modules|build|dist");
        ignorePatternLabel = new JLabel("* Use regex pattern");
        // 设置小号字体和浅灰色
        Font currentFont = ignorePatternLabel.getFont();
        ignorePatternLabel.setFont(currentFont.deriveFont(currentFont.getSize() - 2.0f));
        ignorePatternLabel.setForeground(JBColor.GRAY);
        ignoreStyleCheckBox = initCheckBox(DEFAULT_USE_IGNORE_STYLE);
        ignoreStyleCheckBox.setToolTipText("Use an empty hyperlink to prevent incorrect hyperlinks generated by other plugins. This feature is not supported in the Terminal. (experimental)");
        bindCheckBoxAndComponents(ignorePatternCheckBox, ignorePatternTextField);
    }

    public void initIgnorePattern(boolean useIgnorePattern, String text, boolean useIgnoreStyle) {
        setupCheckBoxAndText(ignorePatternCheckBox, useIgnorePattern, ignorePatternTextField, text);
        setupCheckBox(ignoreStyleCheckBox, useIgnoreStyle);
    }

    private void setupFixChooseTargetFileCheckBox() {
        fixChooseTargetFileCheckBox = initCheckBox(DEFAULT_FIX_CHOOSE_TARGET_FILE);
        fixChooseTargetFileCheckBox.setToolTipText("Uncheck if this fix is not compatible with your newer version of IDE.");
    }

    private void setupFileTypes() {
        fileTypesCheckBox = initCheckBox(DEFAULT_USE_FILE_TYPES);
        fileTypesCheckBox.setToolTipText("Fix some files still open in external programs, uncheck if you don't need it.");
        fileTypesTextField = initTextField(DEFAULT_FILE_TYPES);
        fileTypesTextField.setToolTipText("Comma-separated list of file extensions that should be treated as non-text files. Example: png,jpg,gif,pdf");
        bindCheckBoxAndComponents(fileTypesCheckBox, fileTypesTextField);
    }

    public void initFileTypes(boolean enabled, String text) {
        setupCheckBoxAndText(fileTypesCheckBox, enabled, fileTypesTextField, text);
    }

    private void setupResolveSymlink() {
        resolveSymlinkCheckBox = initCheckBox(DEFAULT_RESOLVE_SYMLINK);
        resolveSymlinkCheckBox.setToolTipText("Check this to resolve symlinks. (experimental)");
    }

    private void setupPreserveAnsiColors() {
        preserveAnsiColorsCheckBox = initCheckBox(DEFAULT_PRESERVE_ANSI_COLORS);
        preserveAnsiColorsCheckBox.setToolTipText("Preserve ANSI color codes and formatting in console output. Useful for modern shell prompts (oh-my-posh, starship).");
    }

    private void setupShowNotifications() {
        showNotificationsCheckBox = initCheckBox(DEFAULT_SHOW_NOTIFICATIONS);
        showNotificationsCheckBox.setToolTipText("Uncheck to disable all plugin notifications.");
    }

    private void setupUnderlineOnly() {
        underlineOnlyCheckBox = initCheckBox(DEFAULT_UNDERLINE_ONLY);
        underlineOnlyCheckBox.setToolTipText("When enabled, hyperlinks will only show underline without changing text color.");
    }

    /**
     * 设置索引管理组件
     */
    private void setupIndexManagement() {
        indexStatusLabel = new JLabel("Index Status: Not initialized");
        indexStatusLabel.setForeground(JBColor.GRAY);
        indexStatusLabel.setToolTipText("Displays the current state of the file index including total files, matched files, and ignored files.");

        indexProgressBar = new JProgressBar(0, 100);
        indexProgressBar.setStringPainted(true);
        indexProgressBar.setIndeterminate(false);
        indexProgressBar.setVisible(true);
        indexProgressBar.setValue(0);
        indexProgressBar.setString("0%");
        
        // 应用自定义双色进度条UI
        dualColorProgressBarUI = new DualColorProgressBarUI();
        indexProgressBar.setUI(dualColorProgressBarUI);

        rebuildIndexButton = new JButton("Rebuild");
        rebuildIndexButton.setToolTipText("Rebuild the file index by scanning all project files. This may take a while for large projects.");
        rebuildIndexButton.addActionListener(e -> rebuildIndex());

        clearIndexButton = new JButton("Clear");
        clearIndexButton.setToolTipText("Clear the file index. It will be automatically rebuilt when needed.");
        clearIndexButton.addActionListener(e -> clearIndex());

        // 创建索引管理服务
        indexManagementService = new IndexManagementService();
    }

    /**
     * 获取当前活动项目
     */
    private Project getCurrentProject() {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) {
            return null;
        }
        return openProjects[0];
    }

    /**
     * 更新索引状态显示
     * <p>
     * 文件缓存在后台异步构建。打开设置页时若直接读取，会在首次构建完成前
     * 得到全 0 统计。构建未完成时先显示 Building，并通过实时进度监听更新进度条，
     * 再在 {@link IndexManagementService#whenCacheReady(Project)} 完成后刷新最终统计。
     */
    public void updateIndexStatus() {
        if (disposed) {
            return;
        }
        Project project = getCurrentProject();
        if (project == null) {
            indexStatusLabel.setText("Index Status: No project opened");
            indexStatusLabel.setForeground(JBColor.GRAY);
            rebuildIndexButton.setEnabled(false);
            clearIndexButton.setEnabled(false);
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                boolean reloadRunning = indexManagementService.isReloadScheduledOrRunning(project);
                if (reloadRunning) {
                    String action = indexManagementService.isCacheBuilding(project)
                            ? ACTION_BUILDING
                            : ACTION_REBUILDING;
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (disposed || indexStatusLabel == null) {
                            return;
                        }
                        showIndexingStartUI(project.getName(), action);
                    }, ModalityState.any());
                    // 必须先挂接监听，再注册 whenComplete：若重建刚好结束，
                    // completedFuture.whenComplete 会同步执行并立刻 detach
                    attachIndexingProgressListener(project, action);
                }

                indexManagementService.whenCacheReady(project).whenComplete((ignored, error) -> {
                    detachBuildingProgressListener();
                    if (disposed) {
                        return;
                    }
                    if (error instanceof CancellationException) {
                        return;
                    }
                    if (error != null) {
                        Exception exception = error instanceof Exception
                                ? (Exception) error
                                : new RuntimeException(error);
                        ExceptionHandling.rethrowIfExceptionMustNotBeLogged(exception);
                        logger.error("Failed to wait for index: " + error.getMessage(), exception);
                        ApplicationManager.getApplication().invokeLater(() -> {
                            if (disposed || indexStatusLabel == null) {
                                return;
                            }
                            indexStatusLabel.setText("Index Status: Error - " + error.getMessage());
                            indexStatusLabel.setForeground(JBColor.RED);
                        }, ModalityState.any());
                        return;
                    }
                    refreshIndexStatusFromService(project);
                });
            } catch (Exception e) {
                ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
                logger.error("Failed to update index status: " + e.getMessage(), e);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (disposed || indexStatusLabel == null) {
                        return;
                    }
                    indexStatusLabel.setText("Index Status: Error - " + e.getMessage());
                    indexStatusLabel.setForeground(JBColor.RED);
                }, ModalityState.any());
            }
        });
    }

    /**
     * 从索引服务读取最新统计并刷新 UI
     */
    private void refreshIndexStatusFromService(Project project) {
        try {
            AwesomeLinkFilter.IndexStatistics stats = indexManagementService.getIndexStatistics(project);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (disposed || indexStatusLabel == null) {
                    return;
                }
                if (stats != null) {
                    updateIndexStatusUI(project.getName(), stats);
                    rebuildIndexButton.setEnabled(true);
                    clearIndexButton.setEnabled(true);
                } else {
                    indexStatusLabel.setText("Index Status: Service not available");
                    indexStatusLabel.setForeground(JBColor.RED);
                    rebuildIndexButton.setEnabled(false);
                    clearIndexButton.setEnabled(false);
                }
            }, ModalityState.any());
        } catch (Exception e) {
            ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
            logger.error("Failed to update index status: " + e.getMessage(), e);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (disposed || indexStatusLabel == null) {
                    return;
                }
                indexStatusLabel.setText("Index Status: Error - " + e.getMessage());
                indexStatusLabel.setForeground(JBColor.RED);
            }, ModalityState.any());
        }
    }

    /**
     * 自动初始化与手动 Rebuild 共用的开始态
     */
    private void showIndexingStartUI(String projectName, String action) {
        indexingEstimatedTotal = 1000;
        indexStatusLabel.setText(String.format("Index Status [%s]: %s...", projectName, action));
        indexStatusLabel.setForeground(new JBColor(new Color(33, 150, 243), new Color(100, 181, 246)));
        indexProgressBar.setValue(0);
        indexProgressBar.setString("Indexing...");
        if (dualColorProgressBarUI != null) {
            dualColorProgressBarUI.updatePercentages(0, 0);
        }
    }

    /**
     * 挂接实时进度监听，重建已开始后也能收到后续节流更新
     */
    private void attachIndexingProgressListener(Project project, String action) {
        detachBuildingProgressListener();
        indexingEstimatedTotal = 1000;
        buildingProgressProject = project;
        buildingProgressListener = progress -> scheduleIndexingProgressUi(project.getName(), action, progress);
        indexManagementService.addReloadProgressListener(project, buildingProgressListener);
    }

    /**
     * 取消实时进度监听，避免设置页关闭后仍刷新 UI
     */
    private void detachBuildingProgressListener() {
        if (buildingProgressListener != null && buildingProgressProject != null) {
            indexManagementService.removeReloadProgressListener(buildingProgressProject, buildingProgressListener);
        }
        buildingProgressListener = null;
        buildingProgressProject = null;
        pendingIndexingProgress = null;
        pendingIndexingAction = null;
    }

    /**
     * 合并高频进度回调，避免每个 50ms 节流点都往 EDT 塞事件。
     * 自动初始化与手动 Rebuild 共用。
     */
    private void scheduleIndexingProgressUi(String projectName, String action, AwesomeLinkFilter.ReloadProgress progress) {
        pendingIndexingProgress = progress;
        pendingIndexingAction = action;
        if (!indexingProgressUiScheduled.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            indexingProgressUiScheduled.set(false);
            AwesomeLinkFilter.ReloadProgress latest = pendingIndexingProgress;
            String latestAction = pendingIndexingAction;
            if (disposed || latest == null || latestAction == null
                    || indexStatusLabel == null || indexProgressBar == null) {
                return;
            }
            applyIndexingProgressUI(projectName, latestAction, latest);
        }, ModalityState.any());
    }

    /**
     * 用正在构建的临时缓存进度更新状态文本和进度条（自动加载与手动 Rebuild 共用）
     */
    private void applyIndexingProgressUI(String projectName, String action, AwesomeLinkFilter.ReloadProgress progress) {
        int processed = progress.getProcessedCount();
        if (processed > indexingEstimatedTotal) {
            indexingEstimatedTotal = processed + 100;
        }
        int barValue = processed <= 0
                ? 0
                : Math.min(95, (processed * 95) / Math.max(indexingEstimatedTotal, 1));

        String statusText = String.format("Index Status [%s]: %s... %d files processed",
                projectName, action, processed);
        if (progress.getIgnoredCount() > 0) {
            statusText += String.format(" (Matched: %d, Ignored: %d)",
                    progress.getIndexedCount(), progress.getIgnoredCount());
        }
        indexStatusLabel.setText(statusText);
        indexStatusLabel.setForeground(new JBColor(new Color(33, 150, 243), new Color(100, 181, 246)));

        indexProgressBar.setValue(barValue);
        indexProgressBar.setString(processed <= 0 ? "Indexing..." : processed + " files");

        int denom = Math.max(processed, 1);
        int matchedPct = (progress.getIndexedCount() * barValue) / denom;
        int ignoredPct = (progress.getIgnoredCount() * barValue) / denom;
        if (dualColorProgressBarUI != null) {
            dualColorProgressBarUI.updatePercentages(matchedPct, ignoredPct);
        }
    }

    /**
     * Last rebuild 等诊断信息放到 tooltip，避免单行 JLabel 把 Settings 对话框撑宽。
     */
    private String buildIndexStatusToolTip(String statusText, AwesomeLinkFilter.IndexStatistics stats) {
        if (stats.getLastRebuildTime() <= 0) {
            return statusText;
        }
        StringBuilder tooltip = new StringBuilder(statusText);
        long elapsed = System.currentTimeMillis() - stats.getLastRebuildTime();
        tooltip.append(String.format(" - Last rebuild: %s ago", indexManagementService.formatDuration(elapsed)));
        if (stats.getLastRebuildDuration() > 0) {
            tooltip.append(String.format(" (took %s)", indexManagementService.formatDuration(stats.getLastRebuildDuration())));
        }
        return tooltip.toString();
    }

    /**
     * 更新索引状态 UI
     */
    private void updateIndexStatusUI(String projectName, AwesomeLinkFilter.IndexStatistics stats) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Index Status [%s]: %d files indexed (%d filenames, %d basenames)",
                projectName, stats.getTotalCachedFiles(), stats.getFileCacheSize(), stats.getFileBaseCacheSize()));

        if (stats.hasIgnoreStatistics()) {
            sb.append(String.format(" - Matched: %d, Ignored: %d",
                    stats.getMatchedFiles(), stats.getIgnoredFiles()));
        }

        String statusText = sb.toString();
        indexStatusLabel.setText(statusText);
        indexStatusLabel.setToolTipText(buildIndexStatusToolTip(statusText, stats));
        indexStatusLabel.setForeground(new JBColor(new Color(76, 175, 80), new Color(129, 199, 132)));

        // 更新进度条
        updateProgressBarFromStats(stats);
    }

    /**
     * 重建索引
     */
    private void rebuildIndex() {
        Project project = getCurrentProject();
        if (project == null) {
            return;
        }

        indexManagementService.rebuildIndex(project, mainPanel, new IndexManagementService.ProgressCallback() {
            @Override
            public void onStart(String operationType) {
                // 检查 UI 组件是否已销毁
                if (rebuildIndexButton == null || clearIndexButton == null || indexStatusLabel == null || indexProgressBar == null) {
                    return;
                }
                rebuildIndexButton.setEnabled(false);
                clearIndexButton.setEnabled(false);
                rebuildIndexButton.setText("Rebuilding...");
                showIndexingStartUI(project.getName(), ACTION_REBUILDING);
            }

            @Override
            public void onProgress(AwesomeLinkFilter.ReloadProgress progress) {
                if (disposed || indexStatusLabel == null || indexProgressBar == null) {
                    return;
                }
                scheduleIndexingProgressUi(project.getName(), ACTION_REBUILDING, progress);
            }

            @Override
            public void onComplete(String operationType, AwesomeLinkFilter.IndexStatistics stats, long duration) {
                // 检查 UI 组件是否已销毁
                if (rebuildIndexButton == null || clearIndexButton == null || indexProgressBar == null) {
                    logger.info("Rebuild completed in background (UI already disposed)");
                    return;
                }
                rebuildIndexButton.setEnabled(true);
                clearIndexButton.setEnabled(true);
                rebuildIndexButton.setText("Rebuild");
                indexProgressBar.setValue(100);
                updateIndexStatus();
            }

            @Override
            public void onError(String operationType, String error) {
                // 检查 UI 组件是否已销毁
                if (rebuildIndexButton == null || clearIndexButton == null || indexStatusLabel == null || indexProgressBar == null) {
                    logger.error("Rebuild failed in background (UI already disposed): " + error);
                    return;
                }
                rebuildIndexButton.setEnabled(true);
                clearIndexButton.setEnabled(true);
                rebuildIndexButton.setText("Rebuild");
                indexStatusLabel.setText("Index Status: Error");
                indexStatusLabel.setForeground(JBColor.RED);
                indexProgressBar.setValue(0);
                indexProgressBar.setString("0%");
                if (dualColorProgressBarUI != null) {
                    dualColorProgressBarUI.updatePercentages(0, 0);
                }
            }
        });
    }

    /**
     * 清除索引
     */
    private void clearIndex() {
        Project project = getCurrentProject();
        if (project == null) {
            return;
        }

        // 使用 IntelliJ Messages（DialogWrapper）而非 JOptionPane：
        // JOptionPane 走 JVM 默认 Locale 与原生窗口装饰，按钮语言/标题栏主题不会跟随 IDE
        int result = Messages.showYesNoDialog(
                mainPanel,
                "Are you sure you want to clear the file index?\nIt will be automatically rebuilt when needed.",
                "Confirm Clear",
                Messages.getWarningIcon());
        if (result != Messages.YES) {
            return;
        }

        indexManagementService.clearIndex(project, mainPanel, new IndexManagementService.ProgressCallback() {
            @Override
            public void onStart(String operationType) {
                // 检查 UI 组件是否已销毁
                if (rebuildIndexButton == null || clearIndexButton == null || indexStatusLabel == null || indexProgressBar == null) {
                    return;
                }
                rebuildIndexButton.setEnabled(false);
                clearIndexButton.setEnabled(false);
                clearIndexButton.setText("Clearing...");
                indexStatusLabel.setText(String.format("Clearing index [%s]...", project.getName()));
                indexStatusLabel.setForeground(new JBColor(new Color(244, 67, 54), new Color(239, 83, 80)));
            }

            @Override
            public void onComplete(String operationType, AwesomeLinkFilter.IndexStatistics stats, long duration) {
                // 检查 UI 组件是否已销毁
                if (rebuildIndexButton == null || clearIndexButton == null || indexProgressBar == null) {
                    logger.info("Clear completed in background (UI already disposed)");
                    return;
                }
                rebuildIndexButton.setEnabled(true);
                clearIndexButton.setEnabled(true);
                clearIndexButton.setText("Clear");
                // 清除完成后，索引为空，进度条应该显示 0%
                indexProgressBar.setValue(0);
                indexProgressBar.setString("0%");
                if (dualColorProgressBarUI != null) {
                    dualColorProgressBarUI.updatePercentages(0, 0);
                }
                updateIndexStatus();
            }

            @Override
            public void onError(String operationType, String error) {
                // 检查 UI 组件是否已销毁
                if (rebuildIndexButton == null || clearIndexButton == null || indexStatusLabel == null || indexProgressBar == null) {
                    logger.error("Clear failed in background (UI already disposed): " + error);
                    return;
                }
                rebuildIndexButton.setEnabled(true);
                clearIndexButton.setEnabled(true);
                clearIndexButton.setText("Clear");
                indexStatusLabel.setText("Index Status: Error");
                indexStatusLabel.setForeground(JBColor.RED);
                indexProgressBar.setValue(0);
                indexProgressBar.setString("0%");
                if (dualColorProgressBarUI != null) {
                    dualColorProgressBarUI.updatePercentages(0, 0);
                }
            }
        });
    }

    /**
     * 根据进度百分比更新进度条颜色
     */
    private void updateProgressBarColor(int percentage) {
        Color color;
        if (percentage == 0) {
            color = new JBColor(new Color(158, 158, 158), new Color(97, 97, 97));
        } else if (percentage == 100) {
            color = new JBColor(new Color(76, 175, 80), new Color(129, 199, 132));
        } else {
            color = new JBColor(new Color(255, 193, 7), new Color(255, 235, 59));
        }
        indexProgressBar.setForeground(color);
    }

    /**
     * 更新进度条颜色（双色模式，支持忽略文件统计）
     */
    private void updateProgressBarWithIgnoreStats(int totalFiles, int matchedFiles, int ignoredFiles) {
        if (totalFiles == 0) {
            indexProgressBar.setForeground(new JBColor(new Color(158, 158, 158), new Color(97, 97, 97)));
            return;
        }

        int matchedPercentage = (matchedFiles * 100) / totalFiles;
        int ignoredPercentage = (ignoredFiles * 100) / totalFiles;
        int totalPercentage = matchedPercentage + ignoredPercentage;

        if (ignoredFiles == 0) {
            updateProgressBarColor(totalPercentage);
            return;
        }

        if (totalPercentage == 100) {
            indexProgressBar.setForeground(new JBColor(new Color(76, 175, 80), new Color(129, 199, 132)));
        } else if (totalPercentage > 0) {
            indexProgressBar.setForeground(new JBColor(new Color(76, 175, 80), new Color(129, 199, 132)));
        } else {
            indexProgressBar.setForeground(new JBColor(new Color(158, 158, 158), new Color(97, 97, 97)));
        }
    }

    /**
     * 根据索引统计信息更新进度条
     */
    private void updateProgressBarFromStats(AwesomeLinkFilter.IndexStatistics stats) {
        if (stats == null) {
            indexProgressBar.setValue(0);
            indexProgressBar.setString("0%");
            if (dualColorProgressBarUI != null) {
                dualColorProgressBarUI.updatePercentages(0, 0);
            }
            return;
        }

        int scannedFiles = stats.getScannedFiles();
        if (scannedFiles == 0) {
            indexProgressBar.setValue(0);
            indexProgressBar.setString("0%");
            if (dualColorProgressBarUI != null) {
                dualColorProgressBarUI.updatePercentages(0, 0);
            }
        } else {
            indexProgressBar.setValue(100);

            if (stats.hasIgnoreStatistics()) {
                int matchedPercentage = stats.getMatchedPercentage();
                int ignoredPercentage = stats.getIgnoredPercentage();

                // 进度条文本仅显示匹配文件占比，分母为扫描总数（匹配 + 忽略）
                indexProgressBar.setString(String.format("%d%%", matchedPercentage));
                if (dualColorProgressBarUI != null) {
                    dualColorProgressBarUI.updatePercentages(matchedPercentage, ignoredPercentage);
                }
            } else {
                // 无忽略文件时，显示100%绿色
                indexProgressBar.setString("100%");
                if (dualColorProgressBarUI != null) {
                    dualColorProgressBarUI.updatePercentages(100, 0);
                }
            }
        }
    }


    /**
     * 清理资源（由 AwesomeConsoleConfig.disposeUIResources() 调用）
     * <p>
     * 索引操作会在后台继续完成，不会被中断。此处仅标记表单已销毁，
     * 避免关闭设置页后仍刷新已失效的 UI。
     */
    public void dispose() {
        disposed = true;
        detachBuildingProgressListener();
    }
}
