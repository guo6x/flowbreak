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

    /**
     * A settings-only update must never turn an unconfigured installation
     * into one eligible for future boot recovery. Only an explicit enabling
     * action establishes that state. Existing configured state is retained.
     */
    static boolean shouldMarkConfigured(
            boolean wasConfigured,
            boolean explicitMonitoringKey,
            boolean requestedEnabled
    ) {
        return wasConfigured || (explicitMonitoringKey && requestedEnabled);
    }

    static boolean mayReload(
            boolean wasConfigured,
            boolean protectionRuntimeAvailable,
            boolean backgroundStabilitySatisfied
    ) {
        return wasConfigured && protectionRuntimeAvailable && backgroundStabilitySatisfied;
    }
}
