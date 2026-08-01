package com.flowbreak.app;

/**
 * 进程死亡后状态恢复协调器。
 *
 * 从 FlowForegroundService.load() 抽取，负责：
 * - 读取 SharedPreferences 持久化状态
 * - RestSessionManager 恢复决策
 * - 必要时同步持久化自动完成结果
 * - 构造 BlockStateMachine
 * - 读取 pullback snapshot
 * - 返回恢复结果
 *
 * 不持有 Context、Service、Activity，不访问 Repository、通知、overlay 或线程。
 * 时间由调用方传入，不读取系统时间。
 */
public final class FlowServiceRecoveryCoordinator {

    /** 恢复结果：包含配置、machine 和 pullback 快照。 */
    public static final class Result {
        public final FlowServiceStateStore.Config config;
        public final BlockStateMachine machine;
        public final PullbackSessionCoordinator.Snapshot pullbackSnapshot;
        public final boolean autoCompletedRest;

        Result(
                FlowServiceStateStore.Config config,
                BlockStateMachine machine,
                PullbackSessionCoordinator.Snapshot pullbackSnapshot,
                boolean autoCompletedRest
        ) {
            this.config = config;
            this.machine = machine;
            this.pullbackSnapshot = pullbackSnapshot;
            this.autoCompletedRest = autoCompletedRest;
        }
    }

    /**
     * 执行完整恢复链路。纯函数，不持有任何外部引用。
     *
     * @param stateStore 持久化状态存储
     * @param now 当前时间
     * @return 恢复结果
     */
    public static Result restore(FlowServiceStateStore stateStore, long now) {
        // 1) 读取配置
        FlowServiceStateStore.Config config = stateStore.loadConfig();

        // 2) 读取 machine 快照
        FlowServiceStateStore.MachineSnapshot snapshot = stateStore.loadMachineSnapshot();

        // 3) 恢复决策
        RestSessionManager.RestoreDecision decision = RestSessionManager.decideRestore(
                new RestSessionManager.RestoreInput(
                        snapshot.persistedState,
                        snapshot.sessionMs,
                        snapshot.graceUntil,
                        snapshot.leftTargetsAt,
                        snapshot.blockedPackage,
                        config.limitMinutes,
                        snapshot.restStartedAt,
                        snapshot.restRequiredMs,
                        snapshot.restSessionId,
                        now
                )
        );

        // 4) 自动完成时同步持久化
        if (decision.autoCompletedRest) {
            stateStore.persistAutoCompletedRest(
                    decision.generatedGraceUntil,
                    decision.completedRestSessionId
            );
        }

        // 5) 计算最终 machine 字段
        BlockStateMachine.State restoredState = decision.restoredState;
        long machineSessionMs = decision.autoCompletedRest ? 0L : snapshot.sessionMs;
        long machineGraceUntil = decision.autoCompletedRest
                ? decision.generatedGraceUntil
                : snapshot.graceUntil;
        String machineBlockedPackage = decision.autoCompletedRest ? "" : snapshot.blockedPackage;

        // 6) 创建 machine
        BlockStateMachine machine = new BlockStateMachine(
                restoredState,
                machineSessionMs,
                machineGraceUntil,
                snapshot.leftTargetsAt,
                machineBlockedPackage
        );

        // 7) 读取 pullback snapshot
        PullbackSessionCoordinator.Snapshot pullbackSnapshot = stateStore.loadPullbackSnapshot();

        return new Result(config, machine, pullbackSnapshot, decision.autoCompletedRest);
    }

    private FlowServiceRecoveryCoordinator() { }
}
