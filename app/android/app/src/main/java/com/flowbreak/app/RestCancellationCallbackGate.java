package com.flowbreak.app;

import java.util.concurrent.atomic.AtomicInteger;

/** Linearizes queued REST cancellation callbacks against service shutdown. */
final class RestCancellationCallbackGate {
    private static final int PENDING = 0;
    private static final int RUNNING = 1;
    private static final int FINISHED = 2;

    private final AtomicInteger state = new AtomicInteger(PENDING);

    boolean start() {
        return state.compareAndSet(PENDING, RUNNING);
    }

    /** Finishes a pending callback only; a running monitor operation owns its final result. */
    boolean deferIfPending() {
        return state.compareAndSet(PENDING, FINISHED);
    }

    /** Completes either a pending enqueue failure or an already-running operation exactly once. */
    boolean finish() {
        int current;
        do {
            current = state.get();
            if (current == FINISHED) return false;
        } while (!state.compareAndSet(current, FINISHED));
        return true;
    }
}
