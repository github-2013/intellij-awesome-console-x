package awesome.console.config;

import com.intellij.openapi.util.text.StringUtil;

import java.awt.*;
import java.util.*;
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

    private Map<JCheckBox, Set<JComponent>> bindMap;
    private Map<JComponent, Set<JCheckBox>> bindMap2;

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
        mainPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        final JLabel label1 = new JLabel();
        label1.setText("Awesome Console X");
        mainPanel.add(label1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(15, 4, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        debugModeCheckBox.setText("Debug Mode");
        panel1.add(debugModeCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        limitLineMatchingByCheckBox.setText("Limit line matching by");
        panel1.add(limitLineMatchingByCheckBox, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(14, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("chars.");
        panel1.add(label2, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
        final JLabel label3 = new JLabel();
        label3.setText("results.");
        panel2.add(label3, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel2.add(spacer2, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
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
        final JLabel label4 = new JLabel();
        label4.setText("Use regex pattern");
        panel1.add(label4, new GridConstraints(8, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
