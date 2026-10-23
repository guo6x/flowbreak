package com.flowbreak.app;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class RestCompletionHistoryGateTest {
    private static final long SESSION_ID = 101L;
    private static final long STARTED_AT = 1_000_000L;
    private static final long REQUIRED_MS = 60_000L;
    private static final long NOW = STARTED_AT + REQUIRED_MS;

    @Test
    public void completeHistoryBelowThresholdAllowsCompletion() {
        assertEquals(RestCompletionHistoryGate.Decision.ALLOW, evaluate(true, 4_999L));
    }

    @Test
    public void completeHistoryAtThresholdCancelsInsteadOfRewarding() {
        assertEquals(RestCompletionHistoryGate.Decision.CHEATED, evaluate(true, 5_000L));
    }

    @Test
    public void queryToSubmissionTailCannotCrossThresholdUnverified() {
        assertEquals(RestCompletionHistoryGate.Decision.DEFER,
                evaluate(true, 4_990L, 20L));
        assertEquals(RestCompletionHistoryGate.Decision.ALLOW,
                evaluate(true, 4_990L, 9L));
    }

    @Test
    public void complete4000Plus1200MillisecondWindowIsRejectedByFullHistory() {
        // The live counter may still read 4000ms. Authorization uses the full
        // reconstructed interval instead of adding a second partial tail.
        assertEquals(RestCompletionHistoryGate.Decision.CHEATED, evaluate(true, 5_200L));
    }

    @Test
    public void separatedTargetVisitsAreSummedFromTheCompleteRestTimeline() {
        String target = "com.example.target";
        TargetSessionGapReconciler.Result history = TargetSessionGapReconciler.reconcile(
                new TargetSessionGapReconciler.Input(
                        STARTED_AT,
                        NOW,
                        "com.example.other",
                        false,
                        true,
                        Collections.singleton(target),
                        Arrays.asList(
                                TargetSessionGapReconciler.Event.foreground(
                                        target, "Main", STARTED_AT + 10_000L
                                ),
                                TargetSessionGapReconciler.Event.background(
                                        target, "Main", STARTED_AT + 14_000L
                                ),
                                TargetSessionGapReconciler.Event.foreground(
                                        target, "Main", STARTED_AT + 20_000L
                                ),
                                TargetSessionGapReconciler.Event.background(
                                        target, "Main", STARTED_AT + 21_200L
                                )
                        )
                )
        );
        long verifiedTargetMs = RestCheatReplay.verifiedTargetMsDuring(
                history,
                STARTED_AT,
                NOW
        );

        assertEquals(TargetSessionGapReconciler.Status.SUCCESS, history.status);
        assertEquals(REQUIRED_MS, history.coverageMs);
        assertEquals(5_200L, verifiedTargetMs);
        assertEquals(RestCompletionHistoryGate.Decision.CHEATED,
                RestCompletionHistoryGate.evaluate(
                        BlockStateMachine.State.RESTING.name(), SESSION_ID, SESSION_ID, 0L,
                        STARTED_AT, REQUIRED_MS, NOW, true, verifiedTargetMs
                ));
    }

    @Test
    public void missingOrIncompleteHistoryDefers() {
        assertEquals(RestCompletionHistoryGate.Decision.DEFER,
                RestCompletionHistoryGate.evaluate(
                        BlockStateMachine.State.RESTING.name(), SESSION_ID, SESSION_ID, 0L,
                        STARTED_AT, REQUIRED_MS, NOW, false, 0L
                ));
        assertEquals(RestCompletionHistoryGate.Decision.DEFER, evaluate(true, -1L));
    }

    @Test
    public void staleSessionCannotCompleteCurrentRest() {
        assertEquals(RestCompletionHistoryGate.Decision.SESSION_MISMATCH,
                RestCompletionHistoryGate.evaluate(
                        BlockStateMachine.State.RESTING.name(), SESSION_ID - 1L, SESSION_ID, 0L,
                        STARTED_AT, REQUIRED_MS, NOW, true, 0L
                ));
    }

    @Test
    public void incompleteRestAndMissingSessionCannotComplete() {
        assertEquals(RestCompletionHistoryGate.Decision.NOT_READY,
                RestCompletionHistoryGate.evaluate(
                        BlockStateMachine.State.RESTING.name(), SESSION_ID, SESSION_ID, 0L,
                        STARTED_AT, REQUIRED_MS, NOW - 1L, true, 0L
                ));
        assertEquals(RestCompletionHistoryGate.Decision.SESSION_MISMATCH,
                RestCompletionHistoryGate.evaluate(
                        BlockStateMachine.State.RESTING.name(), 0L, SESSION_ID, 0L,
                        STARTED_AT, REQUIRED_MS, NOW, true, 0L
                ));
    }

    @Test
    public void committedRetryRemainsIdempotentlyAllowedWithoutHistoryReplay() {
        assertEquals(RestCompletionHistoryGate.Decision.ALLOW,
                RestCompletionHistoryGate.evaluate(
                        BlockStateMachine.State.GRACE.name(), SESSION_ID, 0L, SESSION_ID,
                        0L, 0L, NOW, false, -1L
                ));
    }

    private static RestCompletionHistoryGate.Decision evaluate(
            boolean historyComplete,
            long verifiedTargetMs
    ) {
        return evaluate(historyComplete, verifiedTargetMs, 0L);
    }

    private static RestCompletionHistoryGate.Decision evaluate(
            boolean historyComplete,
            long verifiedTargetMs,
            long unverifiedTailMs
    ) {
        return RestCompletionHistoryGate.evaluate(
                BlockStateMachine.State.RESTING.name(), SESSION_ID, SESSION_ID, 0L,
                STARTED_AT, REQUIRED_MS, NOW, historyComplete, verifiedTargetMs,
                unverifiedTailMs
        );
    }

    @Test
    public void productionClockGateRejectsWallClockFastForward() {
        assertEquals(RestCompletionHistoryGate.Decision.NOT_READY,
                RestCompletionHistoryGate.evaluate(
                        BlockStateMachine.State.RESTING.name(),
                        SESSION_ID,
                        SESSION_ID,
                        0L,
                        STARTED_AT,
                        STARTED_AT,
                        4,
                        REQUIRED_MS,
                        STARTED_AT + 24 * 60 * 60_000L,
                        STARTED_AT + 10_000L,
                        4,
                        0L,
                        true,
                        0L,
                        0L
                ));
    }

    @Test
    public void productionClockGateRejectsBootChangeAndTimeSetMarker() {
        assertEquals(RestCompletionHistoryGate.Decision.NOT_READY,
                evaluateProduction(5, 0L));
        assertEquals(RestCompletionHistoryGate.Decision.NOT_READY,
                evaluateProduction(4, SESSION_ID));
    }

    private static RestCompletionHistoryGate.Decision evaluateProduction(
            int currentBootCount,
            long invalidatedSessionId
    ) {
        return RestCompletionHistoryGate.evaluate(
                BlockStateMachine.State.RESTING.name(),
                SESSION_ID,
                SESSION_ID,
                0L,
                STARTED_AT,
                STARTED_AT,
                4,
                REQUIRED_MS,
                NOW,
                NOW,
                currentBootCount,
                invalidatedSessionId,
                true,
                0L,
                0L
        );
    }
}
