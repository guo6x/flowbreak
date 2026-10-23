package com.flowbreak.app;

/**
 * Process-local liveness for the Domestic Accessibility service.
 *
 * <p>Settings enablement and a live bound service are intentionally separate
 * facts. A generation token prevents a stale service instance from clearing a
 * newer connection during an asynchronous unbind/destroy sequence.</p>
 */
public final class AccessibilityRuntimeState {
    private static final Object LOCK = new Object();
    private static long nextGeneration;
    private static long connectedGeneration;
    private static boolean connected;

    private AccessibilityRuntimeState() { }

    /** Marks a newly connected service instance and returns its generation. */
    public static long connect() {
        synchronized (LOCK) {
            connectedGeneration = ++nextGeneration;
            connected = true;
            return connectedGeneration;
        }
    }

    /**
     * Clears the connection only when the token still belongs to the current
     * service instance.
     */
    public static void disconnect(long generation) {
        synchronized (LOCK) {
            if (generation > 0L && generation == connectedGeneration) {
                connected = false;
                connectedGeneration = 0L;
            }
        }
    }

    public static boolean isConnected() {
        synchronized (LOCK) {
            return connected;
        }
    }

    public static long connectedGeneration() {
        synchronized (LOCK) {
            return connectedGeneration;
        }
    }

    /** Test-only reset; production processes start with the default false state. */
    static void resetForTests() {
        synchronized (LOCK) {
            nextGeneration = 0L;
            connectedGeneration = 0L;
            connected = false;
        }
    }
}
