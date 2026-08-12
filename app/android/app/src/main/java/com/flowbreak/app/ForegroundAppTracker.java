package com.flowbreak.app;

import android.app.usage.UsageEvents;

/**
 * Keeps an in-memory view of the foreground package from UsageEvents.
 *
 * UsageStats#getLastTimeUsed is not a foreground signal: it can keep pointing
 * at an app after the screen is locked or another surface takes focus. This
 * small, deterministic tracker only changes state when UsageEvents says so,
 * which makes it safe to exercise with local unit tests.
 *
 * Activity events are tracked per instance (package + class name): a PAUSED or
 * STOPPED event only clears the foreground when it belongs to the activity
 * instance that is currently considered foreground. Same-package handovers
 * (for example a splash activity handing over to the main activity) therefore
 * cannot clear a package that is still on screen, even when the old instance's
 * STOPPED event is reported late.
 */
public final class ForegroundAppTracker {
    private String foregroundPackage = "";
    private String foregroundInstance = "";
    private long lastEventAt;

    /** Package-level convenience overload used by legacy callers. */
    public void accept(String packageName, int eventType, long timestamp) {
        accept(packageName, null, eventType, timestamp);
    }

    public void accept(String packageName, String className, int eventType, long timestamp) {
        if (packageName == null || packageName.isEmpty()) return;
        if (timestamp < lastEventAt) return;

        if (isForegroundEvent(eventType)) {
            foregroundPackage = packageName;
            foregroundInstance = instanceKey(packageName, className);
            lastEventAt = timestamp;
            return;
        }

        if (!isBackgroundEvent(eventType) || !packageName.equals(foregroundPackage)) return;

        // ACTIVITY_PAUSED and MOVE_TO_BACKGROUND share the same event type
        // value (2), and ACTIVITY_RESUMED shares value 1 with
        // MOVE_TO_FOREGROUND. The class name is the only reliable signal:
        // activity events always carry it, package-level events never do.
        if (className == null) {
            // Package-level MOVE_TO_BACKGROUND: the whole package left.
            foregroundPackage = "";
            foregroundInstance = "";
        } else if (!instanceKey(packageName, className).equals(foregroundInstance)) {
            // Stale background event for an activity that is not the current
            // foreground instance: the package may still be on screen.
            return;
        } else {
            foregroundPackage = "";
            foregroundInstance = "";
        }
        lastEventAt = timestamp;
    }

    public void clear(long timestamp) {
        foregroundPackage = "";
        foregroundInstance = "";
        lastEventAt = Math.max(lastEventAt, timestamp);
    }

    public void reset() {
        foregroundPackage = "";
        foregroundInstance = "";
        lastEventAt = 0;
    }

    public String getForegroundPackage() {
        return foregroundPackage;
    }

    public long getLastEventAt() {
        return lastEventAt;
    }

    private static String instanceKey(String packageName, String className) {
        return packageName + "/" + (className == null ? "" : className);
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
