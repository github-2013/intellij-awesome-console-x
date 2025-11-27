package awesome.console.config;

import com.intellij.util.messages.Topic;

/**
 * Awesome Console 配置变更监听器
 * 当用户修改插件配置并点击 Apply 或 OK 时，会触发此监听器
 * 用于通知各个组件（如 AwesomeLinkFilter）配置已变更，需要更新状态
 */
public interface AwesomeConsoleConfigListener {
	
	/**
	 * 配置变更事件的 Topic
	 * 使用应用级别的 MessageBus，因为配置是全局的
	 */
	Topic<AwesomeConsoleConfigListener> TOPIC = Topic.create(
		"AwesomeConsoleConfigChanged",
		AwesomeConsoleConfigListener.class
	);
	
	/**
	 * 配置变更回调方法
	 * 当配置发生变更时调用
	 * 
	 * @param changeType 变更类型，指示哪些配置发生了变化
	 */
	void configChanged(ConfigChangeType changeType);
	
	/**
	 * 配置变更类型枚举
	 * 用于指示哪些配置发生了变化，以便监听器可以有针对性地处理
	 */
	enum ConfigChangeType {
		/** 文件搜索功能启用/禁用 */
		SEARCH_FILES_CHANGED,
		
		/** 类搜索功能启用/禁用 */
		SEARCH_CLASSES_CHANGED,
		
		/** 忽略模式变更（包括启用/禁用和正则表达式变更） */
		IGNORE_PATTERN_CHANGED,
		
		/** 文件类型过滤变更（包括启用/禁用和文件类型列表变更） */
		FILE_TYPES_CHANGED,
		
		/** 其他配置变更（不需要重建缓存） */
		OTHER_CHANGED
	}
}
