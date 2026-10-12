package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProtectionSettingsSaveGateTest {
    @Test public void editingExistingLimitIsNotMonitoringActivation() {
        assertFalse(ProtectionSettingsSaveGate.requiresActivationCheck(false, true));
        assertFalse(ProtectionSettingsSaveGate.requiresActivationCheck(false, false));
    }

    @Test public void explicitEnableMustStillPassNativeFailClosedGate() {
        assertTrue(ProtectionSettingsSaveGate.requiresActivationCheck(true, true));
        assertFalse(ProtectionSettingsSaveGate.requiresActivationCheck(true, false));
    }

    @Test public void passiveSettingsCannotCreateBootConfiguredState() {
        assertFalse(ProtectionSettingsSaveGate.shouldMarkConfigured(false, true));
        assertFalse(ProtectionSettingsSaveGate.shouldMarkConfigured(false, false));
        assertFalse(ProtectionSettingsSaveGate.shouldMarkConfigured(true, false));
        assertTrue(ProtectionSettingsSaveGate.shouldMarkConfigured(true, true));
    }

    @Test public void neverReloadWhenOemUnsupportedOrStabilityUnsatisfied() {
        assertFalse(ProtectionSettingsSaveGate.mayReload(true, false, true));
        assertFalse(ProtectionSettingsSaveGate.mayReload(true, true, false));
        assertFalse(ProtectionSettingsSaveGate.mayReload(false, true, true));
        assertTrue(ProtectionSettingsSaveGate.mayReload(true, true, true));
    }
}
