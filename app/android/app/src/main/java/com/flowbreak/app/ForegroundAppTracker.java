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
    private final ForegroundEventState state = new ForegroundEventState();

    /** Package-level convenience overload used by legacy callers. */
    public void accept(String packageName, int eventType, long timestamp) {
        accept(packageName, null, eventType, timestamp);
    }

    public void accept(String packageName, String className, int eventType, long timestamp) {
        state.accept(
                packageName,
                className,
                isForegroundEvent(eventType),
                isBackgroundEvent(eventType),
                timestamp
        );
    }

    public void clear(long timestamp) {
        state.clear(timestamp);
    }

    public void reset() {
        state.reset();
    }

    public String getForegroundPackage() {
        return state.getForegroundPackage();
    }

    public long getLastEventAt() {
        return state.getLastEventAt();
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
