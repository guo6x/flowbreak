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

    /** Legacy synthetic-clock overload retained for focused pure tests. */
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
                persistedState, expectedSessionId, activeSessionId, completedSessionId,
                restStartedAt, restRequiredMs, nowWallMs, historyComplete, verifiedTargetMs, 0L
        );
    }

    /** Legacy synthetic-clock overload retained for focused pure tests. */
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
        return evaluate(
                persistedState,
                expectedSessionId,
                activeSessionId,
                completedSessionId,
                restStartedAt,
                restStartedAt,
                0,
                restRequiredMs,
                nowWallMs,
                nowWallMs,
                0,
                0L,
                historyComplete,
                verifiedTargetMs,
                unverifiedTailMs
        );
    }

    /** Clock- and session-bound gate used by every production completion path. */
    public static Decision evaluate(
            String persistedState,
            long expectedSessionId,
            long activeSessionId,
            long completedSessionId,
            long restStartedAtWallMs,
            long restStartedAtElapsedMs,
            int restStartedBootCount,
            long restRequiredMs,
            long nowWallMs,
            long nowElapsedMs,
            int currentBootCount,
            long invalidatedSessionId,
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
        if (!RestSessionClock.isValid(
                activeSessionId,
                invalidatedSessionId,
                restStartedAtWallMs,
                restStartedAtElapsedMs,
                restStartedBootCount,
                nowWallMs,
                nowElapsedMs,
                currentBootCount
        ) || !RestSessionValidator.isComplete(
                restStartedAtElapsedMs, restRequiredMs, nowElapsedMs
        )) {
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
        if (possibleTargetMs >= RestCheatTracker.CHEAT_THRESHOLD_MS) return Decision.DEFER;
        return Decision.ALLOW;
    }

    private RestCompletionHistoryGate() { }
}
