package com.flowbreak.app;

/**
 * Pure coordination for applying verified target time to an active rest
 * session. The five-second threshold remains exclusively in
 * {@link RestCheatTracker}; this class only connects that tracker to the
 * existing state-machine cancellation path.
 */
public final class RestCheatReplay {
    private RestCheatReplay() { }

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
