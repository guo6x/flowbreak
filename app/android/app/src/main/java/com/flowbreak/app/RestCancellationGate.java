package com.flowbreak.app;

/** Pure policy for rejecting stale CANCEL_REST commands. */
public final class RestCancellationGate {
    private RestCancellationGate() { }

    public static boolean shouldCancel(
            BlockStateMachine.State currentState,
            long expectedSessionId,
            long currentSessionId,
            String expectedBeginRequestToken,
            String activeBeginRequestToken
    ) {
        if (currentState != BlockStateMachine.State.RESTING
                || expectedBeginRequestToken == null
                || expectedBeginRequestToken.isEmpty()
                || !expectedBeginRequestToken.equals(activeBeginRequestToken)) {
            return false;
        }
        return currentSessionId > 0L
                && (expectedSessionId <= 0L || expectedSessionId == currentSessionId);
    }

    public static boolean shouldAcceptBegin(
            String requestedBeginRequestToken,
            String activeBeginRequestToken
    ) {
        return requestedBeginRequestToken != null
                && !requestedBeginRequestToken.isEmpty()
                && requestedBeginRequestToken.equals(activeBeginRequestToken);
    }
}
