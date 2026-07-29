package com.flowbreak.app;

/**
 * Pure evaluator for the ten-minute window after a completed rest.
 *
 * A pullback is successful when the user never returns to a target app during
 * the window, or returns and then voluntarily stays away from every target app
 * for thirty continuous seconds before the window ends. Returning is recorded
 * independently, so a session can contain both a return and a later pullback.
 */
public final class PullbackOutcomeTracker {
    public static final long AWAY_SUCCESS_MS = 30_000L;

    public static final class Update {
        public final boolean returnObservedNow;
        public final boolean resolvedNow;
        public final boolean success;
        public final long targetSeconds;

        private Update(
                boolean returnObservedNow,
                boolean resolvedNow,
                boolean success,
                long targetSeconds
        ) {
            this.returnObservedNow = returnObservedNow;
            this.resolvedNow = resolvedNow;
            this.success = success;
            this.targetSeconds = Math.max(0L, targetSeconds);
        }

        private static Update unchanged(PullbackOutcomeTracker tracker) {
            return new Update(false, false, tracker.success, tracker.targetMs / 1000L);
        }
    }

    private final long sessionId;
    private final long startedAt;
    private long targetMs;
    private long leftTargetsAt;
    private boolean sawTarget;
    private boolean returnReported;
    private boolean resolved;
    private boolean success;

    public PullbackOutcomeTracker(
            long sessionId,
            long startedAt,
            long targetMs,
            long leftTargetsAt,
            boolean sawTarget,
            boolean returnReported,
            boolean resolved,
            boolean success
    ) {
        this.sessionId = Math.max(0L, sessionId);
        this.startedAt = Math.max(0L, startedAt);
        this.targetMs = Math.max(0L, targetMs);
        this.leftTargetsAt = Math.max(0L, leftTargetsAt);
        this.sawTarget = sawTarget;
        this.returnReported = returnReported;
        this.resolved = resolved;
        this.success = success;
    }

    public Update update(boolean targetInForeground, long targetDeltaMs, long now, long graceUntil) {
        if (resolved || sessionId <= 0L || startedAt <= 0L || graceUntil <= startedAt) {
            return Update.unchanged(this);
        }

        boolean returnObservedNow = false;
        if (targetInForeground) {
            sawTarget = true;
            targetMs += Math.max(0L, targetDeltaMs);
            leftTargetsAt = 0L;
            if (!returnReported) {
                returnReported = true;
                returnObservedNow = true;
            }
        } else if (sawTarget && leftTargetsAt == 0L) {
            leftTargetsAt = now;
        }

        boolean stayedAway = sawTarget
                && leftTargetsAt > 0L
                && now - leftTargetsAt >= AWAY_SUCCESS_MS;
        boolean expired = now >= graceUntil;
        if (!stayedAway && !expired) {
            return new Update(returnObservedNow, false, false, targetMs / 1000L);
        }

        resolved = true;
        success = stayedAway || !sawTarget;
        return new Update(returnObservedNow, true, success, targetMs / 1000L);
    }

    public long getSessionId() { return sessionId; }
    public long getStartedAt() { return startedAt; }
    public long getTargetMs() { return targetMs; }
    public long getLeftTargetsAt() { return leftTargetsAt; }
    public boolean hasSeenTarget() { return sawTarget; }
    public boolean isReturnReported() { return returnReported; }
    public boolean isResolved() { return resolved; }
    public boolean isSuccess() { return success; }
}
