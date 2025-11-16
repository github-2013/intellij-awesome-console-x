package awesome.console.config;

import awesome.console.AwesomeLinkFilter;
import awesome.console.AwesomeLinkFilterProvider;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.JTextComponent;
import javax.swing.text.NumberFormatter;
import javax.swing.text.StyleContext;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.text.DecimalFormat;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("SameParameterValue")
public class AwesomeConsoleConfigForm implements AwesomeConsoleDefaults {
    public JPanel mainPanel;
    public JCheckBox debugModeCheckBox;
    public JCheckBox limitLineMatchingByCheckBox;
    public JFormattedTextField maxLengthTextField;
    public JCheckBox matchLinesLongerThanCheckBox;
    public JCheckBox searchForURLsCheckBox;
    public JCheckBox searchForFilesCheckBox;
    public JCheckBox searchForClassesCheckBox;
    public JCheckBox limitResultCheckBox;
    public JSpinner limitResultSpinner;
    public JCheckBox filePatternCheckBox;
    public JTextArea filePatternTextArea;
    public JLabel filePatternLabel;
    public JCheckBox ignorePatternCheckBox;
    public JTextField ignorePatternTextField;
    public JCheckBox ignoreStyleCheckBox;
    public JCheckBox fixChooseTargetFileCheckBox;
    public JCheckBox fileTypesCheckBox;
    public JTextField fileTypesTextField;
    public JCheckBox resolveSymlinkCheckBox;
    public JCheckBox preserveAnsiColorsCheckBox;

    // 索引管理相关字段
    public JLabel indexStatusLabel;
    public JProgressBar indexProgressBar;
    public JButton rebuildIndexButton;
    public JButton clearIndexButton;

    private Map<JCheckBox, Set<JComponent>> bindMap;
    private Map<JComponent, Set<JCheckBox>> bindMap2;

    // 防抖机制
    private long lastRebuildTime = 0;
    private static final long REBUILD_INTERVAL_MS = 5000; // 5秒间隔

    // 操作互斥标志
    private volatile boolean isOperationInProgress = false;

    // 通知组
    private static final NotificationGroup NOTIFICATION_GROUP =
            NotificationGroupManager.getInstance().getNotificationGroup("Awesome Console X");

    private void createUIComponents() {
        bindMap = new HashMap<>();
        bindMap2 = new HashMap<>();
        setupDebugMode();
        setupLineLimit();
        setupSplitLineIntoChunk();
        setupMatchURLs();
        setupMatchFiles();
        setupIgnorePattern();
        setupFixChooseTargetFileCheckBox();
        setupFileTypes();
        setupResolveSymlink();
        setupPreserveAnsiColors();
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

    private void setupDebugMode() {
        debugModeCheckBox = initCheckBox(DEFAULT_DEBUG_MODE);
    }

    private void setupLineLimit() {
        limitLineMatchingByCheckBox = new JCheckBox("limitLineMatchingByCheckBox");
        limitLineMatchingByCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final boolean selected = limitLineMatchingByCheckBox.isSelected();
                maxLengthTextField.setEnabled(selected);
                maxLengthTextField.setEditable(selected);
                matchLinesLongerThanCheckBox.setEnabled(selected);
            }
        });

        final DecimalFormat decimalFormat = new DecimalFormat("#####");
        final NumberFormatter formatter = new NumberFormatter(decimalFormat);
        formatter.setMinimum(0);
        formatter.setValueClass(Integer.class);
        maxLengthTextField = new JFormattedTextField(formatter);
        maxLengthTextField.setColumns(5);

        JPopupMenu popup = new JPopupMenu("Defaults");
        maxLengthTextField.setComponentPopupMenu(popup);

        final JMenuItem itm = popup.add("Restore defaults");
        itm.setMnemonic(KeyEvent.VK_R);
        itm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                maxLengthTextField.setText(String.valueOf(DEFAULT_LINE_MAX_LENGTH));
                maxLengthTextField.setEnabled(true);
                maxLengthTextField.setEditable(true);
                limitLineMatchingByCheckBox.setSelected(DEFAULT_LIMIT_LINE_LENGTH);
                matchLinesLongerThanCheckBox.setEnabled(true);
            }
        });
    }

    private void setupSplitLineIntoChunk() {
        matchLinesLongerThanCheckBox = new JCheckBox("matchLinesLongerThanCheckBox");
        matchLinesLongerThanCheckBox.setToolTipText("Check this to keep on matching the text of a line longer than the defined limit. Keep in mind: The text will be matched chunk by chunk, so it might miss some links.");
        JPopupMenu popup = new JPopupMenu("Defaults");
        matchLinesLongerThanCheckBox.setComponentPopupMenu(popup);

        final JMenuItem itm = popup.add("Restore defaults");
        itm.setMnemonic(KeyEvent.VK_R);
        itm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                matchLinesLongerThanCheckBox.setSelected(DEFAULT_SPLIT_ON_LIMIT);
            }
        });
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
        limitResultSpinner = initSpinner(DEFAULT_RESULT_LIMIT);
        limitResultSpinner.setModel(new SpinnerNumberModel(DEFAULT_RESULT_LIMIT, DEFAULT_MIN_RESULT_LIMIT, Integer.MAX_VALUE, 10));

        filePatternCheckBox = initCheckBox(DEFAULT_USE_FILE_PATTERN);
        filePatternCheckBox.setToolTipText("Check this to custom File Pattern. (experimental)");
        filePatternTextArea = initTextArea(DEFAULT_FILE_PATTERN_TEXT);
        filePatternTextArea.setLineWrap(true);
        final String groupExample = FILE_PATTERN_REQUIRED_GROUPS[0];
        filePatternLabel = new JLabel(String.format(
                "      * Required regex group names: [%s], where %s,%s1...%s%d all correspond to the group %s.",
                StringUtil.join(FILE_PATTERN_REQUIRED_GROUPS, ", "),
                groupExample, groupExample, groupExample, DEFAULT_GROUP_RETRIES, groupExample
        ));

        bindCheckBoxAndComponents(searchForFilesCheckBox, searchForClassesCheckBox, limitResultCheckBox, filePatternCheckBox);
        bindComponentToCheckBoxes(limitResultSpinner, searchForFilesCheckBox, limitResultCheckBox);
        bindComponentToCheckBoxes(filePatternTextArea, searchForFilesCheckBox, filePatternCheckBox);
    }

    public void initMatchFiles(boolean enableFiles, boolean enableClasses, boolean enableFilePattern, String filePattern) {
        setupCheckBox(searchForFilesCheckBox, enableFiles);
        setupCheckBox(searchForClassesCheckBox, enableClasses);
        setupCheckBoxAndText(filePatternCheckBox, enableFilePattern, filePatternTextArea, filePattern);
    }

    public void initLimitResult(boolean enabled, int value) {
        setupCheckBox(limitResultCheckBox, enabled);
        limitResultSpinner.setValue(value);
    }

    private void setupIgnorePattern() {
        ignorePatternCheckBox = initCheckBox(DEFAULT_USE_IGNORE_PATTERN);
        ignorePatternTextField = initTextField(DEFAULT_IGNORE_PATTERN_TEXT);
        ignoreStyleCheckBox = initCheckBox(DEFAULT_USE_IGNORE_STYLE);
        ignoreStyleCheckBox.setToolTipText("Use an empty hyperlink to prevent incorrect hyperlinks generated by other plugins. This feature is not supported in the Terminal. (experimental)");
        bindCheckBoxAndComponents(ignorePatternCheckBox, ignorePatternTextField, ignoreStyleCheckBox);
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
        fileTypesTextField.setToolTipText("Use , to separate types.");
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

    /**
     * 设置索引管理组件
     */
    private void setupIndexManagement() {
        indexStatusLabel = new JLabel("Index Status: Not initialized");
        indexStatusLabel.setForeground(JBColor.GRAY);

        indexProgressBar = new JProgressBar(0, 100);
        indexProgressBar.setStringPainted(true);
        indexProgressBar.setVisible(true);
        indexProgressBar.setValue(0);
        indexProgressBar.setString("0%");
        // 设置默认颜色
        updateProgressBarColor(0);

        rebuildIndexButton = new JButton("Rebuild Index");
        rebuildIndexButton.addActionListener(e -> rebuildIndex());

        clearIndexButton = new JButton("Clear Index");
        clearIndexButton.addActionListener(e -> clearIndex());
    }

    /**
     * 根据进度百分比更新进度条颜色
     * @param percentage 进度百分比 (0-100)
     */
    /**
     * 更新进度条颜色（单色模式）
     */
    /**
     * 更新进度条颜色（单色模式）
     */
    private void updateProgressBarColor(int percentage) {
        Color color;
        if (percentage == 0) {
            // 0% - 默认颜色（灰色）
            color = new Color(158, 158, 158);
        } else if (percentage == 100) {
            // 100% - 深绿色
            color = new Color(76, 175, 80);
        } else {
            // 部分完成 - 黄色
            color = new Color(255, 193, 7);
        }

        // 设置进度条颜色
        indexProgressBar.setForeground(color);
    }

    /**
     * 更新进度条颜色（双色模式，支持忽略文件统计）
     *
     * @param totalFiles   总文件数
     * @param matchedFiles 匹配的文件数
     * @param ignoredFiles 忽略的文件数
     */
    private void updateProgressBarWithIgnoreStats(int totalFiles, int matchedFiles, int ignoredFiles) {
        if (totalFiles == 0) {
            // 没有文件时使用默认颜色
            indexProgressBar.setForeground(new Color(158, 158, 158));
            return;
        }

        // 计算百分比
        int matchedPercentage = (matchedFiles * 100) / totalFiles;
        int ignoredPercentage = (ignoredFiles * 100) / totalFiles;
        int totalPercentage = matchedPercentage + ignoredPercentage;

        // 如果没有忽略文件，使用单色模式
        if (ignoredFiles == 0) {
            updateProgressBarColor(totalPercentage);
            return;
        }

        // 有忽略文件时，使用绿色作为主色（匹配的文件）
        // 黄色部分将通过进度条的文本显示来体现
        if (totalPercentage == 100) {
            // 完成时使用绿色
            indexProgressBar.setForeground(new Color(76, 175, 80));
        } else if (totalPercentage > 0) {
            // 进行中使用绿色（主要表示匹配的文件）
            indexProgressBar.setForeground(new Color(76, 175, 80));
        } else {
            // 初始状态
            indexProgressBar.setForeground(new Color(158, 158, 158));
        }
    }

    /**
     * 根据索引统计信息更新进度条
     *
     * @param stats 索引统计信息
     */
    private void updateProgressBarFromStats(AwesomeLinkFilter.IndexStatistics stats) {
        if (stats == null) {
            indexProgressBar.setValue(0);
            indexProgressBar.setString("0%");
            updateProgressBarColor(0);
            return;
        }

        int totalFiles = stats.getTotalFiles();
        int percentage;

        if (totalFiles == 0) {
            percentage = 0;
            indexProgressBar.setValue(percentage);
            indexProgressBar.setString(percentage + "%");
            updateProgressBarColor(percentage);
        } else {
            // 假设项目有文件就认为是100%索引完成
            percentage = 100;
            indexProgressBar.setValue(percentage);

            // 如果有忽略文件统计，显示详细信息
            if (stats.hasIgnoreStatistics()) {
                int matchedFiles = stats.getMatchedFiles();
                int ignoredFiles = stats.getIgnoredFiles();
                int matchedPercentage = (matchedFiles * 100) / totalFiles;
                int ignoredPercentage = (ignoredFiles * 100) / totalFiles;

                // 显示匹配和忽略的百分比
                indexProgressBar.setString(String.format("100%% (✓%d%% ⚠%d%%)",
                        matchedPercentage, ignoredPercentage));
                updateProgressBarWithIgnoreStats(totalFiles, matchedFiles, ignoredFiles);
            } else {
                indexProgressBar.setString(percentage + "%");
                updateProgressBarColor(percentage);
            }
        }
    }

    /**
     * 获取当前活动项目
     */
    private Project getCurrentProject() {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) {
            return null;
        }
        return openProjects[0]; // 简化实现：返回第一个项目
    }

    /**
     * 更新索引状态显示
     */
    public void updateIndexStatus() {
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
                AwesomeLinkFilter filter = AwesomeLinkFilterProvider.getFilter(project);
                if (filter != null) {
                    AwesomeLinkFilter.IndexStatistics stats = filter.getIndexStatistics();

                    ApplicationManager.getApplication().invokeLater(() -> {
                        String statusText = formatIndexStatus(project.getName(), stats);
                        indexStatusLabel.setText(statusText);
                        indexStatusLabel.setForeground(new Color(76, 175, 80)); // 绿色

                        // 更新进度条
                        updateProgressBarFromStats(stats);

                        rebuildIndexButton.setEnabled(true);
                        clearIndexButton.setEnabled(true);
                    });
                } else {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        indexStatusLabel.setText("Index Status: Service not available");
                        indexStatusLabel.setForeground(JBColor.RED);
                        rebuildIndexButton.setEnabled(false);
                        clearIndexButton.setEnabled(false);
                    });
                }
            } catch (Exception e) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    indexStatusLabel.setText("Index Status: Error - " + e.getMessage());
                    indexStatusLabel.setForeground(JBColor.RED);
                });
            }
        });
    }

    /**
     * 格式化索引状态文本
     */
    private String formatIndexStatus(String projectName, AwesomeLinkFilter.IndexStatistics stats) {
        StringBuilder sb = new StringBuilder();

        // 基本信息
        sb.append(String.format("Index Status [%s]: %d files indexed (%d filenames, %d basenames)",
                projectName, stats.getTotalFiles(), stats.getFileCacheSize(), stats.getFileBaseCacheSize()));

        // 忽略文件统计
        if (stats.hasIgnoreStatistics()) {
            int matchedFiles = stats.getMatchedFiles();
            int ignoredFiles = stats.getIgnoredFiles();
            sb.append(String.format(" - Matched: %d, Ignored: %d", matchedFiles, ignoredFiles));
        }

        // 重建时间信息
        if (stats.getLastRebuildTime() > 0) {
            long elapsed = System.currentTimeMillis() - stats.getLastRebuildTime();
            sb.append(String.format(" - Last rebuild: %s ago", formatDuration(elapsed)));

            if (stats.getLastRebuildDuration() > 0) {
                sb.append(String.format(" (took %s)", formatDuration(stats.getLastRebuildDuration())));
            }
        }

        return sb.toString();
    }

    /**
     * 格式化时间间隔
     */
    private String formatDuration(long millis) {
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
     * 重建索引
     */
    private void rebuildIndex() {
        Project project = getCurrentProject();
        if (project == null) {
            showNotification(null, "No project is currently opened.", NotificationType.ERROR);
            return;
        }

        // 检查是否有操作正在进行
        if (isOperationInProgress) {
            showNotification(project, "Another index operation is already in progress.", NotificationType.WARNING);
            return;
        }

        // 防抖机制：检查重建间隔
        long now = System.currentTimeMillis();
        if (now - lastRebuildTime < REBUILD_INTERVAL_MS) {
            long remaining = (REBUILD_INTERVAL_MS - (now - lastRebuildTime)) / 1000;
            showNotification(project,
                    String.format("Please wait %d seconds before rebuilding again.", remaining),
                    NotificationType.WARNING);
            return;
        }
        lastRebuildTime = now;

        // 设置操作状态
        isOperationInProgress = true;
        rebuildIndexButton.setEnabled(false);
        clearIndexButton.setEnabled(false);
        rebuildIndexButton.setText("Rebuilding...");
        indexStatusLabel.setText(String.format("Rebuilding index [%s]...", project.getName()));
        indexStatusLabel.setForeground(new Color(33, 150, 243)); // 蓝色

        // 重置并初始化进度条
        indexProgressBar.setValue(0);
        indexProgressBar.setIndeterminate(false);
        indexProgressBar.setString("0%");
        updateProgressBarColor(0);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                AwesomeLinkFilter filter = AwesomeLinkFilterProvider.getFilter(project);
                if (filter != null) {
                    long startTime = System.currentTimeMillis();

                    try {
                        AtomicInteger callbackCount = new AtomicInteger(0);
                        AtomicInteger totalEstimatedFiles = new AtomicInteger(1000); // 预估文件数，用于进度条

                        filter.manualRebuild(count -> {
                            try {
                                callbackCount.incrementAndGet();
                                
                                // 调试日志
                                System.out.println("[AwesomeConsole] Progress callback triggered: count=" + count);

                                // 动态调整预估总数
                                if (count > totalEstimatedFiles.get()) {
                                    totalEstimatedFiles.set(count + 100);
                                }

                                ApplicationManager.getApplication().invokeLater(() -> {
                                    try {
                                        // 调试日志
                                        System.out.println("[AwesomeConsole] UI update triggered: count=" + count);
                                        
                                        // 获取实时统计数据
                                        AwesomeLinkFilter.IndexStatistics currentStats = filter.getIndexStatistics();
                                        int ignoredFiles = currentStats.getIgnoredFiles();
                                        int matchedFiles = Math.max(0, count - ignoredFiles);
                                        int totalFiles = currentStats.getTotalFiles();

                                        // 判断是否是最后一次回调（文件数不再增长）
                                        boolean isLastCallback = (totalFiles > 0 && count == totalFiles);

                                        // 计算进度百分比
                                        int progress;
                                        if (isLastCallback) {
                                            // 最后一次回调，直接设置为100%
                                            progress = 100;
                                        } else {
                                            // 非最后一次回调，限制在95%以内
                                            progress = Math.min(95, (count * 95) / Math.max(totalEstimatedFiles.get(), 1));
                                        }

                                        // 计算已用时间（用于完成时显示）
                                        long elapsed = System.currentTimeMillis() - startTime;

                                        // 更新状态标签
                                        String statusText;
                                        if (isLastCallback) {
                                            statusText = String.format("Rebuild completed [%s]: %d files indexed in %s",
                                                    project.getName(), count, formatDuration(elapsed));
                                        } else {
                                            statusText = String.format("Rebuilding index [%s]... %d files processed",
                                                    project.getName(), count);
                                        }
                                        if (ignoredFiles > 0) {
                                            statusText += String.format(" (Matched: %d, Ignored: %d)", matchedFiles, ignoredFiles);
                                        }
                                        indexStatusLabel.setText(statusText);

                                        // 更新进度条
                                        indexProgressBar.setValue(progress);
                                        
                                        // 强制刷新进度条显示
                                        indexProgressBar.repaint();

                                        // 设置进度条文本
                                        if (ignoredFiles > 0 && totalFiles > 0) {
                                            int matchedPercentage = (matchedFiles * 100) / totalFiles;
                                            int ignoredPercentage = (ignoredFiles * 100) / totalFiles;
                                            indexProgressBar.setString(String.format("%d%% (✓%d%% ⚠%d%%)",
                                                    progress, matchedPercentage, ignoredPercentage));
                                            updateProgressBarWithIgnoreStats(totalFiles, matchedFiles, ignoredFiles);
                                        } else {
                                            indexProgressBar.setString(progress + "%");
                                            updateProgressBarColor(progress);
                                        }
                                        
                                        // 强制刷新进度条
                                        indexProgressBar.repaint();

                                        // 如果是最后一次回调，恢复按钮状态并显示通知
                                        if (isLastCallback) {
                                            rebuildIndexButton.setText("Rebuild Index");
                                            rebuildIndexButton.setEnabled(true);
                                            clearIndexButton.setEnabled(true);
                                            isOperationInProgress = false;

                                            // 更新索引状态
                                            updateIndexStatus();

                                            // 显示通知
                                            String notificationMessage = String.format("File index rebuilt successfully! %d files indexed in %s",
                                                    totalFiles, formatDuration(currentStats.getLastRebuildDuration()));
                                            if (ignoredFiles > 0) {
                                                notificationMessage += String.format(" (Matched: %d, Ignored: %d)",
                                                        matchedFiles, ignoredFiles);
                                            }
                                            showNotification(project, notificationMessage, NotificationType.INFORMATION);
                                        }
                                    } catch (Exception e) {
                                        // 忽略UI更新异常，避免影响重建过程
                                    }
                                });
                            } catch (Exception e) {
                                // 忽略进度回调异常，避免影响重建过程
                            }
                        });

                        // manualRebuild 是同步执行的，执行到这里表示重建已完成
                        // 如果回调没有被触发（空项目等情况），手动触发完成逻辑
                        if (callbackCount.get() == 0) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                try {
                                    indexProgressBar.setValue(100);
                                    indexProgressBar.setString("100%");
                                    updateProgressBarColor(100);
                                    indexStatusLabel.setText("Rebuild completed [" + project.getName() + "]: 0 files");

                                    rebuildIndexButton.setText("Rebuild Index");
                                    rebuildIndexButton.setEnabled(true);
                                    clearIndexButton.setEnabled(true);
                                    isOperationInProgress = false;

                                    updateIndexStatus();
                                    showNotification(project, "File index rebuilt successfully! 0 files indexed", NotificationType.INFORMATION);
                                } catch (Exception e) {
                                    showIndexError("Failed to complete rebuild: " + e.getMessage());
                                }
                            });
                        }
                    } catch (Exception e) {
                        // 重建过程中出现异常
                        showIndexError("Failed to rebuild index: " + e.getMessage());
                    }
                } else {
                    showIndexError("Index service not available");
                }
            } catch (Exception e) {
                showIndexError("Failed to rebuild index: " + e.getMessage());
            }
        });
    }

    /**
     * 清除索引
     */
    private void clearIndex() {
        Project project = getCurrentProject();
        if (project == null) {
            showNotification(null, "No project is currently opened.", NotificationType.ERROR);
            return;
        }

        // 检查是否有操作正在进行
        if (isOperationInProgress) {
            showNotification(project, "Another index operation is already in progress.", NotificationType.WARNING);
            return;
        }

        int result = JOptionPane.showConfirmDialog(mainPanel,
                "Are you sure you want to clear the file index?\nIt will be automatically rebuilt when needed.",
                "Confirm Clear", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        // 设置操作状态
        isOperationInProgress = true;
        clearIndexButton.setEnabled(false);
        rebuildIndexButton.setEnabled(false);
        clearIndexButton.setText("Clearing...");
        indexStatusLabel.setText(String.format("Clearing index [%s]...", project.getName()));
        indexStatusLabel.setForeground(new Color(244, 67, 54)); // 红色

        // 重置进度条为初始状态
        indexProgressBar.setValue(0);
        indexProgressBar.setIndeterminate(false);
        indexProgressBar.setString("0%");
        updateProgressBarColor(0);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                AwesomeLinkFilter filter = AwesomeLinkFilterProvider.getFilter(project);
                if (filter != null) {
                    try {
                        // 获取当前缓存信息
                        int totalFiles = filter.getTotalCachedFiles();
                        int fileCacheSize = filter.getFileCacheSize();
                        int fileBaseCacheSize = filter.getFileBaseCacheSize();

                        long startTime = System.currentTimeMillis();

                        // 显示开始清除
                        ApplicationManager.getApplication().invokeLater(() -> {
                            indexStatusLabel.setText(String.format(
                                    "Clearing index [%s]... %d files (%d filenames, %d basenames)",
                                    project.getName(), totalFiles, fileCacheSize, fileBaseCacheSize
                            ));
                            indexProgressBar.setValue(50);
                            indexProgressBar.setString("50%");
                            updateProgressBarColor(50);
                        });

                        // 执行清除操作（这是瞬时的）
                        filter.clearCache();

                        // 计算耗时
                        long totalTime = System.currentTimeMillis() - startTime;

                        // 更新完成状态
                        ApplicationManager.getApplication().invokeLater(() -> {
                            try {
                                // 立即将进度条设置为100%
                                indexProgressBar.setValue(100);
                                indexProgressBar.setString("100%");
                                updateProgressBarColor(100);

                                // 立即恢复按钮状态
                                clearIndexButton.setText("Clear Index");
                                clearIndexButton.setEnabled(true);
                                rebuildIndexButton.setEnabled(true);
                                isOperationInProgress = false;

                                // 立即更新索引状态
                                updateIndexStatus();

                                // 使用通知栏提示
                                showNotification(project,
                                        String.format("File index cleared successfully! Cleared %d files (%d filenames, %d basenames) in %s. Index will be rebuilt automatically when needed.",
                                                totalFiles, fileCacheSize, fileBaseCacheSize, formatDuration(totalTime)),
                                        NotificationType.INFORMATION);
                            } catch (Exception e) {
                                // 如果完成时出现异常，仍然要恢复按钮状态
                                showIndexError("Failed to complete clear operation: " + e.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        // 清除过程中出现异常
                        showIndexError("Failed to clear index: " + e.getMessage());
                    }
                } else {
                    showIndexError("Index service not available");
                }
            } catch (Exception e) {
                showIndexError("Failed to clear index: " + e.getMessage());
            }
        });
    }

    /**
     * 显示索引错误
     */
    private void showIndexError(String message) {
        Project project = getCurrentProject();
        ApplicationManager.getApplication().invokeLater(() -> {
            indexStatusLabel.setText("Index Status: Error");
            indexStatusLabel.setForeground(JBColor.RED);

            // 重置进度条为错误状态
            indexProgressBar.setValue(0);
            indexProgressBar.setIndeterminate(false);
            indexProgressBar.setString("0%");
            updateProgressBarColor(0);

            // 恢复按钮状态
            rebuildIndexButton.setEnabled(true);
            rebuildIndexButton.setText("Rebuild Index");
            clearIndexButton.setEnabled(true);
            clearIndexButton.setText("Clear Index");

            // 重置操作状态
            isOperationInProgress = false;

            showNotification(project, message, NotificationType.ERROR);
        });
    }

    /**
     * 显示通知
     */
    private void showNotification(Project project, String content, NotificationType type) {
        Notification notification = NOTIFICATION_GROUP.createNotification(
                "Awesome Console - Index Management",
                content,
                type
        );
        notification.notify(project);
    }

    /**
     * 清理资源（配置面板关闭时调用）
     */
    public void dispose() {
        // 当前实现中，后台线程会自然结束，无需特殊处理
        // 如果未来添加了需要取消的长时间任务，可以在这里处理
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(20, 4, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        debugModeCheckBox.setText("Debug Mode");
        panel1.add(debugModeCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        limitLineMatchingByCheckBox.setText("Limit line matching by");
        panel1.add(limitLineMatchingByCheckBox, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("chars.");
        panel1.add(label1, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        maxLengthTextField.setText("");
        panel1.add(maxLengthTextField, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        matchLinesLongerThanCheckBox.setText("Match lines longer than the limit chunk by chunk.");
        panel1.add(matchLinesLongerThanCheckBox, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        searchForURLsCheckBox.setText("Match URLs (file, ftp, http(s)).");
        panel1.add(searchForURLsCheckBox, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        searchForFilesCheckBox.setText("Match file paths.");
        panel1.add(searchForFilesCheckBox, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        searchForClassesCheckBox.setText("Match Java-like Classes.");
        panel1.add(searchForClassesCheckBox, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(5, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        limitResultCheckBox.setText("Each hyperlink matches at most");
        panel2.add(limitResultCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panel2.add(limitResultSpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("results.");
        panel2.add(label2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel2.add(spacer1, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        filePatternCheckBox.setText("Files matching pattern:");
        panel1.add(filePatternCheckBox, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel1.add(scrollPane1, new GridConstraints(6, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        filePatternTextArea.setMargin(new Insets(4, 4, 4, 4));
        scrollPane1.setViewportView(filePatternTextArea);
        panel1.add(filePatternLabel, new GridConstraints(7, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ignorePatternCheckBox.setText("Ignore matches:");
        panel1.add(ignorePatternCheckBox, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ignorePatternTextField.setText("");
        panel1.add(ignorePatternTextField, new GridConstraints(8, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Use regex pattern");
        panel1.add(label3, new GridConstraints(8, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ignoreStyleCheckBox.setText("Use ignore style.");
        panel1.add(ignoreStyleCheckBox, new GridConstraints(9, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fixChooseTargetFileCheckBox.setText("Fix \"Choose Target File\" popup. (Verified in 2021.2.1 ~ 2023.2.3)");
        panel1.add(fixChooseTargetFileCheckBox, new GridConstraints(10, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fileTypesCheckBox.setText("Non-text file types:");
        panel1.add(fileTypesCheckBox, new GridConstraints(11, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fileTypesTextField.setText("");
        panel1.add(fileTypesTextField, new GridConstraints(11, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        resolveSymlinkCheckBox.setText("Resolve Symlinks (compatible with IDEA Resolve Symlinks plugin).");
        panel1.add(resolveSymlinkCheckBox, new GridConstraints(12, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        preserveAnsiColorsCheckBox.setText("Preserve ANSI color.");
        preserveAnsiColorsCheckBox.setToolTipText("Preserve ANSI color codes and formatting in console output.");
        panel1.add(preserveAnsiColorsCheckBox, new GridConstraints(13, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JSeparator separator1 = new JSeparator();
        panel1.add(separator1, new GridConstraints(14, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("File Index Management");
        panel1.add(label4, new GridConstraints(15, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        indexStatusLabel.setText("Index Status: Not initialized");
        panel1.add(indexStatusLabel, new GridConstraints(16, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        indexProgressBar.setStringPainted(true);
        indexProgressBar.setVisible(true);
        panel1.add(indexProgressBar, new GridConstraints(17, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 8), null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel1.add(panel3, new GridConstraints(18, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        rebuildIndexButton.setText("Rebuild");
        panel3.add(rebuildIndexButton);
        clearIndexButton.setText("Clear");
        panel3.add(clearIndexButton);
        final Spacer spacer2 = new Spacer();
        panel1.add(spacer2, new GridConstraints(19, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }
}
