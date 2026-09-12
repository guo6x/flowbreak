package com.flowbreak.app;

/**
 * Pure gate for the transition from a persisted BLOCKED state into a rest
 * session.
 *
 * <p>Manual rest and an already RESTING session are intentionally not
 * constrained by this gate. The gate exists to prevent a stale BLOCKED state
 * from being converted into RESTING when the current protection runtime can
 * no longer be confirmed alive.</p>
 */
public final class BlockedRestEntryGate {
    private BlockedRestEntryGate() { }

    public static boolean isAllowed(
            String persistedState,
            boolean currentCoreRuntimeHealthy
    ) {
        return !BlockStateMachine.State.BLOCKED.name().equals(persistedState)
                || currentCoreRuntimeHealthy;
    }
}
