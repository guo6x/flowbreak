package com.flowbreak.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * FlowForegroundService 的配置加载、状态持久化、心跳节流和数据清除状态。
 *
 * 从 FlowForegroundService 抽取，保持原有 SharedPreferences 语义：
 *
 * - loadConfig: 读取 targetApps/limitMinutes/monitoringEnabled/restDuration/allowEmergencyUnlock
 * - loadMachineSnapshot: 读取 machine 构造参数和绑定到 restSessionId 的 RESTING 目标快照
 * - persist: 一次 Editor 写入 machine 字段 + pullback 字段，使用 apply()
 * - commitRestCancellation: 原子持久化取消后的 machine/checkpoint 并移除 REST session
 * - clearActiveRestSession: 移除 REST 会话及其 session-bound target snapshot
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

    /** Minimal persisted monitor checkpoint used to reconcile long callbacks. */
    public static final class Checkpoint {
        public final long wallMs;
        public final long elapsedMs;
        public final boolean targetActive;
        public final boolean interactionAvailable;
        public final String foregroundPackage;

        public Checkpoint(
                long wallMs,
                long elapsedMs,
                boolean targetActive,
                boolean interactionAvailable,
                String foregroundPackage
        ) {
            this.wallMs = wallMs;
            this.elapsedMs = elapsedMs;
            this.targetActive = targetActive;
            this.interactionAvailable = interactionAvailable;
            this.foregroundPackage = foregroundPackage == null ? "" : foregroundPackage;
        }
    }

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

    /** machine 构造参数和 RESTING 恢复所需字段；restTargetApps 为 null 表示没有有效快照。 */
    public static final class MachineSnapshot {
        public final BlockStateMachine.State persistedState;
        public final long sessionMs;
        public final long graceUntil;
        public final long leftTargetsAt;
        public final String blockedPackage;
        public final long restStartedAt;
        public final long restRequiredMs;
        public final long restSessionId;
        public final boolean restStartTargetActive;
        public final boolean restStartInteractionAvailable;
        public final String restStartForegroundPackage;
        /** Null means this persisted REST session predates or lacks its target snapshot. */
        public final Set<String> restTargetApps;
        public final long checkpointWallMs;
        public final long checkpointElapsedMs;
        public final boolean checkpointTargetActive;
        public final boolean checkpointInteractionAvailable;
        public final String checkpointForegroundPackage;

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
            this(
                    persistedState,
                    sessionMs,
                    graceUntil,
                    leftTargetsAt,
                    blockedPackage,
                    restStartedAt,
                    restRequiredMs,
                    restSessionId,
                    false,
                    false,
                    "",
                    0L,
                    0L,
                    false,
                    false,
                    ""
            );
        }

        public MachineSnapshot(
                BlockStateMachine.State persistedState,
                long sessionMs,
                long graceUntil,
                long leftTargetsAt,
                String blockedPackage,
                long restStartedAt,
                long restRequiredMs,
                long restSessionId,
                long checkpointWallMs,
                long checkpointElapsedMs,
                boolean checkpointTargetActive,
                boolean checkpointInteractionAvailable,
                String checkpointForegroundPackage
        ) {
            this(
                    persistedState,
                    sessionMs,
                    graceUntil,
                    leftTargetsAt,
                    blockedPackage,
                    restStartedAt,
                    restRequiredMs,
                    restSessionId,
                    false,
                    false,
                    "",
                    checkpointWallMs,
                    checkpointElapsedMs,
                    checkpointTargetActive,
                    checkpointInteractionAvailable,
                    checkpointForegroundPackage
            );
        }

        public MachineSnapshot(
                BlockStateMachine.State persistedState,
                long sessionMs,
                long graceUntil,
                long leftTargetsAt,
                String blockedPackage,
                long restStartedAt,
                long restRequiredMs,
                long restSessionId,
                boolean restStartTargetActive,
                boolean restStartInteractionAvailable,
                String restStartForegroundPackage,
                long checkpointWallMs,
                long checkpointElapsedMs,
                boolean checkpointTargetActive,
                boolean checkpointInteractionAvailable,
                String checkpointForegroundPackage
        ) {
            this(
                    persistedState,
                    sessionMs,
                    graceUntil,
                    leftTargetsAt,
                    blockedPackage,
                    restStartedAt,
                    restRequiredMs,
                    restSessionId,
                    restStartTargetActive,
                    restStartInteractionAvailable,
                    restStartForegroundPackage,
                    checkpointWallMs,
                    checkpointElapsedMs,
                    checkpointTargetActive,
                    checkpointInteractionAvailable,
                    checkpointForegroundPackage,
                    null
            );
        }

        public MachineSnapshot(
                BlockStateMachine.State persistedState,
                long sessionMs,
                long graceUntil,
                long leftTargetsAt,
                String blockedPackage,
                long restStartedAt,
                long restRequiredMs,
                long restSessionId,
                boolean restStartTargetActive,
                boolean restStartInteractionAvailable,
                String restStartForegroundPackage,
                long checkpointWallMs,
                long checkpointElapsedMs,
                boolean checkpointTargetActive,
                boolean checkpointInteractionAvailable,
                String checkpointForegroundPackage,
                Set<String> restTargetApps
        ) {
            this.persistedState = persistedState;
            this.sessionMs = sessionMs;
            this.graceUntil = graceUntil;
            this.leftTargetsAt = leftTargetsAt;
            this.blockedPackage = blockedPackage;
            this.restStartedAt = restStartedAt;
            this.restRequiredMs = restRequiredMs;
            this.restSessionId = restSessionId;
            this.restStartTargetActive = restStartTargetActive;
            this.restStartInteractionAvailable = restStartInteractionAvailable;
            this.restStartForegroundPackage = restStartForegroundPackage == null
                    ? ""
                    : restStartForegroundPackage;
            this.restTargetApps = restTargetApps == null
                    ? null
                    : Collections.unmodifiableSet(new HashSet<>(restTargetApps));
            this.checkpointWallMs = checkpointWallMs;
            this.checkpointElapsedMs = checkpointElapsedMs;
            this.checkpointTargetActive = checkpointTargetActive;
            this.checkpointInteractionAvailable = checkpointInteractionAvailable;
            this.checkpointForegroundPackage = checkpointForegroundPackage == null
                    ? ""
                    : checkpointForegroundPackage;
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
        long restSessionId = prefs.getLong(FlowForegroundService.PREF_REST_SESSION_ID, 0L);
        Set<String> restTargetApps = null;
        if (restSessionId > 0L
                && prefs.contains(FlowForegroundService.PREF_REST_TARGET_APPS)
                && prefs.getLong(FlowForegroundService.PREF_REST_TARGET_SESSION_ID, 0L)
                        == restSessionId) {
            Set<String> storedTargets = prefs.getStringSet(
                    FlowForegroundService.PREF_REST_TARGET_APPS,
                    Collections.emptySet()
            );
            if (storedTargets != null) restTargetApps = new HashSet<>(storedTargets);
        }
        return new MachineSnapshot(
                state,
                prefs.getLong("sessionMs", 0L),
                prefs.getLong("graceUntil", 0L),
                prefs.getLong("leftTargetsAt", 0L),
                prefs.getString("blockedPackage", ""),
                prefs.getLong(FlowForegroundService.PREF_REST_STARTED_AT, 0L),
                prefs.getLong(FlowForegroundService.PREF_REST_REQUIRED_MS, 0L),
                restSessionId,
                prefs.getBoolean(FlowForegroundService.PREF_REST_START_TARGET_ACTIVE, false),
                prefs.getBoolean(FlowForegroundService.PREF_REST_START_INTERACTION_AVAILABLE, false),
                prefs.getString(FlowForegroundService.PREF_REST_START_FOREGROUND_PACKAGE, ""),
                prefs.getLong("checkpointWallMs", 0L),
                prefs.getLong("checkpointElapsedMs", 0L),
                prefs.getBoolean("checkpointTargetActive", false),
                prefs.getBoolean("checkpointInteractionAvailable", false),
                prefs.getString("checkpointForegroundPackage", ""),
                restTargetApps
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
        persist(machine, pullback, null);
    }

    /** Persists machine, pullback and the latest monitor checkpoint together. */
    public void persist(
            BlockStateMachine machine,
            PullbackSessionCoordinator.Snapshot pullback,
            Checkpoint checkpoint
    ) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString("blockState", machine.getState().name())
                .putLong("sessionMs", machine.getSessionMs())
                .putLong("graceUntil", machine.getGraceUntil())
                .putLong("leftTargetsAt", machine.getLeftTargetsAt())
                .putString("blockedPackage", machine.getBlockedPackage());
        if (checkpoint != null) {
            editor.putLong("checkpointWallMs", checkpoint.wallMs)
                    .putLong("checkpointElapsedMs", checkpoint.elapsedMs)
                    .putBoolean("checkpointTargetActive", checkpoint.targetActive)
                    .putBoolean("checkpointInteractionAvailable", checkpoint.interactionAvailable)
                    .putString("checkpointForegroundPackage", checkpoint.foregroundPackage);
        }
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
        if (restSessionId <= 0L
                || !BlockStateMachine.State.RESTING.name().equals(
                        prefs.getString("blockState", BlockStateMachine.State.IDLE.name()))
                || prefs.getLong(FlowForegroundService.PREF_REST_SESSION_ID, 0L)
                        != restSessionId
                || !prefs.contains(FlowForegroundService.PREF_REST_TARGET_APPS)
                || prefs.getLong(FlowForegroundService.PREF_REST_TARGET_SESSION_ID, 0L)
                        != restSessionId) {
            return false;
        }
        return prefs.edit()
                .putString("blockState", BlockStateMachine.State.GRACE.name())
                .putLong("sessionMs", 0L)
                .putLong("graceUntil", graceUntil)
                .putString("blockedPackage", "")
                .remove(FlowForegroundService.PREF_REST_STARTED_AT)
                .remove(FlowForegroundService.PREF_REST_REQUIRED_MS)
                .remove(FlowForegroundService.PREF_REST_SESSION_ID)
                .remove(FlowForegroundService.PREF_REST_START_TARGET_ACTIVE)
                .remove(FlowForegroundService.PREF_REST_START_INTERACTION_AVAILABLE)
                .remove(FlowForegroundService.PREF_REST_START_FOREGROUND_PACKAGE)
                .remove(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN)
                .remove(FlowForegroundService.PREF_REST_TARGET_APPS)
                .remove(FlowForegroundService.PREF_REST_TARGET_SESSION_ID)
                .putLong(FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID, restSessionId)
                .putLong(FlowForegroundService.PREF_COMPLETED_REST_GRACE_UNTIL, graceUntil)
                .commit();
    }

    /**
     * Atomically commits a token-bound REST cancellation and clears its active session fields.
     * The monitor remains the sole state-machine owner; this synchronized preference transaction
     * prevents a stale cancellation from clearing a newer session and makes the state transition
     * durable before the service publishes it in memory.
     */
    public boolean commitRestCancellation(
            long expectedRestSessionId,
            String expectedBeginRequestToken,
            BlockStateMachine.State resultingState,
            long sessionMs,
            long graceUntil,
            long leftTargetsAt,
            String blockedPackage,
            PullbackSessionCoordinator.Snapshot pullback,
            Checkpoint checkpoint
    ) {
        if (resultingState == null) return false;
        synchronized (RestBeginRequestTokenStore.LOCK) {
            long activeRestSessionId = prefs.getLong(
                    FlowForegroundService.PREF_REST_SESSION_ID,
                    0L
            );
            String activeBeginRequestToken = prefs.getString(
                    FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN,
                    ""
            );
            if (!BlockStateMachine.State.RESTING.name().equals(
                            prefs.getString("blockState", BlockStateMachine.State.IDLE.name()))
                    || !RestCancellationGate.shouldCancel(
                            BlockStateMachine.State.RESTING,
                            expectedRestSessionId,
                            activeRestSessionId,
                            expectedBeginRequestToken,
                            activeBeginRequestToken
                    )) {
                return false;
            }

            SharedPreferences.Editor editor = prefs.edit()
                    .putString("blockState", resultingState.name())
                    .putLong("sessionMs", sessionMs)
                    .putLong("graceUntil", graceUntil)
                    .putLong("leftTargetsAt", leftTargetsAt)
                    .putString("blockedPackage", blockedPackage == null ? "" : blockedPackage)
                    .remove(FlowForegroundService.PREF_REST_STARTED_AT)
                    .remove(FlowForegroundService.PREF_REST_REQUIRED_MS)
                    .remove(FlowForegroundService.PREF_REST_SESSION_ID)
                    .remove(FlowForegroundService.PREF_REST_START_TARGET_ACTIVE)
                    .remove(FlowForegroundService.PREF_REST_START_INTERACTION_AVAILABLE)
                    .remove(FlowForegroundService.PREF_REST_START_FOREGROUND_PACKAGE)
                    .remove(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN)
                    .remove(FlowForegroundService.PREF_REST_TARGET_APPS)
                    .remove(FlowForegroundService.PREF_REST_TARGET_SESSION_ID);
            if (checkpoint != null) {
                editor.putLong("checkpointWallMs", checkpoint.wallMs)
                        .putLong("checkpointElapsedMs", checkpoint.elapsedMs)
                        .putBoolean("checkpointTargetActive", checkpoint.targetActive)
                        .putBoolean("checkpointInteractionAvailable",
                                checkpoint.interactionAvailable)
                        .putString("checkpointForegroundPackage", checkpoint.foregroundPackage);
            }
            if (pullback != null && pullback.present) {
                editor.putLong(FlowForegroundService.PREF_PULLBACK_SESSION_ID, pullback.sessionId)
                        .putLong(FlowForegroundService.PREF_PULLBACK_STARTED_AT, pullback.startedAt)
                        .putLong(FlowForegroundService.PREF_PULLBACK_TARGET_MS, pullback.targetMs)
                        .putLong(FlowForegroundService.PREF_PULLBACK_LEFT_AT, pullback.leftTargetsAt)
                        .putBoolean(FlowForegroundService.PREF_PULLBACK_SAW_TARGET,
                                pullback.sawTarget)
                        .putBoolean(FlowForegroundService.PREF_PULLBACK_RETURN_REPORTED,
                                pullback.returnReported)
                        .putBoolean(FlowForegroundService.PREF_PULLBACK_RESOLVED, pullback.resolved)
                        .putBoolean(FlowForegroundService.PREF_PULLBACK_SUCCESS, pullback.success);
            }
            return editor.commit();
        }
    }

    /** 写入 beginRest 的 session 字段；未传目标快照时恢复必须 fail-closed。 */
    public void persistBeginRest(long startedAt, long requiredMs, long sessionId) {
        persistBeginRest(startedAt, requiredMs, sessionId, false, false, "", "");
    }

    public boolean persistBeginRest(
            long startedAt,
            long requiredMs,
            long sessionId,
            boolean startTargetActive,
            boolean startInteractionAvailable,
            String startForegroundPackage
    ) {
        return persistBeginRest(
                startedAt,
                requiredMs,
                sessionId,
                startTargetActive,
                startInteractionAvailable,
                startForegroundPackage,
                ""
        );
    }

    /** Writes the REST session and its verified foreground baseline. */
    public boolean persistBeginRest(
            long startedAt,
            long requiredMs,
            long sessionId,
            boolean startTargetActive,
            boolean startInteractionAvailable,
            String startForegroundPackage,
            String beginRequestToken
    ) {
        return persistBeginRest(
                startedAt,
                requiredMs,
                sessionId,
                startTargetActive,
                startInteractionAvailable,
                startForegroundPackage,
                beginRequestToken,
                null
        );
    }

    /** Writes a REST session and the exact target set that governs that session. */
    public boolean persistBeginRest(
            long startedAt,
            long requiredMs,
            long sessionId,
            boolean startTargetActive,
            boolean startInteractionAvailable,
            String startForegroundPackage,
            String beginRequestToken,
            Set<String> restTargetApps
    ) {
        SharedPreferences.Editor editor = prefs.edit()
                .putLong(FlowForegroundService.PREF_REST_STARTED_AT, startedAt)
                .putLong(FlowForegroundService.PREF_REST_REQUIRED_MS, requiredMs)
                .putLong(FlowForegroundService.PREF_REST_SESSION_ID, sessionId)
                .putBoolean(FlowForegroundService.PREF_REST_START_TARGET_ACTIVE, startTargetActive)
                .putBoolean(FlowForegroundService.PREF_REST_START_INTERACTION_AVAILABLE,
                        startInteractionAvailable)
                .putString(FlowForegroundService.PREF_REST_START_FOREGROUND_PACKAGE,
                        startForegroundPackage == null ? "" : startForegroundPackage)
                .remove(FlowForegroundService.PREF_REST_TARGET_APPS)
                .remove(FlowForegroundService.PREF_REST_TARGET_SESSION_ID);
        if (restTargetApps != null) {
            editor.putStringSet(
                            FlowForegroundService.PREF_REST_TARGET_APPS,
                            new HashSet<>(restTargetApps)
                    )
                    .putLong(FlowForegroundService.PREF_REST_TARGET_SESSION_ID, sessionId);
        }
        String normalizedToken = beginRequestToken == null ? "" : beginRequestToken;
        if (!normalizedToken.isEmpty()) {
            return RestBeginRequestTokenStore.applySessionIfCurrent(
                    prefs,
                    normalizedToken,
                    editor
            );
        }
        synchronized (RestBeginRequestTokenStore.LOCK) {
            editor.putString(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, "").apply();
        }
        return true;
    }

    /** Bind a remounted REST page's current BEGIN token to the existing session. */
    public boolean bindActiveRestBeginRequestToken(String beginRequestToken) {
        return RestBeginRequestTokenStore.bindExistingSession(prefs, beginRequestToken);
    }

    /** 移除 REST 会话和目标快照字段，使用 apply()。 */
    public void clearActiveRestSession() {
        prefs.edit()
                .remove(FlowForegroundService.PREF_REST_STARTED_AT)
                .remove(FlowForegroundService.PREF_REST_REQUIRED_MS)
                .remove(FlowForegroundService.PREF_REST_SESSION_ID)
                .remove(FlowForegroundService.PREF_REST_START_TARGET_ACTIVE)
                .remove(FlowForegroundService.PREF_REST_START_INTERACTION_AVAILABLE)
                .remove(FlowForegroundService.PREF_REST_START_FOREGROUND_PACKAGE)
                .remove(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN)
                .remove(FlowForegroundService.PREF_REST_TARGET_APPS)
                .remove(FlowForegroundService.PREF_REST_TARGET_SESSION_ID)
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

    /**
     * 读取 pullback session 持久化字段，与 restorePullbackTracker() 规则一致：
     * - pullbackSessionId <= 0 返回 Snapshot.empty()
     * - 有 session 时完整读取所有字段
     */
    public PullbackSessionCoordinator.Snapshot loadPullbackSnapshot() {
        long sessionId = prefs.getLong(FlowForegroundService.PREF_PULLBACK_SESSION_ID, 0L);
        if (sessionId <= 0L) {
            return PullbackSessionCoordinator.Snapshot.empty();
        }
        return new PullbackSessionCoordinator.Snapshot(
                true,
                sessionId,
                prefs.getLong(FlowForegroundService.PREF_PULLBACK_STARTED_AT, 0L),
                prefs.getLong(FlowForegroundService.PREF_PULLBACK_TARGET_MS, 0L),
                prefs.getLong(FlowForegroundService.PREF_PULLBACK_LEFT_AT, 0L),
                prefs.getBoolean(FlowForegroundService.PREF_PULLBACK_SAW_TARGET, false),
                prefs.getBoolean(FlowForegroundService.PREF_PULLBACK_RETURN_REPORTED, false),
                prefs.getBoolean(FlowForegroundService.PREF_PULLBACK_RESOLVED, false),
                prefs.getBoolean(FlowForegroundService.PREF_PULLBACK_SUCCESS, false)
        );
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
