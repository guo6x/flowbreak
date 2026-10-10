package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.util.Collections;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowUsageStatsManager;

/**
 * Replays actual Android UsageEvents through the same detector, domestic target
 * classifier, usage observation anchor, anti-cheat threshold and native state
 * machine components used in FlowForegroundService. This is a JVM integration
 * test, NOT a physical HyperOS device acceptance test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RestCheatUsageEventsPipelineTest {
    private static final long START = 1_000_000_000L;
    private static final long LIMIT_MS = 300_000L;
    private static final String BILIBILI = "tv.danmaku.bili";
    private static final String SELF = "com.flowbreak.app.cn";
    private static final String SAFE = "com.example.reader";
    private ShadowUsageStatsManager shadow;

    @Before public void before() {
        Context context = ApplicationProvider.getApplicationContext();
        UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);
        shadow = Shadows.shadowOf(manager);
    }

    @After public void after() {
        shadow.reset();
    }

    private void resumed(String pkg, long at) {
        UsageEvents.Event event = new UsageEvents.Event();
        try {
            setField(event, "mPackage", pkg);
            setField(event, "mClass", "MainActivity");
            setField(event, "mTimeStamp", at);
            setField(event, "mEventType", UsageEvents.Event.ACTIVITY_RESUMED);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to construct Robolectric UsageEvents", e);
        }
        shadow.addEvent(event);
    }

    private static void setField(Object instance, String name, Object value)
            throws ReflectiveOperationException {
        java.lang.reflect.Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static final class Pipeline {
        private final ForegroundUsageDetector detector =
                new ForegroundUsageDetector(ApplicationProvider.getApplicationContext());
        private final TargetAppClassifier classifier = new TargetAppClassifier(true);
        private final Set<String> targets = Collections.singleton(BILIBILI);
        private final UsageAccumulator usage = new UsageAccumulator();
        private final RestCheatTracker tracker = new RestCheatTracker();
        private final RestCheatEvidence evidence = new RestCheatEvidence();
        private final BlockStateMachine machine;
        private final boolean paused;
        private long pausedObservedAt;
        private long cancellationAt;

        Pipeline(boolean paused) {
            this.paused = paused;
            machine = new BlockStateMachine(
                    BlockStateMachine.State.RESTING,
                    paused ? 0L : 400_000L,
                    0L,
                    0L,
                    paused ? "" : BILIBILI
            );
            evidence.onRestStarted();
        }

        void tick(long now) {
            String foreground = detector.detect(now);
            assertTrue("UsageEvents query must succeed", detector.wasLastLiveUsageQuerySuccessful());
            boolean isTarget = classifier.isTarget(foreground, targets, false, 0L, now);
            long previousObservedAt = paused ? pausedObservedAt : usage.getLastObservedAt();
            if (paused) {
                pausedObservedAt = now;
            } else {
                usage.observe(isTarget, foreground, now);
            }
            boolean resting = machine.getState() == BlockStateMachine.State.RESTING;
            tracker.observe(resting, isTarget, previousObservedAt, now);
            if (resting) {
                evidence.recordLive(paused, !foreground.isEmpty(),
                        targets.contains(foreground), isTarget, tracker.accumulatedMs(), now);
            }
            if (tracker.triggered()) {
                RestCheatReplay.Decision decision = RestCheatReplay.cancelTriggered(
                        machine, tracker, LIMIT_MS
                );
                if (decision.cancelled) {
                    evidence.recordCancellation(decision.accumulatedMs, now);
                    cancellationAt = now;
                }
            }
        }
    }

    @Test public void stableBilibiliForegroundForThirtyThreeSecondsCancelsActiveRest() {
        resumed(SELF, START + 100L);
        Pipeline pipeline = new Pipeline(false);
        pipeline.tick(START + 1_000L);
        resumed(BILIBILI, START + 1_500L);
        for (long tickAt = START + 3_000L; tickAt <= START + 33_000L; tickAt += 2_000L) {
            pipeline.tick(tickAt);
        }

        assertEquals(BlockStateMachine.State.BLOCKED, pipeline.machine.getState());
        assertEquals(1L, pipeline.evidence.snapshot().cancellationCount);
        assertTrue(pipeline.evidence.snapshot().lastCancelledAccumulatedMs >= 5_000L);
        assertTrue("must cancel before 33 seconds", pipeline.cancellationAt < START + 33_000L);
        assertEquals(0L, pipeline.tracker.accumulatedMs());
        assertTrue(pipeline.evidence.snapshot().sessionTargetObservations >= 3L);
    }

    @Test public void stableBilibiliForegroundForThirtyThreeSecondsCancelsPausedRest() {
        resumed(SELF, START + 100L);
        Pipeline pipeline = new Pipeline(true);
        pipeline.tick(START + 1_000L);
        resumed(BILIBILI, START + 1_500L);
        for (long tickAt = START + 3_000L; tickAt <= START + 33_000L; tickAt += 2_000L) {
            pipeline.tick(tickAt);
        }

        assertEquals(BlockStateMachine.State.IDLE, pipeline.machine.getState());
        assertEquals(1L, pipeline.evidence.snapshot().cancellationCount);
        assertTrue(pipeline.evidence.snapshot().lastCancelledAccumulatedMs >= 5_000L);
        assertTrue(pipeline.evidence.snapshot().sessionPausedObservations >= 3L);
        assertTrue(pipeline.cancellationAt < START + 33_000L);
    }

    @Test public void safeForegroundGapKeepsRestUntilCumulativeTargetThreshold() {
        resumed(SELF, START + 100L);
        Pipeline pipeline = new Pipeline(true);
        pipeline.tick(START + 1_000L);
        resumed(BILIBILI, START + 1_500L);
        pipeline.tick(START + 3_000L);
        pipeline.tick(START + 5_000L);
        assertTrue(pipeline.tracker.accumulatedMs() > 0L);
        long beforeGap = pipeline.tracker.accumulatedMs();

        resumed(SAFE, START + 5_100L);
        pipeline.tick(START + 7_000L);
        pipeline.tick(START + 9_000L);
        assertEquals(BlockStateMachine.State.RESTING, pipeline.machine.getState());
        assertEquals(beforeGap, pipeline.tracker.accumulatedMs());
        assertFalse(pipeline.tracker.triggered());

        resumed(BILIBILI, START + 9_100L);
        pipeline.tick(START + 11_000L);
        pipeline.tick(START + 13_000L);

        assertEquals(BlockStateMachine.State.IDLE, pipeline.machine.getState());
        assertEquals(1L, pipeline.evidence.snapshot().cancellationCount);
        assertTrue(pipeline.evidence.snapshot().lastCancelledAccumulatedMs >= 5_000L);
    }
}
