package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RestSessionClockTest {
    private static final long SESSION_ID = 88L;
    private static final long START_WALL_MS = 1_700_000_000_000L;
    private static final long START_ELAPSED_MS = 900_000L;
    private static final int BOOT_COUNT = 12;

    @Test public void sameBootAndConsistentClocksRemainValidAfterProcessRecreation() {
        assertTrue(RestSessionClock.isValid(
                SESSION_ID,
                0L,
                START_WALL_MS,
                START_ELAPSED_MS,
                BOOT_COUNT,
                START_WALL_MS + 75_000L,
                START_ELAPSED_MS + 75_000L,
                BOOT_COUNT
        ));
    }

    @Test public void wallClockFastForwardCannotCompleteAnUnelapsedRest() {
        assertFalse(RestSessionClock.isValid(
                SESSION_ID,
                0L,
                START_WALL_MS,
                START_ELAPSED_MS,
                BOOT_COUNT,
                START_WALL_MS + 24 * 60 * 60_000L,
                START_ELAPSED_MS + 10_000L,
                BOOT_COUNT
        ));
    }

    @Test public void bootIdentityChangeRejectsEvenWhenNewElapsedExceedsOldAnchor() {
        assertFalse(RestSessionClock.isValid(
                SESSION_ID,
                0L,
                START_WALL_MS,
                START_ELAPSED_MS,
                BOOT_COUNT,
                START_WALL_MS + 60_000L,
                START_ELAPSED_MS + 60_000L,
                BOOT_COUNT + 1
        ));
    }

    @Test public void missingElapsedOrBootAnchorFailsClosed() {
        assertFalse(RestSessionClock.isValid(
                SESSION_ID, 0L, START_WALL_MS, 0L, BOOT_COUNT,
                START_WALL_MS + 180_000L, START_ELAPSED_MS + 180_000L, BOOT_COUNT
        ));
        assertFalse(RestSessionClock.isValid(
                SESSION_ID, 0L, START_WALL_MS, START_ELAPSED_MS, -1,
                START_WALL_MS + 180_000L, START_ELAPSED_MS + 180_000L, BOOT_COUNT
        ));
    }

    @Test public void persistedTimeSetMarkerCannotBeClearedByChangingWallClockBack() {
        assertFalse(RestSessionClock.isValid(
                SESSION_ID,
                SESSION_ID,
                START_WALL_MS,
                START_ELAPSED_MS,
                BOOT_COUNT,
                START_WALL_MS + 180_000L,
                START_ELAPSED_MS + 180_000L,
                BOOT_COUNT
        ));
    }

    @Test public void ordinaryClockSamplingSkewIsToleratedButLargeDriftIsRejected() {
        assertTrue(RestSessionClock.isValid(
                SESSION_ID, 0L, START_WALL_MS, START_ELAPSED_MS, BOOT_COUNT,
                START_WALL_MS + 30_000L + 5_000L,
                START_ELAPSED_MS + 30_000L,
                BOOT_COUNT
        ));
        assertFalse(RestSessionClock.isValid(
                SESSION_ID, 0L, START_WALL_MS, START_ELAPSED_MS, BOOT_COUNT,
                START_WALL_MS + 30_000L + 5_001L,
                START_ELAPSED_MS + 30_000L,
                BOOT_COUNT
        ));
    }

    @Test public void elapsedWindowMapsIntoUsageEventsWallClockDomain() {
        assertEquals(
                START_WALL_MS + 45_000L,
                RestSessionClock.wallTimeAtElapsed(
                        START_WALL_MS,
                        START_ELAPSED_MS,
                        START_ELAPSED_MS + 45_000L
                )
        );
        assertEquals(0L, RestSessionClock.wallTimeAtElapsed(
                START_WALL_MS, START_ELAPSED_MS, START_ELAPSED_MS - 1L
        ));
    }

    @Test public void remainingTimeUsesElapsedRealtimeAndExactCompletionBoundary() {
        assertEquals(1L, RestSessionClock.remainingMs(
                START_ELAPSED_MS, 60_000L, START_ELAPSED_MS + 59_999L
        ));
        assertEquals(0L, RestSessionClock.remainingMs(
                START_ELAPSED_MS, 60_000L, START_ELAPSED_MS + 60_000L
        ));
    }
}
