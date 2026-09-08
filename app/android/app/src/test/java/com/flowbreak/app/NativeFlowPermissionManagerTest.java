package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NativeFlowPermissionManagerTest {
    @Test public void vivoAndIqooRequireUnsupportedFailClosed() {
        assertTrue(NativeFlowPermissionManager.requiresUnsupportedOemFailClosed("vivo"));
        assertTrue(NativeFlowPermissionManager.requiresUnsupportedOemFailClosed("VIVO"));
        assertTrue(NativeFlowPermissionManager.requiresUnsupportedOemFailClosed("iQOO"));
        assertTrue(NativeFlowPermissionManager.requiresUnsupportedOemFailClosed("iqoo-pro"));
    }

    @Test public void unsupportedPolicyDoesNotGeneralizeToOtherOems() {
        assertFalse(NativeFlowPermissionManager.requiresUnsupportedOemFailClosed("xiaomi"));
        assertFalse(NativeFlowPermissionManager.requiresUnsupportedOemFailClosed("redmi"));
        assertFalse(NativeFlowPermissionManager.requiresUnsupportedOemFailClosed("samsung"));
        assertFalse(NativeFlowPermissionManager.requiresUnsupportedOemFailClosed("oppo"));
        assertFalse(NativeFlowPermissionManager.requiresUnsupportedOemFailClosed("honor"));
        assertFalse(NativeFlowPermissionManager.requiresUnsupportedOemFailClosed(null));
    }

    @Test public void runtimeAvailabilityIsInverseOfUnsupportedPolicy() {
        assertFalse(NativeFlowPermissionManager.isProtectionRuntimeAvailable("vivo"));
        assertFalse(NativeFlowPermissionManager.isProtectionRuntimeAvailable("iQOO"));
        assertTrue(NativeFlowPermissionManager.isProtectionRuntimeAvailable("xiaomi"));
    }

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
