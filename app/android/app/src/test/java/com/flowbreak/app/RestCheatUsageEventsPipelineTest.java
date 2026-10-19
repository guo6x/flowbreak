package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.Field;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowUsageStatsManager;

/** Covers the Bilibili foreground-history to anti-cheat REST-cancellation pipeline. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RestCheatUsageEventsPipelineTest {
    private static final String BILIBILI = "tv.danmaku.bili";
    private static final String APP = "com.flowbreak.app.cn";
    private static final long REST_START = 1_000_000_000L;
    private static final long BILIBILI_START = REST_START + 500L;
    private static final long BILIBILI_END = REST_START + 33_500L;
    private static final long LIMIT_MS = 300_000L;

    private ShadowUsageStatsManager shadow;

    @Before public void setUp() {
        UsageStatsManager manager = ApplicationProvider.getApplicationContext()
                .getSystemService(UsageStatsManager.class);
        shadow = Shadows.shadowOf(manager);
    }

    @After public void tearDown() {
        shadow.reset();
    }

    @Test public void thirtyThreeSecondsOfBilibiliUsageDuringRestCancelsRest() {
        addEvent(BILIBILI, "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, BILIBILI_START);
        addEvent(BILIBILI, "MainActivity", UsageEvents.Event.ACTIVITY_PAUSED, BILIBILI_END);
        addEvent(APP, "MainActivity", UsageEvents.Event.ACTIVITY_RESUMED, BILIBILI_END);

        Context context = ApplicationProvider.getApplicationContext();
        UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);
        ForegroundUsageDetector detector = new ForegroundUsageDetector(
                context,
                new ForegroundAppTracker(),
                ForegroundUsageDetector.INITIAL_EVENT_LOOKBACK_MS,
                manager::queryEvents
        );
        ForegroundUsageDetector.GapQuery gap = detector.queryEventsForReconciliation(
                REST_START,
                REST_START + 34_000L
        );

        assertTrue("UsageEvents query must succeed", gap.succeeded);
        TargetSessionGapReconciler.Result history = TargetSessionGapReconciler.reconcile(
                new TargetSessionGapReconciler.Input(
                        REST_START,
                        REST_START + 34_000L,
                        APP,
                        false,
                        true,
                        Collections.singleton(BILIBILI),
                        gap.events
                )
        );
        assertEquals(TargetSessionGapReconciler.Status.SUCCESS, history.status);

        long verifiedBilibiliMs = RestCheatReplay.verifiedTargetMsDuring(
                history,
                REST_START,
                REST_START + 34_000L
        );
        assertEquals(33_000L, verifiedBilibiliMs);

        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.RESTING,
                400_000L,
                0L,
                0L,
                BILIBILI
        );
        machine.seedCheckpoint(REST_START, true);
        RestCheatReplay.Decision decision = RestCheatReplay.applyVerifiedTargetMs(
                machine,
                new RestCheatTracker(),
                verifiedBilibiliMs,
                LIMIT_MS
        );

        assertTrue("verified target use above the threshold must cancel rest", decision.cancelled);
        assertEquals(33_000L, decision.accumulatedMs);
        assertEquals(BlockStateMachine.State.BLOCKED, decision.state);
        assertEquals(BlockStateMachine.State.BLOCKED, machine.getState());
    }

    private void addEvent(String packageName, String className, int eventType, long timestamp) {
        UsageEvents.Event event = new UsageEvents.Event();
        try {
            setField(event, "mPackage", packageName);
            setField(event, "mClass", className);
            setField(event, "mTimeStamp", timestamp);
            setField(event, "mEventType", eventType);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create a Robolectric UsageEvents fixture", exception);
        }
        shadow.addEvent(event);
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
