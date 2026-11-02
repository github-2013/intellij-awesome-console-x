package awesome.console.util;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

/**
 * 以磁盘绝对路径为身份的多文件 chooser。
 * 上链时不在读锁里兑 VFS；点击后再 refresh，避免 VFS 滞后时整段放弃。
 */
public class PathListHyperlinkInfo implements HyperlinkInfo {

	private final List<String> absolutePaths;
	private final List<VirtualFile> virtualFiles;
	private final int row;
	private final int col;
	private final AtomicBoolean navigateInFlight = new AtomicBoolean();

	public PathListHyperlinkInfo(@NotNull List<String> absolutePaths, int row, int col) {
		this(absolutePaths, List.of(), row, col);
	}

	public PathListHyperlinkInfo(@NotNull List<String> absolutePaths, @NotNull List<VirtualFile> virtualFiles,
			int row, int col) {
		this.absolutePaths = List.copyOf(absolutePaths);
		this.virtualFiles = List.copyOf(virtualFiles);
		this.row = row;
		this.col = col;
	}

	@NotNull
	public List<String> getAbsolutePaths() {
		return absolutePaths;
	}

	/**
	 * 在非读锁线程 refresh 后收集 VFS 身份，供测试与 navigate 共用。
	 */
	@NotNull
	public List<VirtualFile> refreshAndCollectVirtualFiles() {
		Map<String, VirtualFile> files = new LinkedHashMap<>();
		// 非磁盘 VFS 必须保留协议身份，不能用裸路径通过 LocalFileSystem 恢复。
		for (VirtualFile file : virtualFiles) {
			if (file.isValid()) files.put(file.getUrl(), file);
		}
		for (String path : absolutePaths) {
			VirtualFile virtualFile = FileUtils.refreshAndFindLocalFile(path);
			if (virtualFile != null && virtualFile.isValid()) {
				files.put(virtualFile.getUrl(), virtualFile);
			}
		}
		return new ArrayList<>(files.values());
	}

	@Override
	public void navigate(@NotNull Project project) {
		if (project.isDisposed()) {
			return;
		}
		if (!navigateInFlight.compareAndSet(false, true)) {
			return;
		}
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			List<VirtualFile> files;
			try {
				files = refreshAndCollectVirtualFiles();
			} finally {
				navigateInFlight.set(false);
			}
			ApplicationManager.getApplication().invokeLater(() -> {
				if (project.isDisposed()) {
					return;
				}
				if (files.size() >= 2) {
					HyperlinkUtils.buildMultipleFilesHyperlinkInfo(project, files, row, col)
							.navigate(project);
					return;
				}
				// 刷新后仍不足 2 个身份：不把第 0 条升成单链
				Messages.showErrorDialog(
						project,
						"Cannot find files " + StringUtil.trimMiddle(String.join(", ", absolutePaths), 150),
						"Cannot Open File"
				);
			});
		});
	}
}
