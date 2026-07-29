package com.flowbreak.app;

import android.content.Context;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 负责使用时长、仪表盘、历史统计、效果验证、每日反馈等数据查询和转换。
 *
 * 保持原有：
 * - 天数限制 1~90
 * - rescuedSeconds 兼容字段
 * - unlockSeconds 字段
 * - legacy screen time fallback
 * - reflection 数值映射（helped=3, neutral=2, not_helped=1）
 * - 所有统计字段名称
 *
 * 日期键格式为 yyyy-MM-dd（US locale）。
 */
public final class NativeFlowStatisticsService {
    private final Context context;

    public NativeFlowStatisticsService(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 当天使用时长（秒），取 daily_usage 总和与 legacyScreenSeconds 的较大值。 */
    public JSObject usageStats() {
        FlowDao dao = FlowDatabase.get(context).flowDao();
        String today = today();
        DailySummaryEntity summary = dao.summaryForDay(today);
        long total = Math.max(
                dao.totalUsageForDay(today),
                summary == null ? 0L : summary.legacyScreenSeconds
        );
        JSObject result = new JSObject();
        result.put("screenTimeSeconds", total);
        return result;
    }

    /** 当天应用使用时长列表。 */
    public JSObject appUsageList() {
        JSArray apps = new JSArray();
        for (DailyUsageEntity entry : FlowDatabase.get(context).flowDao()
                .usageForDay(today())) {
            JSObject item = new JSObject();
            item.put("packageName", entry.packageName);
            item.put("totalTimeSeconds", entry.seconds);
            apps.put(item);
        }
        JSObject result = new JSObject();
        result.put("apps", apps);
        return result;
    }

    /** 仪表盘汇总。保持 rescuedSeconds 兼容字段。 */
    public JSObject dashboardSummary() {
        FlowDao dao = FlowDatabase.get(context).flowDao();
        DailySummaryEntity summary = dao.summaryForDay(today());
        JSObject result = new JSObject();
        result.put("blockCount", summary == null ? 0 : summary.blockCount);
        result.put("restCount", summary == null ? 0 : summary.restCount);
        result.put("pullbackOutcomeCount", summary == null ? 0 : summary.pullbackOutcomeCount);
        result.put("successfulPullbackCount", summary == null ? 0 : summary.successfulPullbackCount);
        result.put("postRestReturnCount", summary == null ? 0 : summary.postRestReturnCount);
        result.put("postRestTargetSeconds", summary == null ? 0L : summary.postRestTargetSeconds);
        result.put("reflectionValue", summary == null ? 0 : summary.reflectionValue);
        long unlockSeconds = summary == null ? 0L : summary.graceSeconds;
        // Kept for older WebView bundles while the UI switches to the
        // more accurate "rest-earned access window" wording.
        result.put("rescuedSeconds", unlockSeconds);
        result.put("unlockSeconds", unlockSeconds);
        ProgressEntity progress = dao.getProgress();
        result.put("points", progress == null ? 0 : progress.points);
        result.put("streak", progress == null ? 0 : progress.streak);
        return result;
    }

    /**
     * 最近 N 天历史统计。days 限制在 1~90。
     * 无数据日期补零，保持原有字段名和顺序。
     */
    public JSObject statisticsHistory(int days) {
        int bounded = boundDays(days);
        FlowDao dao = FlowDatabase.get(context).flowDao();
        String firstDay = dayDaysAgo(bounded - 1);
        Map<String, Long> usageByDay = new HashMap<>();
        for (DailyUsageEntity row : dao.usageSince(firstDay)) {
            usageByDay.put(
                    row.date,
                    usageByDay.getOrDefault(row.date, 0L) + Math.max(0L, row.seconds)
            );
        }
        Map<String, DailySummaryEntity> summariesByDay = new HashMap<>();
        for (DailySummaryEntity row : dao.summariesSince(firstDay)) {
            summariesByDay.put(row.date, row);
        }
        JSArray history = new JSArray();
        for (int daysAgo = bounded - 1; daysAgo >= 0; daysAgo--) {
            String date = dayDaysAgo(daysAgo);
            DailySummaryEntity summary = summariesByDay.get(date);
            JSObject item = new JSObject();
            item.put("date", date);
            item.put("screenTimeSeconds", Math.max(
                    usageByDay.getOrDefault(date, 0L),
                    summary == null ? 0L : summary.legacyScreenSeconds
            ));
            item.put("restCount", summary == null ? 0 : summary.restCount);
            item.put("interventionCount", summary == null ? 0 : summary.interventionCount);
            item.put("blockCount", summary == null ? 0 : summary.blockCount);
            item.put("unlockSeconds", summary == null ? 0L : summary.graceSeconds);
            item.put("pullbackOutcomeCount", summary == null ? 0 : summary.pullbackOutcomeCount);
            item.put("successfulPullbackCount", summary == null ? 0 : summary.successfulPullbackCount);
            item.put("postRestReturnCount", summary == null ? 0 : summary.postRestReturnCount);
            item.put("postRestTargetSeconds", summary == null ? 0L : summary.postRestTargetSeconds);
            item.put("reflectionValue", summary == null ? 0 : summary.reflectionValue);
            history.put(item);
        }
        JSObject result = new JSObject();
        result.put("days", history);
        return result;
    }

    /**
     * 效果验证汇总。days 限制在 1~90。
     * reflectionValue: 3=helped, 2=neutral, 1=not_helped。
     */
    public JSObject validationSummary(int days) {
        int bounded = boundDays(days);
        int rests = 0;
        int outcomes = 0;
        int successes = 0;
        int returns = 0;
        long targetSeconds = 0L;
        int helped = 0;
        int neutral = 0;
        int notHelped = 0;
        for (DailySummaryEntity row : FlowDatabase.get(context).flowDao()
                .summariesSince(dayDaysAgo(bounded - 1))) {
            rests += row.restCount;
            outcomes += row.pullbackOutcomeCount;
            successes += row.successfulPullbackCount;
            returns += row.postRestReturnCount;
            targetSeconds += row.postRestTargetSeconds;
            if (row.reflectionValue == 3) helped++;
            else if (row.reflectionValue == 2) neutral++;
            else if (row.reflectionValue == 1) notHelped++;
        }
        JSObject result = new JSObject();
        result.put("days", bounded);
        result.put("restCount", rests);
        result.put("outcomeCount", outcomes);
        result.put("successfulPullbackCount", successes);
        result.put("postRestReturnCount", returns);
        result.put("postRestTargetSeconds", targetSeconds);
        result.put("helpedDays", helped);
        result.put("neutralDays", neutral);
        result.put("notHelpedDays", notHelped);
        return result;
    }

    /**
     * 保存每日反馈。value 必须是 helped/neutral/not_helped 之一。
     * 返回 null 表示输入无效，由调用方 reject。
     */
    public JSObject saveDailyReflection(String value) {
        int numeric = reflectionValue(value);
        if (numeric == 0) return null;
        long now = System.currentTimeMillis();
        FlowDatabase.get(context).flowDao().saveReflection(
                day(now), numeric, now,
                new FlowEventEntity(now, "daily_reflection", "", "", 0,
                        "{\"value\":" + numeric + "}")
        );
        return new JSObject();
    }

    /**
     * 把字符串反馈值映射为数值。
     * helped=3, neutral=2, not_helped=1, 其他=0（无效）。
     */
    public static int reflectionValue(String value) {
        if ("helped".equals(value)) return 3;
        if ("neutral".equals(value)) return 2;
        if ("not_helped".equals(value)) return 1;
        return 0;
    }

    /** 把 days 限制在 1~90。 */
    public static int boundDays(int days) {
        return Math.max(1, Math.min(90, days));
    }

    /** 格式化时间戳为 yyyy-MM-dd。 */
    public static String day(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(timestamp));
    }

    /** 计算 N 天前的日期键。 */
    public static String dayDaysAgo(int daysAgo) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -Math.max(0, daysAgo));
        return day(calendar.getTimeInMillis());
    }

    private String today() {
        return day(System.currentTimeMillis());
    }
}
