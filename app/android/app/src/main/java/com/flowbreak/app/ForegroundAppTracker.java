package com.flowbreak.app;

import android.app.usage.UsageEvents;

/**
 * Keeps an in-memory view of the foreground package from UsageEvents.
 *
 * UsageStats#getLastTimeUsed is not a foreground signal: it can keep pointing
 * at an app after the screen is locked or another surface takes focus. This
 * small, deterministic tracker only changes state when UsageEvents says so,
 * which makes it safe to exercise with local unit tests.
 */
public final class ForegroundAppTracker {
    private String foregroundPackage = "";
    private long lastEventAt;

    public void accept(String packageName, int eventType, long timestamp) {
        if (packageName == null || packageName.isEmpty()) return;
        if (timestamp < lastEventAt) return;

        if (isForegroundEvent(eventType)) {
            foregroundPackage = packageName;
            lastEventAt = timestamp;
            return;
        }

        if (isBackgroundEvent(eventType) && packageName.equals(foregroundPackage)) {
            foregroundPackage = "";
            lastEventAt = timestamp;
        }
    }

    public void clear(long timestamp) {
        foregroundPackage = "";
        lastEventAt = Math.max(lastEventAt, timestamp);
    }

    public void reset() {
        foregroundPackage = "";
        lastEventAt = 0;
    }

    public String getForegroundPackage() {
        return foregroundPackage;
    }

    public long getLastEventAt() {
        return lastEventAt;
    }

    public static boolean isForegroundEvent(int eventType) {
        return eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                || eventType == UsageEvents.Event.ACTIVITY_RESUMED;
    }

    public static boolean isBackgroundEvent(int eventType) {
        return eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
                || eventType == UsageEvents.Event.ACTIVITY_PAUSED
                || eventType == UsageEvents.Event.ACTIVITY_STOPPED;
    }
}
