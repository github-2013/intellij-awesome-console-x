package awesome.console;

import com.intellij.execution.filters.ConsoleDependentFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.terminal.TerminalExecutionConsole;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Awesome Link Filter 提供者
 * 负责为控制台和终端提供链接过滤器，支持在控制台输出中识别并高亮显示文件路径和URL链接
 * 使用缓存机制为每个项目维护单例Filter，并在项目关闭时清理缓存
 */
// 定义公共类 AwesomeLinkFilterProvider，继承 ConsoleDependentFilterProvider 以提供控制台过滤器
public class AwesomeLinkFilterProvider extends ConsoleDependentFilterProvider {
	/** Filter缓存，为每个项目维护一个Filter实例 */
	// 声明私有静态final成员变量cache，使用ConcurrentHashMap存储项目到过滤器数组的映射，保证线程安全
	private static final Map<Project, Filter[]> cache = new ConcurrentHashMap<>();

	/**
	 * 构造函数，订阅项目关闭事件以清理缓存
	 */
	// 定义公共无参构造函数
	public AwesomeLinkFilterProvider() {
		// 获取应用程序实例，然后获取消息总线，建立连接并订阅项目管理器主题，传入项目管理器监听器
		ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
			// 使用@Override注解标记此方法重写了父接口的方法
			@Override
			// 定义项目关闭时的回调方法，接收被关闭的项目作为参数
			public void projectClosed(@NotNull Project project) {
				// 从缓存中移除已关闭项目对应的过滤器，释放资源
				cache.remove(project);
			}
		});
	}

	/**
	 * 为指定的控制台视图获取默认过滤器
	 * 
	 * @param consoleView 控制台视图
	 * @param project 项目实例
	 * @param globalSearchScope 全局搜索范围
	 * @return Filter数组
	 */
	// 使用@NotNull注解标记返回值不为空
	@NotNull
	// 使用@Override注解标记此方法重写了父类的方法
	@Override
	// 定义公共方法getDefaultFilters，接收控制台视图、项目和全局搜索范围参数，返回非空的Filter数组
	public Filter @NotNull [] getDefaultFilters(@NotNull final ConsoleView consoleView, @NotNull final Project project, @NotNull final GlobalSearchScope globalSearchScope) {
		// 声明布尔变量isTerminal并初始化为false，用于标识是否为终端环境
		boolean isTerminal = false;
		// 使用try-catch块捕获可能的异常
		try {
			// TerminalExecutionConsole is used in JBTerminalWidget
			// 判断控制台视图是否为TerminalExecutionConsole的实例，以确定是否为终端环境
			isTerminal = consoleView instanceof TerminalExecutionConsole;
		} catch (Throwable ignored) {
			// 捕获所有异常并忽略，保持isTerminal为false
		}
		// 调用重载的getDefaultFilters方法，传入项目和终端标识，返回过滤器数组
		return getDefaultFilters(project, isTerminal);
	}

	/**
	 * 为指定项目获取默认过滤器
	 * 
	 * @param project 项目实例
	 * @return Filter数组
	 */
	// 使用@NotNull注解标记返回值不为空
	@NotNull
	// 使用@Override注解标记此方法重写了父类的方法
	@Override
	// 定义公共方法getDefaultFilters，接收项目参数，返回非空的Filter数组
	public Filter @NotNull [] getDefaultFilters(@NotNull final Project project) {
		// 调用重载的getDefaultFilters方法，传入项目和true（默认为终端环境），返回过滤器数组
		return getDefaultFilters(project, true);
	}

	/**
	 * 为指定项目获取默认过滤器，并指定是否为终端环境
	 * 
	 * @param project 项目实例
	 * @param isTerminal 是否为终端环境
	 * @return Filter数组
	 */
	// 使用@NotNull注解标记返回值不为空
	@NotNull
	// 定义公共方法getDefaultFilters，接收项目和终端标识参数，返回Filter数组
	public Filter[] getDefaultFilters(@NotNull final Project project, final boolean isTerminal) {
		// TODO Hack: In the Terminal, Filter only belongs to one thread, but in ConsoleView,
		//     Filter will run in multiple threads, so set the default value of isTerminal to
		//     false. There's no better way determine whether a Filter is running in the Terminal,
		//     even if it's not a good way. Since Filter is cached as a singleton, everything has
		//     become more complicated.
		// 从缓存中获取项目对应的过滤器数组，如果不存在则创建新的AwesomeLinkFilter实例并存入缓存
		Filter[] filters = cache.computeIfAbsent(project, (key) -> new Filter[]{new AwesomeLinkFilter(project)});
		// 将过滤器数组的第一个元素转换为AwesomeLinkFilter类型，并设置其isTerminal属性值
		((AwesomeLinkFilter) filters[0]).isTerminal.set(isTerminal);
		// 返回过滤器数组
		return filters;
	}
}