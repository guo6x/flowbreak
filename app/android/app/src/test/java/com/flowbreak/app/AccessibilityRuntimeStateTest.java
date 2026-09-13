package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AccessibilityRuntimeStateTest {
    @Before public void setUp() {
        AccessibilityRuntimeState.resetForTests();
    }

    @After public void tearDown() {
        AccessibilityRuntimeState.resetForTests();
    }

    @Test public void oldGenerationCannotDisconnectNewConnection() {
        long first = AccessibilityRuntimeState.connect();
        long second = AccessibilityRuntimeState.connect();

        AccessibilityRuntimeState.disconnect(first);

        assertTrue(AccessibilityRuntimeState.isConnected());
        assertTrue(AccessibilityRuntimeState.connectedGeneration() == second);
    }

    @Test public void currentGenerationDisconnects() {
        long generation = AccessibilityRuntimeState.connect();

        AccessibilityRuntimeState.disconnect(generation);

        assertFalse(AccessibilityRuntimeState.isConnected());
    }

    @Test public void initialStateIsDisconnected() {
        assertFalse(AccessibilityRuntimeState.isConnected());
    }
}
