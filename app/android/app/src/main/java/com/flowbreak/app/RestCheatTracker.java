package com.flowbreak.app;

/**
 * 休息防作弊纯累计逻辑。
 *
 * 从 FlowForegroundService.tick() 抽取，保持原有算法：
 * - 只有 state=RESTING 且 isTarget=true 才累计
 * - previousObservedAt<=0 时 delta=0
 * - delta=clamp(now-previousObservedAt, 0, 10_000)
 * - 累计阈值 5_000ms 触发
 * - RESTING 期间暂时离开目标应用（isTarget=false）不清零累计
 * - 只有状态不再是 RESTING 才清零
 * - 触发后由调用方负责 reset()
 *
 * 不访问 Context、Repository、状态机或 UI，便于 JVM 单元测试。
 */
public final class RestCheatTracker {
    public static final long CHEAT_THRESHOLD_MS = 5_000L;
    public static final long MAX_CHEAT_DELTA_MS = 10_000L;

    private long detectedAt;
    private long accumulatedMs;

    /**
     * 观察本 tick 的防作弊 delta。
     *
     * @param resting 当前状态是否 RESTING
     * @param isTarget 当前前台是否目标应用
     * @param previousObservedAt 上一次观测时间戳（usage accumulator 的 lastObservedAt）
     * @param now 当前时间
     * @return 本 tick 的累计值，达到阈值时 >= CHEAT_THRESHOLD_MS
     */
    public long observe(boolean resting, boolean isTarget, long previousObservedAt, long now) {
        if (resting && isTarget) {
            long cheatDelta = previousObservedAt > 0L
                    ? Math.max(0L, Math.min(MAX_CHEAT_DELTA_MS, now - previousObservedAt))
                    : 0L;
            if (detectedAt == 0L) detectedAt = now;
            accumulatedMs += cheatDelta;
        } else if (accumulatedMs != 0L && !resting) {
            // 只有退出 RESTING 状态才清零；RESTING 期间离开目标应用不清零
            detectedAt = 0L;
            accumulatedMs = 0L;
        }
        return accumulatedMs;
    }

    /** 是否已达到触发阈值。 */
    public boolean triggered() {
        return accumulatedMs >= CHEAT_THRESHOLD_MS;
    }

    /** 触发时间戳，未累计时为 0。 */
    public long detectedAt() {
        return detectedAt;
    }

    /** 当前累计毫秒。 */
    public long accumulatedMs() {
        return accumulatedMs;
    }

    /** 手动重置（触发后由 Service 调用）。 */
    public void reset() {
        detectedAt = 0L;
        accumulatedMs = 0L;
    }
}
