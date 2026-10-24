package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RestCancellationCallbackGateTest {
    @Test
    public void shutdownDefersOnlyCallbacksThatHaveNotStarted() {
        RestCancellationCallbackGate pending = new RestCancellationCallbackGate();
        assertTrue(pending.deferIfPending());
        assertFalse(pending.deferIfPending());
        assertFalse(pending.start());
        assertFalse(pending.finish());

        RestCancellationCallbackGate running = new RestCancellationCallbackGate();
        assertTrue(running.start());
        assertFalse(running.deferIfPending());
        assertTrue(running.finish());
        assertFalse(running.finish());
    }

    @Test
    public void failedQueueCanCompletePendingCallbackExactlyOnce() {
        RestCancellationCallbackGate gate = new RestCancellationCallbackGate();

        assertTrue(gate.finish());
        assertFalse(gate.finish());
        assertFalse(gate.start());
        assertFalse(gate.deferIfPending());
    }
}
