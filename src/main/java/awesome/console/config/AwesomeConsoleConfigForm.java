package awesome.console.config;

import awesome.console.AwesomeLinkFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.util.*;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("SameParameterValue")
public class AwesomeConsoleConfigForm implements AwesomeConsoleDefaults {
    private static final Logger logger = Logger.getInstance(AwesomeConsoleConfigForm.class);

    public JPanel mainPanel;
    public JCheckBox debugModeCheckBox;
    public JCheckBox limitLineMatchingByCheckBox;
    public JSpinner maxLengthSpinner;
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
    public JLabel ignorePatternLabel;
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

    // 索引管理服务
    private IndexManagementService indexManagementService;

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
        debugModeCheckBox.setToolTipText("Enable debug mode to log detailed information for troubleshooting.");
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

        filePatternCheckBox = initCheckBox(DEFAULT_USE_FILE_PATTERN);
        filePatternCheckBox.setToolTipText("Check this to custom File Pattern. (experimental)");
        filePatternTextArea = initTextArea(DEFAULT_FILE_PATTERN_TEXT);
        filePatternTextArea.setLineWrap(true);
        final String groupExample = FILE_PATTERN_REQUIRED_GROUPS[0];
        filePatternLabel = new JLabel(String.format(
                "* Required regex group names: [%s], where %s,%s1...%s%d all correspond to the group %s.",
                StringUtil.join(FILE_PATTERN_REQUIRED_GROUPS, ", "),
                groupExample, groupExample, groupExample, DEFAULT_GROUP_RETRIES, groupExample
        ));
        // 设置小号字体和浅灰色
        Font currentFont = filePatternLabel.getFont();
        filePatternLabel.setFont(currentFont.deriveFont(currentFont.getSize() - 2.0f));
        filePatternLabel.setForeground(JBColor.GRAY);

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
        ignorePatternCheckBox.setToolTipText("Use regex pattern to ignore specific file paths or URLs from being matched.");
        ignorePatternTextField = initTextField(DEFAULT_IGNORE_PATTERN_TEXT);
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
        indexProgressBar.setIndeterminate(false);
        indexProgressBar.setVisible(true);
        indexProgressBar.setValue(0);
        indexProgressBar.setString("0%");
        updateProgressBarColor(0);

        rebuildIndexButton = new JButton("Rebuild");
        rebuildIndexButton.addActionListener(e -> rebuildIndex());

        clearIndexButton = new JButton("Clear");
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
                AwesomeLinkFilter.IndexStatistics stats = indexManagementService.getIndexStatistics(project);
                if (stats != null) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        updateIndexStatusUI(project.getName(), stats);
                        rebuildIndexButton.setEnabled(true);
                        clearIndexButton.setEnabled(true);
                    }, ModalityState.any());
                } else {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        indexStatusLabel.setText("Index Status: Service not available");
                        indexStatusLabel.setForeground(JBColor.RED);
                        rebuildIndexButton.setEnabled(false);
                        clearIndexButton.setEnabled(false);
                    }, ModalityState.any());
                }
            } catch (Exception e) {
                logger.error("Failed to update index status: " + e.getMessage(), e);
                ApplicationManager.getApplication().invokeLater(() -> {
                    indexStatusLabel.setText("Index Status: Error - " + e.getMessage());
                    indexStatusLabel.setForeground(JBColor.RED);
                }, ModalityState.any());
            }
        });
    }

    /**
     * 更新索引状态 UI
     */
    private void updateIndexStatusUI(String projectName, AwesomeLinkFilter.IndexStatistics stats) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Index Status [%s]: %d files indexed (%d filenames, %d basenames)",
                projectName, stats.getTotalFiles(), stats.getFileCacheSize(), stats.getFileBaseCacheSize()));

        if (stats.hasIgnoreStatistics()) {
            sb.append(String.format(" - Matched: %d, Ignored: %d",
                    stats.getMatchedFiles(), stats.getIgnoredFiles()));
        }

        if (stats.getLastRebuildTime() > 0) {
            long elapsed = System.currentTimeMillis() - stats.getLastRebuildTime();
            sb.append(String.format(" - Last rebuild: %s ago", indexManagementService.formatDuration(elapsed)));

            if (stats.getLastRebuildDuration() > 0) {
                sb.append(String.format(" (took %s)", indexManagementService.formatDuration(stats.getLastRebuildDuration())));
            }
        }

        indexStatusLabel.setText(sb.toString());
        indexStatusLabel.setForeground(new Color(76, 175, 80));

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
                indexStatusLabel.setText(String.format("Rebuilding index [%s]...", project.getName()));
                indexStatusLabel.setForeground(new Color(33, 150, 243));
            }

            @Override
            public void onProgress(int current, int total, AwesomeLinkFilter.IndexStatistics stats) {
                // 检查 UI 组件是否已销毁
                if (indexStatusLabel == null || indexProgressBar == null) {
                    return;
                }

                int totalFiles = stats.getTotalFiles();
                int ignoredFiles = stats.getIgnoredFiles();
                int matchedFiles = Math.max(0, current - ignoredFiles);
                int estimatedTotalFiles = indexManagementService.getEstimatedTotalFiles();
                long rebuildStartTime = indexManagementService.getRebuildStartTime();

                // 判断是否完成
                boolean isComplete = (totalFiles > 0 && current >= totalFiles);
                int progress = isComplete ? 100 : Math.min(95, (current * 95) / Math.max(estimatedTotalFiles, 1));

                // 更新状态文本
                long currentTime = System.currentTimeMillis();
                String statusText = isComplete
                        ? String.format("Rebuild completed [%s]: %d files indexed in %s",
                        project.getName(), current, indexManagementService.formatDuration(currentTime - rebuildStartTime))
                        : String.format("Rebuilding index [%s]... %d files processed", project.getName(), current);

                if (ignoredFiles > 0) {
                    statusText += String.format(" (Matched: %d, Ignored: %d)", matchedFiles, ignoredFiles);
                }
                indexStatusLabel.setText(statusText);

                // 更新进度条
                indexProgressBar.setValue(progress);
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
                indexProgressBar.setString("100%");
                updateProgressBarColor(100);
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
                updateProgressBarColor(0);
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

        // 确认对话框
        int result = JOptionPane.showConfirmDialog(mainPanel,
                "Are you sure you want to clear the file index?\nIt will be automatically rebuilt when needed.",
                "Confirm Clear", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
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
                indexStatusLabel.setForeground(new Color(244, 67, 54));
            }

            @Override
            public void onProgress(int current, int total, AwesomeLinkFilter.IndexStatistics stats) {
                // 清除操作不需要进度更新
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
                updateProgressBarColor(0);
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
                updateProgressBarColor(0);
            }
        });
    }

    /**
     * 根据进度百分比更新进度条颜色
     */
    private void updateProgressBarColor(int percentage) {
        Color color;
        if (percentage == 0) {
            color = new Color(158, 158, 158);
        } else if (percentage == 100) {
            color = new Color(76, 175, 80);
        } else {
            color = new Color(255, 193, 7);
        }
        indexProgressBar.setForeground(color);
    }

    /**
     * 更新进度条颜色（双色模式，支持忽略文件统计）
     */
    private void updateProgressBarWithIgnoreStats(int totalFiles, int matchedFiles, int ignoredFiles) {
        if (totalFiles == 0) {
            indexProgressBar.setForeground(new Color(158, 158, 158));
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
            indexProgressBar.setForeground(new Color(76, 175, 80));
        } else if (totalPercentage > 0) {
            indexProgressBar.setForeground(new Color(76, 175, 80));
        } else {
            indexProgressBar.setForeground(new Color(158, 158, 158));
        }
    }

    /**
     * 根据索引统计信息更新进度条
     */
    private void updateProgressBarFromStats(AwesomeLinkFilter.IndexStatistics stats) {
        if (stats == null) {
            indexProgressBar.setValue(0);
            indexProgressBar.setString("0%");
            updateProgressBarColor(0);
            return;
        }

        int totalFiles = stats.getTotalFiles();
        if (totalFiles == 0) {
            indexProgressBar.setValue(0);
            indexProgressBar.setString("0%");
            updateProgressBarColor(0);
        } else {
            indexProgressBar.setValue(100);

            if (stats.hasIgnoreStatistics()) {
                int matchedFiles = stats.getMatchedFiles();
                int ignoredFiles = stats.getIgnoredFiles();
                int matchedPercentage = (matchedFiles * 100) / totalFiles;
                int ignoredPercentage = (ignoredFiles * 100) / totalFiles;

                indexProgressBar.setString(String.format("100%% (✓%d%% ⚠%d%%)",
                        matchedPercentage, ignoredPercentage));
                updateProgressBarWithIgnoreStats(totalFiles, matchedFiles, ignoredFiles);
            } else {
                indexProgressBar.setString("100%");
                updateProgressBarColor(100);
            }
        }
    }


    /**
     * 清理资源（由 AwesomeConsoleConfig.disposeUIResources() 调用）
     * <p>
     * 注意：
     * 1. 此方法必须保留，即使方法体为空，因为 AwesomeConsoleConfig 会显式调用它
     * 2. 索引操作会在后台继续完成，不会被中断
     * 3. UI 组件由 IntelliJ 平台自动清理
     * 4. 回调方法中已有空指针检查，确保 UI 销毁后不会出错
     */
    public void dispose() {
        // 方法体为空是正确的设计：
        // - 不取消后台索引操作
        // - 不手动清理 UI 引用（由平台管理）
        // - 回调中的空指针检查已足够保证安全性
    }
}
