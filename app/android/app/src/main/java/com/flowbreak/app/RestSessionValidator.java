package com.flowbreak.app;

/** Pure validation for the native rest-unlock contract. */
public final class RestSessionValidator {
    private RestSessionValidator() { }

    public static boolean isComplete(long startedAt, long requiredMs, long now) {
        return startedAt > 0L
                && requiredMs > 0L
                && now >= startedAt
                && now - startedAt >= requiredMs;
    }
}
