package com.flowbreak.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.KeyguardManager;
import android.app.Service;
import android.app.ServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import java.util.Set;

public class FlowForegroundService extends Service {
    public static final String ACTION_START = "com.flowbreak.app.START";
    public static final String ACTION_STOP = "com.flowbreak.app.STOP";
    public static final String ACTION_RELOAD = "com.flowbreak.app.RELOAD";
    public static final String ACTION_BEGIN_REST = "com.flowbreak.app.BEGIN_REST";
    public static final String ACTION_COMPLETE_REST = "com.flowbreak.app.COMPLETE_REST";
    public static final String ACTION_CANCEL_REST = "com.flowbreak.app.CANCEL_REST";
    public static final String ACTION_EMERGENCY = "com.flowbreak.app.EMERGENCY";

    public static final String PREF_REST_STARTED_AT = "restStartedAt";
    public static final String PREF_REST_REQUIRED_MS = "restRequiredMs";
    public static final String PREF_REST_SESSION_ID = "restSessionId";
    public static final String PREF_COMPLETED_REST_SESSION_ID = "completedRestSessionId";
    public static final String PREF_COMPLETED_REST_GRACE_UNTIL = "completedRestGraceUntil";
    public static final String PREF_PULLBACK_SESSION_ID = "pullbackSessionId";
    public static final String PREF_PULLBACK_STARTED_AT = "pullbackStartedAt";
    public static final String PREF_PULLBACK_TARGET_MS = "pullbackTargetMs";
    public static final String PREF_PULLBACK_LEFT_AT = "pullbackLeftAt";
    public static final String PREF_PULLBACK_SAW_TARGET = "pullbackSawTarget";
    public static final String PREF_PULLBACK_RETURN_REPORTED = "pullbackReturnReported";
    public static final String PREF_PULLBACK_RESOLVED = "pullbackResolved";
    public static final String PREF_PULLBACK_SUCCESS = "pullbackSuccess";

    private static final String PREFS = "FlowBreakPrefs";
    private static final String PREF_DATA_ERASING = "dataErasing";
    private static final long GRACE_MS = 10 * 60_000L;
    private static final long EMERGENCY_GRACE_MS = 5 * 60_000L;

    private static volatile BlockStateMachine.State staticState = BlockStateMachine.State.IDLE;
    private static volatile long staticSessionMs;
    private static volatile long staticGraceUntil;
    private static volatile String staticBlockedPackage = "";
    private static volatile String staticForegroundPackage = "";
    private static volatile long staticLastTickAt;
    private static volatile long staticLastUsageEventAt;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private FlowRepository repository;
    private BlockStateMachine machine;
    private Set<String> targetApps;
    private int limitMinutes;
    private boolean monitoringEnabled;
    private boolean screenOn;
    private boolean interactionAvailable;
    private KeyguardManager keyguardManager;
    private BlockStateMachine.State lastAnnouncedState = BlockStateMachine.State.IDLE;
    private long restCheatDetectedAt;
    private long restCheatAccumulatedMs; // 累计在目标应用上的停留时长，避免快速切换绕过
    private PullbackOutcomeTracker pullbackTracker;
    private long lastHeartbeatWrittenAt;

    private ForegroundUsageDetector foregroundDetector;
    private TargetAppClassifier targetClassifier;
    private UsageAccumulator usageAccumulator;
    private FlowNotificationController notificationController;
    private FlowOverlayController overlayController;

    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, 2_000L);
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            long now = System.currentTimeMillis();
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                trackPullbackOutcome(false, 0L, now);
                screenOn = false;
                interactionAvailable = false;
                foregroundDetector.reset();
                usageAccumulator.resetObservation(now);
                staticForegroundPackage = "";
                if (machine != null) {
                    machine.onScreenOff(now);
                    persistState();
                }
                flushPendingUsage(true);
                overlayController.dismissBlocker();
                overlayController.dismissWarningBar();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                screenOn = true;
                interactionAvailable = false;
                foregroundDetector.reset();
                foregroundDetector.resetCursor(Math.max(0, now - ForegroundUsageDetector.INITIAL_EVENT_LOOKBACK_MS));
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        repository = FlowRepository.get(this);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        screenOn = powerManager == null || powerManager.isInteractive();
        interactionAvailable = isInteractionAvailable();
        notificationController = new FlowNotificationController(this);
        notificationController.createChannels();
        foregroundDetector = new ForegroundUsageDetector(this);
        targetClassifier = new TargetAppClassifier("domestic".equals(BuildConfig.CHANNEL));
        usageAccumulator = new UsageAccumulator();
        overlayController = new FlowOverlayController(
                this,
                (android.view.WindowManager) getSystemService(WINDOW_SERVICE),
                handler,
                new FlowOverlayController.Callbacks() {
                    @Override public void onStartRest() { openRest(); }

                    @Override public boolean onEmergencyLongPress() {
                        if (EmergencyUnlockManager.tryUnlock(FlowForegroundService.this)) {
                            repository.log(
                                    "emergency_unlock",
                                    machine.getBlockedPackage(),
                                    "",
                                    300,
                                    ""
                            );
                            machine.emergencyUnlock(System.currentTimeMillis(), EMERGENCY_GRACE_MS);
                            persistState();
                            return true;
                        }
                        return false;
                    }

                    @Override public void onEmergencyExhausted() {
                        notificationController.alert(
                                "今日紧急使用已用完",
                                "完成休息后仍可正常获得访问窗口。"
                        );
                    }

                    @Override public long currentSessionMs() {
                        return machine == null ? 0L : machine.getSessionMs();
                    }

                    @Override public int currentLimitMinutes() {
                        return limitMinutes;
                    }

                    @Override public boolean allowEmergencyUnlock() {
                        return prefs.getBoolean("allowEmergencyUnlock", true);
                    }
                }
        );
        load();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            monitoringEnabled = false;
            flushPendingUsage(true);
            prefs.edit()
                    .putBoolean("monitoringEnabled", false)
                    .apply();
            overlayController.dismissBlocker();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_BEGIN_REST.equals(action)) {
            beginRestSession();
            overlayController.dismissBlocker();
        } else if (ACTION_COMPLETE_REST.equals(action)) {
            // NativeFlowPlugin validates and persists a completed rest before
            // asking a possibly recreated service to refresh its in-memory state.
            load();
            overlayController.dismissBlocker();
        } else if (ACTION_CANCEL_REST.equals(action)) {
            machine.cancelRest(limitMinutes * 60_000L);
            clearActiveRestSession();
            persistState();
        } else if (ACTION_EMERGENCY.equals(action)) {
            machine.emergencyUnlock(System.currentTimeMillis(), EMERGENCY_GRACE_MS);
            clearActiveRestSession();
            clearPullbackTracker();
            persistState();
            overlayController.dismissBlocker();
        } else {
            load();
        }

        promoteToForeground();
        handler.removeCallbacks(monitor);
        handler.post(monitor);
        return START_STICKY;
    }

    /**
     * Promote this service to foreground with the explicit special-use type.
     *
     * Android 14 (API 34) requires the foreground service type to be passed
     * at runtime; older versions keep the original two-argument behaviour.
     * The merged manifests for both Play and Domestic channels declare
     * foregroundServiceType="specialUse" and the matching
     * FOREGROUND_SERVICE_TYPE_SPECIAL_USE permission, which CI verifies
     * after every build.
     */
    // Android Lint may associate this call with the wrong service declaration
    // when services are split across source-set manifests. The merged manifests
    // are verified in CI to contain foregroundServiceType="specialUse".
    @SuppressLint("ForegroundServiceType")
    private void promoteToForeground() {
        Notification notification = notificationController.buildServiceNotification(snapshot());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                    this,
                    FlowNotificationController.SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(FlowNotificationController.SERVICE_NOTIFICATION_ID, notification);
        }
    }

    private FlowNotificationController.State snapshot() {
        return new FlowNotificationController.State(
                machine == null ? null : machine.getState(),
                machine == null ? 0L : machine.getSessionMs(),
                machine == null ? 0L : machine.getGraceUntil(),
                limitMinutes,
                targetApps == null || targetApps.isEmpty()
        );
    }

    private void load() {
        targetApps = PreferenceUtils.getMigratedTargetApps(prefs);
        limitMinutes = Math.max(1, prefs.getInt("limitMinutes", 25));
        monitoringEnabled = prefs.getBoolean("monitoringEnabled", true);
        BlockStateMachine.State restored;
        try {
            restored = BlockStateMachine.State.valueOf(
                    prefs.getString("blockState", BlockStateMachine.State.IDLE.name())
            );
        } catch (Exception ignored) {
            restored = BlockStateMachine.State.IDLE;
        }
        if (restored == BlockStateMachine.State.RESTING) {
            long startedAt = prefs.getLong(PREF_REST_STARTED_AT, 0L);
            long requiredMs = prefs.getLong(PREF_REST_REQUIRED_MS, 0L);
            long now = System.currentTimeMillis();
            if (startedAt <= 0L) {
                // 休息从未开始过：回退到基于使用时长的状态
                restored = BlockStateMachine.stateFor(
                        prefs.getLong("sessionMs", 0),
                        limitMinutes * 60_000L
                );
            } else if (RestSessionValidator.isComplete(startedAt, requiredMs, now)) {
                // 服务被杀后实际已超过所需休息时长：自动完成休息进入 GRACE
                long graceUntil = now + GRACE_MS;
                // 用 commit() 同步写入：此分支在服务被杀恢复时执行，
                // 如果用 apply() 异步写入后再次被杀，关键状态会丢失。
                prefs.edit()
                        .putString("blockState", BlockStateMachine.State.GRACE.name())
                        .putLong("sessionMs", 0)
                        .putLong("graceUntil", graceUntil)
                        .putString("blockedPackage", "")
                        .remove(PREF_REST_STARTED_AT)
                        .remove(PREF_REST_REQUIRED_MS)
                        .putLong(PREF_COMPLETED_REST_SESSION_ID, prefs.getLong(PREF_REST_SESSION_ID, 0L))
                        .putLong(PREF_COMPLETED_REST_GRACE_UNTIL, graceUntil)
                        .commit();
                restored = BlockStateMachine.State.GRACE;
            }
            // 其余情况（startedAt > 0 但尚未到 requiredMs）：保持 RESTING，让用户继续完成
        }
        machine = new BlockStateMachine(
                restored,
                prefs.getLong("sessionMs", 0),
                prefs.getLong("graceUntil", 0),
                prefs.getLong("leftTargetsAt", 0),
                prefs.getString("blockedPackage", "")
        );
        loadPullbackTracker();
        // Restoring a persisted state must not create a second warning or a
        // duplicate block event before the state actually changes.
        lastAnnouncedState = machine.getState();
        publishState();
    }

    private void beginRestSession() {
        long startedAt = prefs.getLong(PREF_REST_STARTED_AT, 0L);
        if (machine.getState() == BlockStateMachine.State.RESTING && startedAt > 0L) {
            // React can remount after an orientation or WebView recreation.
            // Keep the same session rather than granting a fresh timer.
            persistState();
            return;
        }
        long now = System.currentTimeMillis();
        long requiredMs = Math.max(30, prefs.getInt("restDuration", 180)) * 1000L;
        prefs.edit()
                .putLong(PREF_REST_STARTED_AT, now)
                .putLong(PREF_REST_REQUIRED_MS, requiredMs)
                .putLong(PREF_REST_SESSION_ID, now)
                .apply();
        machine.beginRest();
        persistState();
    }

    private void clearActiveRestSession() {
        prefs.edit()
                .remove(PREF_REST_STARTED_AT)
                .remove(PREF_REST_REQUIRED_MS)
                .remove(PREF_REST_SESSION_ID)
                .apply();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        staticLastTickAt = now;
        writeHeartbeat(now);
        if (!monitoringEnabled || targetApps.isEmpty()) {
            usageAccumulator.resetObservation(now);
            overlayController.dismissBlocker();
            return;
        }
        if (!isInteractionAvailable()) {
            trackPullbackOutcome(false, 0L, now);
            if (interactionAvailable && machine != null) {
                machine.onScreenOff(now);
                persistState();
            }
            interactionAvailable = false;
            foregroundDetector.reset();
            usageAccumulator.resetObservation(now);
            staticForegroundPackage = "";
            overlayController.dismissBlocker();
            overlayController.dismissWarningBar();
            return;
        }
        if (!interactionAvailable && machine != null) {
            interactionAvailable = true;
            // Do not reuse the package from before a locked screen. A short
            // post-unlock window is enough to observe the actual foreground.
            foregroundDetector.reset();
            usageAccumulator.resetObservation(now);
            foregroundDetector.resetCursor(Math.max(0, now - 60_000L));
            machine.onScreenOn(now);
            persistState();
        }
        String foreground = foregroundDetector.detect(now);
        staticLastUsageEventAt = Math.max(staticLastUsageEventAt, foregroundDetector.getLastUsageEventAt());
        staticForegroundPackage = foreground;
        boolean isTarget = targetClassifier.isTarget(
                foreground,
                targetApps,
                prefs.getBoolean("wechatInVideoChannel", false),
                prefs.getLong("wechatInVideoChannelAt", 0L),
                now
        );

        long prevObservedAt = usageAccumulator.getLastObservedAt();
        long observedTargetMs = usageAccumulator.observe(isTarget, foreground, now);
        if (isTarget && observedTargetMs > 0L) usageAccumulator.queue(foreground, observedTargetMs);
        trackPullbackOutcome(isTarget, observedTargetMs, now);

        // 休息期间防作弊：累计在目标应用上的停留时长超过 5 秒即取消休息
        // 用累计而非连续，避免用户每 4 秒切换一次绕过检测
        // 注意：observedTargetMs 在首次切回目标应用时为 0（continuedTarget=false），
        // 所以用 prevObservedAt 独立计算 delta，确保首次切回也能被计入
        BlockStateMachine.State currentState = machine.getState();
        if (currentState == BlockStateMachine.State.RESTING && isTarget) {
            long cheatDelta = prevObservedAt > 0
                    ? Math.max(0L, Math.min(10_000L, now - prevObservedAt)) : 0L;
            if (restCheatDetectedAt == 0) restCheatDetectedAt = now;
            restCheatAccumulatedMs += cheatDelta;
            if (restCheatAccumulatedMs >= 5_000L) {
                machine.cancelRest(limitMinutes * 60_000L);
                repository.log("rest_cheat", foreground, "", restCheatAccumulatedMs / 1000L, "");
                notificationController.alert("休息已取消", "检测到在休息期间使用目标应用，未完成本次休息。");
                persistState();
                flushPendingUsage(false);
                restCheatDetectedAt = 0;
                restCheatAccumulatedMs = 0;
                return;
            }
        } else if (restCheatAccumulatedMs != 0 && currentState != BlockStateMachine.State.RESTING) {
            restCheatDetectedAt = 0;
            restCheatAccumulatedMs = 0;
        }

        BlockStateMachine.State state = machine.update(
                isTarget,
                foreground,
                now,
                limitMinutes * 60_000L
        );
        flushPendingUsage(false);

        if (state != lastAnnouncedState) {
            onStateChanged(state, foreground);
            lastAnnouncedState = state;
        }
        if (state == BlockStateMachine.State.BLOCKED && isTarget) {
            overlayController.showBlocker(
                    foreground,
                    machine.getSessionMs(),
                    prefs.getBoolean("allowEmergencyUnlock", true)
            );
            overlayController.dismissWarningBar();
        } else {
            overlayController.dismissBlocker();
            // 渐进式提醒：PERCEPTION / COGNITION 显示顶部浮条
            if (state == BlockStateMachine.State.PERCEPTION && isTarget) {
                overlayController.showWarningBar(1, machine.getSessionMs(), limitMinutes);
            } else if (state == BlockStateMachine.State.COGNITION && isTarget) {
                overlayController.showWarningBar(2, machine.getSessionMs(), limitMinutes);
            } else if (state == BlockStateMachine.State.GRACE) {
                handleGraceCountdown();
                overlayController.dismissWarningBar();
            } else {
                overlayController.dismissWarningBar();
                overlayController.dismissGraceCountdown();
            }
        }
        // GRACE 状态即使不在目标应用也要检查倒计时
        if (state == BlockStateMachine.State.GRACE) {
            handleGraceCountdown();
        }
        notificationController.updateServiceNotification(snapshot());
        persistState();
    }

    private void loadPullbackTracker() {
        long sessionId = prefs.getLong(PREF_PULLBACK_SESSION_ID, 0L);
        pullbackTracker = sessionId <= 0L ? null : new PullbackOutcomeTracker(
                sessionId,
                prefs.getLong(PREF_PULLBACK_STARTED_AT, 0L),
                prefs.getLong(PREF_PULLBACK_TARGET_MS, 0L),
                prefs.getLong(PREF_PULLBACK_LEFT_AT, 0L),
                prefs.getBoolean(PREF_PULLBACK_SAW_TARGET, false),
                prefs.getBoolean(PREF_PULLBACK_RETURN_REPORTED, false),
                prefs.getBoolean(PREF_PULLBACK_RESOLVED, false),
                prefs.getBoolean(PREF_PULLBACK_SUCCESS, false)
        );
    }

    private void trackPullbackOutcome(boolean isTarget, long targetDeltaMs, long now) {
        if (pullbackTracker == null || machine == null) return;
        PullbackOutcomeTracker.Update update = pullbackTracker.update(
                isTarget, targetDeltaMs, now, machine.getGraceUntil()
        );
        if (update.returnObservedNow) {
            repository.recordPostRestReturn(pullbackTracker.getSessionId());
        }
        if (update.resolvedNow) {
            repository.recordPullbackOutcome(
                    update.success, update.targetSeconds, pullbackTracker.getSessionId()
            );
        }
        // 不再单独调用 persistPullbackTracker()：persistState() 已包含所有 pullback 字段，
        // 所有 trackPullbackOutcome 调用路径后续都会调用 persistState()
    }

    private void clearPullbackTracker() {
        pullbackTracker = null;
        prefs.edit()
                .remove(PREF_PULLBACK_SESSION_ID)
                .remove(PREF_PULLBACK_STARTED_AT)
                .remove(PREF_PULLBACK_TARGET_MS)
                .remove(PREF_PULLBACK_LEFT_AT)
                .remove(PREF_PULLBACK_SAW_TARGET)
                .remove(PREF_PULLBACK_RETURN_REPORTED)
                .remove(PREF_PULLBACK_RESOLVED)
                .remove(PREF_PULLBACK_SUCCESS)
                .apply();
    }

    private void writeHeartbeat(long now) {
        if (now - lastHeartbeatWrittenAt < 30_000L) return;
        lastHeartbeatWrittenAt = now;
        prefs.edit().putLong("serviceHeartbeatAt", now).apply();
    }

    private boolean isInteractionAvailable() {
        return screenOn && (keyguardManager == null || !keyguardManager.isKeyguardLocked());
    }

    private void flushPendingUsage(boolean force) {
        usageAccumulator.flush(
                force,
                System.currentTimeMillis(),
                prefs != null && prefs.getBoolean(PREF_DATA_ERASING, false),
                repository::addUsage
        );
    }

    private void onStateChanged(BlockStateMachine.State state, String pkg) {
        if (state == BlockStateMachine.State.PERCEPTION) {
            repository.recordIntervention();
            notificationController.alert("注意连续使用", "已达到共享限额的 80%，建议准备休息。");
            vibrate(new long[]{0, 80});
        } else if (state == BlockStateMachine.State.COGNITION) {
            repository.recordIntervention();
            notificationController.alert("需要休息", "已达到共享限额，请尽快完成一次休息。");
            vibrate(new long[]{0, 120, 80, 120});
        } else if (state == BlockStateMachine.State.BLOCKED) {
            repository.log("block_attempt", pkg, "", machine.getSessionMs() / 1000L, "");
            repository.recordBlock();
            notificationController.alert("应用已暂停访问", "完成配置的休息活动后可获得 10 分钟访问窗口。");
            vibrate(new long[]{0, 200, 100, 200});
        }
    }

    private void persistState() {
        SharedPreferences.Editor editor = prefs.edit()
                .putString("blockState", machine.getState().name())
                .putLong("sessionMs", machine.getSessionMs())
                .putLong("graceUntil", machine.getGraceUntil())
                .putLong("leftTargetsAt", machine.getLeftTargetsAt())
                .putString("blockedPackage", machine.getBlockedPackage());
        if (pullbackTracker != null) {
            editor.putLong(PREF_PULLBACK_SESSION_ID, pullbackTracker.getSessionId())
                    .putLong(PREF_PULLBACK_STARTED_AT, pullbackTracker.getStartedAt())
                    .putLong(PREF_PULLBACK_TARGET_MS, pullbackTracker.getTargetMs())
                    .putLong(PREF_PULLBACK_LEFT_AT, pullbackTracker.getLeftTargetsAt())
                    .putBoolean(PREF_PULLBACK_SAW_TARGET, pullbackTracker.hasSeenTarget())
                    .putBoolean(PREF_PULLBACK_RETURN_REPORTED, pullbackTracker.isReturnReported())
                    .putBoolean(PREF_PULLBACK_RESOLVED, pullbackTracker.isResolved())
                    .putBoolean(PREF_PULLBACK_SUCCESS, pullbackTracker.isSuccess());
        }
        editor.apply();
        publishState();
    }

    private void publishState() {
        staticState = machine.getState();
        staticSessionMs = machine.getSessionMs();
        staticGraceUntil = machine.getGraceUntil();
        staticBlockedPackage = machine.getBlockedPackage();
    }

    public static BlockStateMachine.State getState() { return staticState; }
    public static long getSessionSeconds() { return staticSessionMs / 1000L; }
    public static long getGraceUntil() { return staticGraceUntil; }
    public static String getBlockedPackage() { return staticBlockedPackage; }
    public static long getLastTickAt() { return staticLastTickAt; }
    public static long getLastUsageEventAt() { return staticLastUsageEventAt; }
    public static String getForegroundPackage() { return staticForegroundPackage; }
    public static int getCurrentLevel() {
        if (staticState == BlockStateMachine.State.PERCEPTION) return 1;
        if (staticState == BlockStateMachine.State.COGNITION) return 2;
        if (staticState == BlockStateMachine.State.BLOCKED) return 3;
        return 0;
    }
    public static long getTotalMinutes() { return staticSessionMs / 60_000L; }

    private void handleGraceCountdown() {
        overlayController.showGraceCountdown(machine.getGraceUntil(), System.currentTimeMillis());
    }

    private void vibrate(long[] pattern) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (vm != null && vm.getDefaultVibrator().hasVibrator()) {
                    vm.getDefaultVibrator().vibrate(VibrationEffect.createWaveform(pattern, -1));
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                }
            }
        } catch (Exception ignored) { }
    }

    private void openRest() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("navigateTo", "rest");
        startActivity(intent);
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // START_STICKY is the supported recovery path. Scheduling an exact
        // alarm to resurrect a background foreground-service is both brittle
        // on Android 15+ and unsuitable for a user-trust product.
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        flushPendingUsage(true);
        handler.removeCallbacksAndMessages(null);
        overlayController.clearCallbacks();
        overlayController.dismissAll();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
