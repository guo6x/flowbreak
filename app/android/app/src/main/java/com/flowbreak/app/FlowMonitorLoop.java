package com.flowbreak.app;

/**
 * Owns the lifecycle of the single serialized monitor callback.
 *
 * The scheduler is deliberately tiny so the lifecycle rules can be tested
 * without constructing an Android HandlerThread. The Service supplies a
 * Handler-backed scheduler; all calls are made on that Handler's looper.
 */
final class FlowMonitorLoop {
    static final long PERIOD_MS = 2_000L;

    interface Scheduler {
        void removeCallbacks(Runnable runnable);
        void post(Runnable runnable);
        void postDelayed(Runnable runnable, long delayMs);
    }

    private final Scheduler scheduler;
    private final Runnable monitor;
    private boolean active;
    private boolean shutdown;

    FlowMonitorLoop(Scheduler scheduler, Runnable monitor) {
        this.scheduler = scheduler;
        this.monitor = monitor;
    }

    /** Start or immediately re-arm the one monitor callback. */
    void start() {
        if (shutdown) return;
        scheduler.removeCallbacks(monitor);
        active = true;
        scheduler.post(monitor);
    }

    /** Schedule only the next tick after the current tick has completed. */
    void scheduleNext() {
        if (!active || shutdown) return;
        scheduler.removeCallbacks(monitor);
        scheduler.postDelayed(monitor, PERIOD_MS);
    }

    /** Stop future ticks while keeping the scheduler available for teardown. */
    void stop() {
        active = false;
        scheduler.removeCallbacks(monitor);
    }

    /** Permanently close this loop. Repeated shutdown is harmless. */
    void shutdown() {
        if (shutdown) return;
        stop();
        shutdown = true;
    }

    boolean isActive() { return active; }

    boolean isShutdown() { return shutdown; }
}
