package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RestCancellationGateTest {
    @Test
    public void currentRestSessionCanBeCancelled() {
        assertTrue(RestCancellationGate.shouldCancel(
                BlockStateMachine.State.RESTING,
                101L,
                101L,
                "request-current",
                "request-current"
        ));
    }

    @Test
    public void staleSessionIdentityCannotCancel() {
        assertFalse(RestCancellationGate.shouldCancel(
                BlockStateMachine.State.RESTING,
                100L,
                101L,
                "request-current",
                "request-current"
        ));
        assertFalse(RestCancellationGate.shouldCancel(
                BlockStateMachine.State.RESTING,
                101L,
                0L,
                "request-current",
                "request-current"
        ));
    }

    @Test
    public void matchingPendingBeginCanBeCancelledWithoutWildcardingOtherSessions() {
        assertTrue(RestCancellationGate.shouldCancel(
                BlockStateMachine.State.RESTING, 0L, 101L,
                "request-pending", "request-pending"
        ));
        assertFalse(RestCancellationGate.shouldCancel(
                BlockStateMachine.State.RESTING, 0L, 0L,
                "request-pending", "request-pending"
        ));
        assertFalse(RestCancellationGate.shouldCancel(
                BlockStateMachine.State.RESTING, 0L, 101L,
                "request-old", "request-new"
        ));
    }

    @Test
    public void onlyCurrentBeginRequestMayEnterRest() {
        assertTrue(RestCancellationGate.shouldAcceptBegin("request-current", "request-current"));
        assertFalse(RestCancellationGate.shouldAcceptBegin("request-old", "request-current"));
        assertFalse(RestCancellationGate.shouldAcceptBegin("", "request-current"));
    }

    @Test
    public void missingRequestTokenCannotCancel() {
        assertFalse(RestCancellationGate.shouldCancel(
                BlockStateMachine.State.RESTING, 101L, 101L, null, "request-current"
        ));
        assertFalse(RestCancellationGate.shouldCancel(
                BlockStateMachine.State.RESTING, 101L, 101L, "", "request-current"
        ));
    }

    @Test
    public void cancelCannotChangeGraceOrAnotherNonRestState() {
        for (BlockStateMachine.State state : BlockStateMachine.State.values()) {
            if (state == BlockStateMachine.State.RESTING) continue;
            assertFalse("state=" + state, RestCancellationGate.shouldCancel(
                    state, 101L, 101L, "request-current", "request-current"
            ));
        }
    }
}
