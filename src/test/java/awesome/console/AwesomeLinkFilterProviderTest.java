package awesome.console;

import com.intellij.execution.filters.Filter;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/**
 * AwesomeLinkFilterProvider 缓存与创建入口测试。
 */
public class AwesomeLinkFilterProviderTest extends BasePlatformTestCase {

	/**
	 * 已 dispose 的项目不得走 computeIfAbsent，不得 new Filter，也不得写入 cache。
	 */
	public void testDisposedProjectDoesNotCreateFilter() throws Exception {
		AtomicInteger unexpectedProjectCalls = new AtomicInteger();
		Project disposed = (Project) Proxy.newProxyInstance(
				Project.class.getClassLoader(),
				new Class<?>[]{Project.class},
				(proxy, method, args) -> {
					String name = method.getName();
					if ("isDisposed".equals(name)) {
						return true;
					}
					if ("getName".equals(name)) {
						return "r14-disposed";
					}
					if ("equals".equals(name)) {
						return proxy == args[0];
					}
					if ("hashCode".equals(name)) {
						return System.identityHashCode(proxy);
					}
					if ("toString".equals(name)) {
						return "r14-disposed";
					}
					unexpectedProjectCalls.incrementAndGet();
					throw new AssertionError("已 dispose 项目不应再进入 Filter 构造: " + name);
				});

		int cacheBefore = providerCache().size();
		AwesomeLinkFilter created = AwesomeLinkFilterProvider.getFilter(disposed);
		AwesomeLinkFilterProvider provider = new AwesomeLinkFilterProvider();
		Filter[] defaults = provider.getDefaultFilters(disposed);

		assertNull("getFilter 对已 dispose 项目须返回 null 且不创建", created);
		assertEquals("getDefaultFilters 对已 dispose 项目须返回空数组", 0, defaults.length);
		assertFalse("cache 不得写入已 dispose 的项目", providerCache().containsKey(disposed));
		assertEquals("不得因 dispose 短路而改动其他 cache 条目", cacheBefore, providerCache().size());
		assertNull("getFilterIfExists 仍只读，不得创建", AwesomeLinkFilterProvider.getFilterIfExists(disposed));
		assertEquals("除 isDisposed/身份方法外不得碰 Project", 0, unexpectedProjectCalls.get());
	}

	/**
	 * 未 dispose 项目仍按单例写入 cache；getFilterIfExists 只读不创建。
	 */
	public void testLiveProjectFilterIsCachedSingleton() {
		Project project = getProject();
		assertFalse("测试项目应仍可用", project.isDisposed());

		int cacheBefore = providerCache().size();
		AwesomeLinkFilter peeked = AwesomeLinkFilterProvider.getFilterIfExists(project);
		assertEquals("getFilterIfExists 只读，不得写入 cache",
				cacheBefore, providerCache().size());
		assertSame("连续只读查询不得创建新实例",
				peeked, AwesomeLinkFilterProvider.getFilterIfExists(project));

		AwesomeLinkFilter first = AwesomeLinkFilterProvider.getFilter(project);
		assertNotNull("未 dispose 项目应得到 Filter", first);
		if (peeked != null) {
			assertSame("已缓存时 getFilter 必须复用", peeked, first);
		}
		assertTrue("未 dispose 项目应在 cache 中", providerCache().containsKey(project));
		assertSame("同一项目应复用单例", first, AwesomeLinkFilterProvider.getFilter(project));
		assertSame("getFilterIfExists 只返回已缓存实例",
				first, AwesomeLinkFilterProvider.getFilterIfExists(project));

		AwesomeLinkFilterProvider provider = new AwesomeLinkFilterProvider();
		Filter[] defaults = provider.getDefaultFilters(project);
		assertEquals(1, defaults.length);
		assertSame(first, defaults[0]);
	}

	@NotNull
	@SuppressWarnings("unchecked")
	private static Map<Project, Filter[]> providerCache() {
		try {
			Field field = AwesomeLinkFilterProvider.class.getDeclaredField("cache");
			field.setAccessible(true);
			return (Map<Project, Filter[]>) field.get(null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}
}
