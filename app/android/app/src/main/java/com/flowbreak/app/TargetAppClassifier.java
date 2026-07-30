package com.flowbreak.app;

import java.util.Set;

/**
 * 目标应用判定器，包含 Play/Domestic 渠道微信视频号特殊规则。
 *
 * 从 FlowForegroundService.tick() 抽取，保持原有：
 * - 普通目标包集合判断
 * - Play 渠道 com.tencent.mm 始终不作为目标信号
 * - Domestic 渠道只有 prefValue=true、detectedAt>0 且 now-detectedAt<60_000 才视为目标
 * - 等于 60 秒视为过期（严格小于）
 *
 * 纯逻辑，不访问 SharedPreferences、Context 或 Build。
 */
public final class TargetAppClassifier {
    private final boolean domestic;

    public TargetAppClassifier(boolean domestic) {
        this.domestic = domestic;
    }

    /**
     * 判断微信视频号信号是否新鲜。
     * 60 秒内有效，等于 60 秒视为过期。
     */
    public static boolean isFreshWechatVideoSignal(
            boolean domestic,
            boolean prefValue,
            long detectedAt,
            long now
    ) {
        if (!domestic) return false;
        return prefValue && detectedAt > 0 && (now - detectedAt) < 60_000L;
    }

    /**
     * 判断前台包是否为目标应用。
     *
     * @param foregroundPackage 前台包名（可为 null）
     * @param targetApps 目标应用集合
     * @param wechatInVideoChannelPref SharedPreferences 中的 wechatInVideoChannel 值
     * @param wechatInVideoChannelAt SharedPreferences 中的 wechatInVideoChannelAt 值
     * @param now 当前时间
     */
    public boolean isTarget(
            String foregroundPackage,
            Set<String> targetApps,
            boolean wechatInVideoChannelPref,
            long wechatInVideoChannelAt,
            long now
    ) {
        if (foregroundPackage == null || !targetApps.contains(foregroundPackage)) {
            return false;
        }
        if (!"com.tencent.mm".equals(foregroundPackage)) {
            return true;
        }
        // Play 渠道无法检查视频号子页面，整个微信包不作为目标信号
        return isFreshWechatVideoSignal(domestic, wechatInVideoChannelPref, wechatInVideoChannelAt, now);
    }
}
