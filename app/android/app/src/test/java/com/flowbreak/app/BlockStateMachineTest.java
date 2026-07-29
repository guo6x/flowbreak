package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class BlockStateMachineTest {
    private static final long LIMIT = 100_000L;

    @Test public void mapsThresholdBoundaries() {
        assertEquals(BlockStateMachine.State.IDLE, BlockStateMachine.stateFor(79_999, LIMIT));
        assertEquals(BlockStateMachine.State.PERCEPTION, BlockStateMachine.stateFor(80_000, LIMIT));
        assertEquals(BlockStateMachine.State.COGNITION, BlockStateMachine.stateFor(100_000, LIMIT));
        assertEquals(BlockStateMachine.State.BLOCKED, BlockStateMachine.stateFor(120_000, LIMIT));
    }

    @Test public void switchesBetweenTargetAppsWithoutResettingSharedSession() {
        BlockStateMachine machine = fresh();
        machine.update(true, "one", 1_000, LIMIT);
        machine.update(true, "one", 11_000, LIMIT);
        machine.update(true, "two", 21_000, LIMIT);
        assertEquals(20_000, machine.getSessionMs());
        assertEquals("two", machine.getBlockedPackage());
    }

    @Test public void leavingTargetsForThirtySecondsResetsSession() {
        BlockStateMachine machine = fresh();
        machine.update(true, "one", 1_000, LIMIT);
        machine.update(true, "one", 11_000, LIMIT);
        machine.update(false, "", 12_000, LIMIT);
        machine.update(false, "", 42_000, LIMIT);
        assertEquals(BlockStateMachine.State.IDLE, machine.getState());
        assertEquals(0, machine.getSessionMs());
    }

    @Test public void completedRestCreatesGraceThenStartsNewSession() {
        BlockStateMachine machine = fresh();
        machine.completeRest(10_000, 600_000);
        assertEquals(BlockStateMachine.State.GRACE, machine.update(true, "one", 20_000, LIMIT));
        assertEquals(610_000, machine.getGraceUntil());
        assertEquals(BlockStateMachine.State.IDLE, machine.update(false, "", 610_001, LIMIT));
        machine.update(true, "one", 611_000, LIMIT);
        machine.update(true, "one", 621_000, LIMIT);
        assertEquals(10_000, machine.getSessionMs());
    }

    @Test public void restoredBlockedStateDoesNotGainOfflineTime() {
        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.BLOCKED, 120_000, 0, 0, "one"
        );
        machine.update(true, "one", 5_000_000, LIMIT);
        assertEquals(120_000, machine.getSessionMs());
        assertEquals(BlockStateMachine.State.BLOCKED, machine.getState());
    }

    @Test public void targetAlreadyOpenAtGraceExpiryStartsFromZero() {
        BlockStateMachine machine = fresh();
        machine.completeRest(10_000, 10_000);
        machine.update(true, "one", 15_000, LIMIT);
        machine.update(true, "one", 20_001, LIMIT);
        assertEquals(0, machine.getSessionMs());
        machine.update(true, "one", 30_001, LIMIT);
        assertEquals(10_000, machine.getSessionMs());
    }

    @Test public void cancellingRestRestoresBlockedState() {
        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.BLOCKED, 120_000, 0, 0, "one"
        );
        machine.beginRest();
        assertEquals(BlockStateMachine.State.BLOCKED, machine.cancelRest(LIMIT));
    }

    @Test public void screenOffForThirtySecondsResetsBeforeNextTargetUse() {
        BlockStateMachine machine = fresh();
        machine.update(true, "one", 1_000, LIMIT);
        machine.update(true, "one", 11_000, LIMIT);
        machine.onScreenOff(12_000);
        machine.onScreenOn(43_000);
        machine.update(true, "one", 44_000, LIMIT);
        assertEquals(BlockStateMachine.State.IDLE, machine.getState());
        assertEquals(0, machine.getSessionMs());

        machine.update(true, "one", 54_000, LIMIT);
        assertEquals(10_000, machine.getSessionMs());
    }

    private BlockStateMachine fresh() {
        return new BlockStateMachine(BlockStateMachine.State.IDLE, 0, 0, 0, "");
    }
}
