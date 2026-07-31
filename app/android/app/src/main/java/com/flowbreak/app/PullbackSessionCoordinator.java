package com.flowbreak.app;

/**
 * 协调 PullbackOutcomeTracker 的持久化恢复、update 副作用和清理。
 *
 * 从 FlowForegroundService 的 loadPullbackTracker / trackPullbackOutcome /
 * clearPullbackTracker / persistState(pullback 字段) 抽取，保持原有语义：
 *
 * - restore: 从持久化字段重建 tracker
 * - update: 调用 tracker.update，副作用顺序为
 *   1) returnObservedNow -> sink.recordPostRestReturn
 *   2) resolvedNow -> sink.recordPullbackOutcome
 * - snapshot: 提供持久化所需字段
 * - clear: 清除 tracker 和持久化字段
 *
 * 不直接持有 Service 或 Activity。持久化字段写入通过返回 Snapshot 由
 * FlowServiceStateStore 在同一次 Editor 中完成，避免多次 apply。
 */
public final class PullbackSessionCoordinator {

    /** repository 副作用接口，避免暴露 FlowRepository。 */
    public interface OutcomeSink {
        void recordPostRestReturn(long sessionId);
        void recordPullbackOutcome(boolean success, long targetSeconds, long sessionId);
    }

    /** 不可变快照，供 FlowServiceStateStore 在同一次 apply 中持久化。 */
    public static final class Snapshot {
        public final boolean present;
        public final long sessionId;
        public final long startedAt;
        public final long targetMs;
        public final long leftTargetsAt;
        public final boolean sawTarget;
        public final boolean returnReported;
        public final boolean resolved;
        public final boolean success;

        public Snapshot(
                boolean present,
                long sessionId,
                long startedAt,
                long targetMs,
                long leftTargetsAt,
                boolean sawTarget,
                boolean returnReported,
                boolean resolved,
                boolean success
        ) {
            this.present = present;
            this.sessionId = sessionId;
            this.startedAt = startedAt;
            this.targetMs = targetMs;
            this.leftTargetsAt = leftTargetsAt;
            this.sawTarget = sawTarget;
            this.returnReported = returnReported;
            this.resolved = resolved;
            this.success = success;
        }

        /** 空快照（无 tracker）。 */
        public static Snapshot empty() {
            return new Snapshot(false, 0L, 0L, 0L, 0L, false, false, false, false);
        }
    }

    /** update 调用结果，包含本次副作用标志。 */
    public static final class UpdateResult {
        public final boolean returnObservedNow;
        public final boolean resolvedNow;
        public final boolean success;
        public final long targetSeconds;

        UpdateResult(
                boolean returnObservedNow,
                boolean resolvedNow,
                boolean success,
                long targetSeconds
        ) {
            this.returnObservedNow = returnObservedNow;
            this.resolvedNow = resolvedNow;
            this.success = success;
            this.targetSeconds = targetSeconds;
        }

        static UpdateResult noop() {
            return new UpdateResult(false, false, false, 0L);
        }
    }

    private PullbackOutcomeTracker tracker;

    public PullbackSessionCoordinator() {
        this.tracker = null;
    }

    /** 从持久化字段恢复 tracker。sessionId<=0 时不创建。 */
    public void restore(Snapshot snapshot) {
        if (!snapshot.present || snapshot.sessionId <= 0L) {
            tracker = null;
            return;
        }
        tracker = new PullbackOutcomeTracker(
                snapshot.sessionId,
                snapshot.startedAt,
                snapshot.targetMs,
                snapshot.leftTargetsAt,
                snapshot.sawTarget,
                snapshot.returnReported,
                snapshot.resolved,
                snapshot.success
        );
    }

    /**
     * 执行一次 update，副作用顺序：
     * 1) returnObservedNow -> sink.recordPostRestReturn
     * 2) resolvedNow -> sink.recordPullbackOutcome
     *
     * @param isTarget 当前前台是否目标应用
     * @param targetDeltaMs 本次目标应用 delta
     * @param now 当前时间
     * @param graceUntil machine 的 graceUntil
     * @param sink 副作用接收者，可为 null（仅用于无副作用路径）
     * @return 本次 update 结果
     */
    public UpdateResult update(
            boolean isTarget,
            long targetDeltaMs,
            long now,
            long graceUntil,
            OutcomeSink sink
    ) {
        if (tracker == null) return UpdateResult.noop();
        PullbackOutcomeTracker.Update u = tracker.update(isTarget, targetDeltaMs, now, graceUntil);
        if (u.returnObservedNow && sink != null) {
            sink.recordPostRestReturn(tracker.getSessionId());
        }
        if (u.resolvedNow && sink != null) {
            sink.recordPullbackOutcome(u.success, u.targetSeconds, tracker.getSessionId());
        }
        return new UpdateResult(u.returnObservedNow, u.resolvedNow, u.success, u.targetSeconds);
    }

    /** 当前快照，供持久化使用。 */
    public Snapshot snapshot() {
        if (tracker == null) return Snapshot.empty();
        return new Snapshot(
                true,
                tracker.getSessionId(),
                tracker.getStartedAt(),
                tracker.getTargetMs(),
                tracker.getLeftTargetsAt(),
                tracker.hasSeenTarget(),
                tracker.isReturnReported(),
                tracker.isResolved(),
                tracker.isSuccess()
        );
    }

    /** 是否存在活动 tracker。 */
    public boolean isActive() {
        return tracker != null;
    }

    /** 清除 tracker（不写持久化，持久化由 StateStore 负责）。 */
    public void clear() {
        tracker = null;
    }

    /** 当前 sessionId（无 tracker 时为 0）。 */
    public long currentSessionId() {
        return tracker == null ? 0L : tracker.getSessionId();
    }
}
