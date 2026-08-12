package com.flowbreak.app;

import static org.junit.Assert.assertEquals;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowUsageStatsManager;

/**
 * ForegroundUsageDetector ???????? bootstrap??????????/???
 * ???? ShadowUsageStatsManager.addEvent ??????? queryEvents ???
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ForegroundUsageDetectorTest {
    private static final long NOW = 1_000_000_000L;

    private ShadowUsageStatsManager shadow;

    @Before public void setUp() {
        UsageStatsManager manager = ApplicationProvider.getApplicationContext()
                .getSystemService(UsageStatsManager.class);
        shadow = Shadows.shadowOf(manager);
    }

    @After public void tearDown() {
        shadow.reset();
    }

    private static ForegroundUsageDetector detector() {
        Context context = ApplicationProvider.getApplicationContext();
        return new ForegroundUsageDetector(context);
    }

    private void event(String pkg, String cls, int type, long ts) {
        // Event 的字段在 SDK stub 中不可见，通过反射填充（字段名与
        // ShadowUsageStatsManager 内部读取一致：mPackage/mClass/mTimeStamp/mEventType）。
        UsageEvents.Event event = new UsageEvents.Event();
        try {
            setField(event, "mPackage", pkg);
            setField(event, "mClass", cls);
            setField(event, "mTimeStamp", ts);
            setField(event, "mEventType", type);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        shadow.addEvent(event);
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test public void coldStartIdentifiesTargetAlreadyInForeground() {
        // 冷启动：目标应用已在前台，事件流以旧实例的 STOPPED 收尾
        event("com.example.video", "BrowserActivity", UsageEvents.Event.ACTIVITY_RESUMED, NOW - 60_000L);
        event("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_STOPPED, NOW - 59_000L);
        assertEquals("com.example.video", detector().detect(NOW));
    }

    @Test public void samePackageHandoverIsRecognizedAcrossDetectRounds() {
        event("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, NOW - 10_000L);
        event("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_PAUSED, NOW - 9_000L);
        event("com.example.video", "BrowserActivity", UsageEvents.Event.ACTIVITY_RESUMED, NOW - 8_000L);
        event("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_STOPPED, NOW - 7_000L);
        ForegroundUsageDetector detector = detector();
        // 第一轮只看到一半事件：Main RESUMED + Main PAUSED -> 前台未知
        assertEquals("", detector.detect(NOW - 8_500L));
        // 最迟下一轮必须识别仍在前台的同一包
        assertEquals("com.example.video", detector.detect(NOW));
    }

    @Test public void bootstrapRetriesUntilRecentResumeIsObserved() {
        ForegroundUsageDetector detector = detector();
        // 最迟下一轮必须识别仍在前台的同一包
        assertEquals("", detector.detect(NOW - 10_000L));
        // 新事件到达后，下一轮识别
        event("com.example.video", "BrowserActivity", UsageEvents.Event.ACTIVITY_RESUMED, NOW - 5_000L);
        assertEquals("com.example.video", detector.detect(NOW));
    }

    @Test public void screenOffResetClearsForegroundUntilNewEvents() {
        event("com.example.video", "BrowserActivity", UsageEvents.Event.ACTIVITY_RESUMED, NOW - 60_000L);
        ForegroundUsageDetector detector = detector();
        assertEquals("com.example.video", detector.detect(NOW));

        detector.reset(); // 屏幕关闭语义
        assertEquals("", detector.detect(NOW + 1_000L));
        event("com.example.video", "BrowserActivity", UsageEvents.Event.ACTIVITY_RESUMED, NOW + 2_000L);
        assertEquals("com.example.video", detector.detect(NOW + 3_000L));
    }

    @Test public void crossPackageSwitchUpdatesForeground() {
        event("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, NOW - 20_000L);
        event("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_PAUSED, NOW - 15_000L);
        event("com.android.chrome", "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, NOW - 10_000L);
        assertEquals("com.android.chrome", detector().detect(NOW));
    }

    @Test public void cursorAdvancesPastNowAndLastUsageEventIsTracked() {
        event("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, NOW - 30_000L);
        ForegroundUsageDetector detector = detector();
        detector.detect(NOW);
        assertEquals(NOW - 30_000L, detector.getLastUsageEventAt());
        assertEquals(true, detector.getCursor() >= NOW);
    }
}
