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
 * 
 * @author awesome-console
 */
public class AwesomeLinkFilterProvider extends ConsoleDependentFilterProvider {
	/** Filter缓存，为每个项目维护一个Filter实例 */
	private static final Map<Project, Filter[]> cache = new ConcurrentHashMap<>();

	/**
	 * 构造函数，订阅项目关闭事件以清理缓存
	 */
	public AwesomeLinkFilterProvider() {
		ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
			@Override
			public void projectClosed(@NotNull Project project) {
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
	@NotNull
	@Override
	public Filter @NotNull [] getDefaultFilters(@NotNull final ConsoleView consoleView, @NotNull final Project project, @NotNull final GlobalSearchScope globalSearchScope) {
		boolean isTerminal = false;
		try {
			// TerminalExecutionConsole is used in JBTerminalWidget
			isTerminal = consoleView instanceof TerminalExecutionConsole;
		} catch (Throwable ignored) {
		}
		return getDefaultFilters(project, isTerminal);
	}

	/**
	 * 为指定项目获取默认过滤器
	 * 
	 * @param project 项目实例
	 * @return Filter数组
	 */
	@NotNull
	@Override
	public Filter @NotNull [] getDefaultFilters(@NotNull final Project project) {
		return getDefaultFilters(project, true);
	}

	/**
	 * 为指定项目获取默认过滤器，并指定是否为终端环境
	 * 
	 * @param project 项目实例
	 * @param isTerminal 是否为终端环境
	 * @return Filter数组
	 */
	@NotNull
	public Filter[] getDefaultFilters(@NotNull final Project project, final boolean isTerminal) {
		// TODO Hack: In the Terminal, Filter only belongs to one thread, but in ConsoleView,
		//     Filter will run in multiple threads, so set the default value of isTerminal to
		//     false. There's no better way determine whether a Filter is running in the Terminal,
		//     even if it's not a good way. Since Filter is cached as a singleton, everything has
		//     become more complicated.
		Filter[] filters = cache.computeIfAbsent(project, (key) -> new Filter[]{new AwesomeLinkFilter(project)});
		((AwesomeLinkFilter) filters[0]).isTerminal.set(isTerminal);
		return filters;
	}
}