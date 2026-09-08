package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FlowMonitorLoopTest {
    @Test public void repeatedStartKeepsOnePendingMonitorCallback() {
        FakeScheduler scheduler = new FakeScheduler();
        Runnable monitor = () -> { };
        FlowMonitorLoop loop = new FlowMonitorLoop(scheduler, monitor);

        loop.start();
        loop.start();

        assertTrue(loop.isActive());
        assertEquals(2, scheduler.postCount);
        assertEquals(2, scheduler.removeCount);
        assertEquals(1, scheduler.pendingCallbacks);
    }

    @Test public void completionSchedulesOneDelayedNextTick() {
        FakeScheduler scheduler = new FakeScheduler();
        FlowMonitorLoop loop = new FlowMonitorLoop(scheduler, () -> { });

        loop.start();
        scheduler.pendingCallbacks = 0;
        loop.scheduleNext();

        assertEquals(1, scheduler.delayedPostCount);
        assertEquals(FlowMonitorLoop.PERIOD_MS, scheduler.lastDelayMs);
        assertEquals(1, scheduler.pendingCallbacks);
    }

    @Test public void stopPreventsFurtherScheduling() {
        FakeScheduler scheduler = new FakeScheduler();
        FlowMonitorLoop loop = new FlowMonitorLoop(scheduler, () -> { });

        loop.start();
        loop.stop();
        loop.scheduleNext();

        assertFalse(loop.isActive());
        assertEquals(0, scheduler.pendingCallbacks);
        assertEquals(0, scheduler.delayedPostCount);
    }

    @Test public void shutdownIsIdempotentAndRejectsRestart() {
        FakeScheduler scheduler = new FakeScheduler();
        FlowMonitorLoop loop = new FlowMonitorLoop(scheduler, () -> { });

        loop.start();
        loop.shutdown();
        loop.shutdown();
        loop.start();
        loop.scheduleNext();

        assertTrue(loop.isShutdown());
        assertFalse(loop.isActive());
        assertEquals(0, scheduler.pendingCallbacks);
        assertEquals(2, scheduler.removeCount);
    }

    private static final class FakeScheduler implements FlowMonitorLoop.Scheduler {
        int postCount;
        int delayedPostCount;
        int removeCount;
        int pendingCallbacks;
        long lastDelayMs;

        @Override public void removeCallbacks(Runnable runnable) {
            removeCount++;
            pendingCallbacks = 0;
        }

        @Override public void post(Runnable runnable) {
            postCount++;
            pendingCallbacks++;
        }

        @Override public void postDelayed(Runnable runnable, long delayMs) {
            delayedPostCount++;
            lastDelayMs = delayMs;
            pendingCallbacks++;
        }
    }
}
