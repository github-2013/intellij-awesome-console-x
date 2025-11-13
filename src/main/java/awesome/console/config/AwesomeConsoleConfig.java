package awesome.console.config;

import static awesome.console.config.AwesomeConsoleDefaults.DEFAULT_GROUP_RETRIES;
import static awesome.console.config.AwesomeConsoleDefaults.FILE_PATTERN_REQUIRED_GROUPS;

import awesome.console.util.RegexUtils;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Awesome Console 配置界面控制器
 * 实现 IntelliJ Platform 的 Configurable 接口，负责管理插件的设置界面
 * 
 * 生命周期说明：
 * 1. 构造函数在用户打开设置对话框并选择对应的设置项时才会被调用
 * 2. 实例的生命周期在用户点击 OK 或 Cancel 时结束
 * 3. 设置对话框关闭时会调用 disposeUIResources() 方法
 * 
 * 主要功能：
 * 初始化配置表单界面
 * 检测配置是否被修改
 * 应用配置更改到存储
 * 验证用户输入（正则表达式、数值等）
 * 
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/settings-guide.html">Settings Guide</a>
 */
@SuppressWarnings({"unused", "SameParameterValue"})
public class AwesomeConsoleConfig implements Configurable {

	/** 配置表单界面 */
	private AwesomeConsoleConfigForm form;

	/** 配置存储实例 */
	private final AwesomeConsoleStorage storage;

	/**
	 * 构造函数，初始化配置存储实例
	 */
	public AwesomeConsoleConfig() {
		this.storage = AwesomeConsoleStorage.getInstance();
	}

	/**
	 * 从配置存储中初始化表单界面的各个控件
	 * 将存储中的配置值设置到对应的UI组件中
	 */
	private void initFromConfig() {
		form.debugModeCheckBox.setSelected(storage.DEBUG_MODE);

		form.limitLineMatchingByCheckBox.setSelected(storage.LIMIT_LINE_LENGTH);

		form.matchLinesLongerThanCheckBox.setEnabled(storage.LIMIT_LINE_LENGTH);
		form.matchLinesLongerThanCheckBox.setSelected(storage.SPLIT_ON_LIMIT);

		form.searchForURLsCheckBox.setSelected(storage.searchUrls);
		form.initMatchFiles(storage.searchFiles, storage.searchClasses, storage.useFilePattern, storage.getFilePatternText());
		form.initLimitResult(storage.useResultLimit, storage.getResultLimit());

		form.maxLengthTextField.setText(String.valueOf(storage.LINE_MAX_LENGTH));
		form.maxLengthTextField.setEnabled(storage.LIMIT_LINE_LENGTH);
		form.maxLengthTextField.setEditable(storage.LIMIT_LINE_LENGTH);

		form.initIgnorePattern(storage.useIgnorePattern, storage.getIgnorePatternText(), storage.useIgnoreStyle);

		form.fixChooseTargetFileCheckBox.setSelected(storage.fixChooseTargetFile);

		form.initFileTypes(storage.useFileTypes, storage.getFileTypes());

		form.resolveSymlinkCheckBox.setSelected(storage.resolveSymlink);
		form.preserveAnsiColorsCheckBox.setSelected(storage.preserveAnsiColors);
	}

	/**
	 * 显示错误对话框（默认消息）
	 * 用于数值输入错误的情况
	 */
	private void showErrorDialog() {
		JOptionPane.showMessageDialog(form.mainPanel, "Error: Please enter a positive number.", "Invalid value", JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * 显示自定义错误对话框
	 * 
	 * @param title 对话框标题
	 * @param message 错误消息内容
	 */
	private void showErrorDialog(String title, String message) {
		JOptionPane.showMessageDialog(form.mainPanel, message, title, JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * 检查正则表达式是否有效
	 * 
	 * @param pattern 要检查的正则表达式字符串
	 * @return 如果正则表达式有效则返回true，否则显示错误对话框并返回false
	 */
	private boolean checkRegex(@NotNull final String pattern) {
		if (pattern.isEmpty() || !RegexUtils.isValidRegex(pattern)) {
			showErrorDialog("Invalid value", "Invalid pattern: " + StringUtil.trimMiddle(pattern, 150));
			return false;
		}
		return true;
	}

	/**
	 * 检查正则表达式是否包含必需的命名捕获组
	 * 验证指定的命名组是否存在，以及重试次数是否在允许范围内
	 * 
	 * @param pattern 要检查的正则表达式字符串
	 * @param groups 必需的命名捕获组名称列表
	 * @return 如果所有必需的组都存在且有效则返回true，否则显示错误对话框并返回false
	 */
	private boolean checkRegexGroup(@NotNull final String pattern, @NotNull final String... groups) {
		if (pattern.isEmpty()) {
			return false;
		}
		boolean hasGroup;
		for (String group : groups) {
			try {
				Matcher matcher = Pattern.compile("\\(\\?<" + group + "([1-9][0-9]*)?>").matcher(pattern);
				if (matcher.find()) {
					String index = matcher.group(1);
					hasGroup = StringUtil.isEmpty(index) || Integer.parseInt(index) <= DEFAULT_GROUP_RETRIES;
				} else {
					hasGroup = false;
				}
			} catch (PatternSyntaxException | NumberFormatException e) {
				hasGroup = false;
			}
			if (!hasGroup) {
				showErrorDialog("Invalid value", String.format(
						"Missing required group \"%s\": %s",
						group, StringUtil.trimMiddle(pattern, 150)
				));
				return false;
			}
		}
		return true;
	}

	/**
	 * 获取配置页面的显示名称
	 * 该名称会显示在设置对话框的左侧菜单中
	 * 
	 * @return 配置页面的显示名称
	 */
	@Nls
	@Override
	public String getDisplayName() {
		return "Awesome Console";
	}

	/**
	 * 获取帮助主题标识
	 * 用于关联帮助文档
	 * 
	 * @return 帮助主题标识
	 */
	@Nullable
	@Override
	public String getHelpTopic() {
		return "help topic";
	}

	/**
	 * 创建配置界面组件
	 * 当用户打开设置对话框时调用，创建并初始化表单界面
	 * 
	 * @return 配置界面的主面板组件
	 */
	@Nullable
	@Override
	public JComponent createComponent() {
		form = new AwesomeConsoleConfigForm();
		initFromConfig();
		return form.mainPanel;
	}

	/**
	 * 检查配置是否被修改
	 * 比较表单中的当前值与存储中的值，判断用户是否进行了修改
	 * 
	 * @return 如果配置被修改则返回true，否则返回false
	 */
	@Override
	public boolean isModified() {
		final String text = form.maxLengthTextField.getText().trim();
		if (text.isEmpty()) {
			return true;
		}
		final int len;
		try {
			len = Integer.parseInt(text);
		} catch (final NumberFormatException nfe) {
			return true;
		}
		return form.debugModeCheckBox.isSelected() != storage.DEBUG_MODE
				|| form.limitLineMatchingByCheckBox.isSelected() != storage.LIMIT_LINE_LENGTH
				|| len != storage.LINE_MAX_LENGTH
				|| form.matchLinesLongerThanCheckBox.isSelected() != storage.SPLIT_ON_LIMIT
				|| form.searchForURLsCheckBox.isSelected() != storage.searchUrls
				|| form.searchForFilesCheckBox.isSelected() != storage.searchFiles
				|| form.searchForClassesCheckBox.isSelected() != storage.searchClasses
				|| form.limitResultCheckBox.isSelected() != storage.useResultLimit
				|| !Objects.equals(form.limitResultSpinner.getValue(), storage.getResultLimit())
				|| form.filePatternCheckBox.isSelected() != storage.useFilePattern
				|| !form.filePatternTextArea.getText().trim().equals(storage.getFilePatternText())
				|| form.ignorePatternCheckBox.isSelected() != storage.useIgnorePattern
				|| !form.ignorePatternTextField.getText().trim().equals(storage.getIgnorePatternText())
				|| form.ignoreStyleCheckBox.isSelected() != storage.useIgnoreStyle
				|| form.fixChooseTargetFileCheckBox.isSelected() != storage.fixChooseTargetFile
				|| form.fileTypesCheckBox.isSelected() != storage.useFileTypes
				|| !form.fileTypesTextField.getText().trim().equals(storage.getFileTypes())
				|| form.resolveSymlinkCheckBox.isSelected() != storage.resolveSymlink
				|| form.preserveAnsiColorsCheckBox.isSelected() != storage.preserveAnsiColors
				;
	}

	/**
	 * 应用配置更改
	 * 当用户点击 Apply 或 OK 按钮时调用
	 * 验证用户输入并将表单中的值保存到配置存储中
	 */
	@Override
	public void apply() {
		final String text = form.maxLengthTextField.getText().trim();
		if (text.isEmpty()) {
			showErrorDialog();
			return;
		}
		final int maxLength;
		try {
			maxLength = Integer.parseInt(text);
		} catch (final NumberFormatException nfe) {
			showErrorDialog();
			return;
		}
		if (maxLength < 1) {
			showErrorDialog();
			return;
		}

		final boolean useFilePattern = form.filePatternCheckBox.isSelected();
		final String filePatternText = form.filePatternTextArea.getText().trim();

		if (!Objects.equals(filePatternText, storage.getFilePatternText()) &&
				!(checkRegex(filePatternText) && checkRegexGroup(filePatternText, FILE_PATTERN_REQUIRED_GROUPS))) {
			return;
		}

		final boolean useIgnorePattern = form.ignorePatternCheckBox.isSelected();
		final String ignorePatternText = form.ignorePatternTextField.getText().trim();

		if (!Objects.equals(ignorePatternText, storage.getIgnorePatternText()) &&
				!checkRegex(ignorePatternText)) {
			return;
		}

		storage.DEBUG_MODE = form.debugModeCheckBox.isSelected();
		storage.LIMIT_LINE_LENGTH = form.limitLineMatchingByCheckBox.isSelected();
		storage.LINE_MAX_LENGTH = maxLength;
		storage.SPLIT_ON_LIMIT = form.matchLinesLongerThanCheckBox.isSelected();

		storage.searchUrls = form.searchForURLsCheckBox.isSelected();
		storage.searchFiles = form.searchForFilesCheckBox.isSelected();
		storage.searchClasses = form.searchForClassesCheckBox.isSelected();

		storage.useResultLimit = form.limitResultCheckBox.isSelected();
		storage.setResultLimit((int) form.limitResultSpinner.getValue());

		storage.useFilePattern = useFilePattern;
		storage.setFilePatternText(filePatternText);
		form.filePatternTextArea.setText(filePatternText);

		storage.useIgnorePattern = useIgnorePattern;
		storage.setIgnorePatternText(ignorePatternText);
		form.ignorePatternTextField.setText(ignorePatternText);
		storage.useIgnoreStyle = form.ignoreStyleCheckBox.isSelected();

		storage.fixChooseTargetFile = form.fixChooseTargetFileCheckBox.isSelected();

		storage.useFileTypes = form.fileTypesCheckBox.isSelected();
		storage.setFileTypes(form.fileTypesTextField.getText().trim());
		form.fileTypesTextField.setText(storage.getFileTypes());

		storage.resolveSymlink = form.resolveSymlinkCheckBox.isSelected();
		storage.preserveAnsiColors = form.preserveAnsiColorsCheckBox.isSelected();
	}

	/**
	 * 重置配置界面
	 * 当用户点击 Reset 按钮时调用，将表单恢复到存储中的值
	 */
	@Override
	public void reset() {
		initFromConfig();
	}

	/**
	 * 释放UI资源
	 * 当设置对话框关闭时调用，清理表单引用
	 */
	@Override
	public void disposeUIResources() {
		form = null;
	}
}
