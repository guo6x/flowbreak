package com.flowbreak.app;

/**
 * Package-independent foreground event state used by the live detector and
 * historical replay.  Keeping the activity-instance handover rules here
 * prevents a replay path from inventing different foreground semantics.
 */
final class ForegroundEventState {
    private String foregroundPackage = "";
    private String foregroundInstance = "";
    private boolean foregroundInstanceKnown;
    private boolean seededPackageOnly;
    private long lastEventAt;

    void seed(String packageName, long timestamp) {
        foregroundPackage = packageName == null ? "" : packageName;
        foregroundInstance = foregroundPackage.isEmpty()
                ? ""
                : instanceKey(foregroundPackage, null);
        // The persisted checkpoint deliberately stores only the current
        // package. Until a new activity-resume event supplies its class, a
        // class-qualified background event cannot be proven stale.
        foregroundInstanceKnown = false;
        seededPackageOnly = !foregroundPackage.isEmpty();
        lastEventAt = Math.max(0L, timestamp);
    }

    void accept(
            String packageName,
            String className,
            boolean foregroundEvent,
            boolean backgroundEvent,
            long timestamp
    ) {
        if (packageName == null || packageName.isEmpty()) return;
        if (timestamp < lastEventAt) return;

        if (foregroundEvent) {
            foregroundPackage = packageName;
            foregroundInstance = instanceKey(packageName, className);
            foregroundInstanceKnown = className != null;
            seededPackageOnly = false;
            lastEventAt = timestamp;
            return;
        }

        if (!backgroundEvent || !packageName.equals(foregroundPackage)) return;

        // Package-level events have no class name.  Activity events are
        // matched by instance so a stale PAUSED/STOPPED event from an old
        // activity cannot clear a newer activity in the same package.
        if (className == null) {
            foregroundPackage = "";
            foregroundInstance = "";
            foregroundInstanceKnown = false;
        } else if (!foregroundInstanceKnown && seededPackageOnly) {
            // A checkpoint has no class identity. Treat the first
            // class-qualified background event as leaving the package rather
            // than crediting an interval that cannot be proven foreground.
            foregroundPackage = "";
            foregroundInstance = "";
            foregroundInstanceKnown = false;
            seededPackageOnly = false;
        } else if (!instanceKey(packageName, className).equals(foregroundInstance)) {
            return;
        } else {
            foregroundPackage = "";
            foregroundInstance = "";
            foregroundInstanceKnown = false;
        }
        lastEventAt = timestamp;
    }

    void reset() {
        foregroundPackage = "";
        foregroundInstance = "";
        foregroundInstanceKnown = false;
        seededPackageOnly = false;
        lastEventAt = 0L;
    }

    void clear(long timestamp) {
        foregroundPackage = "";
        foregroundInstance = "";
        foregroundInstanceKnown = false;
        seededPackageOnly = false;
        lastEventAt = Math.max(lastEventAt, timestamp);
    }

    String getForegroundPackage() {
        return foregroundPackage;
    }

    long getLastEventAt() {
        return lastEventAt;
    }

    private static String instanceKey(String packageName, String className) {
        return packageName + "/" + (className == null ? "" : className);
    }
}
