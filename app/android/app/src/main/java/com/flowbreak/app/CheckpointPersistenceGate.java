package com.flowbreak.app;

/**
 * Pure cadence policy for durable monitor checkpoint writes.
 *
 * <p>The service records a checkpoint on every monitor tick in memory, but
 * only asks SharedPreferences to persist it when this gate opens or a force
 * boundary is reached.  Elapsed realtime is the intended clock; a backwards
 * clock movement is treated conservatively and permits an immediate write.</p>
 */
public final class CheckpointPersistenceGate {
    public static final long PERSIST_INTERVAL_MS = 10_000L;

    public static boolean shouldPersist(
            long lastPersistElapsedMs,
            long nowElapsedMs,
            boolean force
    ) {
        if (force || lastPersistElapsedMs <= 0L) return true;
        if (nowElapsedMs < lastPersistElapsedMs) return true;
        return nowElapsedMs - lastPersistElapsedMs >= PERSIST_INTERVAL_MS;
    }

    private CheckpointPersistenceGate() { }
}
