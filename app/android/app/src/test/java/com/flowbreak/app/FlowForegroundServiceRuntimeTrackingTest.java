package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
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

    @Test public void recentTickRingBufferHasMaximum64Entries() {
        FlowForegroundService.RuntimeTrackingCounters counters =
                new FlowForegroundService.RuntimeTrackingCounters();

        for (int i = 0; i < 64; i++) {
            long start = 1_000L + i * 2_000L;
            counters.recordMonitorStart(0L, start, 0L);
            counters.recordTick(start, true, false, true);
            counters.recordMonitorEnd(0L, start + 10L, 0L);
        }

        FlowForegroundService.RuntimeTrackingSnapshot snapshot = counters.snapshot();
        assertEquals(64, snapshot.recentTicks.length);
        assertEquals(1L, snapshot.recentTicks[0].sequence);
        assertEquals(64L, snapshot.recentTicks[63].sequence);
    }

    @Test public void recentTickRingBufferKeepsLatest64AfterOverflow() {
        FlowForegroundService.RuntimeTrackingCounters counters =
                new FlowForegroundService.RuntimeTrackingCounters();

        for (int i = 0; i < 70; i++) {
            long start = 1_000L + i * 2_000L;
            counters.recordMonitorStart(0L, start, 0L);
            counters.recordTick(start, true, false, true);
            counters.recordMonitorEnd(0L, start + 10L, 0L);
        }

        FlowForegroundService.RuntimeTrackingSnapshot snapshot = counters.snapshot();
        assertEquals(64, snapshot.recentTicks.length);
        assertEquals(7L, snapshot.recentTicks[0].sequence);
        assertEquals(70L, snapshot.recentTicks[63].sequence);
    }

    @Test public void executionAndPostDelayGapAreMeasuredSeparately() {
        FlowForegroundService.RuntimeTrackingCounters counters =
                new FlowForegroundService.RuntimeTrackingCounters();

        counters.recordMonitorStart(0L, 1_000L, 0L);
        counters.recordTick(1_000L, true, false, true);
        counters.recordMonitorEnd(0L, 1_100L, 0L);
        counters.recordMonitorStart(0L, 3_100L, 0L);
        counters.recordTick(3_100L, true, false, true);
        counters.recordMonitorEnd(0L, 3_300L, 0L);

        FlowForegroundService.RuntimeTrackingSnapshot snapshot = counters.snapshot();
        assertEquals(200L, snapshot.lastTickExecutionMs);
        assertEquals(200L, snapshot.maxTickExecutionMs);
        assertEquals(150D, snapshot.averageTickExecutionMs, 0.001D);
        assertEquals(2_000L, snapshot.lastPostDelayGapMs);
        assertEquals(2_000L, snapshot.maxPostDelayGapMs);
        assertEquals(2_000D, snapshot.averagePostDelayGapMs, 0.001D);
        assertEquals(2_100L, snapshot.recentTicks[1].startDeltaMs);
        assertEquals(2_000L, snapshot.recentTicks[1].postDelayGapMs);
    }

    @Test public void classifierFalseReasonCountersRemainDistinct() {
        FlowForegroundService.RuntimeTrackingCounters counters =
                new FlowForegroundService.RuntimeTrackingCounters();

        counters.recordTick(1_000L, true, false, true);
        counters.recordClassifierSignals(true, true, false, false, false);
        counters.recordTick(3_000L, true, false, true);
        counters.recordClassifierSignals(true, false, false, false, true);

        FlowForegroundService.RuntimeTrackingSnapshot snapshot = counters.snapshot();
        assertEquals(1L, snapshot.foregroundInRuntimeTargetTickCount);
        assertEquals(1L, snapshot.foregroundNotInRuntimeTargetTickCount);
        assertEquals(1L, snapshot.classifierFalseForegroundInRuntimeTargetCount);
        assertEquals(1L, snapshot.classifierFalseForegroundNotInRuntimeTargetCount);
        assertEquals(0L, snapshot.foregroundIsSelfPackageTickCount);
        assertEquals(1L, snapshot.foregroundIsOtherNonTargetTickCount);
    }

    @Test public void runtimeAndPersistedTargetSetEqualityIsExact() {
        assertTrue(FlowForegroundService.targetSetsMatch(
                new HashSet<>(Arrays.asList("com.example.video", "tv.danmaku.bili")),
                new HashSet<>(Arrays.asList("tv.danmaku.bili", "com.example.video"))
        ));
        assertFalse(FlowForegroundService.targetSetsMatch(
                new HashSet<>(Arrays.asList("com.example.video")),
                new HashSet<>(Arrays.asList("tv.danmaku.bili"))
        ));
        assertFalse(FlowForegroundService.targetSetsMatch(null, new HashSet<String>()));
    }

    @Test public void earlyReturnCountersDoNotBecomeClassifierFalse() {
        FlowForegroundService.RuntimeTrackingCounters counters =
                new FlowForegroundService.RuntimeTrackingCounters();

        counters.recordMonitorStart(0L, 1_000L, 0L);
        counters.recordTick(1_000L, false, false, true);
        counters.recordMonitoringDisabledReturn();
        counters.recordMonitorEnd(0L, 1_001L, 0L);
        counters.recordMonitorStart(0L, 3_000L, 0L);
        counters.recordTick(3_000L, true, true, true);
        counters.recordTargetSetEmptyReturn();
        counters.recordMonitorEnd(0L, 3_001L, 0L);
        counters.recordMonitorStart(0L, 5_000L, 0L);
        counters.recordTick(5_000L, true, false, false);
        counters.recordInteractionUnavailableReturn();
        counters.recordMonitorEnd(0L, 5_001L, 0L);

        FlowForegroundService.RuntimeTrackingSnapshot snapshot = counters.snapshot();
        assertEquals(1L, snapshot.monitoringDisabledTickCount);
        assertEquals(1L, snapshot.targetSetEmptyTickCount);
        assertEquals(1L, snapshot.interactionUnavailableTickCount);
        assertEquals(0L, snapshot.targetTrueTickCount);
        assertEquals(0L, snapshot.targetFalseTickCount);
        assertEquals(3, snapshot.recentTicks.length);
    }

    @Test public void disabledMonitoringTickPreservesAnActiveRestSession() {
        assertFalse(FlowForegroundService.shouldResetMonitoringLifecycleForDisabledTick(
                BlockStateMachine.State.RESTING
        ));
        assertTrue(FlowForegroundService.shouldResetMonitoringLifecycleForDisabledTick(
                BlockStateMachine.State.IDLE
        ));
        assertTrue(FlowForegroundService.shouldResetMonitoringLifecycleForDisabledTick(null));
    }

    @Test public void unavailableRuntimeTickPreservesAnActiveRestSession() {
        assertFalse(FlowForegroundService.shouldResetMonitoringLifecycleForUnavailableTick(
                BlockStateMachine.State.RESTING
        ));
        assertTrue(FlowForegroundService.shouldResetMonitoringLifecycleForUnavailableTick(
                BlockStateMachine.State.BLOCKED
        ));
        assertTrue(FlowForegroundService.shouldResetMonitoringLifecycleForUnavailableTick(null));
    }

    @Test public void integrityFailureCancelsOnlyAnActiveRestSession() {
        assertTrue(FlowForegroundService.shouldCancelRestForIntegrityFailure(
                BlockStateMachine.State.RESTING
        ));
        assertFalse(FlowForegroundService.shouldCancelRestForIntegrityFailure(
                BlockStateMachine.State.BLOCKED
        ));
        assertFalse(FlowForegroundService.shouldCancelRestForIntegrityFailure(
                BlockStateMachine.State.IDLE
        ));
        assertFalse(FlowForegroundService.shouldCancelRestForIntegrityFailure(null));
    }

    private BlockStateMachine freshMachine() {
        return new BlockStateMachine(BlockStateMachine.State.IDLE, 0L, 0L, 0L, "");
    }
}
