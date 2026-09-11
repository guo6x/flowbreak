package com.flowbreak.app;

/**
 * Pure pre-commit gate for rest completion.
 *
 * <p>When the foreground service is alive, a RESTING completion is allowed to
 * commit only after a recent monitor tick. This prevents a stale monitor
 * callback from persisting its old RESTING/BLOCKED snapshot over a completion
 * that was committed while the monitor worker was stalled. Service-loss
 * recovery remains permissive because there is no live worker that can race
 * the completion.</p>
 */
public final class RestCompletionIntegrityGate {
    public static final String RETRYABLE_MESSAGE = "保护状态正在同步，请稍后重试。";

    public enum Decision {
        ALLOW,
        DEFER
    }

    @FunctionalInterface
    public interface CompletionOperation<T> {
        T run() throws Exception;
    }

    /** Result of the gate plus the side-effecting operation when it was allowed. */
    public static final class Attempt<T> {
        public final Decision decision;
        public final T value;

        private Attempt(Decision decision, T value) {
            this.decision = decision;
            this.value = value;
        }

        public boolean deferred() {
            return decision == Decision.DEFER;
        }
    }

    public static Decision evaluate(
            String persistedState,
            boolean serviceRuntimeActive,
            long lastCompletedMonitorTickElapsedMs,
            long nowElapsedMs
    ) {
        if (!BlockStateMachine.State.RESTING.name().equals(persistedState)) {
            return Decision.ALLOW;
        }
        if (!serviceRuntimeActive) {
            return Decision.ALLOW;
        }
        if (lastCompletedMonitorTickElapsedMs <= 0L
                || nowElapsedMs - lastCompletedMonitorTickElapsedMs
                        > RestCheatTracker.CHEAT_THRESHOLD_MS) {
            return Decision.DEFER;
        }
        return Decision.ALLOW;
    }

    /**
     * Executes the completion operation only when the integrity gate allows it.
     * The operation represents the coordinator's complete DB/points/GRACE
     * commit, which gives JVM tests a counter seam for proving DEFER is side
     * effect free.
     */
    public static <T> Attempt<T> execute(
            String persistedState,
            boolean serviceRuntimeActive,
            long lastCompletedMonitorTickElapsedMs,
            long nowElapsedMs,
            CompletionOperation<T> operation
    ) throws Exception {
        Decision decision = evaluate(
                persistedState,
                serviceRuntimeActive,
                lastCompletedMonitorTickElapsedMs,
                nowElapsedMs
        );
        if (decision == Decision.DEFER) {
            return new Attempt<>(decision, null);
        }
        return new Attempt<>(decision, operation.run());
    }

    private RestCompletionIntegrityGate() { }
}
