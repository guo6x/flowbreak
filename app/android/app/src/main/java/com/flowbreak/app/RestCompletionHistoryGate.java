package com.flowbreak.app;

/** Pure authorization gate for committing a user-requested REST completion. */
public final class RestCompletionHistoryGate {
    public enum Decision {
        ALLOW,
        DEFER,
        CHEATED,
        SESSION_MISMATCH,
        NOT_READY
    }

    public static Decision evaluate(
            String persistedState,
            long expectedSessionId,
            long activeSessionId,
            long completedSessionId,
            long restStartedAt,
            long restRequiredMs,
            long nowWallMs,
            boolean historyComplete,
            long verifiedTargetMs
    ) {
        return evaluate(
                persistedState,
                expectedSessionId,
                activeSessionId,
                completedSessionId,
                restStartedAt,
                restRequiredMs,
                nowWallMs,
                historyComplete,
                verifiedTargetMs,
                0L
        );
    }

    public static Decision evaluate(
            String persistedState,
            long expectedSessionId,
            long activeSessionId,
            long completedSessionId,
            long restStartedAt,
            long restRequiredMs,
            long nowWallMs,
            boolean historyComplete,
            long verifiedTargetMs,
            long unverifiedTailMs
    ) {
        if (expectedSessionId <= 0L) return Decision.SESSION_MISMATCH;
        if (BlockStateMachine.State.GRACE.name().equals(persistedState)
                && expectedSessionId == completedSessionId) {
            return Decision.ALLOW;
        }
        if (!BlockStateMachine.State.RESTING.name().equals(persistedState)) {
            return Decision.NOT_READY;
        }
        if (activeSessionId <= 0L || expectedSessionId != activeSessionId) {
            return Decision.SESSION_MISMATCH;
        }
        if (!RestSessionValidator.isComplete(restStartedAt, restRequiredMs, nowWallMs)) {
            return Decision.NOT_READY;
        }
        if (!historyComplete || verifiedTargetMs < 0L) return Decision.DEFER;
        if (verifiedTargetMs >= RestCheatTracker.CHEAT_THRESHOLD_MS) {
            return Decision.CHEATED;
        }
        if (unverifiedTailMs < 0L) return Decision.DEFER;
        long possibleTargetMs = Long.MAX_VALUE - verifiedTargetMs < unverifiedTailMs
                ? Long.MAX_VALUE
                : verifiedTargetMs + unverifiedTailMs;
        if (possibleTargetMs >= RestCheatTracker.CHEAT_THRESHOLD_MS) {
            // The gap after the history query is not evidence of cheating, but
            // it is too large to authorize completion safely. Ask for a fresh
            // query rather than rewarding against an unverified threshold.
            return Decision.DEFER;
        }
        return Decision.ALLOW;
    }

    private RestCompletionHistoryGate() { }
}
