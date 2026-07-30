package com.flowbreak.app;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 使用时长累计器，封装 observe delta 计算、queue 和批量 flush。
 *
 * 从 FlowForegroundService 抽取，保持原有语义：
 * - delta = clamp(now - lastObservedAt, 0, 10_000)
 * - continuedTarget = isTarget && lastObservedTargetPackage 非空
 * - 首次切回目标应用返回 0（lastObservedTargetPackage 为空时）
 * - target A 切到 target B 仍累计 delta（不要求包名一致）
 * - 15 秒 flush 间隔，只写完整秒，保留毫秒余数
 * - data erase 时清空 pending 且不写
 * - 无效包名和非正时长忽略
 *
 * 不持有 Context、Service 或 Activity。
 */
public final class UsageAccumulator {
    public static final long USAGE_FLUSH_MS = 15_000L;
    public static final long MAX_DELTA_MS = 10_000L;

    private final Map<String, Long> pendingUsageMs = new HashMap<>();
    private long lastObservedAt;
    private String lastObservedTargetPackage = "";
    private long lastUsageFlushAt;

    /**
     * 观察一次目标应用状态，返回本次应累计的 delta 毫秒数。
     *
     * @param isTarget 当前前台是否为目标应用
     * @param packageName 前台包名（可为 null）
     * @param now 当前时间
     * @return continuedTarget ? delta : 0
     */
    public long observe(boolean isTarget, String packageName, long now) {
        long delta = lastObservedAt <= 0L ? 0L : Math.max(0L, Math.min(MAX_DELTA_MS, now - lastObservedAt));
        boolean continuedTarget = isTarget && !lastObservedTargetPackage.isEmpty();
        lastObservedAt = now;
        lastObservedTargetPackage = isTarget && packageName != null ? packageName : "";
        return continuedTarget ? delta : 0L;
    }

    /** 累加到 pendingUsageMs。无效包名或非正时长忽略。 */
    public void queue(String packageName, long durationMs) {
        if (packageName == null || packageName.isEmpty() || durationMs <= 0) return;
        pendingUsageMs.put(packageName, pendingUsageMs.getOrDefault(packageName, 0L) + durationMs);
    }

    /** 重置观测状态（屏幕关闭、监控关闭、目标列表为空时调用）。 */
    public void resetObservation(long now) {
        lastObservedAt = now;
        lastObservedTargetPackage = "";
    }

    /**
     * 批量写入 repository。
     *
     * @param force true 时立即写（onDestroy 或 screen off）；false 时需达到 15 秒间隔
     * @param now 当前时间
     * @param dataErasing 数据清除期间清空 pending 且不写
     * @param sink 写入接口，通常为 repository::addUsage
     */
    public void flush(boolean force, long now, boolean dataErasing, UsageSink sink) {
        if (dataErasing) {
            pendingUsageMs.clear();
            return;
        }
        if (!force && now - lastUsageFlushAt < USAGE_FLUSH_MS) return;
        lastUsageFlushAt = now;
        for (Map.Entry<String, Long> entry : new HashMap<>(pendingUsageMs).entrySet()) {
            long seconds = entry.getValue() / 1000L;
            if (seconds <= 0) continue;
            sink.addUsage(entry.getKey(), seconds);
            pendingUsageMs.put(entry.getKey(), entry.getValue() - seconds * 1000L);
        }
    }

    /** 返回 pending 快照（用于诊断或测试）。 */
    public Map<String, Long> pendingSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(pendingUsageMs));
    }

    public long getLastObservedAt() {
        return lastObservedAt;
    }

    public String getLastObservedTargetPackage() {
        return lastObservedTargetPackage;
    }

    public long getLastUsageFlushAt() {
        return lastUsageFlushAt;
    }

    /** repository 写入接口，避免暴露 FlowRepository 给累计器。 */
    public interface UsageSink {
        void addUsage(String packageName, long seconds);
    }
}
