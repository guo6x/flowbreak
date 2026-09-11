package com.flowbreak.app;

/**
 * Classifies the interval after a monitor callback gap without mutating any
 * live or persisted state.
 *
 * <p>A gap above the historical-replay threshold may still be shorter than
 * the protected live tail. In that case the ordinary live path must retain
 * the original checkpoint so its ten-second clamp can account for the whole
 * observation. Only a closed interval may enter historical replay.</p>
 */
public final class GapReconciliationWindow {
    public static final long RECONCILIATION_THRESHOLD_MS = 5_000L;

    public enum Kind {
        LIVE_CLAMP,
        SKIPPED_LIVE_WINDOW,
        CLOSED_HISTORICAL_WINDOW
    }

    public static final class Decision {
        public final Kind kind;
        public final long safeEndWallMs;

        private Decision(Kind kind, long safeEndWallMs) {
            this.kind = kind;
            this.safeEndWallMs = safeEndWallMs;
        }

        public boolean usesHistoricalReplay() {
            return kind == Kind.CLOSED_HISTORICAL_WINDOW;
        }
    }

    public static Decision classify(long gapMs, long checkpointWallMs, long nowWallMs) {
        if (gapMs <= RECONCILIATION_THRESHOLD_MS) {
            return new Decision(Kind.LIVE_CLAMP, 0L);
        }

        long safeEndWallMs = nowWallMs - TargetSessionGapReconciler.LIVE_TAIL_MS;
        if (safeEndWallMs <= checkpointWallMs) {
            return new Decision(Kind.SKIPPED_LIVE_WINDOW, safeEndWallMs);
        }
        return new Decision(Kind.CLOSED_HISTORICAL_WINDOW, safeEndWallMs);
    }

    private GapReconciliationWindow() { }
}
