package com.flowbreak.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.pm.ServiceInfo;
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

    private static final long EMERGENCY_GRACE_MS = FlowServiceStateStore.EMERGENCY_GRACE_MS;

    private static volatile BlockStateMachine.State staticState = BlockStateMachine.State.IDLE;
    private static volatile long staticSessionMs;
    private static volatile long staticGraceUntil;
    private static volatile String staticBlockedPackage = "";
    private static volatile String staticForegroundPackage = "";
    private static volatile long staticLastTickAt;
    private static volatile long staticLastUsageEventAt;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FlowRepository repository;
    private BlockStateMachine machine;
    private Set<String> targetApps;
    private int limitMinutes;
    private boolean monitoringEnabled;
    private boolean screenOn;
    private boolean interactionAvailable;
    private KeyguardManager keyguardManager;
    private BlockStateMachine.State lastAnnouncedState = BlockStateMachine.State.IDLE;

    // 第一阶段协作类
    private ForegroundUsageDetector foregroundDetector;
    private TargetAppClassifier targetClassifier;
    private UsageAccumulator usageAccumulator;
    private FlowNotificationController notificationController;
    private FlowOverlayController overlayController;

    // 第二阶段协作类
    private FlowServiceStateStore stateStore;
    private PullbackSessionCoordinator pullbackCoordinator;
    private RestCheatTracker restCheatTracker;

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
        stateStore = new FlowServiceStateStore(this);
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
        pullbackCoordinator = new PullbackSessionCoordinator();
        restCheatTracker = new RestCheatTracker();
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
                        alert(
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
                        return stateStore.allowEmergencyUnlock();
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
            stateStore.setMonitoringEnabled(false);
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
            stateStore.clearActiveRestSession();
            persistState();
        } else if (ACTION_EMERGENCY.equals(action)) {
            machine.emergencyUnlock(System.currentTimeMillis(), EMERGENCY_GRACE_MS);
            stateStore.clearActiveRestSession();
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
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(stateStore, System.currentTimeMillis());
        targetApps = result.config.targetApps;
        limitMinutes = result.config.limitMinutes;
        monitoringEnabled = result.config.monitoringEnabled;
        machine = result.machine;
        restorePullbackTrackerFromSnapshot(result.pullbackSnapshot);
        lastAnnouncedState = machine.getState();
        publishState();
    }

    private void beginRestSession() {
        long existingStartedAt = stateStore.preferences().getLong(PREF_REST_STARTED_AT, 0L);
        boolean alreadyResting = machine.getState() == BlockStateMachine.State.RESTING;
        RestSessionManager.BeginRestDecision decision = RestSessionManager.prepareBeginRest(
                alreadyResting,
                existingStartedAt,
                stateStore.restDurationSeconds(),
                System.currentTimeMillis()
        );
        if (decision == null) {
            // React can remount after an orientation or WebView recreation.
            // Keep the same session rather than granting a fresh timer.
            persistState();
            return;
        }
        stateStore.persistBeginRest(decision.startedAt, decision.requiredMs, decision.sessionId);
        machine.beginRest();
        persistState();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        staticLastTickAt = now;
        stateStore.writeHeartbeatIfDue(now);
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
                stateStore.isWechatInVideoChannel(),
                stateStore.wechatInVideoChannelAt(),
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
        long cheatAccumulated = restCheatTracker.observe(
                currentState == BlockStateMachine.State.RESTING,
                isTarget,
                prevObservedAt,
                now
        );
        if (restCheatTracker.triggered()) {
            machine.cancelRest(limitMinutes * 60_000L);
            repository.log("rest_cheat", foreground, "", cheatAccumulated / 1000L, "");
            alert("休息已取消", "检测到在休息期间使用目标应用，未完成本次休息。");
            persistState();
            flushPendingUsage(false);
            restCheatTracker.reset();
            return;
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
                    stateStore.allowEmergencyUnlock()
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

    private void trackPullbackOutcome(boolean isTarget, long targetDeltaMs, long now) {
        pullbackCoordinator.update(
                isTarget,
                targetDeltaMs,
                now,
                machine == null ? 0L : machine.getGraceUntil(),
                repositorySink()
        );
    }

    private PullbackSessionCoordinator.OutcomeSink repositorySink() {
        return new PullbackSessionCoordinator.OutcomeSink() {
            @Override public void recordPostRestReturn(long sessionId) {
                repository.recordPostRestReturn(sessionId);
            }
            @Override public void recordPullbackOutcome(boolean success, long targetSeconds, long sessionId) {
                repository.recordPullbackOutcome(success, targetSeconds, sessionId);
            }
        };
    }

    private void restorePullbackTrackerFromSnapshot(PullbackSessionCoordinator.Snapshot snapshot) {
        if (!snapshot.present || snapshot.sessionId <= 0L) {
            pullbackCoordinator.clear();
            return;
        }
        pullbackCoordinator.restore(snapshot);
    }

    private void clearPullbackTracker() {
        pullbackCoordinator.clear();
        stateStore.clearPullbackSession();
    }

    private boolean isInteractionAvailable() {
        return screenOn && (keyguardManager == null || !keyguardManager.isKeyguardLocked());
    }

    private void flushPendingUsage(boolean force) {
        usageAccumulator.flush(
                force,
                System.currentTimeMillis(),
                stateStore.isDataErasing(),
                repository::addUsage
        );
    }

    private void onStateChanged(BlockStateMachine.State state, String pkg) {
        if (state == BlockStateMachine.State.PERCEPTION) {
            repository.recordIntervention();
            alert("注意连续使用", "已达到共享限额的 80%，建议准备休息。");
            vibrate(new long[]{0, 80});
        } else if (state == BlockStateMachine.State.COGNITION) {
            repository.recordIntervention();
            alert("需要休息", "已达到共享限额，请尽快完成一次休息。");
            vibrate(new long[]{0, 120, 80, 120});
        } else if (state == BlockStateMachine.State.BLOCKED) {
            repository.log("block_attempt", pkg, "", machine.getSessionMs() / 1000L, "");
            repository.recordBlock();
            alert("应用已暂停访问", "完成配置的休息活动后可获得 10 分钟访问窗口。");
            vibrate(new long[]{0, 200, 100, 200});
        }
    }

    /**
     * 发送高优先级提醒通知，导航目标根据当前 machine 状态决定。
     * 保持原 Service 私有方法的语义：BLOCKED 时跳转 rest，否则 dashboard。
     */
    private void alert(String title, String body) {
        boolean blocked = machine != null && machine.getState() == BlockStateMachine.State.BLOCKED;
        notificationController.alert(title, body, blocked);
    }

    private void persistState() {
        stateStore.persist(machine, pullbackCoordinator.snapshot());
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
