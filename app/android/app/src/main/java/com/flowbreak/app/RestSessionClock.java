package com.flowbreak.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/** Validates the clocks that authorize a native REST session. */
public final class RestSessionClock {
    /** Small tolerance for ordinary scheduling and wall-clock sampling skew. */
    public static final long MAX_WALL_ELAPSED_SKEW_MS = 5_000L;
    static final Object LOCK = new Object();

    private RestSessionClock() { }

    public static int currentBootCount(Context context) {
        if (context == null) return -1;
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(), Settings.Global.BOOT_COUNT
            );
        } catch (Exception unavailable) {
            return -1;
        }
    }

    public static boolean isValid(
            long sessionId,
            long invalidatedSessionId,
            long startedAtWallMs,
            long startedAtElapsedMs,
            int startedBootCount,
            long nowWallMs,
            long nowElapsedMs,
            int currentBootCount
    ) {
        if (sessionId <= 0L
                || invalidatedSessionId == sessionId
                || startedAtWallMs <= 0L
                || startedAtElapsedMs <= 0L
                || startedBootCount < 0
                || currentBootCount < 0
                || startedBootCount != currentBootCount
                || nowWallMs < startedAtWallMs
                || nowElapsedMs < startedAtElapsedMs) {
            return false;
        }

        long wallDelta = nowWallMs - startedAtWallMs;
        long elapsedDelta = nowElapsedMs - startedAtElapsedMs;
        if (wallDelta < 0L || elapsedDelta < 0L) return false;
        long skew = wallDelta - elapsedDelta;
        return skew >= -MAX_WALL_ELAPSED_SKEW_MS
                && skew <= MAX_WALL_ELAPSED_SKEW_MS;
    }

    /** Maps elapsed realtime to UsageEvents' wall-clock domain for one REST window. */
    public static long wallTimeAtElapsed(
            long startedAtWallMs,
            long startedAtElapsedMs,
            long atElapsedMs
    ) {
        if (startedAtWallMs <= 0L || startedAtElapsedMs <= 0L
                || atElapsedMs < startedAtElapsedMs) {
            return 0L;
        }
        long elapsedDelta = atElapsedMs - startedAtElapsedMs;
        return Long.MAX_VALUE - startedAtWallMs < elapsedDelta
                ? Long.MAX_VALUE
                : startedAtWallMs + elapsedDelta;
    }

    public static long remainingMs(long startedAtElapsedMs, long requiredMs, long nowElapsedMs) {
        if (startedAtElapsedMs <= 0L || requiredMs <= 0L || nowElapsedMs < startedAtElapsedMs) {
            return Math.max(0L, requiredMs);
        }
        long elapsed = nowElapsedMs - startedAtElapsedMs;
        return elapsed >= requiredMs ? 0L : requiredMs - elapsed;
    }

    /** Persist a session-scoped invalidation so a quick forward-then-back change cannot revive it. */
    public static void invalidateActiveSession(SharedPreferences preferences) {
        if (preferences == null) return;
        synchronized (LOCK) {
            if (!BlockStateMachine.State.RESTING.name().equals(
                    preferences.getString("blockState", BlockStateMachine.State.IDLE.name()))) {
                return;
            }
            long sessionId = preferences.getLong(
                    FlowForegroundService.PREF_REST_SESSION_ID, 0L
            );
            if (sessionId <= 0L) return;
            preferences.edit()
                    .putLong(FlowForegroundService.PREF_REST_CLOCK_INVALIDATED_SESSION_ID, sessionId)
                    .commit();
        }
    }
}
