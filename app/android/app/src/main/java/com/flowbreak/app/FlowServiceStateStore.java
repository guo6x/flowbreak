package com.flowbreak.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Set;

/**
 * FlowForegroundService 的配置加载、状态持久化、心跳节流和数据清除状态。
 *
 * 从 FlowForegroundService 抽取，保持原有 SharedPreferences 语义：
 *
 * - loadConfig: 读取 targetApps/limitMinutes/monitoringEnabled/restDuration/allowEmergencyUnlock
 * - loadMachineSnapshot: 读取 machine 构造参数和 RESTING 恢复所需字段
 * - persist: 一次 Editor 写入 machine 字段 + pullback 字段，使用 apply()
 * - clearActiveRestSession: 移除 restStartedAt/restRequiredMs/restSessionId
 * - clearPullbackSession: 移除全部 pullback 字段
 * - writeHeartbeatIfDue: 30 秒节流，key=serviceHeartbeatAt，apply()
 * - isDataErasing: 读取 dataErasing
 *
 * 不持有 Service 或 Activity，仅持有 application Context。
 */
public final class FlowServiceStateStore {
    public static final String PREFS = "FlowBreakPrefs";
    public static final long GRACE_MS = 10 * 60_000L;
    public static final long EMERGENCY_GRACE_MS = 5 * 60_000L;

    /** 应用配置快照。 */
    public static final class Config {
        public final Set<String> targetApps;
        public final int limitMinutes;
        public final boolean monitoringEnabled;
        public final int restDurationSeconds;
        public final boolean allowEmergencyUnlock;

        public Config(
                Set<String> targetApps,
                int limitMinutes,
                boolean monitoringEnabled,
                int restDurationSeconds,
                boolean allowEmergencyUnlock
        ) {
            this.targetApps = targetApps;
            this.limitMinutes = limitMinutes;
            this.monitoringEnabled = monitoringEnabled;
            this.restDurationSeconds = restDurationSeconds;
            this.allowEmergencyUnlock = allowEmergencyUnlock;
        }
    }

    /** machine 构造参数和 RESTING 恢复所需字段快照。 */
    public static final class MachineSnapshot {
        public final BlockStateMachine.State persistedState;
        public final long sessionMs;
        public final long graceUntil;
        public final long leftTargetsAt;
        public final String blockedPackage;
        public final long restStartedAt;
        public final long restRequiredMs;
        public final long restSessionId;

        public MachineSnapshot(
                BlockStateMachine.State persistedState,
                long sessionMs,
                long graceUntil,
                long leftTargetsAt,
                String blockedPackage,
                long restStartedAt,
                long restRequiredMs,
                long restSessionId
        ) {
            this.persistedState = persistedState;
            this.sessionMs = sessionMs;
            this.graceUntil = graceUntil;
            this.leftTargetsAt = leftTargetsAt;
            this.blockedPackage = blockedPackage;
            this.restStartedAt = restStartedAt;
            this.restRequiredMs = restRequiredMs;
            this.restSessionId = restSessionId;
        }
    }

    private final SharedPreferences prefs;
    private long lastHeartbeatWrittenAt;

    public FlowServiceStateStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 直接暴露 prefs，供 Service 中仍需直接读取的少量字段使用。 */
    public SharedPreferences preferences() {
        return prefs;
    }

    /** 读取应用配置。limitMinutes 最少 1。 */
    public Config loadConfig() {
        return new Config(
                PreferenceUtils.getMigratedTargetApps(prefs),
                Math.max(1, prefs.getInt("limitMinutes", 25)),
                prefs.getBoolean("monitoringEnabled", true),
                prefs.getInt("restDuration", 180),
                prefs.getBoolean("allowEmergencyUnlock", true)
        );
    }

    /**
     * 读取 machine 构造参数和 RESTING 恢复字段。
     * 非法 blockState 值回退为 IDLE。
     */
    public MachineSnapshot loadMachineSnapshot() {
        BlockStateMachine.State state;
        try {
            state = BlockStateMachine.State.valueOf(
                    prefs.getString("blockState", BlockStateMachine.State.IDLE.name())
            );
        } catch (Exception ignored) {
            state = BlockStateMachine.State.IDLE;
        }
        return new MachineSnapshot(
                state,
                prefs.getLong("sessionMs", 0L),
                prefs.getLong("graceUntil", 0L),
                prefs.getLong("leftTargetsAt", 0L),
                prefs.getString("blockedPackage", ""),
                prefs.getLong(FlowForegroundService.PREF_REST_STARTED_AT, 0L),
                prefs.getLong(FlowForegroundService.PREF_REST_REQUIRED_MS, 0L),
                prefs.getLong(FlowForegroundService.PREF_REST_SESSION_ID, 0L)
        );
    }

    /**
     * 持久化 machine 状态和 pullback 快照。
     * 所有字段写入同一个 Editor，使用 apply()。
     * 与原 persistState() 行为一致。
     */
    public void persist(
            BlockStateMachine machine,
            PullbackSessionCoordinator.Snapshot pullback
    ) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString("blockState", machine.getState().name())
                .putLong("sessionMs", machine.getSessionMs())
                .putLong("graceUntil", machine.getGraceUntil())
                .putLong("leftTargetsAt", machine.getLeftTargetsAt())
                .putString("blockedPackage", machine.getBlockedPackage());
        if (pullback != null && pullback.present) {
            editor.putLong(FlowForegroundService.PREF_PULLBACK_SESSION_ID, pullback.sessionId)
                    .putLong(FlowForegroundService.PREF_PULLBACK_STARTED_AT, pullback.startedAt)
                    .putLong(FlowForegroundService.PREF_PULLBACK_TARGET_MS, pullback.targetMs)
                    .putLong(FlowForegroundService.PREF_PULLBACK_LEFT_AT, pullback.leftTargetsAt)
                    .putBoolean(FlowForegroundService.PREF_PULLBACK_SAW_TARGET, pullback.sawTarget)
                    .putBoolean(FlowForegroundService.PREF_PULLBACK_RETURN_REPORTED, pullback.returnReported)
                    .putBoolean(FlowForegroundService.PREF_PULLBACK_RESOLVED, pullback.resolved)
                    .putBoolean(FlowForegroundService.PREF_PULLBACK_SUCCESS, pullback.success);
        }
        editor.apply();
    }

    /**
     * 自动完成 RESTING -> GRACE 的同步持久化。
     * 使用 commit()，关键状态在服务被杀恢复时不能丢失。
     * 返回 commit() 的结果，调用方可记录但不改变对外行为。
     */
    public boolean persistAutoCompletedRest(long graceUntil, long restSessionId) {
        return prefs.edit()
                .putString("blockState", BlockStateMachine.State.GRACE.name())
                .putLong("sessionMs", 0L)
                .putLong("graceUntil", graceUntil)
                .putString("blockedPackage", "")
                .remove(FlowForegroundService.PREF_REST_STARTED_AT)
                .remove(FlowForegroundService.PREF_REST_REQUIRED_MS)
                .putLong(FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID, restSessionId)
                .putLong(FlowForegroundService.PREF_COMPLETED_REST_GRACE_UNTIL, graceUntil)
                .commit();
    }

    /** 写入 beginRest 的 session 字段，使用 apply()。 */
    public void persistBeginRest(long startedAt, long requiredMs, long sessionId) {
        prefs.edit()
                .putLong(FlowForegroundService.PREF_REST_STARTED_AT, startedAt)
                .putLong(FlowForegroundService.PREF_REST_REQUIRED_MS, requiredMs)
                .putLong(FlowForegroundService.PREF_REST_SESSION_ID, sessionId)
                .apply();
    }

    /** 移除 restStartedAt/restRequiredMs/restSessionId，使用 apply()。 */
    public void clearActiveRestSession() {
        prefs.edit()
                .remove(FlowForegroundService.PREF_REST_STARTED_AT)
                .remove(FlowForegroundService.PREF_REST_REQUIRED_MS)
                .remove(FlowForegroundService.PREF_REST_SESSION_ID)
                .apply();
    }

    /** 移除全部 pullback 字段，使用 apply()。 */
    public void clearPullbackSession() {
        prefs.edit()
                .remove(FlowForegroundService.PREF_PULLBACK_SESSION_ID)
                .remove(FlowForegroundService.PREF_PULLBACK_STARTED_AT)
                .remove(FlowForegroundService.PREF_PULLBACK_TARGET_MS)
                .remove(FlowForegroundService.PREF_PULLBACK_LEFT_AT)
                .remove(FlowForegroundService.PREF_PULLBACK_SAW_TARGET)
                .remove(FlowForegroundService.PREF_PULLBACK_RETURN_REPORTED)
                .remove(FlowForegroundService.PREF_PULLBACK_RESOLVED)
                .remove(FlowForegroundService.PREF_PULLBACK_SUCCESS)
                .apply();
    }

    /** 写入 monitoringEnabled，使用 apply()。 */
    public void setMonitoringEnabled(boolean enabled) {
        prefs.edit().putBoolean("monitoringEnabled", enabled).apply();
    }

    /**
     * 节流写入心跳。key=serviceHeartbeatAt，apply()。
     * 使用 HeartbeatGate 纯逻辑判断。
     */
    public boolean writeHeartbeatIfDue(long now) {
        if (!HeartbeatGate.shouldWrite(lastHeartbeatWrittenAt, now)) return false;
        lastHeartbeatWrittenAt = now;
        prefs.edit().putLong("serviceHeartbeatAt", now).apply();
        return true;
    }

    /** 数据清除期间标志。 */
    public boolean isDataErasing() {
        return prefs.getBoolean("dataErasing", false);
    }

    /** 读取 wechatInVideoChannel 偏好（目标判定器使用）。 */
    public boolean isWechatInVideoChannel() {
        return prefs.getBoolean("wechatInVideoChannel", false);
    }

    /** 读取 wechatInVideoChannelAt 偏好（目标判定器使用）。 */
    public long wechatInVideoChannelAt() {
        return prefs.getLong("wechatInVideoChannelAt", 0L);
    }

    /** 读取 limitMinutes，最少 1。 */
    public int limitMinutes() {
        return Math.max(1, prefs.getInt("limitMinutes", 25));
    }

    /** 读取 restDuration 秒数。 */
    public int restDurationSeconds() {
        return prefs.getInt("restDuration", 180);
    }

    /** 读取 allowEmergencyUnlock。 */
    public boolean allowEmergencyUnlock() {
        return prefs.getBoolean("allowEmergencyUnlock", true);
    }
}
