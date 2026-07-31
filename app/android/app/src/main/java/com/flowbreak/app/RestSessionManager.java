package com.flowbreak.app;

/**
 * 休息会话生命周期的纯逻辑协调器。
 *
 * 从 FlowForegroundService.load() / beginRestSession() / clearActiveRestSession()
 * 抽取，保持原有行为：
 *
 * - decideRestore: 纯函数，根据持久化字段决定恢复后的状态
 * - prepareBeginRest: 纯函数，决定是否生成新 session
 * - 实际 SharedPreferences 写入由 FlowServiceStateStore 完成，避免多次 apply
 *
 * 不持有 Service、Activity、Context 或 BlockStateMachine 长期实例。
 */
public final class RestSessionManager {

    public static final long MIN_REST_DURATION_SECONDS = 30L;

    /** 恢复决策的输入。 */
    public static final class RestoreInput {
        public final BlockStateMachine.State persistedState;
        public final long sessionMs;
        public final long graceUntil;
        public final long leftTargetsAt;
        public final String blockedPackage;
        public final int limitMinutes;
        public final long restStartedAt;
        public final long restRequiredMs;
        public final long restSessionId;
        public final long now;

        public RestoreInput(
                BlockStateMachine.State persistedState,
                long sessionMs,
                long graceUntil,
                long leftTargetsAt,
                String blockedPackage,
                int limitMinutes,
                long restStartedAt,
                long restRequiredMs,
                long restSessionId,
                long now
        ) {
            this.persistedState = persistedState;
            this.sessionMs = sessionMs;
            this.graceUntil = graceUntil;
            this.leftTargetsAt = leftTargetsAt;
            this.blockedPackage = blockedPackage;
            this.limitMinutes = limitMinutes;
            this.restStartedAt = restStartedAt;
            this.restRequiredMs = restRequiredMs;
            this.restSessionId = restSessionId;
            this.now = now;
        }
    }

    /** 恢复决策的结果。 */
    public static final class RestoreDecision {
        public final BlockStateMachine.State restoredState;
        public final boolean autoCompletedRest;
        public final long generatedGraceUntil;
        public final long completedRestSessionId;

        private RestoreDecision(
                BlockStateMachine.State restoredState,
                boolean autoCompletedRest,
                long generatedGraceUntil,
                long completedRestSessionId
        ) {
            this.restoredState = restoredState;
            this.autoCompletedRest = autoCompletedRest;
            this.generatedGraceUntil = generatedGraceUntil;
            this.completedRestSessionId = completedRestSessionId;
        }

        /** 非自动完成分支。 */
        static RestoreDecision keep(BlockStateMachine.State state) {
            return new RestoreDecision(state, false, 0L, 0L);
        }

        /** 自动完成 RESTING -> GRACE 分支。 */
        static RestoreDecision autoComplete(long now, long restSessionId) {
            return new RestoreDecision(
                    BlockStateMachine.State.GRACE,
                    true,
                    now + FlowServiceStateStore.GRACE_MS,
                    restSessionId
            );
        }
    }

    /**
     * 决定恢复后的状态。纯函数，不访问任何 Android API。
     *
     * 分支：
     * 1) 非 RESTING: 保持原状态
     * 2) RESTING 且 startedAt<=0: 回退 stateFor(sessionMs, limitMs*60_000)
     * 3) RESTING 且未达 requiredMs: 保持 RESTING
     * 4) RESTING 且已达到: 自动完成进入 GRACE，graceUntil = now + 10min
     */
    public static RestoreDecision decideRestore(RestoreInput input) {
        if (input.persistedState != BlockStateMachine.State.RESTING) {
            return RestoreDecision.keep(input.persistedState);
        }
        if (input.restStartedAt <= 0L) {
            // 休息从未开始过：回退到基于使用时长的状态
            BlockStateMachine.State fallback = BlockStateMachine.stateFor(
                    input.sessionMs,
                    input.limitMinutes * 60_000L
            );
            return RestoreDecision.keep(fallback);
        }
        if (RestSessionValidator.isComplete(input.restStartedAt, input.restRequiredMs, input.now)) {
            // 服务被杀后实际已超过所需休息时长：自动完成休息进入 GRACE
            return RestoreDecision.autoComplete(input.now, input.restSessionId);
        }
        // 其余情况（startedAt > 0 但尚未到 requiredMs）：保持 RESTING
        return RestoreDecision.keep(BlockStateMachine.State.RESTING);
    }

    /**
     * 计算 beginRest 所需的 session 字段。
     *
     * @param alreadyResting machine 是否已 RESTING
     * @param existingStartedAt 持久化的 restStartedAt
     * @param restDurationSeconds 用户配置的休息时长秒数
     * @param now 当前时间
     * @return 非空表示应创建新 session；null 表示已有活动 session，仅 persistState 即可
     */
    public static BeginRestDecision prepareBeginRest(
            boolean alreadyResting,
            long existingStartedAt,
            int restDurationSeconds,
            long now
    ) {
        if (alreadyResting && existingStartedAt > 0L) {
            // React remount：保持同一 session
            return null;
        }
        long requiredMs = Math.max(MIN_REST_DURATION_SECONDS, restDurationSeconds) * 1000L;
        return new BeginRestDecision(now, requiredMs, now);
    }

    /** beginRest 的决策结果。 */
    public static final class BeginRestDecision {
        public final long startedAt;
        public final long requiredMs;
        public final long sessionId;

        BeginRestDecision(long startedAt, long requiredMs, long sessionId) {
            this.startedAt = startedAt;
            this.requiredMs = requiredMs;
            this.sessionId = sessionId;
        }
    }

    private RestSessionManager() { }
}
