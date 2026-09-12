package com.flowbreak.app;

/**
 * Pure evaluator for the single native protection status exposed to the UI.
 * Core capability and optional Domestic Accessibility enhancement are kept
 * separate so a missing enhancement does not disable overlay protection.
 */
public final class ProtectionStatusEvaluator {
    public enum Status {
        PAUSED,
        UNCONFIGURED,
        UNSUPPORTED,
        STARTING_OR_UNCONFIRMED,
        ACTIVE,
        DEGRADED
    }

    public static final class Input {
        public final boolean configured;
        public final boolean monitoringEnabled;
        public final boolean supportedRuntime;
        public final boolean hasTargets;
        public final boolean hasUsageStats;
        public final boolean hasOverlay;
        public final boolean serviceRuntimeActive;
        public final boolean monitorThreadAlive;
        public final long lastCompletedMonitorTickElapsedMs;
        public final long nowElapsedMs;
        public final boolean strongBlockingRequested;
        public final boolean accessibilityEnabledInSettings;
        public final boolean accessibilityRuntimeConnected;
        public final String integrityFailureReason;

        public Input(
                boolean configured,
                boolean monitoringEnabled,
                boolean supportedRuntime,
                boolean hasTargets,
                boolean hasUsageStats,
                boolean hasOverlay,
                boolean serviceRuntimeActive,
                boolean monitorThreadAlive,
                long lastCompletedMonitorTickElapsedMs,
                long nowElapsedMs,
                boolean strongBlockingRequested,
                boolean accessibilityEnabledInSettings,
                boolean accessibilityRuntimeConnected,
                String integrityFailureReason
        ) {
            this.configured = configured;
            this.monitoringEnabled = monitoringEnabled;
            this.supportedRuntime = supportedRuntime;
            this.hasTargets = hasTargets;
            this.hasUsageStats = hasUsageStats;
            this.hasOverlay = hasOverlay;
            this.serviceRuntimeActive = serviceRuntimeActive;
            this.monitorThreadAlive = monitorThreadAlive;
            this.lastCompletedMonitorTickElapsedMs = lastCompletedMonitorTickElapsedMs;
            this.nowElapsedMs = nowElapsedMs;
            this.strongBlockingRequested = strongBlockingRequested;
            this.accessibilityEnabledInSettings = accessibilityEnabledInSettings;
            this.accessibilityRuntimeConnected = accessibilityRuntimeConnected;
            this.integrityFailureReason = integrityFailureReason == null
                    ? ""
                    : integrityFailureReason;
        }
    }

    public static final class Result {
        public final Status status;
        public final String reason;
        public final boolean coreProtectionOperational;
        public final boolean strongBlockingRequested;
        public final boolean strongBlockingOperational;
        public final String strongBlockingDegradedReason;
        public final ProtectionRuntimeHealthEvaluator.Result runtimeHealth;

        private Result(
                Status status,
                String reason,
                boolean coreProtectionOperational,
                boolean strongBlockingRequested,
                boolean strongBlockingOperational,
                String strongBlockingDegradedReason,
                ProtectionRuntimeHealthEvaluator.Result runtimeHealth
        ) {
            this.status = status;
            this.reason = reason;
            this.coreProtectionOperational = coreProtectionOperational;
            this.strongBlockingRequested = strongBlockingRequested;
            this.strongBlockingOperational = strongBlockingOperational;
            this.strongBlockingDegradedReason = strongBlockingDegradedReason;
            this.runtimeHealth = runtimeHealth;
        }
    }

    private ProtectionStatusEvaluator() { }

    public static Result evaluate(Input input) {
        return evaluate(
                input,
                ProtectionRuntimeHealthEvaluator.evaluate(
                        input.serviceRuntimeActive,
                        input.monitorThreadAlive,
                        input.lastCompletedMonitorTickElapsedMs,
                        input.nowElapsedMs,
                        input.integrityFailureReason
                )
        );
    }

    static Result evaluate(
            Input input,
            ProtectionRuntimeHealthEvaluator.Result runtimeHealth
    ) {
        ProtectionPrerequisiteGate.Result prerequisites = ProtectionPrerequisiteGate.evaluate(
                input.supportedRuntime,
                input.monitoringEnabled,
                input.hasTargets,
                input.hasUsageStats,
                input.hasOverlay
        );

        Status status;
        String reason;
        if (!input.supportedRuntime) {
            status = Status.UNSUPPORTED;
            reason = ProtectionPrerequisiteGate.Reason.UNSUPPORTED_DEVICE.name();
        } else if (!input.configured || !input.hasTargets) {
            status = Status.UNCONFIGURED;
            reason = !input.configured
                    ? "NOT_CONFIGURED"
                    : ProtectionPrerequisiteGate.Reason.NO_TARGETS.name();
        } else if (!input.monitoringEnabled) {
            status = Status.PAUSED;
            reason = ProtectionPrerequisiteGate.Reason.MONITORING_DISABLED.name();
        } else if (!prerequisites.isAllowed()) {
            status = Status.DEGRADED;
            reason = prerequisites.reason.name();
        } else if (!runtimeHealth.isHealthy()) {
            status = runtimeHealth.hasIntegrityFailure()
                    ? Status.DEGRADED
                    : Status.STARTING_OR_UNCONFIRMED;
            reason = runtimeHealth.reason;
        } else if (input.strongBlockingRequested
                && !input.accessibilityEnabledInSettings) {
            status = Status.DEGRADED;
            reason = "ACCESSIBILITY_MISSING";
        } else if (input.strongBlockingRequested
                && !input.accessibilityRuntimeConnected) {
            status = Status.DEGRADED;
            reason = "ACCESSIBILITY_SERVICE_NOT_CONNECTED";
        } else {
            status = Status.ACTIVE;
            reason = "";
        }

        boolean coreOperational = prerequisites.isAllowed()
                && runtimeHealth.isHealthy();
        boolean strongOperational = !input.strongBlockingRequested
                || (coreOperational
                && input.accessibilityEnabledInSettings
                && input.accessibilityRuntimeConnected);
        String strongReason = "";
        if (input.strongBlockingRequested && !input.accessibilityEnabledInSettings) {
            strongReason = "ACCESSIBILITY_MISSING";
        } else if (input.strongBlockingRequested
                && !input.accessibilityRuntimeConnected) {
            strongReason = "ACCESSIBILITY_SERVICE_NOT_CONNECTED";
        } else if (input.strongBlockingRequested && !coreOperational) {
            // Keep the enhancement diagnosis separate while still exposing the
            // core failure that prevents any strong-blocking operation.
            strongReason = reason;
        }

        return new Result(
                status,
                reason,
                coreOperational,
                input.strongBlockingRequested,
                strongOperational,
                strongReason,
                runtimeHealth
        );
    }
}
