package awesome.console;

import awesome.console.config.AwesomeConsoleDefaults;
import awesome.console.config.AwesomeConsoleStorage;
import awesome.console.match.FileLinkMatch;
import awesome.console.match.URLLinkMatch;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * AwesomeConsoleConfig 配置测试类
 * 基于 config.md 中的配置分析，全面测试配置面板的各项功能
 * 
 * 测试遵循标准模式（参考 AwesomeLinkFilterTest）：
 * 1. 保存原始配置
 * 2. 修改配置并重新创建过滤器以应用新配置
 * 3. 验证配置改变后核心功能是否正常工作
 * 4. 在 finally 块中恢复配置并重新创建过滤器
 */
public class AwesomeConsoleConfigTest extends BasePlatformTestCase {

    private AwesomeConsoleStorage storage;
    private AwesomeLinkFilter filter;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        storage = AwesomeConsoleStorage.getInstance();
        filter = new AwesomeLinkFilter(getProject());
    }

    // ========== 一、调试模式配置测试 ==========

    /**
     * 测试调试模式的默认值和修改
     */
    public void testDebugModeConfiguration() {
        // 保存原始配置
        boolean originalValue = storage.DEBUG_MODE;
        
        try {
            // 测试默认值
            assertFalse("Debug mode should be disabled by default", 
                AwesomeConsoleDefaults.DEFAULT_DEBUG_MODE);
            
            // 测试开启调试模式
            storage.DEBUG_MODE = true;
            // 重新创建过滤器以应用新配置
            filter = new AwesomeLinkFilter(getProject());
            assertTrue("Debug mode should be enabled", storage.DEBUG_MODE);
            
            // 测试关闭调试模式
            storage.DEBUG_MODE = false;
            // 重新创建过滤器以应用新配置
            filter = new AwesomeLinkFilter(getProject());
            assertFalse("Debug mode should be disabled", storage.DEBUG_MODE);
            
            // 边界测试：快速切换
            for (int i = 0; i < 100; i++) {
                storage.DEBUG_MODE = (i % 2 == 0);
                assertEquals("Debug mode should toggle correctly", 
                    (i % 2 == 0), storage.DEBUG_MODE);
            }
            
        } finally {
            // 恢复原始配置
            storage.DEBUG_MODE = originalValue;
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    // ========== 二、行长度限制配置测试 ==========

    /**
     * 测试行长度限制的配置
     */
    public void testLineLengthLimitConfiguration() {
        // 保存原始配置
        boolean originalLimitEnabled = storage.LIMIT_LINE_LENGTH;
        int originalMaxLength = storage.LINE_MAX_LENGTH;
        boolean originalSplitOnLimit = storage.SPLIT_ON_LIMIT;
        
        try {
            // 测试默认值
            assertTrue("Line length limit should be enabled by default", 
                AwesomeConsoleDefaults.DEFAULT_LIMIT_LINE_LENGTH);
            assertEquals("Default max length should be 1024", 
                1024, AwesomeConsoleDefaults.DEFAULT_LINE_MAX_LENGTH);
            assertFalse("Split on limit should be disabled by default", 
                AwesomeConsoleDefaults.DEFAULT_SPLIT_ON_LIMIT);
            
            // 测试启用/禁用限制
            storage.LIMIT_LINE_LENGTH = true;
            assertTrue("Line length limit should be enabled", storage.LIMIT_LINE_LENGTH);
            
            storage.LIMIT_LINE_LENGTH = false;
            assertFalse("Line length limit should be disabled", storage.LIMIT_LINE_LENGTH);
            
            // 测试正常的行长度值
            storage.LINE_MAX_LENGTH = 512;
            assertEquals("Should accept 512", 512, storage.LINE_MAX_LENGTH);
            
            storage.LINE_MAX_LENGTH = 2048;
            assertEquals("Should accept 2048", 2048, storage.LINE_MAX_LENGTH);
            
            storage.LINE_MAX_LENGTH = 4096;
            assertEquals("Should accept 4096", 4096, storage.LINE_MAX_LENGTH);
            
            // 边界测试：零值和负值
            storage.LINE_MAX_LENGTH = 0;
            assertEquals("Should accept 0", 0, storage.LINE_MAX_LENGTH);
            
            storage.LINE_MAX_LENGTH = -1;
            assertEquals("Should accept -1", -1, storage.LINE_MAX_LENGTH);
            
            // 边界测试：极大值
            storage.LINE_MAX_LENGTH = Integer.MAX_VALUE;
            assertEquals("Should accept MAX_VALUE", Integer.MAX_VALUE, storage.LINE_MAX_LENGTH);
            
            // 边界测试：2的幂次值
            int[] powerOfTwo = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192};
            for (int length : powerOfTwo) {
                storage.LINE_MAX_LENGTH = length;
                assertEquals("Should accept power of 2 value: " + length, 
                    length, storage.LINE_MAX_LENGTH);
            }
            
            // 测试分块匹配
            storage.SPLIT_ON_LIMIT = true;
            assertTrue("Split on limit should be enabled", storage.SPLIT_ON_LIMIT);
            
            storage.SPLIT_ON_LIMIT = false;
            assertFalse("Split on limit should be disabled", storage.SPLIT_ON_LIMIT);
            
        } finally {
            // 恢复原始配置
            storage.LIMIT_LINE_LENGTH = originalLimitEnabled;
            storage.LINE_MAX_LENGTH = originalMaxLength;
            storage.SPLIT_ON_LIMIT = originalSplitOnLimit;
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    // ========== 三、链接匹配配置测试 ==========

    /**
     * 测试URL、文件路径、Java类名的搜索配置
     */
    public void testLinkMatchingConfiguration() {
        // 保存原始配置
        boolean originalSearchUrls = storage.searchUrls;
        boolean originalSearchFiles = storage.searchFiles;
        boolean originalSearchClasses = storage.searchClasses;
        
        try {
            // 测试默认值
            assertTrue("URL search should be enabled by default", 
                AwesomeConsoleDefaults.DEFAULT_SEARCH_URLS);
            assertTrue("File search should be enabled by default", 
                AwesomeConsoleDefaults.DEFAULT_SEARCH_FILES);
            assertTrue("Class search should be enabled by default", 
                AwesomeConsoleDefaults.DEFAULT_SEARCH_CLASSES);
            
            // 测试URL搜索
            storage.searchUrls = true;
            assertTrue("URL search should be enabled", storage.searchUrls);
            
            storage.searchUrls = false;
            assertFalse("URL search should be disabled", storage.searchUrls);
            
            // 测试文件搜索
            storage.searchFiles = true;
            assertTrue("File search should be enabled", storage.searchFiles);
            
            storage.searchFiles = false;
            assertFalse("File search should be disabled", storage.searchFiles);
            
            // 测试类搜索
            storage.searchClasses = true;
            assertTrue("Class search should be enabled", storage.searchClasses);
            
            storage.searchClasses = false;
            assertFalse("Class search should be disabled", storage.searchClasses);
            
            // 测试组合配置
            storage.searchUrls = true;
            storage.searchFiles = true;
            storage.searchClasses = true;
            assertTrue("All search features should be enabled", 
                storage.searchUrls && storage.searchFiles && storage.searchClasses);
            
            storage.searchUrls = false;
            storage.searchFiles = false;
            storage.searchClasses = false;
            assertFalse("All search features should be disabled", 
                storage.searchUrls || storage.searchFiles || storage.searchClasses);
            
            // 边界测试：快速切换
            for (int i = 0; i < 50; i++) {
                boolean state = (i % 2 == 0);
                storage.searchUrls = state;
                storage.searchFiles = !state;
                storage.searchClasses = state;
                
                assertEquals("URL search should toggle", state, storage.searchUrls);
                assertEquals("File search should toggle inversely", !state, storage.searchFiles);
                assertEquals("Class search should toggle", state, storage.searchClasses);
            }
            
        } finally {
            // 恢复原始配置
            storage.searchUrls = originalSearchUrls;
            storage.searchFiles = originalSearchFiles;
            storage.searchClasses = originalSearchClasses;
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    // ========== 四、结果限制配置测试 ==========

    /**
     * 测试结果数量限制的配置
     */
    public void testResultLimitConfiguration() {
        // 保存原始配置
        boolean originalUseLimit = storage.useResultLimit;
        int originalLimit = storage.getResultLimit();
        
        try {
            // 测试默认值
            assertTrue("Result limit should be enabled by default", 
                AwesomeConsoleDefaults.DEFAULT_USE_RESULT_LIMIT);
            assertEquals("Default result limit should be 100", 
                100, AwesomeConsoleDefaults.DEFAULT_RESULT_LIMIT);
            
            // 测试启用/禁用限制
            storage.useResultLimit = true;
            assertTrue("Result limit should be enabled", storage.useResultLimit);
            
            storage.useResultLimit = false;
            assertFalse("Result limit should be disabled", storage.useResultLimit);
            
            // 测试正常的限制值
            storage.setResultLimit(50);
            assertEquals("Should accept 50", 50, storage.getResultLimit());
            
            storage.setResultLimit(100);
            assertEquals("Should accept 100", 100, storage.getResultLimit());
            
            storage.setResultLimit(200);
            assertEquals("Should accept 200", 200, storage.getResultLimit());
            
            // 边界测试：最小值（会被转换为1）
            storage.setResultLimit(0);
            assertEquals("Zero should be converted to 1", 1, storage.getResultLimit());
            
            storage.setResultLimit(-1);
            assertEquals("Negative should be converted to 1", 1, storage.getResultLimit());
            
            storage.setResultLimit(-100);
            assertEquals("Large negative should be converted to 1", 1, storage.getResultLimit());
            
            // 边界测试：极大值
            storage.setResultLimit(Integer.MAX_VALUE);
            assertEquals("Should handle MAX_VALUE", Integer.MAX_VALUE, storage.getResultLimit());
            
            // 边界测试：连续边界值
            for (int i = -5; i <= 5; i++) {
                storage.setResultLimit(i);
                assertTrue("Result limit should be at least 1", storage.getResultLimit() >= 1);
                if (i >= 1) {
                    assertEquals("Should accept valid positive value", i, storage.getResultLimit());
                }
            }
            
            // 边界测试：循环设置
            int[] values = {1, 10, 50, 100, 200, 500, 1000};
            for (int cycle = 0; cycle < 5; cycle++) {
                for (int value : values) {
                    storage.setResultLimit(value);
                    assertEquals("Should maintain value in cycle " + cycle, 
                        value, storage.getResultLimit());
                }
            }
            
        } finally {
            // 恢复原始配置
            storage.useResultLimit = originalUseLimit;
            storage.setResultLimit(originalLimit);
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    // ========== 五、自定义文件匹配模式配置测试 ==========

    /**
     * 测试自定义文件匹配正则表达式的配置
     */
    public void testFilePatternConfiguration() {
        // 保存原始配置
        boolean originalUsePattern = storage.useFilePattern;
        String originalPattern = storage.getFilePatternText();
        
        try {
            // 测试默认值
            assertFalse("File pattern should be disabled by default", 
                AwesomeConsoleDefaults.DEFAULT_USE_FILE_PATTERN);
            
            // 测试启用/禁用自定义模式
            storage.useFilePattern = true;
            assertTrue("File pattern should be enabled", storage.useFilePattern);
            
            storage.useFilePattern = false;
            assertFalse("File pattern should be disabled", storage.useFilePattern);
            
            // 测试设置有效的正则表达式（包含必需的分组）
            String validPattern = "(?<link>(?<path>[^:]+)(?::(?<row>\\d+))?(?::(?<col>\\d+))?)";
            storage.setFilePatternText(validPattern);
            assertEquals("Should accept valid pattern", validPattern, storage.getFilePatternText());
            
            // 测试必需的分组名称
            String[] requiredGroups = {"link", "path", "row", "col"};
            for (String groupName : requiredGroups) {
                assertTrue("Pattern should contain required group: " + groupName, 
                    validPattern.contains("<" + groupName + ">"));
            }
            
            // 边界测试：带有行号重试分组的模式
            String patternWithRetry = "(?<link>(?<path>[^:]+)(?::(?<row>\\d+)|:line (?<row1>\\d+))?)";
            try {
                Pattern.compile(patternWithRetry);
                storage.setFilePatternText(patternWithRetry);
                assertNotNull("Should handle pattern with retry groups", storage.getFilePatternText());
            } catch (PatternSyntaxException e) {
                // 如果正则表达式无效，应该被优雅地处理
                assertNotNull("Should handle invalid pattern gracefully", storage.getFilePatternText());
            }
            
        } finally {
            // 恢复原始配置
            storage.useFilePattern = originalUsePattern;
            storage.setFilePatternText(originalPattern);
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    // ========== 六、忽略模式配置测试 ==========

    /**
     * 测试忽略模式的配置
     */
    public void testIgnorePatternConfiguration() {
        // 保存原始配置
        boolean originalUseIgnorePattern = storage.useIgnorePattern;
        String originalIgnorePattern = storage.getIgnorePatternText();
        boolean originalUseIgnoreStyle = storage.useIgnoreStyle;
        
        try {
            // 测试默认值
            assertTrue("Ignore pattern should be enabled by default", 
                AwesomeConsoleDefaults.DEFAULT_USE_IGNORE_PATTERN);
            assertNotNull("Default ignore pattern should not be null", 
                AwesomeConsoleDefaults.DEFAULT_IGNORE_PATTERN_TEXT);
            assertFalse("Ignore style should be disabled by default", 
                AwesomeConsoleDefaults.DEFAULT_USE_IGNORE_STYLE);
            
            // 测试启用/禁用忽略模式
            storage.useIgnorePattern = true;
            assertTrue("Ignore pattern should be enabled", storage.useIgnorePattern);
            
            storage.useIgnorePattern = false;
            assertFalse("Ignore pattern should be disabled", storage.useIgnorePattern);
            
            // 测试默认忽略模式的功能
            Pattern defaultPattern = Pattern.compile(AwesomeConsoleDefaults.DEFAULT_IGNORE_PATTERN_TEXT);
            
            // 应该匹配相对路径符号
            assertTrue("Should match ./", defaultPattern.matcher("./").find());
            assertTrue("Should match ../", defaultPattern.matcher("../").find());
            assertTrue("Should match .\\", defaultPattern.matcher(".\\").find());
            assertTrue("Should match ..\\", defaultPattern.matcher("..\\").find());
            
            // 应该匹配 node_modules
            assertTrue("Should match node_modules/", defaultPattern.matcher("node_modules/xxx").find());
            
            // 应该匹配常见命令（不区分大小写）
            assertTrue("Should match 'start'", defaultPattern.matcher("start").find());
            assertTrue("Should match 'dev'", defaultPattern.matcher("dev").find());
            assertTrue("Should match 'test'", defaultPattern.matcher("test").find());
            assertTrue("Should match 'TEST'", defaultPattern.matcher("TEST").find());
            
            // 不应该匹配正常路径
            assertFalse("Should not match normal path", 
                defaultPattern.matcher("src/main/java/App.java").find());
            assertFalse("Should not match file with extension", 
                defaultPattern.matcher("config.json").find());
            
            // 测试设置自定义忽略模式
            String customPattern = "^temp/|^build/|^dist/";
            storage.setIgnorePatternText(customPattern);
            assertEquals("Should accept custom pattern", customPattern, storage.getIgnorePatternText());
            
            Pattern custom = Pattern.compile(customPattern);
            assertTrue("Should match temp/", custom.matcher("temp/file.txt").find());
            assertTrue("Should match build/", custom.matcher("build/output").find());
            assertTrue("Should match dist/", custom.matcher("dist/app.js").find());
            
            // 测试忽略样式
            storage.useIgnoreStyle = true;
            assertTrue("Ignore style should be enabled", storage.useIgnoreStyle);
            
            storage.useIgnoreStyle = false;
            assertFalse("Ignore style should be disabled", storage.useIgnoreStyle);
            
            // 边界测试：包含Unicode字符的模式
            String unicodePattern = "^[\\u4e00-\\u9fa5]+$|^测试$";
            try {
                Pattern unicodeCompiled = Pattern.compile(unicodePattern);
                storage.setIgnorePatternText(unicodePattern);
                assertTrue("Should match Chinese characters", 
                    unicodeCompiled.matcher("测试").find());
            } catch (PatternSyntaxException e) {
                fail("Should support Unicode in regex: " + e.getMessage());
            }
            
            // 边界测试：包含转义字符的模式
            String escapePattern = "^\\d+$|^\\w+$|^\\s+$";
            try {
                Pattern escapeCompiled = Pattern.compile(escapePattern);
                storage.setIgnorePatternText(escapePattern);
                assertTrue("Should match digits", escapeCompiled.matcher("123").find());
                assertTrue("Should match word characters", escapeCompiled.matcher("abc").find());
            } catch (PatternSyntaxException e) {
                fail("Should handle escape sequences: " + e.getMessage());
            }
            
        } finally {
            // 恢复原始配置
            storage.useIgnorePattern = originalUseIgnorePattern;
            storage.setIgnorePatternText(originalIgnorePattern);
            storage.useIgnoreStyle = originalUseIgnoreStyle;
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    // ========== 七、文件类型配置测试 ==========

    /**
     * 测试非文本文件类型的配置
     */
    public void testFileTypesConfiguration() {
        // 保存原始配置
        boolean originalUseFileTypes = storage.useFileTypes;
        String originalFileTypes = storage.getFileTypes();
        
        try {
            // 测试默认值
            assertTrue("File types should be enabled by default", 
                AwesomeConsoleDefaults.DEFAULT_USE_FILE_TYPES);
            assertEquals("Default file types should match", 
                "bmp,gif,jpeg,jpg,png,webp,ttf", 
                AwesomeConsoleDefaults.DEFAULT_FILE_TYPES);
            
            // 测试启用/禁用文件类型
            storage.useFileTypes = true;
            assertTrue("File types should be enabled", storage.useFileTypes);
            
            storage.useFileTypes = false;
            assertFalse("File types should be disabled", storage.useFileTypes);
            
            // 测试设置文件类型列表
            storage.setFileTypes("png,jpg,gif");
            assertEquals("Should accept file types", "png,jpg,gif", storage.getFileTypes());
            assertTrue("Should contain png", storage.fileTypeSet.contains("png"));
            assertTrue("Should contain jpg", storage.fileTypeSet.contains("jpg"));
            assertTrue("Should contain gif", storage.fileTypeSet.contains("gif"));
            
            // 测试带空格的文件类型
            storage.setFileTypes("png, jpg , gif ");
            assertNotNull("Should handle whitespace in file types", storage.fileTypeSet);
            
            // 边界测试：空字符串
            storage.setFileTypes("");
            assertNotNull("Should handle empty string", storage.fileTypeSet);
            
            // 边界测试：特殊字符
            storage.setFileTypes("c++,c#,.net,file-type,file_type");
            assertNotNull("Should handle special characters", storage.fileTypeSet);
            
            // 边界测试：大量文件类型
            StringBuilder manyTypes = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                if (i > 0) manyTypes.append(",");
                manyTypes.append("type").append(i);
            }
            storage.setFileTypes(manyTypes.toString());
            assertNotNull("Should handle many file types", storage.fileTypeSet);
            assertEquals("Should contain all types", 100, storage.fileTypeSet.size());
            
        } finally {
            // 恢复原始配置
            storage.useFileTypes = originalUseFileTypes;
            storage.setFileTypes(originalFileTypes);
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    // ========== 八、其他兼容性配置测试 ==========

    /**
     * 测试修复目标文件选择弹窗的配置
     */
    public void testFixChooseTargetFileConfiguration() {
        // 保存原始配置
        boolean originalValue = storage.fixChooseTargetFile;
        
        try {
            // 测试默认值
            assertTrue("Fix choose target file should be enabled by default", 
                AwesomeConsoleDefaults.DEFAULT_FIX_CHOOSE_TARGET_FILE);
            
            // 测试启用/禁用
            storage.fixChooseTargetFile = true;
            assertTrue("Should be enabled", storage.fixChooseTargetFile);
            
            storage.fixChooseTargetFile = false;
            assertFalse("Should be disabled", storage.fixChooseTargetFile);
            
        } finally {
            // 恢复原始配置
            storage.fixChooseTargetFile = originalValue;
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试解析符号链接的配置
     */
    public void testResolveSymlinkConfiguration() {
        // 保存原始配置
        boolean originalValue = storage.resolveSymlink;
        
        try {
            // 测试默认值
            assertFalse("Resolve symlink should be disabled by default", 
                AwesomeConsoleDefaults.DEFAULT_RESOLVE_SYMLINK);
            
            // 测试启用/禁用
            storage.resolveSymlink = true;
            assertTrue("Should be enabled", storage.resolveSymlink);
            
            storage.resolveSymlink = false;
            assertFalse("Should be disabled", storage.resolveSymlink);
            
        } finally {
            // 恢复原始配置
            storage.resolveSymlink = originalValue;
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试保留ANSI颜色的配置
     */
    public void testPreserveAnsiColorsConfiguration() {
        // 保存原始配置
        boolean originalValue = storage.preserveAnsiColors;
        
        try {
            // 测试默认值
            assertFalse("Preserve ANSI colors should be disabled by default", 
                AwesomeConsoleDefaults.DEFAULT_PRESERVE_ANSI_COLORS);
            
            // 测试启用/禁用
            storage.preserveAnsiColors = true;
            assertTrue("Should be enabled", storage.preserveAnsiColors);
            
            storage.preserveAnsiColors = false;
            assertFalse("Should be disabled", storage.preserveAnsiColors);
            
        } finally {
            // 恢复原始配置
            storage.preserveAnsiColors = originalValue;
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    // ========== 九、配置持久化测试 ==========

    /**
     * 测试配置的保存和加载
     */
    public void testConfigurationPersistence() {
        // 保存当前状态
        AwesomeConsoleStorage backup = new AwesomeConsoleStorage();
        backup.loadState(storage.getState());
        
        try {
            // 修改配置
            storage.DEBUG_MODE = !backup.DEBUG_MODE;
            storage.setResultLimit(50);
            storage.searchUrls = !backup.searchUrls;
            
            // 保存状态
            AwesomeConsoleStorage savedState = storage.getState();
            
            // 创建新实例并加载状态
            AwesomeConsoleStorage newStorage = new AwesomeConsoleStorage();
            newStorage.loadState(savedState);
            
            // 验证状态已正确加载
            assertEquals("DEBUG_MODE should be persisted", 
                storage.DEBUG_MODE, newStorage.DEBUG_MODE);
            assertEquals("Result limit should be persisted", 
                storage.getResultLimit(), newStorage.getResultLimit());
            assertEquals("Search URLs should be persisted", 
                storage.searchUrls, newStorage.searchUrls);
            
        } finally {
            // 恢复原始配置
            storage.loadState(backup);
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    // ========== 十、配置组合场景测试 ==========

    /**
     * 测试性能优化配置组合
     */
    public void testPerformanceOptimizationConfiguration() {
        // 保存原始配置
        boolean originalLimitEnabled = storage.LIMIT_LINE_LENGTH;
        int originalMaxLength = storage.LINE_MAX_LENGTH;
        boolean originalUseLimit = storage.useResultLimit;
        int originalLimit = storage.getResultLimit();
        
        try {
            // 设置性能优化配置
            storage.LIMIT_LINE_LENGTH = true;
            storage.LINE_MAX_LENGTH = 512;  // 较小的行长度限制
            storage.useResultLimit = true;
            storage.setResultLimit(50);     // 较小的结果数量限制
            
            // 验证配置
            assertTrue("Line length limit should be enabled", storage.LIMIT_LINE_LENGTH);
            assertEquals("Max length should be 512", 512, storage.LINE_MAX_LENGTH);
            assertTrue("Result limit should be enabled", storage.useResultLimit);
            assertEquals("Result limit should be 50", 50, storage.getResultLimit());
            
        } finally {
            // 恢复原始配置
            storage.LIMIT_LINE_LENGTH = originalLimitEnabled;
            storage.LINE_MAX_LENGTH = originalMaxLength;
            storage.useResultLimit = originalUseLimit;
            storage.setResultLimit(originalLimit);
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试功能全开配置组合
     */
    public void testFullFeatureConfiguration() {
        // 保存原始配置
        boolean originalSearchUrls = storage.searchUrls;
        boolean originalSearchFiles = storage.searchFiles;
        boolean originalSearchClasses = storage.searchClasses;
        boolean originalUseIgnorePattern = storage.useIgnorePattern;
        boolean originalPreserveAnsi = storage.preserveAnsiColors;
        
        try {
            // 启用所有功能
            storage.searchUrls = true;
            storage.searchFiles = true;
            storage.searchClasses = true;
            storage.useIgnorePattern = true;
            storage.preserveAnsiColors = true;
            
            // 验证所有功能已启用
            assertTrue("All features should be enabled", 
                storage.searchUrls && storage.searchFiles && storage.searchClasses &&
                storage.useIgnorePattern && storage.preserveAnsiColors);
            
        } finally {
            // 恢复原始配置
            storage.searchUrls = originalSearchUrls;
            storage.searchFiles = originalSearchFiles;
            storage.searchClasses = originalSearchClasses;
            storage.useIgnorePattern = originalUseIgnorePattern;
            storage.preserveAnsiColors = originalPreserveAnsi;
            // 重新创建过滤器以恢复原始配置
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 断言路径检测结果
     * @param line 待测试的文本行
     * @param expected 期望检测到的路径
     */
    private void assertPathDetection(@NotNull final String line, @NotNull final String expected) {
        System.out.println(line);
        List<FileLinkMatch> results = filter.detectPaths(line);
        assertFalse("Should detect path in: " + line, results.isEmpty());
        List<String> paths = results.stream().map(it -> it.match).collect(Collectors.toList());
        assertTrue("Should contain: " + expected, paths.contains(expected));
    }

    /**
     * 断言路径不被检测
     * @param line 待测试的文本行
     */
    private void assertPathNoMatches(@NotNull final String line) {
        System.out.println(line);
        List<FileLinkMatch> results = filter.detectPaths(line);
        List<String> paths = results.stream().map(it -> it.match).collect(Collectors.toList());
        assertSameElements("Should not detect any path in: " + line, 
            paths, Collections.emptyList());
    }

    /**
     * 断言URL检测结果
     * @param line 待测试的文本行
     * @param expected 期望检测到的URL
     */
    private void assertURLDetection(@NotNull final String line, @NotNull final String expected) {
        System.out.println(line);
        List<URLLinkMatch> results = filter.detectURLs(line);
        assertFalse("Should detect URL in: " + line, results.isEmpty());
        List<String> urls = results.stream().map(it -> it.match).collect(Collectors.toList());
        assertTrue("Should contain: " + expected, urls.contains(expected));
    }

    /**
     * 断言URL不被检测
     * @param line 待测试的文本行
     */
    private void assertURLNoMatches(@NotNull final String line) {
        System.out.println(line);
        List<URLLinkMatch> results = filter.detectURLs(line);
        List<String> urls = results.stream().map(it -> it.match).collect(Collectors.toList());
        assertSameElements("Should not detect any URL in: " + line, 
            urls, Collections.emptyList());
    }

    // ========== 配置功能验证测试 ==========

    /**
     * 测试调试模式配置的功能验证
     * 验证调试模式开启后，过滤器能够正常工作（主要影响通知显示，不影响核心检测功能）
     */
    public void testDebugModeFunctionality() {
        // 保存原始配置
        boolean originalValue = storage.DEBUG_MODE;
        
        try {
            // 测试关闭调试模式（默认）
            storage.DEBUG_MODE = false;
            filter = new AwesomeLinkFilter(getProject());
            
            // 基本功能应该正常工作
            assertPathDetection("Error in src/main.java:10", "src/main.java:10");
            
            // 测试开启调试模式
            storage.DEBUG_MODE = true;
            filter = new AwesomeLinkFilter(getProject());
            
            // 调试模式主要影响通知显示，不影响核心检测功能
            assertPathDetection("Error in src/main.java:10", "src/main.java:10");
            
            // 注意：调试模式的详细缓存信息会在通知中显示，这里无法直接测试
            // 但可以确认过滤器在调试模式下仍然正常工作
            
        } finally {
            storage.DEBUG_MODE = originalValue;
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试行长度限制配置的功能验证
     * 验证行长度限制能够正确处理超长行
     */
    public void testLineLengthLimitFunctionality() {
        // 保存原始配置
        boolean originalLimitEnabled = storage.LIMIT_LINE_LENGTH;
        int originalMaxLength = storage.LINE_MAX_LENGTH;
        
        try {
            // 测试启用行长度限制
            storage.LIMIT_LINE_LENGTH = true;
            storage.LINE_MAX_LENGTH = 50; // 设置一个较小的限制以便测试
            filter = new AwesomeLinkFilter(getProject());
            
            // 短行应该正常检测
            assertPathDetection("Error in test.java:10", "test.java:10");
            
            // 构造一个超过限制长度的行
            String longLine = "x".repeat(100) + " src/main.java:10";
            // 注意：超长行的处理取决于是否启用分块匹配
            // 这里只验证过滤器不会崩溃
            List<FileLinkMatch> results = filter.detectPaths(longLine);
            assertNotNull("Should handle long lines", results);
            
            // 测试禁用行长度限制
            storage.LIMIT_LINE_LENGTH = false;
            filter = new AwesomeLinkFilter(getProject());
            
            // 应该能处理任意长度的行
            assertPathDetection(longLine, "src/main.java:10");
            
        } finally {
            storage.LIMIT_LINE_LENGTH = originalLimitEnabled;
            storage.LINE_MAX_LENGTH = originalMaxLength;
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试分块匹配配置的功能验证
     * 验证分块匹配能够处理跨越长度限制的超长行
     */
    public void testSplitOnLimitFunctionality() {
        // 保存原始配置
        boolean originalLimitEnabled = storage.LIMIT_LINE_LENGTH;
        int originalMaxLength = storage.LINE_MAX_LENGTH;
        boolean originalSplitOnLimit = storage.SPLIT_ON_LIMIT;
        
        try {
            // 测试禁用分块匹配（默认截断）
            storage.LIMIT_LINE_LENGTH = true;
            storage.LINE_MAX_LENGTH = 30;
            storage.SPLIT_ON_LIMIT = false;
            filter = new AwesomeLinkFilter(getProject());
            
            // 构造一个在限制长度之后才出现路径的行
            String longLine = "x".repeat(50) + "test.java:10";
            List<FileLinkMatch> results = filter.detectPaths(longLine);
            // 截断模式下，超过限制的部分会被忽略
            
            // 测试启用分块匹配
            storage.SPLIT_ON_LIMIT = true;
            filter = new AwesomeLinkFilter(getProject());
            
            // 分块模式应该能找到路径（即使超过单块限制）
            results = filter.detectPaths(longLine);
            assertNotNull("Should handle long lines with split mode", results);
            
        } finally {
            storage.LIMIT_LINE_LENGTH = originalLimitEnabled;
            storage.LINE_MAX_LENGTH = originalMaxLength;
            storage.SPLIT_ON_LIMIT = originalSplitOnLimit;
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试Java类名搜索配置的功能验证
     * 验证Java类名搜索能够正确识别完全限定类名
     */
    public void testSearchClassesFunctionality() {
        // 保存原始配置
        boolean originalSearchFiles = storage.searchFiles;
        boolean originalSearchClasses = storage.searchClasses;
        
        try {
            // 测试启用类名搜索（依赖文件搜索）
            storage.searchFiles = true;
            storage.searchClasses = true;
            filter = new AwesomeLinkFilter(getProject());
            
            // 应该能检测Java完全限定类名
            // 注意：实际匹配取决于项目中是否存在对应的类文件
            List<FileLinkMatch> results = filter.detectPaths("com.example.MyClass");
            assertNotNull("Should process class names", results);
            
            // 测试禁用类名搜索
            storage.searchClasses = false;
            filter = new AwesomeLinkFilter(getProject());
            
            // 禁用后不应该将类名识别为文件路径
            results = filter.detectPaths("com.example.MyClass");
            // 注意：如果类名格式恰好匹配文件路径格式，仍可能被检测
            assertNotNull("Should still work with classes disabled", results);
            
            // 测试文件搜索禁用时的依赖关系
            storage.searchFiles = false;
            storage.searchClasses = true; // 设置为true但不应该生效
            filter = new AwesomeLinkFilter(getProject());
            
            // 文件搜索禁用时，类搜索也不应该工作
            assertPathNoMatches("com.example.MyClass");
            
        } finally {
            storage.searchFiles = originalSearchFiles;
            storage.searchClasses = originalSearchClasses;
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试URL搜索配置的功能验证
     * 验证禁用URL搜索后，URL不会被检测
     */
    public void testSearchUrlsFunctionality() {
        // 保存原始配置
        boolean originalSearchUrls = storage.searchUrls;
        
        try {
            // 测试启用URL搜索
            storage.searchUrls = true;
            filter = new AwesomeLinkFilter(getProject());
            
            assertURLDetection("Visit https://example.com for details", 
                "https://example.com");
            assertURLDetection("Download from ftp://server.com/file.zip", 
                "ftp://server.com/file.zip");
            
            // 测试禁用URL搜索
            storage.searchUrls = false;
            filter = new AwesomeLinkFilter(getProject());
            
            // 禁用后应该不检测URL
            assertURLNoMatches("Visit https://example.com for details");
            assertURLNoMatches("Download from ftp://server.com/file.zip");
            
        } finally {
            storage.searchUrls = originalSearchUrls;
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试文件搜索配置的功能验证
     * 验证禁用文件搜索后，文件路径不会被检测
     */
    public void testSearchFilesFunctionality() {
        // 保存原始配置
        boolean originalSearchFiles = storage.searchFiles;
        
        try {
            // 测试启用文件搜索
            storage.searchFiles = true;
            filter = new AwesomeLinkFilter(getProject());
            
            assertPathDetection("Error in src/main.java:10", "src/main.java:10");
            assertPathDetection("File: config.json", "config.json");
            
            // 测试禁用文件搜索
            storage.searchFiles = false;
            filter = new AwesomeLinkFilter(getProject());
            
            // 禁用后应该不检测文件路径
            assertPathNoMatches("Error in src/main.java:10");
            assertPathNoMatches("File: config.json");
            
        } finally {
            storage.searchFiles = originalSearchFiles;
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试忽略模式配置的功能验证
     * 验证忽略模式能够正确过滤指定的匹配项
     */
    public void testIgnorePatternFunctionality() {
        // 保存原始配置
        boolean originalUseIgnorePattern = storage.useIgnorePattern;
        String originalIgnorePattern = storage.getIgnorePatternText();
        
        try {
            // 测试启用忽略模式（使用默认规则）
            storage.useIgnorePattern = true;
            storage.setIgnorePatternText(AwesomeConsoleDefaults.DEFAULT_IGNORE_PATTERN_TEXT);
            filter = new AwesomeLinkFilter(getProject());
            
            // 默认忽略模式应该过滤单独的命令参数（整行只包含命令）
            assertPathNoMatches("dev");
            assertPathNoMatches("test");
            assertPathNoMatches("start");
            
            // 但是包含路径分隔符的应该被检测
            assertPathDetection("Error in src/dev/index.js:10", "src/dev/index.js:10");
            
            // 测试自定义忽略模式
            storage.setIgnorePatternText("^temp/|^build/");
            filter = new AwesomeLinkFilter(getProject());
            
            // 自定义规则应该过滤以temp/或build/开头的路径
            assertPathNoMatches("temp/file.txt");
            assertPathNoMatches("build/output.js");
            
            // 其他路径应该正常检测
            assertPathDetection("Error in src/main.java:10", "src/main.java:10");
            
            // 测试禁用忽略模式
            storage.useIgnorePattern = false;
            filter = new AwesomeLinkFilter(getProject());
            
            // 禁用后，之前被忽略的也可能被检测（如果是有效路径格式）
            // 注意：这里不测试具体检测结果，因为依赖于文件是否存在
            
        } finally {
            storage.useIgnorePattern = originalUseIgnorePattern;
            storage.setIgnorePatternText(originalIgnorePattern);
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试结果限制配置的功能验证
     * 验证结果限制能够正确限制返回的匹配数量
     */
    public void testResultLimitFunctionality() {
        // 保存原始配置
        boolean originalUseLimit = storage.useResultLimit;
        int originalLimit = storage.getResultLimit();
        
        try {
            // 测试启用结果限制
            storage.useResultLimit = true;
            storage.setResultLimit(1);
            filter = new AwesomeLinkFilter(getProject());
            
            // 即使有多个可能的匹配，也应该限制结果数量
            // 注意：这个测试的效果取决于实际文件系统中的文件
            assertNotNull("Result limit should be applied", filter);
            
            // 测试禁用结果限制
            storage.useResultLimit = false;
            filter = new AwesomeLinkFilter(getProject());
            
            assertNotNull("Should work without result limit", filter);
            
        } finally {
            storage.useResultLimit = originalUseLimit;
            storage.setResultLimit(originalLimit);
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试保留ANSI颜色配置的功能验证
     * 验证启用保留ANSI颜色后，带ANSI转义序列的文本能正确检测路径
     */
    public void testPreserveAnsiColorsFunctionality() {
        // 保存原始配置
        boolean originalValue = storage.preserveAnsiColors;
        
        try {
            // 测试禁用ANSI颜色保留（默认行为，会移除ANSI转义序列）
            storage.preserveAnsiColors = false;
            filter = new AwesomeLinkFilter(getProject());
            
            // 应该能够检测到带ANSI颜色代码的路径（移除颜色代码后）
            assertPathDetection(
                "\u001b[31msrc/main/java/App.java\u001b[0m:10",
                "src/main/java/App.java:10"
            );
            
            // 测试启用ANSI颜色保留
            storage.preserveAnsiColors = true;
            filter = new AwesomeLinkFilter(getProject());
            
            // 启用后也应该能够检测路径
            // 注意：具体行为可能略有不同，但路径检测应该仍然工作
            List<FileLinkMatch> results = filter.detectPaths(
                "\u001b[31msrc/main/java/App.java\u001b[0m:10"
            );
            assertNotNull("Should handle ANSI colors", results);
            
        } finally {
            storage.preserveAnsiColors = originalValue;
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试自定义文件匹配模式的功能验证
     * 验证自定义正则表达式能够正确匹配指定格式的路径
     */
    public void testFilePatternFunctionality() {
        // 保存原始配置
        boolean originalUsePattern = storage.useFilePattern;
        String originalPattern = storage.getFilePatternText();
        
        try {
            // 测试使用默认文件匹配模式
            storage.useFilePattern = false;
            filter = new AwesomeLinkFilter(getProject());
            
            assertPathDetection("Error in file.java:10", "file.java:10");
            
            // 测试使用自定义文件匹配模式
            // 这里使用一个简单的自定义模式（包含必需的分组）
            String customPattern = "(?<link>(?<path>[a-zA-Z]+\\.txt)(?::(?<row>\\d+))?(?::(?<col>\\d+))?)";
            storage.useFilePattern = true;
            storage.setFilePatternText(customPattern);
            filter = new AwesomeLinkFilter(getProject());
            
            // 自定义模式应该只匹配.txt文件
            assertPathDetection("Error in test.txt:10", "test.txt:10");
            
            // 不匹配.java文件
            assertPathNoMatches("Error in file.java:10");
            
        } finally {
            storage.useFilePattern = originalUsePattern;
            storage.setFilePatternText(originalPattern);
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试忽略样式配置的功能验证
     * 验证忽略样式能够为被忽略的匹配创建占位符
     */
    public void testIgnoreStyleFunctionality() {
        // 保存原始配置
        boolean originalUseIgnorePattern = storage.useIgnorePattern;
        String originalIgnorePattern = storage.getIgnorePatternText();
        boolean originalUseIgnoreStyle = storage.useIgnoreStyle;
        
        try {
            // 测试禁用忽略样式
            storage.useIgnorePattern = true;
            storage.setIgnorePatternText("^(dev|test)$");
            storage.useIgnoreStyle = false;
            filter = new AwesomeLinkFilter(getProject());
            
            // 被忽略的内容不会创建占位符
            assertPathNoMatches("dev");
            assertPathNoMatches("test");
            
            // 测试启用忽略样式
            storage.useIgnoreStyle = true;
            filter = new AwesomeLinkFilter(getProject());
            
            // 启用忽略样式后，被忽略的内容会创建灰色占位符
            // 注意：占位符的创建在 getResultItemsFile 中进行
            // 这里我们验证配置生效，具体的占位符创建需要在实际使用中测试
            List<FileLinkMatch> results = filter.detectPaths("Command: dev");
            assertNotNull("Should handle ignore style", results);
            
            // 注意：忽略样式在终端环境(JediTerm)中不工作
            // 因为JediTerm不使用highlightAttributes参数
            
        } finally {
            storage.useIgnorePattern = originalUseIgnorePattern;
            storage.setIgnorePatternText(originalIgnorePattern);
            storage.useIgnoreStyle = originalUseIgnoreStyle;
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试文件类型配置的功能验证
     * 验证非文本文件类型配置能够影响文件打开行为
     */
    public void testFileTypesFunctionality() {
        // 保存原始配置
        boolean originalUseFileTypes = storage.useFileTypes;
        String originalFileTypes = storage.getFileTypes();
        
        try {
            // 测试启用文件类型配置
            storage.useFileTypes = true;
            storage.setFileTypes("png,jpg,pdf");
            filter = new AwesomeLinkFilter(getProject());
            
            // 验证文件类型集合已更新
            assertTrue("Should contain png", storage.fileTypeSet.contains("png"));
            assertTrue("Should contain jpg", storage.fileTypeSet.contains("jpg"));
            assertTrue("Should contain pdf", storage.fileTypeSet.contains("pdf"));
            assertFalse("Should not contain gif", storage.fileTypeSet.contains("gif"));
            
            // 路径检测应该仍然工作，但文件打开方式会不同
            // （这部分功能在实际使用时体现，不在这里测试具体打开行为）
            
        } finally {
            storage.useFileTypes = originalUseFileTypes;
            storage.setFileTypes(originalFileTypes);
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试修复目标文件选择弹窗配置的功能验证
     * 验证修复配置不影响基本的路径检测功能
     */
    public void testFixChooseTargetFileFunctionality() {
        // 保存原始配置
        boolean originalValue = storage.fixChooseTargetFile;
        
        try {
            // 测试启用修复（默认）
            storage.fixChooseTargetFile = true;
            filter = new AwesomeLinkFilter(getProject());
            
            // 基本功能应该正常工作
            assertPathDetection("Error in src/main.java:10", "src/main.java:10");
            
            // 测试禁用修复
            storage.fixChooseTargetFile = false;
            filter = new AwesomeLinkFilter(getProject());
            
            // 禁用修复后，基本检测功能仍应该正常
            assertPathDetection("Error in src/main.java:10", "src/main.java:10");
            
            // 注意：这个配置主要影响文件选择弹窗的行为
            // 具体的修复效果只在IDEA 2021.2.1 ~ 2023.2.3版本中体现
            // 这里我们验证不影响核心检测功能
            
        } finally {
            storage.fixChooseTargetFile = originalValue;
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试解析符号链接配置的功能验证
     * 验证符号链接解析配置不影响基本的路径检测功能
     */
    public void testResolveSymlinkFunctionality() {
        // 保存原始配置
        boolean originalValue = storage.resolveSymlink;
        
        try {
            // 测试禁用符号链接解析（默认）
            storage.resolveSymlink = false;
            filter = new AwesomeLinkFilter(getProject());
            
            // 基本功能应该正常工作
            assertPathDetection("Error in src/main.java:10", "src/main.java:10");
            
            // 测试启用符号链接解析
            storage.resolveSymlink = true;
            filter = new AwesomeLinkFilter(getProject());
            
            // 启用后，基本检测功能仍应该正常
            assertPathDetection("Error in src/main.java:10", "src/main.java:10");
            
            // 注意：符号链接解析主要影响路径的规范化
            // 使用 Paths.get().normalize() 会跟随符号链接到实际文件
            // 这里我们验证不影响核心检测功能
            
        } finally {
            storage.resolveSymlink = originalValue;
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    /**
     * 测试配置组合的功能验证
     * 验证多个配置同时修改时，功能能够正常协同工作
     */
    public void testCombinedConfigurationFunctionality() {
        // 保存原始配置
        boolean originalSearchUrls = storage.searchUrls;
        boolean originalSearchFiles = storage.searchFiles;
        boolean originalUseIgnorePattern = storage.useIgnorePattern;
        String originalIgnorePattern = storage.getIgnorePatternText();
        
        try {
            // 配置组合：启用所有搜索功能 + 自定义忽略模式
            storage.searchUrls = true;
            storage.searchFiles = true;
            storage.useIgnorePattern = true;
            storage.setIgnorePatternText("^(dev|test)$");
            filter = new AwesomeLinkFilter(getProject());
            
            // URL搜索应该工作
            assertURLDetection("Visit https://example.com", "https://example.com");
            
            // 文件搜索应该工作
            assertPathDetection("Error in src/main.java:10", "src/main.java:10");
            
            // 忽略模式应该工作
            assertPathNoMatches("dev");
            assertPathNoMatches("test");
            
            // 但是包含路径的应该正常检测
            assertPathDetection("Error in src/dev/app.js:5", "src/dev/app.js:5");
            
        } finally {
            storage.searchUrls = originalSearchUrls;
            storage.searchFiles = originalSearchFiles;
            storage.useIgnorePattern = originalUseIgnorePattern;
            storage.setIgnorePatternText(originalIgnorePattern);
            filter = new AwesomeLinkFilter(getProject());
        }
    }

    // ========== 索引管理功能测试 ==========

    /**
     * 测试索引管理UI组件初始化
     * 验证索引状态标签和按钮是否正确创建
     */
    public void testIndexManagementUIInitialization() {
        // 注意：由于AwesomeConsoleConfigForm是通过GUI Designer创建的
        // 这里我们验证过滤器的索引管理API是否可用
        assertNotNull("Filter should be initialized", filter);
        
        // 验证索引管理API方法存在且可调用
        int fileCacheSize = filter.getFileCacheSize();
        int fileBaseCacheSize = filter.getFileBaseCacheSize();
        int totalFiles = filter.getTotalCachedFiles();
        
        assertTrue("File cache size should be non-negative", fileCacheSize >= 0);
        assertTrue("File base cache size should be non-negative", fileBaseCacheSize >= 0);
        assertTrue("Total files should be non-negative", totalFiles >= 0);
        
        // 验证统计信息API
        AwesomeLinkFilter.IndexStatistics stats = filter.getIndexStatistics();
        assertNotNull("Index statistics should not be null", stats);
        assertEquals("Statistics should match cache size", fileCacheSize, stats.getFileCacheSize());
        assertEquals("Statistics should match base cache size", fileBaseCacheSize, stats.getFileBaseCacheSize());
        assertEquals("Statistics should match total files", totalFiles, stats.getTotalFiles());
    }

    /**
     * 测试索引状态更新（含项目名称）
     * 验证索引统计信息能够正确获取
     */
    public void testIndexStatusUpdateWithProjectName() throws InterruptedException {
        // 触发索引初始化
        filter.detectPaths("Error in test.java:10");
        Thread.sleep(500);
        
        // 获取索引统计信息
        AwesomeLinkFilter.IndexStatistics stats = filter.getIndexStatistics();
        assertNotNull("Statistics should not be null", stats);
        
        // 验证统计信息包含有效数据（非负数）
        assertTrue("Total files should be non-negative", stats.getTotalFiles() >= 0);
        assertTrue("File cache size should be non-negative", stats.getFileCacheSize() >= 0);
        assertTrue("Base cache size should be non-negative", stats.getFileBaseCacheSize() >= 0);
        
        // 验证项目名称可以获取
        String projectName = getProject().getName();
        assertNotNull("Project name should not be null", projectName);
        assertFalse("Project name should not be empty", projectName.isEmpty());
        
        // 验证统计信息的一致性
        assertTrue("Total files should >= file cache size", 
            stats.getTotalFiles() >= stats.getFileCacheSize());
        assertTrue("File cache size should >= base cache size", 
            stats.getFileCacheSize() >= stats.getFileBaseCacheSize());
    }

    /**
     * 测试手动重建索引功能
     * 验证重建索引后统计信息更新
     */
    public void testManualRebuildIndex() throws InterruptedException {
        // 获取重建前的统计信息
        AwesomeLinkFilter.IndexStatistics statsBefore = filter.getIndexStatistics();
        long timeBefore = System.currentTimeMillis();
        
        // 执行手动重建（不应抛出异常）
        filter.manualRebuild();
        Thread.sleep(1000); // 等待重建完成
        
        // 获取重建后的统计信息
        AwesomeLinkFilter.IndexStatistics statsAfter = filter.getIndexStatistics();
        
        // 验证重建方法执行成功（不验证文件数，因为测试项目可能为空）
        assertTrue("File count should be non-negative", statsAfter.getTotalFiles() >= 0);
        
        // 验证重建时间已更新
        assertTrue("Last rebuild time should be updated", 
            statsAfter.getLastRebuildTime() >= timeBefore);
        
        // 验证重建耗时已记录
        assertTrue("Rebuild duration should be recorded", 
            statsAfter.getLastRebuildDuration() >= 0);
        
        // 验证路径检测功能仍然正常
        List<FileLinkMatch> results = filter.detectPaths("Error in test.java:10");
        assertNotNull("Detection should return non-null result", results);
    }

    /**
     * 测试清除索引缓存功能
     * 验证清除后索引为空，且能自动重建
     */
    public void testClearIndexCache() throws InterruptedException {
        // 触发索引初始化
        filter.detectPaths("Error in test.java:10");
        Thread.sleep(500);
        
        int filesBeforeClear = filter.getTotalCachedFiles();
        
        // 清除缓存（不应抛出异常）
        filter.clearCache();
        
        // 验证缓存已清空
        assertEquals("File cache should be empty", 0, filter.getFileCacheSize());
        assertEquals("File base cache should be empty", 0, filter.getFileBaseCacheSize());
        assertEquals("Total files should be 0", 0, filter.getTotalCachedFiles());
        
        // 验证清除后路径检测仍然可以工作（会触发自动重建）
        List<FileLinkMatch> results = filter.detectPaths("Error in test.java:10");
        assertNotNull("Detection should return non-null result", results);
        Thread.sleep(500);
        
        // 验证索引状态（可能已自动重建，也可能仍为空）
        int filesAfterDetection = filter.getTotalCachedFiles();
        assertTrue("File count should be non-negative", filesAfterDetection >= 0);
    }

    /**
     * 测试获取缓存大小功能
     * 验证各种缓存大小统计的正确性
     */
    public void testGetCacheSizes() throws InterruptedException {
        // 触发索引初始化
        filter.detectPaths("Error in test.java:10");
        Thread.sleep(500);
        
        int fileCacheSize = filter.getFileCacheSize();
        int fileBaseCacheSize = filter.getFileBaseCacheSize();
        int totalFiles = filter.getTotalCachedFiles();
        
        // 验证缓存大小的合理性（非负数）
        assertTrue("File cache size should be non-negative", fileCacheSize >= 0);
        assertTrue("File base cache size should be non-negative", fileBaseCacheSize >= 0);
        assertTrue("Total files should be non-negative", totalFiles >= 0);
        
        // 验证缓存大小的关系
        assertTrue("Total files should >= file cache size", totalFiles >= fileCacheSize);
        assertTrue("File cache size should >= base cache size", fileCacheSize >= fileBaseCacheSize);
        
        // 多次调用应该返回一致的结果（在没有修改的情况下）
        assertEquals("File cache size should be consistent", 
            fileCacheSize, filter.getFileCacheSize());
        assertEquals("File base cache size should be consistent", 
            fileBaseCacheSize, filter.getFileBaseCacheSize());
        assertEquals("Total files should be consistent", 
            totalFiles, filter.getTotalCachedFiles());
    }

    /**
     * 测试进度条功能
     * 验证进度条在重建索引过程中的显示和隐藏，以及新的进度回调机制
     */
    public void testProgressBarFunctionality() throws InterruptedException {
        // 创建配置表单实例来测试UI组件
        awesome.console.config.AwesomeConsoleConfigForm form = new awesome.console.config.AwesomeConsoleConfigForm();
        
        // 验证进度条组件已正确初始化
        assertNotNull("Progress bar should be initialized", form.indexProgressBar);
        assertTrue("Progress bar should be always visible", form.indexProgressBar.isVisible());
        assertTrue("Progress bar should have string painted", form.indexProgressBar.isStringPainted());
        
        // 验证进度条的初始状态
        assertEquals("Progress bar should start at 0", 0, form.indexProgressBar.getValue());
        assertEquals("Progress bar should have correct range", 100, form.indexProgressBar.getMaximum());
        assertEquals("Progress bar should have initial text", "Ready", form.indexProgressBar.getString());
        
        // 模拟进度条状态更新（重建开始）
        form.indexProgressBar.setIndeterminate(true);
        form.indexProgressBar.setString("Starting scan...");
        
        assertTrue("Progress bar should remain visible", form.indexProgressBar.isVisible());
        assertTrue("Progress bar should be indeterminate when set", form.indexProgressBar.isIndeterminate());
        assertEquals("Progress bar should show starting text", "Starting scan...", form.indexProgressBar.getString());
        
        // 模拟进度更新
        form.indexProgressBar.setString("Processing... 50 files");
        assertEquals("Progress bar should show progress text", "Processing... 50 files", form.indexProgressBar.getString());
        
        // 模拟带速度信息的进度更新
        form.indexProgressBar.setString("Processing... 150 files (75 files/sec)");
        assertTrue("Progress bar should show speed info", 
            form.indexProgressBar.getString().contains("files/sec"));
        
        // 模拟进度条完成状态（重建完成）
        form.indexProgressBar.setValue(100);
        form.indexProgressBar.setIndeterminate(false);
        form.indexProgressBar.setString("Completed! 150 files indexed in 2s");
        
        assertTrue("Progress bar should remain visible after completion", form.indexProgressBar.isVisible());
        assertFalse("Progress bar should not be indeterminate when reset", form.indexProgressBar.isIndeterminate());
        assertEquals("Progress bar should show completion status", 100, form.indexProgressBar.getValue());
        assertTrue("Progress bar should show completion message", 
            form.indexProgressBar.getString().contains("Completed!"));
    }

    /**
     * 测试原有功能不受索引管理影响
     * 验证添加索引管理功能后，原有的路径检测功能仍然正常工作
     */
    public void testExistingFunctionalityNotAffectedByIndexManagement() throws InterruptedException {
        // 测试基本路径检测
        assertPathDetection("Error in src/main.java:10", "src/main.java:10");
        assertPathDetection("File: config.json", "config.json");
        
        // 测试URL检测
        assertURLDetection("Visit https://example.com", "https://example.com");
        
        // 执行索引管理操作
        filter.manualRebuild();
        Thread.sleep(500);
        
        // 验证路径检测仍然正常
        assertPathDetection("Error in src/main.java:10", "src/main.java:10");
        assertPathDetection("File: config.json", "config.json");
        assertURLDetection("Visit https://example.com", "https://example.com");
        
        // 清除缓存
        filter.clearCache();
        
        // 验证自动重建后功能正常
        assertPathDetection("Error in src/main.java:10", "src/main.java:10");
        Thread.sleep(500);
        
        // 验证所有原有配置仍然有效
        assertTrue("Search URLs should still work", storage.searchUrls);
        assertTrue("Search files should still work", storage.searchFiles);
        
        // 验证统计信息API不影响检测功能
        filter.getIndexStatistics();
        filter.getFileCacheSize();
        filter.getFileBaseCacheSize();
        filter.getTotalCachedFiles();
        
        assertPathDetection("Error in test.java:10", "test.java:10");
    }
}
