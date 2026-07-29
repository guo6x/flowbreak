package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PullbackOutcomeTrackerTest {
    private static final long START = 10_000L;
    private static final long END = START + 600_000L;

    @Test public void noReturnDuringWindowCountsAsSuccessfulPullback() {
        PullbackOutcomeTracker tracker = fresh();

        PullbackOutcomeTracker.Update update = tracker.update(false, 0L, END, END);

        assertTrue(update.resolvedNow);
        assertTrue(update.success);
        assertFalse(update.returnObservedNow);
        assertEquals(0L, update.targetSeconds);
    }

    @Test public void returningThenLeavingForThirtySecondsCountsBothSignals() {
        PullbackOutcomeTracker tracker = fresh();

        PullbackOutcomeTracker.Update returned = tracker.update(true, 4_000L, START + 20_000L, END);
        tracker.update(true, 6_000L, START + 26_000L, END);
        tracker.update(false, 0L, START + 30_000L, END);
        PullbackOutcomeTracker.Update pulledBack = tracker.update(
                false, 0L, START + 60_000L, END
        );

        assertTrue(returned.returnObservedNow);
        assertFalse(returned.resolvedNow);
        assertTrue(pulledBack.resolvedNow);
        assertTrue(pulledBack.success);
        assertEquals(10L, pulledBack.targetSeconds);
    }

    @Test public void stayingInTargetUntilWindowExpiresDoesNotCountAsPullback() {
        PullbackOutcomeTracker tracker = fresh();
        tracker.update(true, 120_000L, START + 120_000L, END);

        PullbackOutcomeTracker.Update update = tracker.update(true, 480_000L, END, END);

        assertTrue(update.resolvedNow);
        assertFalse(update.success);
        assertEquals(600L, update.targetSeconds);
    }

    @Test public void twentyNineSecondsAwayIsNotEnough() {
        PullbackOutcomeTracker tracker = fresh();
        tracker.update(true, 10_000L, START + 10_000L, END);
        tracker.update(false, 0L, START + 20_000L, END);

        PullbackOutcomeTracker.Update early = tracker.update(
                false, 0L, START + 49_999L, END
        );
        PullbackOutcomeTracker.Update exact = tracker.update(
                false, 0L, START + 50_000L, END
        );

        assertFalse(early.resolvedNow);
        assertTrue(exact.resolvedNow);
        assertTrue(exact.success);
    }

    @Test public void restoredReportedReturnIsNotEmittedTwice() {
        PullbackOutcomeTracker tracker = new PullbackOutcomeTracker(
                7L, START, 8_000L, 0L, true, true, false, false
        );

        PullbackOutcomeTracker.Update update = tracker.update(
                true, 2_000L, START + 20_000L, END
        );

        assertFalse(update.returnObservedNow);
        assertFalse(update.resolvedNow);
        assertEquals(10L, tracker.getTargetMs() / 1000L);
    }

    private PullbackOutcomeTracker fresh() {
        return new PullbackOutcomeTracker(
                7L, START, 0L, 0L, false, false, false, false
        );
    }
}
