package com.flowbreak.app;

/**
 * Package-name-free, in-memory evidence for distinguishing native REST
 * cancellation from a stale WebView countdown on a physical Android device.
 *
 * This is passive telemetry: it never changes a rest decision, persisted state,
 * target set, permissions, or the RestCheatTracker's five-second threshold.
 */
final class RestCheatEvidence {
    private long sessionObservations;
    private long sessionTargetObservations;
    private long sessionPausedObservations;
    private long sessionHistoricalReplayMs;
    private long lastAccumulatedMs;
    private long maxAccumulatedMs;
    private long lastObservationAt;
    private long cancellationCount;
    private long lastCancellationAt;
    private long lastCancelledAccumulatedMs;
    private boolean lastForegroundPresent;
    private boolean lastForegroundInTargets;
    private boolean lastIsTarget;
    private String lastSource = "NONE";

    synchronized void onRestStarted() {
        sessionObservations = 0L;
        sessionTargetObservations = 0L;
        sessionPausedObservations = 0L;
        sessionHistoricalReplayMs = 0L;
        lastAccumulatedMs = 0L;
        maxAccumulatedMs = 0L;
        lastObservationAt = 0L;
        lastForegroundPresent = false;
        lastForegroundInTargets = false;
        lastIsTarget = false;
        lastSource = "AWAITING_NATIVE_OBSERVATION";
        // Lifetime cancellation evidence intentionally survives a new REST.
    }

    synchronized void recordLive(
            boolean paused,
            boolean foregroundPresent,
            boolean foregroundInTargets,
            boolean isTarget,
            long accumulatedMs,
            long observedAt
    ) {
        sessionObservations++;
        if (paused) sessionPausedObservations++;
        if (isTarget) sessionTargetObservations++;
        lastSource = paused ? "PAUSED_REST" : "ACTIVE_REST";
        lastForegroundPresent = foregroundPresent;
        lastForegroundInTargets = foregroundInTargets;
        lastIsTarget = isTarget;
        lastAccumulatedMs = Math.max(0L, accumulatedMs);
        maxAccumulatedMs = Math.max(maxAccumulatedMs, lastAccumulatedMs);
        lastObservationAt = Math.max(0L, observedAt);
    }

    /** Replay data is REST anti-cheat evidence only when REST was active. */
    static boolean shouldRecordHistoricalReplay(boolean wasResting, long verifiedTargetMs) {
        return wasResting && verifiedTargetMs > 0L;
    }

    synchronized void recordHistoricalReplay(
            long verifiedTargetMs,
            long accumulatedMs,
            long observedAt
    ) {
        sessionHistoricalReplayMs += Math.max(0L, verifiedTargetMs);
        lastSource = "HISTORICAL_REPLAY";
        lastAccumulatedMs = Math.max(0L, accumulatedMs);
        maxAccumulatedMs = Math.max(maxAccumulatedMs, lastAccumulatedMs);
        lastObservationAt = Math.max(0L, observedAt);
    }

    synchronized void recordCancellation(long accumulatedMs, long cancelledAt) {
        cancellationCount++;
        lastCancelledAccumulatedMs = Math.max(0L, accumulatedMs);
        lastCancellationAt = Math.max(0L, cancelledAt);
        maxAccumulatedMs = Math.max(maxAccumulatedMs, lastCancelledAccumulatedMs);
        // Preserve evidence even though the authoritative tracker resets.
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(
                sessionObservations,
                sessionTargetObservations,
                sessionPausedObservations,
                sessionHistoricalReplayMs,
                lastAccumulatedMs,
                maxAccumulatedMs,
                lastObservationAt,
                cancellationCount,
                lastCancellationAt,
                lastCancelledAccumulatedMs,
                lastForegroundPresent,
                lastForegroundInTargets,
                lastIsTarget,
                lastSource
        );
    }

    static final class Snapshot {
        final long sessionObservations;
        final long sessionTargetObservations;
        final long sessionPausedObservations;
        final long sessionHistoricalReplayMs;
        final long lastAccumulatedMs;
        final long maxAccumulatedMs;
        final long lastObservationAt;
        final long cancellationCount;
        final long lastCancellationAt;
        final long lastCancelledAccumulatedMs;
        final boolean lastForegroundPresent;
        final boolean lastForegroundInTargets;
        final boolean lastIsTarget;
        final String lastSource;

        Snapshot(
                long sessionObservations,
                long sessionTargetObservations,
                long sessionPausedObservations,
                long sessionHistoricalReplayMs,
                long lastAccumulatedMs,
                long maxAccumulatedMs,
                long lastObservationAt,
                long cancellationCount,
                long lastCancellationAt,
                long lastCancelledAccumulatedMs,
                boolean lastForegroundPresent,
                boolean lastForegroundInTargets,
                boolean lastIsTarget,
                String lastSource
        ) {
            this.sessionObservations = sessionObservations;
            this.sessionTargetObservations = sessionTargetObservations;
            this.sessionPausedObservations = sessionPausedObservations;
            this.sessionHistoricalReplayMs = sessionHistoricalReplayMs;
            this.lastAccumulatedMs = lastAccumulatedMs;
            this.maxAccumulatedMs = maxAccumulatedMs;
            this.lastObservationAt = lastObservationAt;
            this.cancellationCount = cancellationCount;
            this.lastCancellationAt = lastCancellationAt;
            this.lastCancelledAccumulatedMs = lastCancelledAccumulatedMs;
            this.lastForegroundPresent = lastForegroundPresent;
            this.lastForegroundInTargets = lastForegroundInTargets;
            this.lastIsTarget = lastIsTarget;
            this.lastSource = lastSource;
        }
    }
}
