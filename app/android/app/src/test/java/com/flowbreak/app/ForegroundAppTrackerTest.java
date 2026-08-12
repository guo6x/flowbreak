package com.flowbreak.app;

import android.app.usage.UsageEvents;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ForegroundAppTrackerTest {
    @Test public void tracksDuplicateResumesAndClearsOnPauseOfSameInstance() {
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", UsageEvents.Event.ACTIVITY_RESUMED, 100L);
        tracker.accept("com.example.video", UsageEvents.Event.ACTIVITY_RESUMED, 200L);
        assertEquals("com.example.video", tracker.getForegroundPackage());

        tracker.accept("com.example.video", UsageEvents.Event.ACTIVITY_PAUSED, 300L);
        assertEquals("", tracker.getForegroundPackage());
    }

    @Test public void systemForegroundReplacesTargetAndStaleEventsAreIgnored() {
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", UsageEvents.Event.MOVE_TO_FOREGROUND, 100L);
        tracker.accept("com.android.settings", UsageEvents.Event.MOVE_TO_FOREGROUND, 200L);
        tracker.accept("com.example.video", UsageEvents.Event.ACTIVITY_RESUMED, 150L);
        assertEquals("com.android.settings", tracker.getForegroundPackage());
    }

    @Test public void samePackageActivityHandoverKeepsPackageForeground() {
        // 冷启动同包跳转：RESUMED Main -> PAUSED Main -> RESUMED Browser -> STOPPED Main
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, 100L);
        tracker.accept("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_PAUSED, 101L);
        tracker.accept("com.example.video", "BrowserActivity", UsageEvents.Event.ACTIVITY_RESUMED, 102L);
        tracker.accept("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_STOPPED, 110L);
        assertEquals("com.example.video", tracker.getForegroundPackage());
    }

    @Test public void resumeOfNewInstanceIgnoresStalePauseOfOldInstance() {
        // RESUME B 后 PAUSE A：旧实例的 PAUSED 不得清空仍在前台的包
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", "ActivityA", UsageEvents.Event.ACTIVITY_RESUMED, 100L);
        tracker.accept("com.example.video", "ActivityB", UsageEvents.Event.ACTIVITY_RESUMED, 200L);
        tracker.accept("com.example.video", "ActivityA", UsageEvents.Event.ACTIVITY_PAUSED, 300L);
        assertEquals("com.example.video", tracker.getForegroundPackage());
    }

    @Test public void stopOfOldInstanceKeepsNewInstanceForeground() {
        // STOP A 后 B 仍前台：旧实例 STOPPED 不影响当前实例
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", "ActivityA", UsageEvents.Event.ACTIVITY_RESUMED, 100L);
        tracker.accept("com.example.video", "ActivityB", UsageEvents.Event.ACTIVITY_RESUMED, 200L);
        tracker.accept("com.example.video", "ActivityA", UsageEvents.Event.ACTIVITY_STOPPED, 300L);
        assertEquals("com.example.video", tracker.getForegroundPackage());
    }

    @Test public void targetToLauncherUpdatesForeground() {
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, 100L);
        tracker.accept("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_PAUSED, 200L);
        tracker.accept("com.miui.home", "Launcher", UsageEvents.Event.ACTIVITY_RESUMED, 300L);
        assertEquals("com.miui.home", tracker.getForegroundPackage());
    }

    @Test public void targetToAnotherAppUpdatesForeground() {
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, 100L);
        tracker.accept("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_PAUSED, 200L);
        tracker.accept("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_STOPPED, 250L);
        tracker.accept("com.android.chrome", "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, 300L);
        assertEquals("com.android.chrome", tracker.getForegroundPackage());
    }

    @Test public void moveToBackgroundClearsPackage() {
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", null, UsageEvents.Event.MOVE_TO_FOREGROUND, 100L);
        tracker.accept("com.example.video", null, UsageEvents.Event.MOVE_TO_BACKGROUND, 200L);
        assertEquals("", tracker.getForegroundPackage());
    }

    @Test public void resetClearsForeground() {
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, 100L);
        tracker.reset();
        assertEquals("", tracker.getForegroundPackage());
        tracker.accept("com.example.video", "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, 200L);
        assertEquals("com.example.video", tracker.getForegroundPackage());
    }

    @Test public void staleOutOfOrderBackgroundEventIsIgnored() {
        // 乱序旧事件：时间戳小于已处理的最新事件，不得改变状态
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", "ActivityA", UsageEvents.Event.ACTIVITY_RESUMED, 200L);
        tracker.accept("com.example.video", "ActivityB", UsageEvents.Event.ACTIVITY_RESUMED, 300L);
        tracker.accept("com.example.video", "ActivityA", UsageEvents.Event.ACTIVITY_PAUSED, 150L);
        tracker.accept("com.example.video", "ActivityA", UsageEvents.Event.ACTIVITY_STOPPED, 140L);
        assertEquals("com.example.video", tracker.getForegroundPackage());
    }

    @Test public void packageLevelResumeThenActivityStopInSamePackageKeepsForeground() {
        // MOVE_TO_FOREGROUND 后同一包内旧 Activity 的 STOPPED 不清前台
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", null, UsageEvents.Event.MOVE_TO_FOREGROUND, 100L);
        tracker.accept("com.example.video", "SplashActivity", UsageEvents.Event.ACTIVITY_STOPPED, 200L);
        assertEquals("com.example.video", tracker.getForegroundPackage());
    }
}
