package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** Regression coverage for verified target time during an active RESTING session. */
public class RestCheatReplayIntegrationTest {
    private static final String TARGET = "com.example.target";
    private static final long START = 1_000_000L;
    private static final long LIMIT_MS = 300_000L;

    @Test
    public void historicalTargetUseDuringRestCancelsRest() {
        BlockStateMachine machine = restingMachine(400_000L);
        RestCheatTracker tracker = new RestCheatTracker();

        RestCheatReplay.Decision decision = apply(machine, tracker, 60_000L);

        assertTrue(decision.cancelled);
        assertEquals(BlockStateMachine.State.BLOCKED, machine.getState());
        assertEquals(60_000L, decision.accumulatedMs);
        assertEquals(0L, tracker.accumulatedMs());

        // A later live non-target sample cannot make the cancelled rest valid again.
        machine.updateAfterVerifiedGap(
                false,
                "com.flowbreak.app",
                START + 70_000L,
                START + 60_000L,
                LIMIT_MS
        );
        tracker.observe(machine.getState() == BlockStateMachine.State.RESTING,
                false, START + 60_000L, START + 70_000L);
        assertEquals(BlockStateMachine.State.BLOCKED, machine.getState());
        assertEquals(0L, tracker.accumulatedMs());
    }

    @Test
    public void historicalRestCheatBelowThresholdKeepsResting() {
        BlockStateMachine machine = restingMachine(400_000L);
        RestCheatTracker tracker = new RestCheatTracker();

        RestCheatReplay.Decision decision = apply(machine, tracker, 4_000L);

        assertFalse(decision.cancelled);
        assertEquals(BlockStateMachine.State.RESTING, machine.getState());
        assertEquals(4_000L, tracker.accumulatedMs());
    }

    @Test
    public void historicalPlusExistingCheatIsCumulative() {
        BlockStateMachine machine = restingMachine(400_000L);
        RestCheatTracker tracker = new RestCheatTracker();
        tracker.observe(true, true, 1_000L, 4_000L); // existing live cheat = 3000ms

        RestCheatReplay.Decision decision = apply(machine, tracker, 3_000L);

        assertTrue(decision.cancelled);
        assertEquals(6_000L, decision.accumulatedMs);
        assertEquals(BlockStateMachine.State.BLOCKED, machine.getState());
    }

    @Test
    public void historicalTargetUseOutsideRestingDoesNotTriggerCheat() {
        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.COGNITION,
                300_000L,
                0L,
                0L,
                TARGET
        );
        RestCheatTracker tracker = new RestCheatTracker();

        RestCheatReplay.Decision decision = apply(machine, tracker, 60_000L);

        assertFalse(decision.cancelled);
        assertEquals(BlockStateMachine.State.COGNITION, machine.getState());
        assertEquals(0L, tracker.accumulatedMs());
    }

    @Test
    public void historicalRestCheatHandledOnce() {
        BlockStateMachine machine = restingMachine(400_000L);
        RestCheatTracker tracker = new RestCheatTracker();
        TargetSessionGapReconciler.Result result = historicalResult(60_000L);

        RestCheatReplay.Decision first = RestCheatReplay.applyVerifiedTargetMs(
                machine, tracker, result.historicalTargetMsRecovered, LIMIT_MS
        );
        RestCheatReplay.Decision second = RestCheatReplay.applyVerifiedTargetMs(
                machine, tracker, result.historicalTargetMsRecovered, LIMIT_MS
        );

        assertTrue(first.cancelled);
        assertFalse(second.cancelled);
        assertEquals(BlockStateMachine.State.BLOCKED, machine.getState());
        assertEquals(0L, tracker.accumulatedMs());
    }

    private static BlockStateMachine restingMachine(long sessionMs) {
        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.RESTING,
                sessionMs,
                0L,
                0L,
                TARGET
        );
        machine.seedCheckpoint(START, true);
        return machine;
    }

    private static TargetSessionGapReconciler.Result historicalResult(long targetMs) {
        Set<String> targets = new HashSet<>();
        targets.add(TARGET);
        return TargetSessionGapReconciler.reconcile(
                new TargetSessionGapReconciler.Input(
                        START,
                        START + targetMs,
                        TARGET,
                        true,
                        true,
                        targets,
                        Collections.emptyList()
                )
        );
    }

    private static RestCheatReplay.Decision apply(
            BlockStateMachine machine,
            RestCheatTracker tracker,
            long targetMs
    ) {
        TargetSessionGapReconciler.Result result = historicalResult(targetMs);
        assertEquals(targetMs, result.historicalTargetMsRecovered);
        return RestCheatReplay.applyVerifiedTargetMs(
                machine,
                tracker,
                result.historicalTargetMsRecovered,
                LIMIT_MS
        );
    }
}
