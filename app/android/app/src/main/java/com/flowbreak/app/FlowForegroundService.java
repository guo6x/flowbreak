package com.flowbreak.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
    private static final String CHANNEL_SERVICE = "flowbreak_service";
    private static final String CHANNEL_ALERT = "flowbreak_alert";
    private static final int SERVICE_NOTIFICATION_ID = 1001;
    private static final int ALERT_NOTIFICATION_ID = 1002;
    private static final long GRACE_MS = 10 * 60_000L;
    private static final long EMERGENCY_GRACE_MS = 5 * 60_000L;
    private static final long USAGE_FLUSH_MS = 15_000L;
    private static final long INITIAL_EVENT_LOOKBACK_MS = 36 * 60 * 60_000L;

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
    private WindowManager windowManager;
    private View blockerView;
    private View warningBar;
    private int warningBarLevel; // 0=无, 1=80%, 2=100%
    private View graceCountdownBar;
    private TextView graceCountdownText;
    private Runnable emergencyRunnable;
    private long restCheatDetectedAt;
    private long restCheatAccumulatedMs; // 累计在目标应用上的停留时长，避免快速切换绕过
    private final ForegroundAppTracker foregroundTracker = new ForegroundAppTracker();
    private final Map<String, Long> pendingUsageMs = new HashMap<>();
    private long usageEventsCursor;
    private long lastUsageFlushAt;
    private long lastObservedAt;
    private String lastObservedTargetPackage = "";
    private long lastHeartbeatWrittenAt;
    private PullbackOutcomeTracker pullbackTracker;

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
                foregroundTracker.reset();
                resetObservedUsage(now);
                staticForegroundPackage = "";
                if (machine != null) {
                    machine.onScreenOff(now);
                    persistState();
                }
                flushPendingUsage(true);
                dismissBlocker();
                dismissWarningBar();
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                screenOn = true;
                interactionAvailable = false;
                foregroundTracker.reset();
                usageEventsCursor = Math.max(0, now - INITIAL_EVENT_LOOKBACK_MS);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        repository = FlowRepository.get(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        screenOn = powerManager == null || powerManager.isInteractive();
        interactionAvailable = isInteractionAvailable();
        createChannels();
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
            dismissBlocker();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_BEGIN_REST.equals(action)) {
            beginRestSession();
            dismissBlocker();
        } else if (ACTION_COMPLETE_REST.equals(action)) {
            // NativeFlowPlugin validates and persists a completed rest before
            // asking a possibly recreated service to refresh its in-memory state.
            load();
            dismissBlocker();
        } else if (ACTION_CANCEL_REST.equals(action)) {
            machine.cancelRest(limitMinutes * 60_000L);
            clearActiveRestSession();
            persistState();
        } else if (ACTION_EMERGENCY.equals(action)) {
            machine.emergencyUnlock(System.currentTimeMillis(), EMERGENCY_GRACE_MS);
            clearActiveRestSession();
            clearPullbackTracker();
            persistState();
            dismissBlocker();
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
        Notification notification = serviceNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                    this,
                    SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification);
        }
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
            resetObservedUsage(now);
            dismissBlocker();
            return;
        }
        if (!isInteractionAvailable()) {
            trackPullbackOutcome(false, 0L, now);
            if (interactionAvailable && machine != null) {
                machine.onScreenOff(now);
                persistState();
            }
            interactionAvailable = false;
            foregroundTracker.reset();
            resetObservedUsage(now);
            staticForegroundPackage = "";
            dismissBlocker();
            dismissWarningBar();
            return;
        }
        if (!interactionAvailable && machine != null) {
            interactionAvailable = true;
            // Do not reuse the package from before a locked screen. A short
            // post-unlock window is enough to observe the actual foreground.
            foregroundTracker.reset();
            resetObservedUsage(now);
            usageEventsCursor = Math.max(0, now - 60_000L);
            machine.onScreenOn(now);
            persistState();
        }
        String foreground = getTopPackage(now);
        staticForegroundPackage = foreground;
        boolean isTarget = foreground != null && targetApps.contains(foreground);

        // Google Play cannot inspect a WeChat sub-page. Do not pretend that
        // the whole WeChat package is a short-video signal there.
        if (isTarget && "com.tencent.mm".equals(foreground)) {
            boolean inVideoChannel = false;
            if ("domestic".equals(BuildConfig.CHANNEL)) {
                long detectedAt = prefs.getLong("wechatInVideoChannelAt", 0L);
                boolean prefValue = prefs.getBoolean("wechatInVideoChannel", false);
                // 只信任 60 秒内的检测数据，超时视为过期不信任（无障碍服务可能已关闭）
                inVideoChannel = prefValue && detectedAt > 0
                        && (now - detectedAt) < 60_000L;
            }
            if (!inVideoChannel) {
                isTarget = false;
            }
        }

        long prevObservedAt = lastObservedAt;
        long observedTargetMs = observedTargetDelta(isTarget, foreground, now);
        if (isTarget && observedTargetMs > 0L) queueUsage(foreground, observedTargetMs);
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
                alert("休息已取消", "检测到在休息期间使用目标应用，未完成本次休息。");
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
            showBlocker(foreground);
            dismissWarningBar();
        } else {
            dismissBlocker();
            // 渐进式提醒：PERCEPTION / COGNITION 显示顶部浮条
            if (state == BlockStateMachine.State.PERCEPTION && isTarget) {
                showWarningBar(1, foreground);
            } else if (state == BlockStateMachine.State.COGNITION && isTarget) {
                showWarningBar(2, foreground);
            } else if (state == BlockStateMachine.State.GRACE) {
                handleGraceCountdown();
                dismissWarningBar();
            } else {
                dismissWarningBar();
                dismissGraceCountdown();
            }
        }
        // GRACE 状态即使不在目标应用也要检查倒计时
        if (state == BlockStateMachine.State.GRACE) {
            handleGraceCountdown();
        }
        updateServiceNotification();
        persistState();
    }

    private void queueUsage(String packageName, long durationMs) {
        if (packageName == null || packageName.isEmpty() || durationMs <= 0) return;
        pendingUsageMs.put(packageName, pendingUsageMs.getOrDefault(packageName, 0L) + durationMs);
    }

    private long observedTargetDelta(boolean isTarget, String packageName, long now) {
        long delta = lastObservedAt <= 0L ? 0L : Math.max(0L, Math.min(10_000L, now - lastObservedAt));
        boolean continuedTarget = isTarget && !lastObservedTargetPackage.isEmpty();
        lastObservedAt = now;
        lastObservedTargetPackage = isTarget && packageName != null ? packageName : "";
        return continuedTarget ? delta : 0L;
    }

    private void resetObservedUsage(long now) {
        lastObservedAt = now;
        lastObservedTargetPackage = "";
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

    private void persistPullbackTracker() {
        if (pullbackTracker == null) return;
        prefs.edit()
                .putLong(PREF_PULLBACK_SESSION_ID, pullbackTracker.getSessionId())
                .putLong(PREF_PULLBACK_STARTED_AT, pullbackTracker.getStartedAt())
                .putLong(PREF_PULLBACK_TARGET_MS, pullbackTracker.getTargetMs())
                .putLong(PREF_PULLBACK_LEFT_AT, pullbackTracker.getLeftTargetsAt())
                .putBoolean(PREF_PULLBACK_SAW_TARGET, pullbackTracker.hasSeenTarget())
                .putBoolean(PREF_PULLBACK_RETURN_REPORTED, pullbackTracker.isReturnReported())
                .putBoolean(PREF_PULLBACK_RESOLVED, pullbackTracker.isResolved())
                .putBoolean(PREF_PULLBACK_SUCCESS, pullbackTracker.isSuccess())
                .apply();
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
        if (prefs != null && prefs.getBoolean(PREF_DATA_ERASING, false)) {
            pendingUsageMs.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastUsageFlushAt < USAGE_FLUSH_MS) return;
        lastUsageFlushAt = now;
        for (Map.Entry<String, Long> entry : new HashMap<>(pendingUsageMs).entrySet()) {
            long seconds = entry.getValue() / 1000L;
            if (seconds <= 0) continue;
            repository.addUsage(entry.getKey(), seconds);
            pendingUsageMs.put(entry.getKey(), entry.getValue() - seconds * 1000L);
        }
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

    private void showBlocker(String packageName) {
        if (blockerView != null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            openBlockActivity(packageName);
            return;
        }
        handler.post(() -> {
            if (blockerView != null) return;
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setPadding(dp(28), dp(48), dp(28), dp(48));
            root.setBackgroundColor(Color.rgb(245, 248, 245));

            TextView title = text("先休息，再继续", 28, Color.rgb(24, 45, 33));
            title.setGravity(Gravity.CENTER);
            root.addView(title);

            TextView detail = text(
                    appLabel(packageName) + " 已暂停\n连续使用 "
                            + Math.max(1, machine.getSessionMs() / 60_000L)
                            + " 分钟 · 完成休息可解锁 10 分钟",
                    16,
                    Color.rgb(83, 101, 91)
            );
            detail.setGravity(Gravity.CENTER);
            detail.setPadding(0, dp(18), 0, dp(28));
            root.addView(detail);

            Button rest = new Button(this);
            rest.setText("开始休息");
            rest.setTextSize(17);
            rest.setTextColor(Color.WHITE);
            rest.setBackgroundColor(Color.rgb(50, 145, 87));
            rest.setOnClickListener(v -> openRest());
            root.addView(rest, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
            ));

            if (prefs.getBoolean("allowEmergencyUnlock", true)) {
                TextView emergency = text("紧急使用（长按 10 秒，每日一次）", 13, Color.rgb(150, 73, 62));
                emergency.setGravity(Gravity.CENTER);
                emergency.setPadding(0, dp(28), 0, dp(20));
                emergency.setOnTouchListener((v, event) -> handleEmergencyTouch(event));
                root.addView(emergency, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(72)
                ));
            }

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE
            );
            blockerView = root;
            try {
                windowManager.addView(root, params);
            } catch (Exception ignored) {
                blockerView = null;
                openBlockActivity(packageName);
            }
        });
    }

    private boolean handleEmergencyTouch(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            emergencyRunnable = () -> {
                if (EmergencyUnlockManager.tryUnlock(this)) {
                    repository.log("emergency_unlock", machine.getBlockedPackage(), "", 300, "");
                    machine.emergencyUnlock(System.currentTimeMillis(), EMERGENCY_GRACE_MS);
                    persistState();
                    dismissBlocker();
                } else {
                    alert("今日紧急使用已用完", "完成休息后仍可正常获得访问窗口。");
                }
            };
            handler.postDelayed(emergencyRunnable, 10_000L);
        } else if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (emergencyRunnable != null) handler.removeCallbacks(emergencyRunnable);
        }
        return true;
    }

    private void dismissBlocker() {
        if (emergencyRunnable != null) handler.removeCallbacks(emergencyRunnable);
        if (blockerView == null) return;
        View view = blockerView;
        blockerView = null;
        handler.post(() -> {
            try { windowManager.removeView(view); } catch (Exception ignored) { }
        });
    }

    /**
     * 渐进式提醒浮条：在屏幕顶部显示一条不可忽视但非阻断的彩色横条。
     * level 1 (PERCEPTION 80%): 橙色，温和提示"准备休息"
     * level 2 (COGNITION 100%): 红色，强烈提示"即将阻断"
     */
    private void showWarningBar(int level, String packageName) {
        if (warningBar != null && warningBarLevel == level) return;
        dismissWarningBar();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            return; // 无悬浮窗权限时回退到通知（已在 alert 中发送）
        }
        warningBarLevel = level;
        handler.post(() -> {
            if (warningBar != null) return;
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(20), dp(12), dp(20), dp(12));

            GradientDrawable bg = new GradientDrawable();
            int color = level == 1 ? Color.rgb(255, 152, 0) : Color.rgb(244, 67, 54);
            bg.setColor(color);
            bg.setCornerRadius(dp(12));
            root.setBackground(bg);

            TextView msg = new TextView(this);
            msg.setTextColor(Color.WHITE);
            msg.setTextSize(14);
            int usedMin = (int) (machine.getSessionMs() / 60_000L);
            int limitMin = limitMinutes;
            int pct = limitMin > 0 ? (int) (usedMin * 100 / limitMin) : 0;
            if (level == 1) {
                msg.setText("已用 " + pct + "% · 准备休息了");
            } else {
                msg.setText("已用满 " + limitMin + " 分钟 · 即将暂停，请尽快完成一次休息");
            }
            msg.setShadowLayer(2, 1, 1, Color.argb(80, 0, 0, 0));
            root.addView(msg, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ));

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP;
            params.y = dp(40);
            warningBar = root;
            try {
                windowManager.addView(root, params);
            } catch (Exception ignored) {
                warningBar = null;
            }
        });
    }

    private void dismissWarningBar() {
        if (warningBar == null) {
            warningBarLevel = 0;
            return;
        }
        View view = warningBar;
        warningBar = null;
        warningBarLevel = 0;
        handler.post(() -> {
            try { windowManager.removeView(view); } catch (Exception ignored) { }
        });
    }

    /**
     * GRACE 窗口最后 2 分钟倒计时浮条：避免用户在 10 分钟窗口结束时遭遇二次戒断。
     */
    private void handleGraceCountdown() {
        long graceUntil = machine.getGraceUntil();
        long now = System.currentTimeMillis();
        long remaining = graceUntil - now;
        if (remaining <= 0 || remaining > 2 * 60_000L) {
            dismissGraceCountdown();
            return;
        }
        int secs = (int) Math.ceil(remaining / 1000d);
        int mins = secs / 60;
        int remSecs = secs % 60;
        String countdown = "访问窗口还剩 " + mins + ":"
                + String.format(Locale.ROOT, "%02d", remSecs)
                + " · 准备保存进度";
        if (graceCountdownBar != null) {
            if (graceCountdownText != null) graceCountdownText.setText(countdown);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            return;
        }
        handler.post(() -> {
            if (graceCountdownBar != null) return;
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(20), dp(10), dp(20), dp(10));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.argb(220, 244, 67, 54));
            bg.setCornerRadius(dp(10));
            root.setBackground(bg);

            TextView msg = new TextView(this);
            msg.setTextColor(Color.WHITE);
            msg.setTextSize(13);
            msg.setText(countdown);
            root.addView(msg, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.BOTTOM;
            params.y = dp(40);
            graceCountdownBar = root;
            graceCountdownText = msg;
            try {
                windowManager.addView(root, params);
            } catch (Exception ignored) {
                graceCountdownBar = null;
                graceCountdownText = null;
            }
        });
    }

    private void dismissGraceCountdown() {
        if (graceCountdownBar == null) return;
        View view = graceCountdownBar;
        graceCountdownBar = null;
        graceCountdownText = null;
        handler.post(() -> {
            try { windowManager.removeView(view); } catch (Exception ignored) { }
        });
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

    private void openBlockActivity(String packageName) {
        Intent intent = new Intent(this, BlockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("blockedPackage", packageName);
        try { startActivity(intent); } catch (Exception ignored) { }
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String appLabel(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)
            ).toString();
        } catch (Exception ignored) {
            return packageName == null ? "目标应用" : packageName;
        }
    }

    private String getTopPackage(long now) {
        UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        if (manager == null) return "";
        try {
            long begin = usageEventsCursor > 0
                    ? usageEventsCursor
                    : Math.max(0, now - INITIAL_EVENT_LOOKBACK_MS);
            UsageEvents events = manager.queryEvents(begin, now);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event);
                foregroundTracker.accept(
                        event.getPackageName(), event.getEventType(), event.getTimeStamp()
                );
                staticLastUsageEventAt = Math.max(staticLastUsageEventAt, event.getTimeStamp());
                usageEventsCursor = Math.max(usageEventsCursor, event.getTimeStamp());
            }
            // The current foreground state has now been reconstructed. Future
            // polls only need fresh events instead of repeatedly scanning the
            // whole lookback window when no new event was emitted.
            usageEventsCursor = Math.max(usageEventsCursor, now);
        } catch (Exception ignored) { }
        return foregroundTracker.getForegroundPackage();
    }

    private Notification serviceNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String content = buildNotificationContent();
        String title = targetApps.isEmpty()
                ? "FlowBreak 未配置目标应用"
                : "FlowBreak 正在保护你";
        if (targetApps.isEmpty()) {
            content = "尚未选择要限制的应用，点击进入设置。";
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_SERVICE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(content)
                .setOngoing(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        // 进度条：已用/限额
        int usedSec = (int) (machine != null ? machine.getSessionMs() / 1000L : 0);
        int limitSec = limitMinutes * 60;
        if (limitSec > 0) {
            int progress = Math.min(100, usedSec * 100 / limitSec);
            builder.setProgress(100, progress, false);
        }
        return builder.build();
    }

    /**
     * 构建常驻通知正文：显示当前状态与剩余额度。
     */
    private String buildNotificationContent() {
        if (machine == null) return "仅在本机检测所选应用的连续使用";
        BlockStateMachine.State state = machine.getState();
        int usedMin = (int) (machine.getSessionMs() / 60_000L);
        int limitMin = limitMinutes;
        int remainMin = Math.max(0, limitMin - usedMin);
        if (state == BlockStateMachine.State.BLOCKED) {
            return "已暂停 · 完成休息可解锁 10 分钟";
        }
        if (state == BlockStateMachine.State.GRACE) {
            long graceRemain = Math.max(0, machine.getGraceUntil() - System.currentTimeMillis());
            int graceMin = (int) (graceRemain / 60_000L);
            return "访问窗口 · 还剩 " + Math.max(0, graceMin) + " 分钟";
        }
        if (state == BlockStateMachine.State.RESTING) {
            return "休息中 · 完成后解锁 10 分钟";
        }
        if (remainMin > 0) {
            return "连续使用 " + usedMin + "/" + limitMin + " 分钟 · 还剩 " + remainMin + " 分钟";
        }
        return "连续使用 " + usedMin + "/" + limitMin + " 分钟";
    }

    /**
     * 刷新常驻通知（每次 tick 调用，更新剩余额度）。
     */
    private void updateServiceNotification() {
        if (!canPostNotifications()) return;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(SERVICE_NOTIFICATION_ID, serviceNotification());
        } catch (Exception ignored) { }
    }

    private void alert(String title, String body) {
        if (!canPostNotifications()) return;
        Intent open = new Intent(this, MainActivity.class);
        boolean blocked = machine != null && machine.getState() == BlockStateMachine.State.BLOCKED;
        open.putExtra("navigateTo", blocked ? "rest" : "dashboard");
        PendingIntent pending = PendingIntent.getActivity(
                this, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ALERT_NOTIFICATION_ID, notification);
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        // 只在 channel 不存在时创建，避免每次服务重启覆盖用户在系统设置中调整的重要性
        if (manager.getNotificationChannel(CHANNEL_SERVICE) == null) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_SERVICE, "FlowBreak 运行状态", NotificationManager.IMPORTANCE_LOW
            ));
        }
        if (manager.getNotificationChannel(CHANNEL_ALERT) == null) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ALERT, "休息与阻断提醒", NotificationManager.IMPORTANCE_HIGH
            ));
        }
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
        dismissBlocker();
        dismissWarningBar();
        dismissGraceCountdown();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
