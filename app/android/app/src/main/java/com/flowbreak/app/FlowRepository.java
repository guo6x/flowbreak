package com.flowbreak.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class FlowRepository {
    private static volatile FlowRepository instance;
    private final FlowDao dao;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private FlowRepository(Context context) {
        dao = FlowDatabase.get(context).flowDao();
        prefs = context.getApplicationContext()
                .getSharedPreferences("FlowBreakPrefs", Context.MODE_PRIVATE);
    }

    public static FlowRepository get(Context context) {
        if (instance == null) {
            synchronized (FlowRepository.class) {
                if (instance == null) instance = new FlowRepository(context);
            }
        }
        return instance;
    }

    public FlowDao dao() { return dao; }

    public void addUsage(String packageName, long seconds) {
        if (seconds <= 0 || packageName == null || packageName.isEmpty()) return;
        executor.execute(() -> dao.addUsage(today(), packageName, seconds));
    }

    public void log(String type, String packageName, String activity, long duration, String metadata) {
        executor.execute(() -> dao.insertEvent(new FlowEventEntity(
                System.currentTimeMillis(), type, packageName, activity, duration, metadata
        )));
    }

    public void recordBlock() {
        executor.execute(() -> dao.recordBlock(today()));
    }

    public void recordIntervention() {
        executor.execute(() -> dao.recordIntervention(today()));
    }

    /**
     * 幂等记录休息完成。通过 sessionId 防止同一次休息被重复计数。
     * 即使 NativeFlowPlugin 层的幂等检查被绕过（如 commit 成功但前端重试），
     * 此处仍能通过 SharedPreferences 中的 sessionId 比对拦截重复写入。
     */
    public void recordRestWithIdempotency(long sessionId, long durationSeconds) {
        if (sessionId <= 0L) {
            executor.execute(() -> dao.recordRest(today(), durationSeconds));
            return;
        }
        long lastRecorded = prefs.getLong("repoLastRestSessionId", 0L);
        if (sessionId == lastRecorded) return; // 幂等：已记录过同一次休息
        prefs.edit().putLong("repoLastRestSessionId", sessionId).apply();
        executor.execute(() -> dao.recordRest(today(), durationSeconds));
    }

    public void recordPostRestReturn(long sessionId) {
        executor.execute(() -> dao.recordPostRestReturn(
                today(),
                new FlowEventEntity(
                        System.currentTimeMillis(), "post_rest_return", "", "", 0,
                        "{\"sessionId\":" + Math.max(0L, sessionId) + "}"
                )
        ));
    }

    public void recordPullbackOutcome(boolean success, long targetSeconds, long sessionId) {
        executor.execute(() -> dao.recordPullbackOutcome(
                today(),
                success,
                Math.max(0L, targetSeconds),
                new FlowEventEntity(
                        System.currentTimeMillis(),
                        success ? "pullback_success" : "pullback_unresolved",
                        "",
                        "",
                        Math.max(0L, targetSeconds),
                        "{\"sessionId\":" + Math.max(0L, sessionId) + "}"
                )
        ));
    }

    public void clearAllBlocking() throws Exception {
        Future<?> clear = executor.submit(dao::clearAll);
        clear.get();
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}
