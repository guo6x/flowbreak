package com.flowbreak.app;

/**
 * Separates editing persisted settings from activating a protection runtime.
 * This policy cannot grant permissions or start the native service.
 */
final class ProtectionSettingsSaveGate {
    private ProtectionSettingsSaveGate() { }

    static boolean requiresActivationCheck(boolean explicitMonitoringKey, boolean requestedEnabled) {
        return explicitMonitoringKey && requestedEnabled;
    }

    /** A passive settings edit cannot create an active-monitoring boot flag. */
    static boolean shouldMarkConfigured(boolean explicitMonitoringKey, boolean requestedEnabled) {
        return explicitMonitoringKey && requestedEnabled;
    }

    static boolean mayReload(
            boolean wasConfigured,
            boolean protectionRuntimeAvailable,
            boolean backgroundStabilitySatisfied
    ) {
        return wasConfigured && protectionRuntimeAvailable && backgroundStabilitySatisfied;
    }
}
