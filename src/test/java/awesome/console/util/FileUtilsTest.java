package awesome.console.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.Assert;

/**
 * FileUtils 路径兑 VFS 测试。
 */
public class FileUtilsTest extends BasePlatformTestCase {

	/**
	 * 尚未进 VFS 的 jar 条目：refreshAndFindLocalFile 须先 refresh 本地 jar 再找到条目。
	 * 读锁下 findFileByPath 仍不得同步 refresh。
	 */
	public void testRefreshAndFindLocalFileRefreshesJarBeforeEntryLookup() throws Exception {
		Path workDir = Files.createTempDirectory("r11-jar");
		Path jarPath = workDir.resolve("r11-sample.jar");
		Path localFile = workDir.resolve("r11-local.txt");
		try {
			VirtualFile dirVf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(workDir);
			Assert.assertNotNull("工作目录应先进入 VFS", dirVf);
			dirVf.getChildren();

			try (OutputStream out = Files.newOutputStream(jarPath);
					JarOutputStream jar = new JarOutputStream(out)) {
				jar.putNextEntry(new JarEntry("r11-entry.txt"));
				jar.write("hello-r11".getBytes(StandardCharsets.UTF_8));
				jar.closeEntry();
			}
			Files.writeString(localFile, "local-r11\n");

			String jarAbs = jarPath.toAbsolutePath().toString().replace('\\', '/');
			String localAbs = localFile.toAbsolutePath().toString().replace('\\', '/');
			String entryPath = jarAbs + FileUtils.JAR_SEPARATOR + "r11-entry.txt";

			Assert.assertNull("夹具 jar 在父目录已缓存后不得自动进 VFS",
					LocalFileSystem.getInstance().findFileByPath(jarAbs));
			Assert.assertNull("修前 JarFileSystem.find 对未 refresh 的 jar 应为 null",
					JarFileSystem.getInstance().findFileByPath(entryPath));
			Assert.assertNull("夹具本地文件不得提前进入 VFS",
					LocalFileSystem.getInstance().findFileByPath(localAbs));

			VirtualFile fromRead = refreshOnPooledReadAction(() -> FileUtils.findFileByPath(entryPath));
			Assert.assertNull("读锁下 findFileByPath 不得因 jar 去同步 refresh", fromRead);
			VirtualFile localFromRead = refreshOnPooledReadAction(() -> FileUtils.findFileByPath(localAbs));
			Assert.assertNull("读锁下 findFileByPath 不得同步 refresh 本地文件", localFromRead);

			VirtualFile entry = refreshOnPooled(() -> FileUtils.refreshAndFindLocalFile(entryPath));
			Assert.assertNotNull("refresh 本地 jar 后应找到条目", entry);
			Assert.assertTrue("条目路径应落在 jar 内",
					entry.getPath().replace('\\', '/').endsWith("r11-entry.txt"));
			Assert.assertEquals("hello-r11", new String(entry.contentsToByteArray(), StandardCharsets.UTF_8));

			VirtualFile local = refreshOnPooled(() -> FileUtils.refreshAndFindLocalFile(localAbs));
			Assert.assertNotNull("普通本地文件仍应 refresh 得到", local);
			Assert.assertTrue(local.getPath().replace('\\', '/').endsWith("r11-local.txt"));
		} finally {
			Files.deleteIfExists(jarPath);
			Files.deleteIfExists(localFile);
			Files.deleteIfExists(workDir);
		}
	}

	private static VirtualFile refreshOnPooled(Supplier<VirtualFile> action) {
		AtomicReference<VirtualFile> result = new AtomicReference<>();
		AtomicBoolean done = new AtomicBoolean();
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try {
				result.set(action.get());
			} finally {
				done.set(true);
			}
		});
		PlatformTestUtil.waitWithEventsDispatching(
				() -> "等待 pooled 线程兑 VFS",
				done::get,
				10_000
		);
		return result.get();
	}

	private static VirtualFile refreshOnPooledReadAction(Supplier<VirtualFile> action) {
		return refreshOnPooled(() -> ReadAction.compute(action::get));
	}
}
