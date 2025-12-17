package awesome.console.config;

import awesome.console.util.RegexUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.application.ApplicationManager;
import java.util.Objects;
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

	/** 日志记录器 */
	private static final Logger logger = Logger.getInstance(AwesomeConsoleConfig.class);

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
		form.limitLineMatchingByCheckBox.setSelected(storage.LIMIT_LINE_LENGTH);

		form.matchLinesLongerThanCheckBox.setEnabled(storage.LIMIT_LINE_LENGTH);
		form.matchLinesLongerThanCheckBox.setSelected(storage.SPLIT_ON_LIMIT);

		form.searchForURLsCheckBox.setSelected(storage.searchUrls);
		form.initMatchFiles(storage.searchFiles, storage.searchClasses);
		form.initLimitResult(storage.useResultLimit, storage.getResultLimit());

		form.maxLengthSpinner.setValue(storage.LINE_MAX_LENGTH);
		form.maxLengthSpinner.setEnabled(storage.LIMIT_LINE_LENGTH);

		form.initIgnorePattern(storage.useIgnorePattern, storage.getIgnorePatternText(), storage.useIgnoreStyle);

		form.fixChooseTargetFileCheckBox.setSelected(storage.fixChooseTargetFile);

		form.initFileTypes(storage.useFileTypes, storage.getFileTypes());

		form.resolveSymlinkCheckBox.setSelected(storage.resolveSymlink);
		form.preserveAnsiColorsCheckBox.setSelected(storage.preserveAnsiColors);
		form.showNotificationsCheckBox.setSelected(storage.showNotifications);
		form.underlineOnlyCheckBox.setSelected(storage.underlineOnly);
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
		// 更新索引状态
		form.updateIndexStatus();
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
		return form.limitLineMatchingByCheckBox.isSelected() != storage.LIMIT_LINE_LENGTH
			|| !Objects.equals(form.maxLengthSpinner.getValue(), storage.LINE_MAX_LENGTH)
				|| form.matchLinesLongerThanCheckBox.isSelected() != storage.SPLIT_ON_LIMIT
				|| form.searchForURLsCheckBox.isSelected() != storage.searchUrls
				|| form.searchForFilesCheckBox.isSelected() != storage.searchFiles
			|| form.searchForClassesCheckBox.isSelected() != storage.searchClasses
				|| form.limitResultCheckBox.isSelected() != storage.useResultLimit
				|| !Objects.equals(form.limitResultSpinner.getValue(), storage.getResultLimit())
				|| form.ignorePatternCheckBox.isSelected() != storage.useIgnorePattern
				|| !form.ignorePatternTextField.getText().trim().equals(storage.getIgnorePatternText())
				|| form.ignoreStyleCheckBox.isSelected() != storage.useIgnoreStyle
				|| form.fixChooseTargetFileCheckBox.isSelected() != storage.fixChooseTargetFile
				|| form.fileTypesCheckBox.isSelected() != storage.useFileTypes
				|| !form.fileTypesTextField.getText().trim().equals(storage.getFileTypes())
				|| form.resolveSymlinkCheckBox.isSelected() != storage.resolveSymlink
				|| form.preserveAnsiColorsCheckBox.isSelected() != storage.preserveAnsiColors
				|| form.showNotificationsCheckBox.isSelected() != storage.showNotifications
				|| form.underlineOnlyCheckBox.isSelected() != storage.underlineOnly
				;
	}

	/**
	 * 应用配置更改
	 * 当用户点击 Apply 或 OK 按钮时调用
	 * 验证用户输入并将表单中的值保存到配置存储中
	 */
	@Override
	public void apply() {
		final int maxLength = (int) form.maxLengthSpinner.getValue();
		if (maxLength < 1) {
			showErrorDialog();
			return;
		}

		final boolean useIgnorePattern = form.ignorePatternCheckBox.isSelected();
		final String ignorePatternText = form.ignorePatternTextField.getText().trim();

		if (!Objects.equals(ignorePatternText, storage.getIgnorePatternText()) &&
				!checkRegex(ignorePatternText)) {
			return;
		}

		// 检测配置变更，用于决定是否需要通知监听器
		AwesomeConsoleConfigListener.ConfigChangeType changeType = detectConfigChanges(useIgnorePattern, ignorePatternText);

		storage.LIMIT_LINE_LENGTH = form.limitLineMatchingByCheckBox.isSelected();
		storage.LINE_MAX_LENGTH = maxLength;
		storage.SPLIT_ON_LIMIT = form.matchLinesLongerThanCheckBox.isSelected();

		storage.searchUrls = form.searchForURLsCheckBox.isSelected();
		storage.searchFiles = form.searchForFilesCheckBox.isSelected();
		storage.searchClasses = form.searchForClassesCheckBox.isSelected();

		storage.useResultLimit = form.limitResultCheckBox.isSelected();
		storage.setResultLimit((int) form.limitResultSpinner.getValue());

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
		storage.showNotifications = form.showNotificationsCheckBox.isSelected();
		storage.underlineOnly = form.underlineOnlyCheckBox.isSelected();

		// 发布配置变更事件（包括需要重建缓存的变更和其他配置变更）
		if (changeType != null) {
			notifyConfigChanged(changeType);
		}
	}

	/**
	 * 检测配置变更，判断是否需要重建缓存，并返回具体的变更类型
	 * 
	 * @param useIgnorePattern 新的忽略模式启用状态
	 * @param ignorePatternText 新的忽略模式正则表达式
	 * @return 配置变更类型，如果没有任何变更则返回null
	 */
	private AwesomeConsoleConfigListener.ConfigChangeType detectConfigChanges(boolean useIgnorePattern, String ignorePatternText) {
		// 1. 文件搜索功能变更（开启或关闭都需要重建，因为关闭时需要清理缓存）
		if (storage.searchFiles != form.searchForFilesCheckBox.isSelected()) {
			logger.debug(String.format("Config change detected: searchFiles %b -> %b", 
					storage.searchFiles, form.searchForFilesCheckBox.isSelected()));
			return AwesomeConsoleConfigListener.ConfigChangeType.SEARCH_FILES_CHANGED;
		}

		// 2. 类搜索功能变更（开启或关闭都需要重建，因为关闭时需要清理 fileBaseCache）
		if (storage.searchClasses != form.searchForClassesCheckBox.isSelected()) {
			logger.debug(String.format("Config change detected: searchClasses %b -> %b", 
					storage.searchClasses, form.searchForClassesCheckBox.isSelected()));
			return AwesomeConsoleConfigListener.ConfigChangeType.SEARCH_CLASSES_CHANGED;
		}

		// 3. 忽略模式变更（启用状态变化或正则表达式内容变化）
		if (storage.useIgnorePattern != useIgnorePattern ||
				!Objects.equals(storage.getIgnorePatternText(), ignorePatternText)) {
			logger.debug(String.format("Config change detected: ignorePattern enabled=%b, pattern='%s' -> enabled=%b, pattern='%s'",
					storage.useIgnorePattern, storage.getIgnorePatternText(),
					useIgnorePattern, ignorePatternText));
			return AwesomeConsoleConfigListener.ConfigChangeType.IGNORE_PATTERN_CHANGED;
		}

		// 4. 文件类型过滤变更（启用状态变化或文件类型列表变化）
		String newFileTypes = form.fileTypesTextField.getText().trim();
		if (storage.useFileTypes != form.fileTypesCheckBox.isSelected() ||
				!Objects.equals(storage.getFileTypes(), newFileTypes)) {
			logger.debug(String.format("Config change detected: fileTypes enabled=%b, types='%s' -> enabled=%b, types='%s'",
					storage.useFileTypes, storage.getFileTypes(),
					form.fileTypesCheckBox.isSelected(), newFileTypes));
			return AwesomeConsoleConfigListener.ConfigChangeType.FILE_TYPES_CHANGED;
		}

		// 5. 检测其他配置变更（不需要重建缓存）
		if (storage.LIMIT_LINE_LENGTH != form.limitLineMatchingByCheckBox.isSelected() ||
				!Objects.equals(form.maxLengthSpinner.getValue(), storage.LINE_MAX_LENGTH) ||
				storage.SPLIT_ON_LIMIT != form.matchLinesLongerThanCheckBox.isSelected() ||
				storage.searchUrls != form.searchForURLsCheckBox.isSelected() ||
				storage.useResultLimit != form.limitResultCheckBox.isSelected() ||
				!Objects.equals(form.limitResultSpinner.getValue(), storage.getResultLimit()) ||
				storage.useIgnoreStyle != form.ignoreStyleCheckBox.isSelected() ||
				storage.fixChooseTargetFile != form.fixChooseTargetFileCheckBox.isSelected() ||
				storage.resolveSymlink != form.resolveSymlinkCheckBox.isSelected() ||
				storage.preserveAnsiColors != form.preserveAnsiColorsCheckBox.isSelected() ||
				storage.showNotifications != form.showNotificationsCheckBox.isSelected() ||
				storage.underlineOnly != form.underlineOnlyCheckBox.isSelected()) {
			logger.debug("Config change detected: other settings changed (no cache rebuild needed)");
			return AwesomeConsoleConfigListener.ConfigChangeType.OTHER_CHANGED;
		}

		return null;
	}

	/**
	 * 通知配置变更
	 * 通过 MessageBus 发布配置变更事件，通知所有监听器
	 * 
	 * @param changeType 配置变更类型
	 * 
	 * 注意：使用 ApplicationManager 而非项目级别的 MessageBus，
	 * 因为配置是全局的（存储在 awesomeconsole.xml 中），需要通知所有打开的项目
	 */
	private void notifyConfigChanged(AwesomeConsoleConfigListener.ConfigChangeType changeType) {
		ApplicationManager.getApplication()
			.getMessageBus()
			.syncPublisher(AwesomeConsoleConfigListener.TOPIC)
			.configChanged(changeType);
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
		if (form != null) {
			form.dispose();
			form = null;
		}
	}
}
