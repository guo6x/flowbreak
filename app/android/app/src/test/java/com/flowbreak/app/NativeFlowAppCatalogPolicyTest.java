package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * 验证目标应用过滤策略的纯逻辑部分。
 * 不依赖 PackageManager 或 BuildConfig。
 *
 * 覆盖：
 * - Play 渠道拒绝 com.tencent.mm
 * - Domestic 渠道允许 com.tencent.mm
 * - 受保护包名被过滤
 * - 空字符串被忽略
 * - 重复包名去重
 * - 自身渠道包不可限制
 */
public class NativeFlowAppCatalogPolicyTest {
    private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
            "com.flowbreak.app",
            "com.flowbreak.app.cn",
            "com.android.settings",
            "com.android.launcher"
    ));

    @Test public void playChannelRejectsWeChat() {
        assertFalse(NativeFlowAppCatalog.isSupportedTargetPackage("com.tencent.mm", true));
    }

    @Test public void domesticChannelAllowsWeChat() {
        assertTrue(NativeFlowAppCatalog.isSupportedTargetPackage("com.tencent.mm", false));
    }

    @Test public void nonWeChatPackagesAreAlwaysSupported() {
        assertTrue(NativeFlowAppCatalog.isSupportedTargetPackage("com.example.app", true));
        assertTrue(NativeFlowAppCatalog.isSupportedTargetPackage("com.example.app", false));
    }

    @Test public void protectedPackagesAreFilteredOut() {
        Set<String> input = new HashSet<>(Arrays.asList(
                "com.flowbreak.app", "com.flowbreak.app.cn", "com.android.settings",
                "com.example.target"
        ));
        Set<String> result = NativeFlowAppCatalog.filterTargetApps(input, PROTECTED, false);
        assertEquals(1, result.size());
        assertTrue(result.contains("com.example.target"));
    }

    @Test public void emptyStringsAreIgnored() {
        Set<String> input = new HashSet<>(Arrays.asList("", "  ", "com.example.target"));
        Set<String> result = NativeFlowAppCatalog.filterTargetApps(input, PROTECTED, false);
        assertEquals(1, result.size());
        assertTrue(result.contains("com.example.target"));
    }

    @Test public void duplicatePackagesAreDeduplicated() {
        Set<String> input = new HashSet<>(Arrays.asList(
                "com.example.target", "com.example.target", "com.example.target"
        ));
        Set<String> result = NativeFlowAppCatalog.filterTargetApps(input, PROTECTED, false);
        assertEquals(1, result.size());
    }

    @Test public void playChannelFiltersWeChatFromTargetApps() {
        Set<String> input = new HashSet<>(Arrays.asList(
                "com.tencent.mm", "com.example.target"
        ));
        Set<String> result = NativeFlowAppCatalog.filterTargetApps(input, PROTECTED, true);
        assertEquals(1, result.size());
        assertTrue(result.contains("com.example.target"));
        assertFalse(result.contains("com.tencent.mm"));
    }

    @Test public void domesticChannelKeepsWeChatInTargetApps() {
        Set<String> input = new HashSet<>(Arrays.asList(
                "com.tencent.mm", "com.example.target"
        ));
        Set<String> result = NativeFlowAppCatalog.filterTargetApps(input, PROTECTED, false);
        assertEquals(2, result.size());
        assertTrue(result.contains("com.tencent.mm"));
        assertTrue(result.contains("com.example.target"));
    }

    @Test public void selfPackagesAreAlwaysProtected() {
        Set<String> input = new HashSet<>(Arrays.asList(
                "com.flowbreak.app", "com.flowbreak.app.cn"
        ));
        Set<String> result = NativeFlowAppCatalog.filterTargetApps(input, PROTECTED, false);
        assertTrue(result.isEmpty());
    }

    @Test public void emptyInputReturnsEmptySet() {
        Set<String> result = NativeFlowAppCatalog.filterTargetApps(
                Collections.emptySet(), PROTECTED, false
        );
        assertTrue(result.isEmpty());
    }

    @Test public void nullValuesAreSkipped() {
        Set<String> input = new HashSet<>();
        input.add(null);
        input.add("com.example.target");
        Set<String> result = NativeFlowAppCatalog.filterTargetApps(input, PROTECTED, false);
        assertEquals(1, result.size());
        assertTrue(result.contains("com.example.target"));
    }

    @Test public void trimmedValuesAreUsed() {
        Set<String> input = new HashSet<>();
        input.add("  com.example.target  ");
        Set<String> result = NativeFlowAppCatalog.filterTargetApps(input, PROTECTED, false);
        assertEquals(1, result.size());
        assertTrue(result.contains("com.example.target"));
    }
}
