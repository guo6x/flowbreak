package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FlowForegroundServiceRuntimeTrackingTest {
    private static final long LIMIT_MS = 100_000L;

    @Test public void recordingDiagnosticsDoesNotChangeStateMachineBehavior() {
        BlockStateMachine observed = freshMachine();
        BlockStateMachine baseline = freshMachine();
        FlowForegroundService.RuntimeTrackingCounters counters =
                new FlowForegroundService.RuntimeTrackingCounters();

        boolean[] targetStates = {true, true, false, true, true};
        long[] times = {1_000L, 3_000L, 5_000L, 7_000L, 9_000L};
        for (int i = 0; i < targetStates.length; i++) {
            boolean isTarget = targetStates[i];
            long before = observed.getSessionMs();
            BlockStateMachine.State observedState = observed.update(
                    isTarget, isTarget ? "tv.danmaku.bili" : "", times[i], LIMIT_MS
            );
            long after = observed.getSessionMs();

            counters.recordTick(times[i]);
            counters.recordDetector(isTarget, i > 0);
            counters.recordClassifier(isTarget);
            counters.recordAccumulator(isTarget && i > 0 ? 2_000L : 0L, isTarget);
            counters.recordMachineSession(before, after);

            BlockStateMachine.State baselineState = baseline.update(
                    isTarget, isTarget ? "tv.danmaku.bili" : "", times[i], LIMIT_MS
            );
            assertEquals(baselineState, observedState);
            assertEquals(baseline.getSessionMs(), observed.getSessionMs());
        }
    }

    @Test public void consecutiveTargetTicksGrowAndFalseTickClearsThem() {
        FlowForegroundService.RuntimeTrackingCounters counters =
                new FlowForegroundService.RuntimeTrackingCounters();

        counters.recordClassifier(true);
        counters.recordClassifier(true);
        counters.recordClassifier(true);
        FlowForegroundService.RuntimeTrackingSnapshot snapshot = counters.snapshot();
        assertEquals(3L, snapshot.targetTrueTickCount);
        assertEquals(3L, snapshot.consecutiveTargetTicks);
        assertEquals(3L, snapshot.maxConsecutiveTargetTicks);
        assertTrue(snapshot.lastClassifierIsTarget);

        counters.recordClassifier(false);
        snapshot = counters.snapshot();
        assertEquals(1L, snapshot.targetFalseTickCount);
        assertEquals(0L, snapshot.consecutiveTargetTicks);
        assertEquals(3L, snapshot.maxConsecutiveTargetTicks);
        assertFalse(snapshot.lastClassifierIsTarget);
    }

    @Test public void machineSessionIncreaseCountOnlyCountsActualGrowth() {
        FlowForegroundService.RuntimeTrackingCounters counters =
                new FlowForegroundService.RuntimeTrackingCounters();

        counters.recordMachineSession(0L, 0L);
        counters.recordMachineSession(10_000L, 10_000L);
        counters.recordMachineSession(20_000L, 19_000L);
        counters.recordMachineSession(20_000L, 25_000L);

        FlowForegroundService.RuntimeTrackingSnapshot snapshot = counters.snapshot();
        assertEquals(1L, snapshot.machineSessionIncreaseCount);
        assertEquals(20_000L, snapshot.machineSessionBeforeMs);
        assertEquals(25_000L, snapshot.machineSessionAfterMs);
    }

    @Test public void detectorAndAccumulatorSignalsRemainDistinct() {
        FlowForegroundService.RuntimeTrackingCounters counters =
                new FlowForegroundService.RuntimeTrackingCounters();

        counters.recordTick(1_000L);
        counters.recordDetector(true, true);
        counters.recordClassifier(false);
        counters.recordAccumulator(0L, false);
        counters.recordTick(3_000L);
        counters.recordDetector(false, true);
        counters.recordClassifier(false);
        counters.recordAccumulator(0L, false);
        counters.recordTick(5_000L);
        counters.recordDetector(true, true);
        counters.recordClassifier(true);
        counters.recordAccumulator(1_000L, true);

        FlowForegroundService.RuntimeTrackingSnapshot snapshot = counters.snapshot();
        assertEquals(3L, snapshot.tickCount);
        assertEquals(2L, snapshot.detectorNonEmptyTickCount);
        assertEquals(1L, snapshot.targetTrueTickCount);
        assertEquals(2L, snapshot.targetFalseTickCount);
        assertEquals(1L, snapshot.positiveObservedTargetTickCount);
        assertEquals(2_000L, snapshot.lastTickDeltaMs);
        assertEquals(3L, snapshot.foregroundChangedCount);
        assertTrue(snapshot.lastForegroundPresent);
        assertTrue(snapshot.accumulatorLastTargetPresent);
    }

    private BlockStateMachine freshMachine() {
        return new BlockStateMachine(BlockStateMachine.State.IDLE, 0L, 0L, 0L, "");
    }
}
