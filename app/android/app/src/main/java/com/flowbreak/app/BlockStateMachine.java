package com.flowbreak.app;

/**
 * Pure, deterministic blocker state machine. Android services only provide time
 * and foreground-package observations; all threshold behavior lives here so it
 * can be covered by local unit tests.
 */
public final class BlockStateMachine {
    public enum State {
        IDLE, PERCEPTION, COGNITION, BLOCKED, RESTING, GRACE
    }

    public static final long LEAVE_RESET_MS = 30_000L;

    private State state;
    private long sessionMs;
    private long graceUntil;
    private long lastCheckAt;
    private long leftTargetsAt;
    private String blockedPackage;
    private boolean targetActive;

    public BlockStateMachine(
            State state,
            long sessionMs,
            long graceUntil,
            long leftTargetsAt,
            String blockedPackage
    ) {
        this.state = state == null ? State.IDLE : state;
        this.sessionMs = Math.max(0, sessionMs);
        this.graceUntil = Math.max(0, graceUntil);
        this.leftTargetsAt = Math.max(0, leftTargetsAt);
        this.blockedPackage = blockedPackage == null ? "" : blockedPackage;
    }

    public State update(boolean targetInForeground, String foregroundPackage, long now, long limitMs) {
        limitMs = Math.max(1_000L, limitMs);
        long delta = lastCheckAt <= 0 ? 0 : Math.max(0, Math.min(10_000L, now - lastCheckAt));
        lastCheckAt = now;

        if (graceUntil > now) {
            state = State.GRACE;
            leftTargetsAt = 0;
            targetActive = targetInForeground;
            return state;
        }
        if (state == State.GRACE && graceUntil <= now) {
            sessionMs = 0;
            graceUntil = 0;
            state = State.IDLE;
            targetActive = false;
        }
        if (state == State.RESTING) {
            return state;
        }

        if (targetInForeground) {
            // A target can be foreground again after the phone was locked or
            // FlowBreak was backgrounded. Reset before accepting it when the
            // user has been away from every target for the full grace period.
            // BLOCKED is sticky: only a completed rest or a legitimate
            // emergency unlock may leave it, never plain time away.
            if (leftTargetsAt > 0
                    && now - leftTargetsAt >= LEAVE_RESET_MS
                    && state != State.BLOCKED) {
                reset();
            }
            leftTargetsAt = 0;
            if (targetActive) sessionMs += delta;
            targetActive = true;
            blockedPackage = foregroundPackage == null ? "" : foregroundPackage;
            state = stateFor(sessionMs, limitMs);
            return state;
        }

        if (state == State.BLOCKED) {
            // Once blocked, staying away must not reset the session: returning
            // to a target later must re-enter BLOCKED immediately.
            if (leftTargetsAt == 0 && sessionMs > 0) leftTargetsAt = now;
            targetActive = false;
            return state;
        }

        if (leftTargetsAt == 0 && sessionMs > 0) {
            leftTargetsAt = now;
        } else if (now - leftTargetsAt >= LEAVE_RESET_MS) {
            reset();
        }
        targetActive = false;
        return state;
    }

    public State beginRest() {
        state = State.RESTING;
        return state;
    }

    /** Marks a screen-off transition as leaving all target applications. */
    public void onScreenOff(long now) {
        targetActive = false;
        lastCheckAt = now;
        if (state != State.RESTING && state != State.GRACE && sessionMs > 0 && leftTargetsAt == 0) {
            leftTargetsAt = now;
        }
    }

    /** Prevents elapsed wall-clock time while the display was off from being counted. */
    public void onScreenOn(long now) {
        targetActive = false;
        lastCheckAt = now;
    }

    public State completeRest(long now, long graceMs) {
        sessionMs = 0;
        leftTargetsAt = 0;
        blockedPackage = "";
        targetActive = false;
        graceUntil = now + Math.max(0, graceMs);
        state = State.GRACE;
        lastCheckAt = now;
        return state;
    }

    public State cancelRest(long limitMs) {
        state = stateFor(sessionMs, limitMs);
        return state;
    }

    public State emergencyUnlock(long now, long graceMs) {
        return completeRest(now, graceMs);
    }

    public void reset() {
        state = State.IDLE;
        sessionMs = 0;
        leftTargetsAt = 0;
        blockedPackage = "";
    }

    public static State stateFor(long sessionMs, long limitMs) {
        double ratio = sessionMs / (double) Math.max(1L, limitMs);
        if (ratio >= 1.20d) return State.BLOCKED;
        if (ratio >= 1.00d) return State.COGNITION;
        if (ratio >= 0.80d) return State.PERCEPTION;
        return State.IDLE;
    }

    public State getState() { return state; }
    public long getSessionMs() { return sessionMs; }
    public long getGraceUntil() { return graceUntil; }
    public long getLeftTargetsAt() { return leftTargetsAt; }
    public String getBlockedPackage() { return blockedPackage; }
}
