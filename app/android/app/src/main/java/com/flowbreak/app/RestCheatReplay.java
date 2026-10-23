package com.flowbreak.app;

/**
 * Pure coordination for applying verified target time to an active rest
 * session. The five-second threshold remains exclusively in
 * {@link RestCheatTracker}; this class only connects that tracker to the
 * existing state-machine cancellation path.
 */
public final class RestCheatReplay {
    private RestCheatReplay() { }

    /**
     * Returns independently verified target time intersecting a REST window.
     * A negative result means that the supplied timeline was not complete or
     * the requested window was invalid.
     */
    public static long verifiedTargetMsDuring(
            TargetSessionGapReconciler.Result result,
            long restStartWallMs,
            long restEndWallMs
    ) {
        if (result == null
                || result.status != TargetSessionGapReconciler.Status.SUCCESS
                || restStartWallMs < 0L
                || restEndWallMs <= restStartWallMs) {
            return -1L;
        }
        long targetMs = 0L;
        for (TargetSessionGapReconciler.TimelineSegment segment : result.segments) {
            if (!segment.targetActive || !segment.interactionAvailable) continue;
            long start = Math.max(restStartWallMs, segment.startWallMs);
            long end = Math.min(restEndWallMs, segment.endWallMs);
            if (end > start) {
                long duration = end - start;
                targetMs = Long.MAX_VALUE - targetMs < duration
                        ? Long.MAX_VALUE
                        : targetMs + duration;
            }
        }
        return targetMs;
    }

    public static Decision applyVerifiedTargetMs(
            BlockStateMachine machine,
            RestCheatTracker tracker,
            long verifiedTargetMs,
            long limitMs
    ) {
        if (machine == null || tracker == null) {
            return new Decision(0L, false, BlockStateMachine.State.IDLE);
        }
        boolean resting = machine.getState() == BlockStateMachine.State.RESTING;
        long accumulatedMs = tracker.applyVerifiedTargetMs(resting, verifiedTargetMs);
        if (!resting || !tracker.triggered()) {
            return new Decision(accumulatedMs, false, machine.getState());
        }
        return cancelTriggered(machine, tracker, limitMs);
    }

    public static Decision cancelTriggered(
            BlockStateMachine machine,
            RestCheatTracker tracker,
            long limitMs
    ) {
        if (machine == null
                || tracker == null
                || machine.getState() != BlockStateMachine.State.RESTING
                || !tracker.triggered()) {
            return new Decision(
                    tracker == null ? 0L : tracker.accumulatedMs(),
                    false,
                    machine == null ? BlockStateMachine.State.IDLE : machine.getState()
            );
        }
        long accumulatedMs = tracker.accumulatedMs();
        BlockStateMachine.State state = machine.cancelRest(limitMs);
        tracker.reset();
        return new Decision(accumulatedMs, true, state);
    }

    public static final class Decision {
        public final long accumulatedMs;
        public final boolean cancelled;
        public final BlockStateMachine.State state;

        private Decision(long accumulatedMs, boolean cancelled, BlockStateMachine.State state) {
            this.accumulatedMs = accumulatedMs;
            this.cancelled = cancelled;
            this.state = state;
        }
    }
}
