package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Regression coverage for the paused manual REST integrity path. */
public class PausedRestIntegrityTest {
    private static final long LIMIT_MS = 300_000L;
    private static final long START = 1_000L;

    @Test public void pausedTargetUseAtLeastFiveSecondsCancelsRestWithoutGrace() {
        BlockStateMachine machine = restingMachine();
        RestCheatTracker tracker = new RestCheatTracker();

        tracker.observe(true, true, 0L, START);
        tracker.observe(true, true, START, START + 3_000L);
        tracker.observe(true, true, START + 3_000L, START + 6_000L);

        RestCheatReplay.Decision decision = RestCheatReplay.cancelTriggered(
                machine,
                tracker,
                LIMIT_MS
        );

        assertTrue(decision.cancelled);
        assertEquals(BlockStateMachine.State.IDLE, machine.getState());
        assertFalse(machine.getState() == BlockStateMachine.State.GRACE);
        assertEquals(6_000L, decision.accumulatedMs);
        assertEquals(0L, tracker.accumulatedMs());
    }

    @Test public void pausedNonTargetObservationKeepsRestAndMayRefreshVerifiedTick() {
        assertTrue(FlowForegroundService.isPausedRestIntegrityObservationTrustworthy(
                true,
                true,
                true,
                true
        ));

        BlockStateMachine machine = restingMachine();
        RestCheatTracker tracker = new RestCheatTracker();
        tracker.observe(true, false, 0L, START);

        assertEquals(BlockStateMachine.State.RESTING, machine.getState());
        assertEquals(0L, tracker.accumulatedMs());
    }

    @Test public void pausedUsageEventsFailureCannotProduceVerifiedTick() {
        assertFalse(FlowForegroundService.isPausedRestIntegrityObservationTrustworthy(
                true,
                true,
                true,
                false
        ));
    }

    @Test public void pausedUsageAccessRevocationCannotProduceVerifiedTick() {
        assertFalse(FlowForegroundService.isPausedRestIntegrityObservationTrustworthy(
                false,
                true,
                true,
                true
        ));
    }

    @Test public void pausedTargetCheatRemainsCumulativeAcrossNonTargetGap() {
        BlockStateMachine machine = restingMachine();
        RestCheatTracker tracker = new RestCheatTracker();

        tracker.observe(true, true, START, START + 3_000L);
        tracker.observe(true, false, START + 3_000L, START + 4_000L);
        tracker.observe(true, true, START + 4_000L, START + 6_000L);

        RestCheatReplay.Decision decision = RestCheatReplay.cancelTriggered(
                machine,
                tracker,
                LIMIT_MS
        );

        assertTrue(decision.cancelled);
        assertEquals(5_000L, decision.accumulatedMs);
        assertEquals(BlockStateMachine.State.IDLE, machine.getState());
    }

    private static BlockStateMachine restingMachine() {
        return new BlockStateMachine(
                BlockStateMachine.State.RESTING,
                0L,
                0L,
                0L,
                ""
        );
    }
}
