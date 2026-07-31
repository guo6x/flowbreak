package com.flowbreak.app;

/**
 * Heartbeat gate - pure logic for 30-second throttle.
 *
 * Extracted from FlowForegroundService.writeHeartbeat() for JVM unit testing.
 *
 * Rules:
 * - now < 0: reject
 * - first time (lastWrittenAt <= 0): allow
 * - time going backwards (now < lastWrittenAt): reject
 * - elapsed >= 30s since last write: allow
 * - otherwise: reject
 *
 * Holds no Android types, allowing pure JVM testing.
 */
public final class HeartbeatGate {
    public static final long HEARTBEAT_INTERVAL_MS = 30_000L;

    /**
     * Decide whether the current tick should write a heartbeat.
     *
     * @param lastWrittenAt timestamp of the last heartbeat write, <=0 means never
     * @param now current timestamp
     * @return true if a write should occur
     */
    public static boolean shouldWrite(long lastWrittenAt, long now) {
        if (now < 0L) return false;
        if (lastWrittenAt <= 0L) return true;
        if (now < lastWrittenAt) return false;
        return now - lastWrittenAt >= HEARTBEAT_INTERVAL_MS;
    }

    private HeartbeatGate() { }
}
