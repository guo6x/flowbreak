package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BlockedRestEntryGateTest {
    @Test public void unhealthyCoreRejectsTransitionFromBlocked() {
        assertFalse(BlockedRestEntryGate.isAllowed(
                BlockStateMachine.State.BLOCKED.name(),
                false
        ));
    }

    @Test public void healthyCoreAllowsTransitionFromBlocked() {
        assertTrue(BlockedRestEntryGate.isAllowed(
                BlockStateMachine.State.BLOCKED.name(),
                true
        ));
    }

    @Test public void nonBlockedStatesKeepManualAndRestorationSemantics() {
        assertTrue(BlockedRestEntryGate.isAllowed(
                BlockStateMachine.State.IDLE.name(),
                false
        ));
        assertTrue(BlockedRestEntryGate.isAllowed(
                BlockStateMachine.State.RESTING.name(),
                false
        ));
    }
}
