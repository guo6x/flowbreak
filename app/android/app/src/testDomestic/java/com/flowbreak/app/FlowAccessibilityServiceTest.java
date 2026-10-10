package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.accessibilityservice.AccessibilityService;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import androidx.test.core.app.ApplicationProvider;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAccessibilityService;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowWindowManagerImpl;

/**
 * HyperOS 强阻断 fallback 回归测试（domestic 渠道）。
 *
 * 覆盖：命中已阻断目标回 HOME、无障碍横幅入口、重复事件不堆叠、
 * BLOCKED 解除清理、BlockActivity 失败不静默、微信视频号路径、service 断开清理。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class FlowAccessibilityServiceTest {
    private SharedPreferences prefs;
    private ServiceController<TestableFlowAccessibilityService> controller;
    private TestableFlowAccessibilityService service;

    @Before public void setUp() {
        AccessibilityRuntimeState.resetForTests();
        prefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences("FlowBreakPrefs", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        controller = Robolectric.buildService(TestableFlowAccessibilityService.class);
        service = controller.create().get();
        service.onServiceConnected();
    }

    @After public void tearDown() {
        controller.destroy();
        AccessibilityRuntimeState.resetForTests();
        prefs.edit().clear().commit();
        ShadowLog.reset();
    }

    private void enableBlocked(String... targets) {
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.BLOCKED.name())
                .putBoolean("strongBlockingEnabled", true)
                .putStringSet("targetApps", new HashSet<>(Arrays.asList(targets)))
                .commit();
    }

    private static AccessibilityEvent windowEvent(String pkg, String cls) {
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        event.setPackageName(pkg);
        event.setClassName(cls);
        return event;
    }

    private static ShadowAccessibilityService shadowA11y(FlowAccessibilityService service) {
        return (ShadowAccessibilityService) Shadows.shadowOf((Service) service);
    }

    private List<View> windowViews() {
        WindowManager wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        return ((ShadowWindowManagerImpl) Shadows.shadowOf(wm)).getViews();
    }

    private static List<View> windowViewsFor(FlowAccessibilityService target) {
        WindowManager wm = (WindowManager) target.getSystemService(Context.WINDOW_SERVICE);
        return ((ShadowWindowManagerImpl) Shadows.shadowOf(wm)).getViews();
    }

    private static Button findButton(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof Button) return (Button) child;
        }
        return null;
    }

    private void assertNoEnforcement() {
        assertTrue(shadowA11y(service).getGlobalActionsPerformed().isEmpty());
        assertFalse(service.isBlockBannerShowing());
        assertEquals(0, service.blockActivityAttempts);
    }

    @Test public void blockedTargetWindowEventSendsHomeAndShowsBanner() {
        enableBlocked("com.example.video");
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertTrue(shadowA11y(service).getGlobalActionsPerformed()
                .contains(AccessibilityService.GLOBAL_ACTION_HOME));
        assertTrue(service.isBlockBannerShowing());
    }

    @Test public void nonTargetAppDoesNotTriggerStrongBlock() {
        enableBlocked("com.example.video");
        service.onAccessibilityEvent(windowEvent("com.android.chrome", "com.android.chrome.MainActivity"));

        assertTrue(shadowA11y(service).getGlobalActionsPerformed().isEmpty());
        assertFalse(service.isBlockBannerShowing());
    }

    @Test public void targetWithoutBlockedStateDoesNotTriggerStrongBlock() {
        enableBlocked("com.example.video");
        prefs.edit().putString("blockState", BlockStateMachine.State.IDLE.name()).commit();
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertTrue(shadowA11y(service).getGlobalActionsPerformed().isEmpty());
        assertFalse(service.isBlockBannerShowing());
    }

    @Test public void strongBlockingDisabledDoesNotTriggerStrongBlock() {
        enableBlocked("com.example.video");
        prefs.edit().putBoolean("strongBlockingEnabled", false).commit();
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertTrue(shadowA11y(service).getGlobalActionsPerformed().isEmpty());
        assertFalse(service.isBlockBannerShowing());
    }

    @Test public void monitoringDisabledDoesNotEnforceBlockedTarget() {
        enableBlocked("com.example.video");
        prefs.edit().putBoolean("monitoringEnabled", false).commit();

        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertTrue(shadowA11y(service).getGlobalActionsPerformed().isEmpty());
        assertFalse(service.isBlockBannerShowing());
    }

    @Test public void accessibilitySettingsDisabledDoesNotEnforceBlockedTarget() {
        enableBlocked("com.example.video");
        service.accessibilitySettings = false;

        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertNoEnforcement();
    }

    @Test public void disconnectedAccessibilityRuntimeDoesNotEnforceBlockedTarget() {
        service.onUnbind(new Intent());
        enableBlocked("com.example.video");

        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertNoEnforcement();
    }

    @Test public void serviceRuntimeAbsentDoesNotEnforcePersistedBlockedTarget() {
        enableBlocked("com.example.video");
        service.serviceRuntimeActive = false;

        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertNoEnforcement();
    }

    @Test public void deadMonitorThreadDoesNotEnforcePersistedBlockedTarget() {
        enableBlocked("com.example.video");
        service.monitorThreadAlive = false;

        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertNoEnforcement();
    }

    @Test public void staleMonitorHeartbeatDoesNotEnforcePersistedBlockedTarget() {
        enableBlocked("com.example.video");
        service.nowElapsed = service.lastCompletedMonitorTickElapsed
                + ProtectionRuntimeHealthEvaluator.SERVICE_HEARTBEAT_STALE_MS;

        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertNoEnforcement();
    }

    @Test public void integrityFailureDoesNotEnforcePersistedBlockedTarget() {
        enableBlocked("com.example.video");
        service.integrityFailureReason = "LIVE_USAGE_QUERY_FAILED";

        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertNoEnforcement();
    }

    @Test public void missingUsageAccessDoesNotEnforcePersistedBlockedTarget() {
        enableBlocked("com.example.video");
        service.usageAccess = false;

        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertNoEnforcement();
    }

    @Test public void missingOverlayDoesNotEnforcePersistedBlockedTarget() {
        enableBlocked("com.example.video");
        service.overlayPermission = false;

        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertNoEnforcement();
    }

    @Test public void healthyCoreRuntimePreservesStrongBlockingBehavior() {
        enableBlocked("com.example.video");

        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertTrue(shadowA11y(service).getGlobalActionsPerformed()
                .contains(AccessibilityService.GLOBAL_ACTION_HOME));
        assertTrue(service.isBlockBannerShowing());
        assertEquals(1, service.blockActivityAttempts);
    }

    @Test public void unhealthyCoreRuntimePollRemovesExistingBanner() {
        enableBlocked("com.example.video");
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));
        assertTrue(service.isBlockBannerShowing());

        service.monitorThreadAlive = false;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_500L));

        assertFalse(service.isBlockBannerShowing());
        assertEquals(0, windowViews().size());
        assertEquals(1, service.blockActivityAttempts);
    }

    @Test public void unhealthyCoreRuntimeRestButtonCannotStartRest() {
        enableBlocked("com.example.video");
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));
        Button rest = findButton((ViewGroup) windowViews().get(0));
        assertNotNull(rest);

        service.serviceRuntimeActive = false;
        rest.performClick();

        assertFalse(service.isBlockBannerShowing());
        assertNull(Shadows.shadowOf((ContextWrapper) service).getNextStartedService());
        assertEquals(1, service.blockActivityAttempts);
    }

    @Test public void monitoringDisabledCleansExistingBannerAndStopsPolling() {
        enableBlocked("com.example.video");
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));
        assertTrue(service.isBlockBannerShowing());
        assertEquals(1, shadowA11y(service).getGlobalActionsPerformed().size());

        prefs.edit().putBoolean("monitoringEnabled", false).commit();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_500L));

        assertFalse(service.isBlockBannerShowing());
        assertEquals(0, windowViews().size());
        assertEquals(1, shadowA11y(service).getGlobalActionsPerformed().size());
    }

    @Test public void monitoringDisabledRestButtonDoesNotStartRest() {
        enableBlocked("com.example.video");
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));
        Button rest = findButton((ViewGroup) windowViews().get(0));
        assertNotNull(rest);

        prefs.edit().putBoolean("monitoringEnabled", false).commit();
        rest.performClick();

        assertFalse(service.isBlockBannerShowing());
        assertNull(Shadows.shadowOf((ContextWrapper) service).getNextStartedService());
        assertTrue(shadowA11y(service).getGlobalActionsPerformed().size() == 1);
    }

    @Test public void repeatedKicksDoNotStackBanner() {
        enableBlocked("com.example.video");
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.BrowserActivity"));
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.BrowserActivity"));

        assertTrue(service.isBlockBannerShowing());
        assertEquals(1, windowViews().size());
    }

    @Test public void bannerDismissesWhenBlockedStateResolves() {
        enableBlocked("com.example.video");
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));
        assertTrue(service.isBlockBannerShowing());

        // 完成休息/紧急解锁后 blockState 变为 GRACE，轮询应自动清理横幅
        prefs.edit().putString("blockState", BlockStateMachine.State.GRACE.name()).commit();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_500L));
        assertFalse(service.isBlockBannerShowing());
        assertEquals(0, windowViews().size());
    }

    @Test public void bannerButtonStartsRestAndDismissesBanner() {
        enableBlocked("com.example.video");
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));
        assertEquals(1, windowViews().size());

        Button rest = findButton((ViewGroup) windowViews().get(0));
        assertNotNull(rest);
        rest.performClick();

        assertFalse(service.isBlockBannerShowing());
        Intent started = Shadows.shadowOf((ContextWrapper) service).getNextStartedService();
        assertNotNull(started);
        assertEquals(FlowForegroundService.ACTION_BEGIN_REST, started.getAction());
    }

    @Test public void unsupportedRuntimeDoesNotSendHomeForBlockedTarget() {
        ServiceController<UnsupportedRuntimeService> unsupportedController =
                Robolectric.buildService(UnsupportedRuntimeService.class);
        UnsupportedRuntimeService unsupported = unsupportedController.create().get();
        unsupported.onServiceConnected();
        enableBlocked("com.example.video");

        unsupported.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertTrue(shadowA11y(unsupported).getGlobalActionsPerformed().isEmpty());
        assertFalse(unsupported.isBlockBannerShowing());
        assertEquals(BlockStateMachine.State.BLOCKED.name(),
                prefs.getString("blockState", BlockStateMachine.State.IDLE.name()));
        unsupportedController.destroy();
    }

    @Test public void unsupportedRuntimeDoesNotShowBannerForBlockedTarget() {
        ServiceController<UnsupportedRuntimeService> unsupportedController =
                Robolectric.buildService(UnsupportedRuntimeService.class);
        UnsupportedRuntimeService unsupported = unsupportedController.create().get();
        unsupported.onServiceConnected();
        enableBlocked("com.example.video");

        unsupported.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));

        assertFalse(unsupported.isBlockBannerShowing());
        unsupportedController.destroy();
    }

    @Test public void unsupportedRuntimePollDismissesExistingBannerAndStops() {
        ServiceController<ToggleableRuntimeService> toggleController =
                Robolectric.buildService(ToggleableRuntimeService.class);
        ToggleableRuntimeService toggle = toggleController.create().get();
        toggle.onServiceConnected();
        enableBlocked("com.example.video");
        toggle.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));
        assertTrue(toggle.isBlockBannerShowing());

        toggle.runtimeAvailable = false;
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_500L));

        assertFalse(toggle.isBlockBannerShowing());
        assertEquals(0, windowViewsFor(toggle).size());
        toggleController.destroy();
    }

    @Test public void unsupportedRuntimeRestEntryDoesNotStartService() {
        ServiceController<ToggleableRuntimeService> toggleController =
                Robolectric.buildService(ToggleableRuntimeService.class);
        ToggleableRuntimeService toggle = toggleController.create().get();
        toggle.onServiceConnected();
        enableBlocked("com.example.video");
        toggle.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));
        Button rest = findButton((ViewGroup) windowViewsFor(toggle).get(0));
        assertNotNull(rest);

        toggle.runtimeAvailable = false;
        rest.performClick();

        assertFalse(toggle.isBlockBannerShowing());
        assertNull(Shadows.shadowOf((ContextWrapper) toggle).getNextStartedService());
        assertEquals(BlockStateMachine.State.BLOCKED.name(),
                prefs.getString("blockState", BlockStateMachine.State.IDLE.name()));
        toggleController.destroy();
    }

    @Test public void blockActivityRejectionIsLoggedNotSilent() {
        ThrowingService throwing = Robolectric.buildService(ThrowingService.class).create().get();
        throwing.onServiceConnected();

        assertFalse(throwing.tryStartBlockActivity("com.example.video"));
        List<ShadowLog.LogItem> logs = ShadowLog.getLogsForTag("FlowAccessibility");
        assertTrue(logs.stream().anyMatch(item ->
                item.msg != null && item.msg.contains("startBlockActivity rejected")));
    }

    @Test public void wechatVideoChannelBlockedKicksHome() {
        enableBlocked("com.tencent.mm");
        service.onAccessibilityEvent(windowEvent("com.tencent.mm", "com.tencent.mm.plugin.finder.ui.FinderActivity"));

        assertTrue(shadowA11y(service).getGlobalActionsPerformed()
                .contains(AccessibilityService.GLOBAL_ACTION_HOME));
        assertTrue(service.isBlockBannerShowing());
    }

    @Test public void unbindDismissesBanner() {
        enableBlocked("com.example.video");
        service.onAccessibilityEvent(windowEvent("com.example.video", "com.example.video.MainActivity"));
        assertTrue(service.isBlockBannerShowing());

        service.onUnbind(new Intent());
        assertFalse(service.isBlockBannerShowing());
        assertFalse(AccessibilityRuntimeState.isConnected());
    }

    @Test public void destroyClearsAccessibilityRuntimeConnection() {
        assertTrue(AccessibilityRuntimeState.isConnected());

        service.onDestroy();

        assertFalse(AccessibilityRuntimeState.isConnected());
    }

    /** 模拟 MIUI 拒绝后台 Activity 启动（MIUILOG- Permission Denied Activity）。 */
    static class TestableFlowAccessibilityService extends FlowAccessibilityService {
        boolean runtimeAvailable = true;
        boolean usageAccess = true;
        boolean overlayPermission = true;
        boolean accessibilitySettings = true;
        boolean serviceRuntimeActive = true;
        boolean monitorThreadAlive = true;
        long lastCompletedMonitorTickElapsed = 1_000L;
        long nowElapsed = 1_000L;
        String integrityFailureReason = "";
        int blockActivityAttempts;

        @Override boolean protectionRuntimeAvailable() { return runtimeAvailable; }

        @Override boolean hasUsageStats() { return usageAccess; }

        @Override boolean hasOverlay() { return overlayPermission; }

        @Override boolean accessibilityEnabledInSettings() { return accessibilitySettings; }

        @Override ProtectionRuntimeHealthEvaluator.Result currentCoreRuntimeHealth() {
            return ProtectionRuntimeHealthEvaluator.evaluate(
                    serviceRuntimeActive,
                    monitorThreadAlive,
                    lastCompletedMonitorTickElapsed,
                    nowElapsed,
                    integrityFailureReason
            );
        }

        @Override boolean tryStartBlockActivity(String packageName) {
            blockActivityAttempts++;
            return super.tryStartBlockActivity(packageName);
        }
    }

    static class ThrowingService extends TestableFlowAccessibilityService {
        @Override public void startActivity(Intent intent) {
            throw new IllegalStateException("MIUI background start rejected");
        }
    }

    static class UnsupportedRuntimeService extends TestableFlowAccessibilityService {
        @Override boolean protectionRuntimeAvailable() { return false; }
    }

    static class ToggleableRuntimeService extends TestableFlowAccessibilityService {
    }
}
