package com.flowbreak.app;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

/**
 * 检测当前前台应用，封装 UsageEvents 游标与 ForegroundAppTracker。
 *
 * 从 FlowForegroundService.getTopPackage() 抽取，保持原有：
 * - 初次游标为空时回看 36 小时
 * - 每次查询后游标推进到 now
 * - 异常安全降级，返回 tracker 当前状态
 * - 通过 accept() 喂入事件，MOVE_TO_FOREGROUND 等才会更新前台包
 *
 * 不持有 Service 或 Activity，不访问 SharedPreferences 或状态机。
 */
public final class ForegroundUsageDetector {
    public static final long INITIAL_EVENT_LOOKBACK_MS = 36 * 60 * 60_000L;

    private final Context context;
    private final ForegroundAppTracker tracker;
    private final long initialLookbackMs;

    private long usageEventsCursor;
    private long lastUsageEventAt;

    public ForegroundUsageDetector(Context context) {
        this(context, new ForegroundAppTracker(), INITIAL_EVENT_LOOKBACK_MS);
    }

    public ForegroundUsageDetector(Context context, ForegroundAppTracker tracker, long initialLookbackMs) {
        this.context = context.getApplicationContext();
        this.tracker = tracker;
        this.initialLookbackMs = initialLookbackMs;
    }

    /**
     * 查询 UsageEvents 并返回当前前台包名。
     * 每次查询后游标至少推进到 now，staticLastUsageEventAt 由调用方读取 getLastUsageEventAt() 后更新。
     */
    public String detect(long now) {
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return "";
        try {
            long begin = usageEventsCursor > 0
                    ? usageEventsCursor
                    : Math.max(0, now - initialLookbackMs);
            UsageEvents events = manager.queryEvents(begin, now);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event);
                tracker.accept(
                        event.getPackageName(), event.getEventType(), event.getTimeStamp()
                );
                lastUsageEventAt = Math.max(lastUsageEventAt, event.getTimeStamp());
                usageEventsCursor = Math.max(usageEventsCursor, event.getTimeStamp());
            }
            // 后续轮询只需新事件，避免重复扫描整个回看窗口
            usageEventsCursor = Math.max(usageEventsCursor, now);
        } catch (Exception ignored) { }
        return tracker.getForegroundPackage();
    }

    /** 重置游标和 tracker（屏幕关闭或重新交互时调用）。 */
    public void reset() {
        tracker.reset();
    }

    /** 设置游标到指定时间（screen on 后回看 36 小时；post-unlock 回看 60 秒）。 */
    public void resetCursor(long cursor) {
        usageEventsCursor = cursor;
    }

    public long getLastUsageEventAt() {
        return lastUsageEventAt;
    }

    public long getCursor() {
        return usageEventsCursor;
    }
}
