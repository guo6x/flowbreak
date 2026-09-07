package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NativeFlowPermissionManagerTest {
    @Test public void requiresBackgroundStabilityForEvidenceBackedManufacturers() {
        assertTrue(NativeFlowPermissionManager.requiresBackgroundStability("vivo"));
        assertTrue(NativeFlowPermissionManager.requiresBackgroundStability("VIVO"));
        assertTrue(NativeFlowPermissionManager.requiresBackgroundStability("iQOO"));
    }

    @Test public void doesNotExpandRequirementWithoutDeviceEvidence() {
        assertFalse(NativeFlowPermissionManager.requiresBackgroundStability("samsung"));
        assertFalse(NativeFlowPermissionManager.requiresBackgroundStability("xiaomi"));
        assertFalse(NativeFlowPermissionManager.requiresBackgroundStability(null));
    }
}
