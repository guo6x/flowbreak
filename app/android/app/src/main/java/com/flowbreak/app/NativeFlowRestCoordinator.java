package com.flowbreak.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.getcapacitor.JSObject;

/**
 * 负责休息完成和解锁逻辑的高风险协调器。
 *
 * 必须保持原有操作顺序：
 * 1. 先检查已经成功提交的重试（GRACE + sessionId 匹配 completedRestSessionId）
 * 2. 再验证 RESTING/sessionId/休息时长
 * 3. 使用 sessionId 防止 Room 重复写入（dbRestRecordedSessionId）
 * 4. 更新事件和 Progress
 * 5. 提交 SharedPreferences 的 GRACE 状态（commit）
 * 6. commit 失败必须报错
 * 7. Service 刷新失败不能让已经成功的完成操作回滚
 * 8. 返回原有 graceUntil、points、streak、achievement
 *
 * 不持有 Activity，不调用 PluginCall。
 * Service 刷新由调用方在 commit 成功后执行（允许失败）。
 */
public final class NativeFlowRestCoordinator {
    private static final long UNLOCK_MS = 10 * 60_000L;

    private final Context context;

    public NativeFlowRestCoordinator(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 规范化 activity 值。eye/stretch/breathe 保持原值，非法值回退到 breathe。
     */
    public static String normalizeActivity(String requested) {
        if ("eye".equals(requested) || "stretch".equals(requested)
                || "breathe".equals(requested)) {
            return requested;
        }
        return "breathe";
    }

    /**
     * 完成休息并解锁。返回包含 graceUntil、points、streak、achievement 的 JSObject。
     *
     * 失败场景：
     * - 休息尚未完成：抛出 RestPendingException（前端文案"休息尚未完成，请继续完成本次引导。"）
     * - 保存休息记录失败：抛出 Exception（前端文案"保存休息记录失败"）
     *
     * Service 刷新由调用方执行，本方法只返回是否需要刷新的标记。
     */
    public Result complete(SharedPreferences prefs, String requestedActivity) {
        String activity = normalizeActivity(requestedActivity);
        FlowDao dao = FlowDatabase.get(context).flowDao();
        long now = System.currentTimeMillis();
        long sessionId = prefs.getLong(
                FlowForegroundService.PREF_REST_SESSION_ID, 0L
        );
        String state = prefs.getString(
                "blockState", BlockStateMachine.State.IDLE.name()
        );

        // A retry after a successful native commit must be idempotent:
        // return the original grace window instead of awarding points twice.
        if (BlockStateMachine.State.GRACE.name().equals(state)
                && sessionId > 0L
                && sessionId == prefs.getLong(
                        FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID, -1L
                )) {
            ProgressEntity existing = dao.getProgress();
            JSObject result = new JSObject();
            result.put("graceUntil", prefs.getLong(
                    FlowForegroundService.PREF_COMPLETED_REST_GRACE_UNTIL,
                    prefs.getLong("graceUntil", 0L)
            ));
            result.put("points", existing == null ? 0 : existing.points);
            result.put("streak", existing == null ? 0 : existing.streak);
            result.put("achievement", "");
            return new Result(result, false);
        }

        long startedAt = prefs.getLong(
                FlowForegroundService.PREF_REST_STARTED_AT, 0L
        );
        long requiredMs = prefs.getLong(
                FlowForegroundService.PREF_REST_REQUIRED_MS, 0L
        );
        if (!BlockStateMachine.State.RESTING.name().equals(state)
                || sessionId <= 0L
                || !RestSessionValidator.isComplete(startedAt, requiredMs, now)) {
            throw new RestPendingException();
        }

        long duration = requiredMs / 1000L;
        // 幂等保护：用 sessionId 标记 DB 写入是否已完成。
        // 如果 prefs commit 失败导致前端重试，此处可拦截重复的 DB 写入。
        long dbRecorded = prefs.getLong("dbRestRecordedSessionId", 0L);
        ProgressEntity progress;
        boolean firstRest;
        if (sessionId != dbRecorded) {
            dao.insertEvent(new FlowEventEntity(
                    now, "rest_complete", "", activity, duration, ""
            ));
            FlowRepository.get(context).recordRestWithIdempotency(sessionId, duration);
            progress = dao.getProgress();
            if (progress == null) progress = new ProgressEntity(0, 0, "", "[]");
            String today = NativeFlowStatisticsService.day(now);
            String yesterday = NativeFlowStatisticsService.day(now - 86_400_000L);
            if (!today.equals(progress.lastRestDay)) {
                progress.streak = yesterday.equals(progress.lastRestDay)
                        ? progress.streak + 1 : 1;
                progress.lastRestDay = today;
            }
            progress.points += 10;
            firstRest = dao.countEventsSince("rest_complete", 0) == 1;
            if (firstRest) {
                progress.points += 10;
                progress.achievementsJson = "[\"health_guardian\"]";
            }
            dao.saveProgress(progress);
            prefs.edit().putLong("dbRestRecordedSessionId", sessionId).commit();
        } else {
            // 重试场景：DB 已写入过，从 DB 读取当前值用于响应
            progress = dao.getProgress();
            if (progress == null) progress = new ProgressEntity(0, 0, "", "[]");
            firstRest = false;
        }
        long graceUntil = now + UNLOCK_MS;
        boolean committed = prefs.edit()
                .putString("blockState", BlockStateMachine.State.GRACE.name())
                .putLong("sessionMs", 0)
                .putLong("graceUntil", graceUntil)
                .putString("blockedPackage", "")
                .remove(FlowForegroundService.PREF_REST_STARTED_AT)
                .remove(FlowForegroundService.PREF_REST_REQUIRED_MS)
                .putLong(FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID, sessionId)
                .putLong(FlowForegroundService.PREF_COMPLETED_REST_GRACE_UNTIL, graceUntil)
                .putLong(FlowForegroundService.PREF_PULLBACK_SESSION_ID, sessionId)
                .putLong(FlowForegroundService.PREF_PULLBACK_STARTED_AT, now)
                .putLong(FlowForegroundService.PREF_PULLBACK_TARGET_MS, 0L)
                .putLong(FlowForegroundService.PREF_PULLBACK_LEFT_AT, 0L)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_SAW_TARGET, false)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_RETURN_REPORTED, false)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_RESOLVED, false)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_SUCCESS, false)
                .commit();
        if (!committed) throw new IllegalStateException("无法保存解锁状态");
        JSObject result = new JSObject();
        result.put("graceUntil", graceUntil);
        result.put("points", progress.points);
        result.put("streak", progress.streak);
        result.put("achievement", firstRest ? "health_guardian" : "");
        return new Result(result, true);
    }

    /**
     * 紧急解锁。返回 allowed/graceUntil/remainingToday 字段。
     * allowed=true 时 graceUntil = now + 5min，需要调用方发送 ACTION_EMERGENCY。
     */
    public JSObject requestEmergencyUnlock(SharedPreferences prefs, FlowRepository repository) {
        boolean isBlocked = BlockStateMachine.State.BLOCKED.name().equals(
                prefs.getString("blockState", BlockStateMachine.State.IDLE.name())
        );
        boolean allowed = isBlocked && EmergencyUnlockManager.tryUnlock(context);
        long graceUntil = 0;
        if (allowed) {
            graceUntil = System.currentTimeMillis() + 5 * 60_000L;
            repository.log(
                    "emergency_unlock", FlowForegroundService.getBlockedPackage(), "", 300, ""
            );
        }
        JSObject result = new JSObject();
        result.put("allowed", allowed);
        result.put("graceUntil", graceUntil);
        result.put("remainingToday", EmergencyUnlockManager.remainingToday(context));
        return result;
    }

    /** 休息尚未完成时抛出。调用方映射到原中文文案。 */
    public static final class RestPendingException extends RuntimeException {
        public RestPendingException() { super("休息尚未完成"); }
    }

    /** complete() 返回值。needServiceRefresh 表示是否需要调用方发送 ACTION_COMPLETE_REST。 */
    public static final class Result {
        public final JSObject response;
        public final boolean needServiceRefresh;

        public Result(JSObject response, boolean needServiceRefresh) {
            this.response = response;
            this.needServiceRefresh = needServiceRefresh;
        }
    }
}
