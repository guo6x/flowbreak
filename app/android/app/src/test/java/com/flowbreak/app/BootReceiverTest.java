package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BootReceiverTest {
    @Test public void unsupportedConfiguredDeviceDoesNotRestartMonitoring() {
        assertFalse(BootReceiver.shouldStartMonitoring(true, true, "vivo", true));
        assertFalse(BootReceiver.shouldStartMonitoring(true, true, "iQOO", true));
    }

    @Test public void supportedConfiguredDeviceCanRestartWhenStable() {
        assertTrue(BootReceiver.shouldStartMonitoring(true, true, "xiaomi", true));
    }

    @Test public void disabledOrUnconfiguredDeviceDoesNotRestart() {
        assertFalse(BootReceiver.shouldStartMonitoring(false, true, "xiaomi", true));
        assertFalse(BootReceiver.shouldStartMonitoring(true, false, "xiaomi", true));
        assertFalse(BootReceiver.shouldStartMonitoring(true, true, "xiaomi", false));
    }
}
