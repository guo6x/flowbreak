package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public class GapReconciliationWindowTest {
    private static final String TARGET = "com.example.target";
    private static final long CHECKPOINT = 1_000_000L;
    private static final long LIMIT_MS = 300_000L;

    @Test
    public void shortSixSecondGapFallsBackToLiveClamp() {
        assertShortGapUsesLivePath(6_000L);
    }

    @Test
    public void shortNineSecondGapPreservesContinuousTarget() {
        assertShortGapUsesLivePath(9_000L);
    }

    @Test
    public void tenSecondBoundaryDoesNotBecomeZero() {
        GapReconciliationWindow.Decision decision = GapReconciliationWindow.classify(
                10_000L, CHECKPOINT, CHECKPOINT + 10_000L
        );
        assertEquals(GapReconciliationWindow.Kind.SKIPPED_LIVE_WINDOW, decision.kind);
        assertLiveAccounting(10_000L);
    }

    @Test
    public void twelveSecondGapUsesClosedHistoryAndLiveTailWithoutDuplicate() {
        GapReconciliationWindow.Decision decision = GapReconciliationWindow.classify(
                12_000L, CHECKPOINT, CHECKPOINT + 12_000L
        );
        assertEquals(GapReconciliationWindow.Kind.CLOSED_HISTORICAL_WINDOW, decision.kind);
        assertEquals(CHECKPOINT + 2_000L, decision.safeEndWallMs);

        TargetSessionGapReconciler.Result history = TargetSessionGapReconciler.reconcile(
                new TargetSessionGapReconciler.Input(
                        CHECKPOINT,
                        decision.safeEndWallMs,
                        TARGET,
                        true,
                        true,
                        Collections.singleton(TARGET),
                        Collections.emptyList()
                )
        );
        assertEquals(TargetSessionGapReconciler.Status.SUCCESS, history.status);
        assertEquals(2_000L, history.historicalTargetMsRecovered);

        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.IDLE, 0L, 0L, 0L, ""
        );
        machine.seedCheckpoint(CHECKPOINT, true);
        machine.updateVerifiedHistory(true, TARGET, decision.safeEndWallMs, LIMIT_MS);
        machine.updateAfterVerifiedGap(
                true, TARGET, CHECKPOINT + 12_000L, decision.safeEndWallMs, LIMIT_MS
        );
        assertEquals(12_000L, machine.getSessionMs());

        UsageAccumulator accumulator = new UsageAccumulator();
        accumulator.restoreObservationAnchor(decision.safeEndWallMs, TARGET, true);
        assertEquals(10_000L, accumulator.observe(true, TARGET, CHECKPOINT + 12_000L));
    }

    private static void assertShortGapUsesLivePath(long gapMs) {
        GapReconciliationWindow.Decision decision = GapReconciliationWindow.classify(
                gapMs, CHECKPOINT, CHECKPOINT + gapMs
        );
        assertEquals(GapReconciliationWindow.Kind.SKIPPED_LIVE_WINDOW, decision.kind);
        assertFalse(decision.usesHistoricalReplay());
        assertLiveAccounting(gapMs);
    }

    private static void assertLiveAccounting(long gapMs) {
        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.IDLE, 0L, 0L, 0L, ""
        );
        machine.seedCheckpoint(CHECKPOINT, true);
        machine.update(true, TARGET, CHECKPOINT + gapMs, LIMIT_MS);
        assertEquals(gapMs, machine.getSessionMs());

        UsageAccumulator accumulator = new UsageAccumulator();
        accumulator.restoreObservationAnchor(CHECKPOINT, TARGET, true);
        assertEquals(gapMs, accumulator.observe(true, TARGET, CHECKPOINT + gapMs));
    }
}
