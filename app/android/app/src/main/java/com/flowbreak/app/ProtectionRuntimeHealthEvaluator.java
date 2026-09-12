package com.flowbreak.app;

/**
 * Pure evaluator for the current-process core protection runtime.
 *
 * <p>A persisted heartbeat is useful history, but it cannot prove that the
 * current service instance and monitor loop are alive. The authoritative
 * freshness clock for this decision is elapsed realtime, which does not move
 * when the user changes the wall clock or the device time zone.</p>
 */
public final class ProtectionRuntimeHealthEvaluator {
    public static final long SERVICE_HEARTBEAT_STALE_MS = 45_000L;

    public static final class Result {
        public final boolean healthy;
        public final String reason;
        public final boolean serviceRuntimeActive;
        public final boolean monitorThreadAlive;
        public final long lastCompletedMonitorTickElapsedMs;
        public final long nowElapsedMs;
        public final boolean heartbeatFresh;
        public final String integrityFailureReason;

        private Result(
                boolean healthy,
                String reason,
                boolean serviceRuntimeActive,
                boolean monitorThreadAlive,
                long lastCompletedMonitorTickElapsedMs,
                long nowElapsedMs,
                boolean heartbeatFresh,
                String integrityFailureReason
        ) {
            this.healthy = healthy;
            this.reason = reason;
            this.serviceRuntimeActive = serviceRuntimeActive;
            this.monitorThreadAlive = monitorThreadAlive;
            this.lastCompletedMonitorTickElapsedMs = lastCompletedMonitorTickElapsedMs;
            this.nowElapsedMs = nowElapsedMs;
            this.heartbeatFresh = heartbeatFresh;
            this.integrityFailureReason = integrityFailureReason;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public boolean hasIntegrityFailure() {
            return !integrityFailureReason.isEmpty();
        }
    }

    private ProtectionRuntimeHealthEvaluator() { }

    public static Result evaluate(
            boolean serviceRuntimeActive,
            boolean monitorThreadAlive,
            long lastCompletedMonitorTickElapsedMs,
            long nowElapsedMs,
            String integrityFailureReason
    ) {
        String normalizedIntegrityFailure = integrityFailureReason == null
                ? ""
                : integrityFailureReason.trim();
        boolean heartbeatFresh = lastCompletedMonitorTickElapsedMs > 0L
                && nowElapsedMs >= lastCompletedMonitorTickElapsedMs
                && nowElapsedMs - lastCompletedMonitorTickElapsedMs
                < SERVICE_HEARTBEAT_STALE_MS;

        String reason;
        if (!serviceRuntimeActive) {
            reason = "SERVICE_NOT_RUNNING";
        } else if (!monitorThreadAlive) {
            reason = "MONITOR_THREAD_NOT_ALIVE";
        } else if (lastCompletedMonitorTickElapsedMs <= 0L) {
            reason = "HEARTBEAT_MISSING";
        } else if (nowElapsedMs < lastCompletedMonitorTickElapsedMs) {
            reason = "HEARTBEAT_FROM_FUTURE";
        } else if (nowElapsedMs - lastCompletedMonitorTickElapsedMs
                >= SERVICE_HEARTBEAT_STALE_MS) {
            reason = "HEARTBEAT_STALE";
        } else if (!normalizedIntegrityFailure.isEmpty()) {
            reason = normalizedIntegrityFailure;
        } else {
            reason = "";
        }

        boolean healthy = serviceRuntimeActive
                && monitorThreadAlive
                && lastCompletedMonitorTickElapsedMs > 0L
                && nowElapsedMs >= lastCompletedMonitorTickElapsedMs
                && nowElapsedMs - lastCompletedMonitorTickElapsedMs
                < SERVICE_HEARTBEAT_STALE_MS
                && normalizedIntegrityFailure.isEmpty();

        return new Result(
                healthy,
                reason,
                serviceRuntimeActive,
                monitorThreadAlive,
                lastCompletedMonitorTickElapsedMs,
                nowElapsedMs,
                heartbeatFresh,
                normalizedIntegrityFailure
        );
    }
}
