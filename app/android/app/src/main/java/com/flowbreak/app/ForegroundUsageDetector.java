package com.flowbreak.app;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 检测当前前台应用，封装 UsageEvents 游标与 ForegroundAppTracker。
 *
 * 从 FlowForegroundService.getTopPackage() 抽取，保持原有：
 * - 初次游标为空时回看 36 小时
 * - 每次查询后游标推进到 now
 * - 查询失败时 fail closed，不返回 tracker 的旧前台状态
 * - 通过 accept() 喂入事件，MOVE_TO_FOREGROUND 等才会更新前台包
 *
 * 冷启动引导（bootstrap）：在 tracker 确认前台包之前，每次 detect 都会
 * 重置 tracker 并回看最近 BOOTSTRAP_RETRY_WINDOW_MS 的事件，确保服务冷启动
 * 或 detector 重建后，已在前台的目标应用在第一次或最迟下一轮 detect 被识别。
 * 一旦前台包已知，后续轮询只增量读取新事件，不重复扫描历史窗口。
 *
 * 不持有 Service 或 Activity，不访问 SharedPreferences 或状态机。
 */
public final class ForegroundUsageDetector {
    public static final long INITIAL_EVENT_LOOKBACK_MS = 36 * 60 * 60_000L;
    static final long BOOTSTRAP_RETRY_WINDOW_MS = 60_000L;
    static final long LATE_EVENT_OVERLAP_MS = 10_000L;
    static final long RECENT_EVENT_RETENTION_MS = 2 * LATE_EVENT_OVERLAP_MS;
    static final int MAX_RECENT_EVENT_CACHE_SIZE = 4_096;

    interface UsageEventsQuery {
        UsageEvents query(long begin, long end);
    }

    private final ForegroundAppTracker tracker;
    private final long initialLookbackMs;
    private final UsageEventsQuery usageEventsQuery;
    private final LinkedHashSet<ObservedEvent> recentSeenEvents = new LinkedHashSet<>();

    private long usageEventsCursor;
    private long lastUsageEventAt;
    private long liveUsageQueryFailureCount;
    private String lastLiveUsageQueryFailureClass = "";
    private boolean lastLiveUsageQuerySucceeded;
    private boolean bootstrapPending = true;

    public ForegroundUsageDetector(Context context) {
        this(context, new ForegroundAppTracker(), INITIAL_EVENT_LOOKBACK_MS,
                systemUsageEventsQuery(context));
    }

    public ForegroundUsageDetector(Context context, ForegroundAppTracker tracker, long initialLookbackMs) {
        this(context, tracker, initialLookbackMs, systemUsageEventsQuery(context));
    }

    ForegroundUsageDetector(
            Context context,
            ForegroundAppTracker tracker,
            long initialLookbackMs,
            UsageEventsQuery usageEventsQuery
    ) {
        this.tracker = tracker;
        this.initialLookbackMs = initialLookbackMs;
        this.usageEventsQuery = usageEventsQuery;
    }

    /**
     * 查询 UsageEvents 并返回当前前台包名。
     * 每次查询后游标至少推进到 now，staticLastUsageEventAt 由调用方读取 getLastUsageEventAt() 后更新。
     */
    public String detect(long now) {
        try {
            long begin;
            if (bootstrapPending) {
                // 冷启动首轮回看完整历史；若上一轮仍未确定前台包，
                // 重置 tracker 后回看最近窗口，避免旧事件被单调时间戳守卫丢弃。
                tracker.reset();
                begin = usageEventsCursor > 0
                        ? Math.max(0, now - BOOTSTRAP_RETRY_WINDOW_MS)
                        : Math.max(0, now - initialLookbackMs);
            } else {
                begin = Math.max(0, usageEventsCursor - LATE_EVENT_OVERLAP_MS);
            }

            List<ObservedEvent> events = readEvents(begin, now);
            lastLiveUsageQuerySucceeded = true;
            lastLiveUsageQueryFailureClass = "";
            updateLastUsageEventAt(events);

            if (bootstrapPending) {
                // A reset starts a fresh tracker. Do not let the recent-event
                // cache make this bootstrap scan skip events it needs.
                tracker.reset();
                applyOrderedEvents(events);
                rebuildRecentSeen(events, now);
            } else {
                pruneRecentSeen(now);
                if (containsLateEvent(events)) {
                    rebuildTracker(now);
                } else {
                    applyUnseenEvents(events, now);
                }
            }

            // 后续轮询只需新事件，避免重复扫描整个回看窗口
            usageEventsCursor = Math.max(usageEventsCursor, now);
            bootstrapPending = tracker.getForegroundPackage().isEmpty();
        } catch (Exception exception) {
            recordLiveQueryFailure(exception);
        }
        return tracker.getForegroundPackage();
    }

    /** 重置游标和 tracker（屏幕关闭或重新交互时调用）。 */
    public void reset() {
        tracker.reset();
        bootstrapPending = true;
        recentSeenEvents.clear();
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

    public long getLiveUsageQueryFailureCount() {
        return liveUsageQueryFailureCount;
    }

    public String getLastLiveUsageQueryFailureClass() {
        return lastLiveUsageQueryFailureClass;
    }

    public boolean wasLastLiveUsageQuerySuccessful() {
        return lastLiveUsageQuerySucceeded;
    }

    /**
     * Reads an explicit historical window for gap reconciliation.  This path
     * reports query failures to the caller instead of silently falling back to
     * the live tracker.
     */
    GapQuery queryEventsForReconciliation(long begin, long end) {
        try {
            UsageEvents usageEvents = usageEventsQuery.query(begin, end);
            if (usageEvents == null) {
                return GapQuery.failure("USAGE_EVENTS_NULL");
            }
            List<ObservedEvent> observedEvents = readEventsFromCursor(usageEvents);
            updateLastUsageEventAt(observedEvents);
            List<TargetSessionGapReconciler.Event> result = new ArrayList<>();
            for (ObservedEvent event : observedEvents) {
                TargetSessionGapReconciler.Event mapped = toReconciliationEvent(event);
                if (mapped != null) result.add(mapped);
            }
            return GapQuery.success(result);
        } catch (Exception exception) {
            return GapQuery.failure(failureClass(exception));
        }
    }

    int recentEventCacheSizeForTest() {
        return recentSeenEvents.size();
    }

    static final class GapQuery {
        final boolean succeeded;
        final String failureClass;
        final List<TargetSessionGapReconciler.Event> events;

        private GapQuery(
                boolean succeeded,
                String failureClass,
                List<TargetSessionGapReconciler.Event> events
        ) {
            this.succeeded = succeeded;
            this.failureClass = failureClass;
            this.events = Collections.unmodifiableList(new ArrayList<>(events));
        }

        static GapQuery success(List<TargetSessionGapReconciler.Event> events) {
            return new GapQuery(true, "", events);
        }

        static GapQuery failure(String failureClass) {
            return new GapQuery(false, failureClass, Collections.emptyList());
        }
    }

    private List<ObservedEvent> readEvents(long begin, long end) {
        UsageEvents events = usageEventsQuery.query(begin, end);
        if (events == null) {
            throw new LiveQueryFailureException("USAGE_EVENTS_NULL");
        }
        return readEventsFromCursor(events);
    }

    private List<ObservedEvent> readEventsFromCursor(UsageEvents events) {
        List<ObservedEvent> result = new ArrayList<>();
        if (events == null) return result;

        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            result.add(new ObservedEvent(
                    event.getPackageName(),
                    event.getClassName(),
                    event.getEventType(),
                    event.getTimeStamp()
            ));
        }
        // List.sort is stable, so query order is retained for equal timestamps.
        result.sort(Comparator.comparingLong(observed -> observed.timestamp));
        return result;
    }

    private static TargetSessionGapReconciler.Event toReconciliationEvent(ObservedEvent event) {
        if (ForegroundAppTracker.isForegroundEvent(event.eventType)) {
            return TargetSessionGapReconciler.Event.foreground(
                    event.packageName,
                    event.className,
                    event.timestamp
            );
        }
        if (ForegroundAppTracker.isBackgroundEvent(event.eventType)) {
            return TargetSessionGapReconciler.Event.background(
                    event.packageName,
                    event.className,
                    event.timestamp
            );
        }
        if (event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
            return TargetSessionGapReconciler.Event.screenInteractive(event.timestamp);
        }
        if (event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
            return TargetSessionGapReconciler.Event.screenNonInteractive(event.timestamp);
        }
        if (event.eventType == UsageEvents.Event.KEYGUARD_SHOWN) {
            return TargetSessionGapReconciler.Event.keyguardShown(event.timestamp);
        }
        if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
            return TargetSessionGapReconciler.Event.keyguardHidden(event.timestamp);
        }
        return null;
    }

    private void updateLastUsageEventAt(List<ObservedEvent> events) {
        for (ObservedEvent event : events) {
            lastUsageEventAt = Math.max(lastUsageEventAt, event.timestamp);
        }
    }

    private boolean containsLateEvent(List<ObservedEvent> events) {
        long trackerLastEventAt = tracker.getLastEventAt();
        for (ObservedEvent event : events) {
            if (!affectsForegroundState(event)) continue;
            if (!recentSeenEvents.contains(event) && event.timestamp <= trackerLastEventAt) {
                return true;
            }
        }
        return false;
    }

    private void applyUnseenEvents(List<ObservedEvent> events, long now) {
        for (ObservedEvent event : events) {
            if (!affectsForegroundState(event)) continue;
            if (!recentSeenEvents.add(event)) continue;
            tracker.accept(
                    event.packageName,
                    event.className,
                    event.eventType,
                    event.timestamp
            );
        }
        pruneRecentSeen(now);
    }

    private void applyOrderedEvents(List<ObservedEvent> events) {
        for (ObservedEvent event : events) {
            if (!affectsForegroundState(event)) continue;
            tracker.accept(
                    event.packageName,
                    event.className,
                    event.eventType,
                    event.timestamp
            );
        }
    }

    private void rebuildTracker(long now) {
        List<ObservedEvent> history = readEvents(
                Math.max(0, now - initialLookbackMs),
                now
        );
        tracker.reset();
        updateLastUsageEventAt(history);
        applyOrderedEvents(history);
        rebuildRecentSeen(history, now);
    }

    private void recordLiveQueryFailure(Exception exception) {
        liveUsageQueryFailureCount++;
        lastLiveUsageQueryFailureClass = failureClass(exception);
        lastLiveUsageQuerySucceeded = false;
        tracker.reset();
        // A failed query may have happened after the cursor advanced.  Force
        // the next bootstrap to use the full initial lookback instead of the
        // short retry window, otherwise an already-foreground app can be
        // silently lost during recovery.
        usageEventsCursor = 0L;
        bootstrapPending = true;
        recentSeenEvents.clear();
    }

    private static String failureClass(Exception exception) {
        if (exception instanceof LiveQueryFailureException) {
            return ((LiveQueryFailureException) exception).category;
        }
        if (exception == null || exception.getClass() == null) return "UNKNOWN";
        String simpleName = exception.getClass().getSimpleName();
        return simpleName == null || simpleName.isEmpty() ? "UNKNOWN" : simpleName;
    }

    private static UsageEventsQuery systemUsageEventsQuery(Context context) {
        Context applicationContext = context.getApplicationContext();
        return (begin, end) -> {
            UsageStatsManager manager = (UsageStatsManager) applicationContext.getSystemService(
                    Context.USAGE_STATS_SERVICE
            );
            if (manager == null) {
                throw new LiveQueryFailureException("USAGE_STATS_MANAGER_UNAVAILABLE");
            }
            return manager.queryEvents(begin, end);
        };
    }

    private static final class LiveQueryFailureException extends RuntimeException {
        private final String category;

        private LiveQueryFailureException(String category) {
            super(category);
            this.category = category;
        }
    }

    private void rebuildRecentSeen(List<ObservedEvent> events, long now) {
        recentSeenEvents.clear();
        long cutoff = now - RECENT_EVENT_RETENTION_MS;
        for (ObservedEvent event : events) {
            if (affectsForegroundState(event)
                    && event.timestamp >= cutoff
                    && event.timestamp <= now) {
                recentSeenEvents.add(event);
            }
        }
        pruneRecentSeen(now);
    }

    private static boolean affectsForegroundState(ObservedEvent event) {
        return ForegroundAppTracker.isForegroundEvent(event.eventType)
                || ForegroundAppTracker.isBackgroundEvent(event.eventType);
    }

    private void pruneRecentSeen(long now) {
        long cutoff = now - RECENT_EVENT_RETENTION_MS;
        Iterator<ObservedEvent> iterator = recentSeenEvents.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().timestamp < cutoff) {
                iterator.remove();
            }
        }
        while (recentSeenEvents.size() > MAX_RECENT_EVENT_CACHE_SIZE) {
            iterator = recentSeenEvents.iterator();
            if (!iterator.hasNext()) return;
            iterator.next();
            iterator.remove();
        }
    }

    private static final class ObservedEvent {
        private final String packageName;
        private final String className;
        private final int eventType;
        private final long timestamp;

        private ObservedEvent(String packageName, String className, int eventType, long timestamp) {
            this.packageName = packageName;
            this.className = className;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ObservedEvent)) return false;
            ObservedEvent event = (ObservedEvent) other;
            return eventType == event.eventType
                    && timestamp == event.timestamp
                    && Objects.equals(packageName, event.packageName)
                    && Objects.equals(className, event.className);
        }

        @Override public int hashCode() {
            return Objects.hash(packageName, className, eventType, timestamp);
        }
    }
}
