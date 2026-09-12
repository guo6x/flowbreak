package com.flowbreak.app;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.KeyguardManager;
import android.app.Service;
import android.app.usage.UsageStatsManager;
import android.content.pm.ServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final long GAP_RECONCILIATION_THRESHOLD_MS =
            GapReconciliationWindow.RECONCILIATION_THRESHOLD_MS;
    private static final long MAX_CLOCK_SKEW_MS = 60_000L;
    public static final long SERVICE_HEARTBEAT_STALE_MS =
            ProtectionRuntimeHealthEvaluator.SERVICE_HEARTBEAT_STALE_MS;

    private static volatile BlockStateMachine.State staticState = BlockStateMachine.State.IDLE;
    private static volatile long staticSessionMs;
    private static volatile long staticGraceUntil;
    private static volatile String staticBlockedPackage = "";
    private static volatile String staticForegroundPackage = "";
    private static volatile long staticLastTickAt;
    private static volatile long staticLastUsageEventAt;
    private static volatile int staticRuntimeTargetCount;
    private static volatile int staticPersistedTargetCount;
    private static volatile boolean staticRuntimeTargetsMatchPersisted;
    private static volatile int staticLimitMinutes;
    private static volatile boolean staticTargetAppsEmpty = true;
    private static volatile boolean staticAllowEmergencyUnlock = true;
    private static volatile boolean staticMonitorLooperIsMain;
    private static volatile HandlerThread staticMonitorThreadRef;
    private static volatile Context staticServiceContext;
    private static volatile Set<String> staticRuntimeTargetSnapshot = Collections.emptySet();
    private static volatile boolean staticServiceRuntimeActive;
    private static volatile long staticLastCompletedMonitorTickElapsedMs;
    private static volatile long staticLastCompletedMonitorTickWallMs;
    private static volatile String staticProtectionIntegrityFailureReason = "";
    private static volatile long staticLiveUsageQueryFailureCount;
    private static volatile String staticLastLiveUsageQueryFailureClass = "";
    private static volatile boolean staticLastLiveUsageQuerySucceeded;
    private static volatile long staticGapDetectedCount;
    private static volatile long staticGapReconciliationCount;
    private static volatile long staticLastGapMs;
    private static volatile long staticLastReconciledMs;
    private static volatile long staticLastReconciliationCoverageMs;
    private static volatile String staticLastReconciliationResult = "SKIPPED";
    private static volatile String staticLastGapQueryFailureClass = "";
    private static volatile long staticHistoricalTargetMsRecovered;
    private static final RuntimeTrackingCounters runtimeTracking = new RuntimeTrackingCounters();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HandlerThread monitorThread;
    private Handler monitorHandler;
    private FlowMonitorLoop monitorLoop;
    private long livenessServiceGeneration;
    private volatile boolean monitorShutdownRequested;
    private boolean monitorShutdownComplete;
    private boolean serviceDestroyRecorded;
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
    private NativeFlowPermissionManager permissionManager;

    // Persisted monitor checkpoint. It is a small observation anchor, not a
    // history buffer; the UsageEvents window is the source of replay evidence.
    private long checkpointWallMs;
    private long checkpointElapsedMs;
    private boolean checkpointTargetActive;
    private boolean checkpointInteractionAvailable;
    private String checkpointForegroundPackage = "";
    private boolean checkpointInitialized;
    private long lastCheckpointPersistElapsedMs;

    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            if (monitorShutdownRequested) return;
            long startElapsed = SystemClock.elapsedRealtime();
            long startWall = System.currentTimeMillis();
            runtimeTracking.recordMonitorStart(livenessServiceGeneration, startElapsed, startWall);
            tick(startWall, startElapsed);
            // This timestamp is a process-local integrity signal. It is updated
            // only after tick() returns successfully and is never persisted.
            staticLastCompletedMonitorTickElapsedMs = SystemClock.elapsedRealtime();
            staticLastCompletedMonitorTickWallMs = System.currentTimeMillis();
            long endElapsed = SystemClock.elapsedRealtime();
            long endWall = System.currentTimeMillis();
            runtimeTracking.recordMonitorEnd(livenessServiceGeneration, endElapsed, endWall);
            if (monitorLoop != null) monitorLoop.scheduleNext();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            long now = System.currentTimeMillis();
            String action = intent == null ? null : intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                postMonitorCommand(() -> handleScreenOff(now));
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                postMonitorCommand(() -> handleScreenOn(now));
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        staticServiceRuntimeActive = true;
        staticLastTickAt = 0L;
        staticLastUsageEventAt = 0L;
        staticForegroundPackage = "";
        staticLastCompletedMonitorTickElapsedMs = 0L;
        staticLastCompletedMonitorTickWallMs = 0L;
        staticProtectionIntegrityFailureReason = "";
        staticLiveUsageQueryFailureCount = 0L;
        staticLastLiveUsageQueryFailureClass = "";
        staticLastLiveUsageQuerySucceeded = false;
        runtimeTracking.initializeProcessIdentity(
                Process.myPid(),
                SystemClock.elapsedRealtime(),
                System.currentTimeMillis()
        );
        Context applicationContext = getApplicationContext();
        staticServiceContext = applicationContext == null ? this : applicationContext;
        livenessServiceGeneration = runtimeTracking.recordServiceCreate(
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime()
        );
        monitorThread = new HandlerThread("FlowBreakMonitor", Process.THREAD_PRIORITY_DEFAULT);
        monitorThread.start();
        monitorHandler = new Handler(monitorThread.getLooper());
        staticMonitorThreadRef = monitorThread;
        monitorLoop = new FlowMonitorLoop(
                new HandlerScheduler(monitorHandler, livenessServiceGeneration),
                monitor
        );
        staticMonitorLooperIsMain = monitorHandler.getLooper() == Looper.getMainLooper();
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
        permissionManager = new NativeFlowPermissionManager(this);
        pullbackCoordinator = new PullbackSessionCoordinator();
        restCheatTracker = new RestCheatTracker();
        overlayController = new FlowOverlayController(
                this,
                (android.view.WindowManager) getSystemService(WINDOW_SERVICE),
                mainHandler,
                new FlowOverlayController.Callbacks() {
                    @Override public void onStartRest() { openRest(); }

                    @Override public boolean onEmergencyLongPress() {
                        return runEmergencyUnlockOnMonitorAndWait();
                    }

                    @Override public void onEmergencyExhausted() {
                        postMonitorCommand(() -> alert(
                                "今日紧急使用已用完",
                                "完成休息后仍可正常获得访问窗口。"
                        ));
                    }

                    @Override public long currentSessionMs() {
                        return staticSessionMs;
                    }

                    @Override public int currentLimitMinutes() {
                        return staticLimitMinutes;
                    }

                    @Override public boolean allowEmergencyUnlock() {
                        return staticAllowEmergencyUnlock;
                    }
                }
        );
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, filter);
        // Establish the engine snapshot before any later service command can
        // reach the monitor owner (for example BEGIN_REST after a cold start).
        postMonitorCommand(this::load);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        runtimeTracking.recordServiceStartCommand(
                livenessServiceGeneration,
                toSafeStartAction(action),
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime()
        );
        if (ACTION_STOP.equals(action)) {
            postMonitorCommand(() -> stopMonitoring(startId));
            return START_NOT_STICKY;
        }

        if (NativeFlowPermissionManager.requiresUnsupportedOemFailClosed(Build.MANUFACTURER)) {
            Log.w("FlowForegroundService",
                    NativeFlowPermissionManager.UNSUPPORTED_DEVICE_FOR_RELIABLE_MONITORING);
            rejectUnsupportedRuntime(startId);
            return START_NOT_STICKY;
        }

        // Foreground promotion stays on the Service/main entry path. The
        // engine command and the monitor loop are serialized on the worker.
        promoteToForeground();
        postMonitorCommand(() -> handleEngineCommand(action));
        return START_STICKY;
    }

    /** Stop a runtime start attempt without deleting the user's configuration. */
    private void rejectUnsupportedRuntime(int startId) {
        stopMonitorLoop(MonitorLivenessDiagnostics.LOOP_REASON_UNSUPPORTED_RUNTIME);
        postOverlayAction(() -> overlayController.dismissAll());
        mainHandler.post(() -> {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelfResult(startId);
        });
    }

    private void handleEngineCommand(String action) {
        if (ACTION_BEGIN_REST.equals(action)) {
            beginRestSession();
            postOverlayAction(() -> overlayController.dismissBlocker());
        } else if (ACTION_COMPLETE_REST.equals(action)) {
            // NativeFlowPlugin validates and persists a completed rest before
            // asking a possibly recreated service to refresh its in-memory state.
            load();
            persistState(true);
            postOverlayAction(() -> overlayController.dismissBlocker());
        } else if (ACTION_CANCEL_REST.equals(action)) {
            machine.cancelRest(limitMinutes * 60_000L);
            stateStore.clearActiveRestSession();
            anchorCheckpointToNow(
                    checkpointTargetActive,
                    checkpointInteractionAvailable,
                    checkpointForegroundPackage
            );
            persistState(true);
        } else if (ACTION_EMERGENCY.equals(action)) {
            machine.emergencyUnlock(System.currentTimeMillis(), EMERGENCY_GRACE_MS);
            stateStore.clearActiveRestSession();
            clearPullbackTracker();
            anchorCheckpointToNow(false, false, "");
            persistState(true);
            postOverlayAction(() -> overlayController.dismissBlocker());
        } else {
            load();
        }

        notificationController.updateServiceNotification(snapshot());
        startMonitorLoop(MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START);
    }

    private void stopMonitoring(int startId) {
        stopMonitorLoop(MonitorLivenessDiagnostics.LOOP_REASON_USER_STOP);
        monitoringEnabled = false;
        resetMonitoringLifecycle(System.currentTimeMillis(), SystemClock.elapsedRealtime());
        stateStore.setMonitoringEnabled(false);
        postOverlayAction(() -> overlayController.dismissBlocker());
        mainHandler.post(() -> {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelfResult(startId);
        });
    }

    /**
     * Clears only live protection lifecycle state. Historical usage rows stay
     * intact, while a later start must begin from a fresh non-target anchor.
     */
    private void resetMonitoringLifecycle(long nowWallMs, long nowElapsedMs) {
        if (usageAccumulator != null && stateStore != null && repository != null) {
            flushPendingUsage(true);
        }
        if (foregroundDetector != null) foregroundDetector.reset();
        if (usageAccumulator != null) usageAccumulator.resetObservation(nowWallMs);
        if (restCheatTracker != null) restCheatTracker.reset();
        staticForegroundPackage = "";
        if (machine != null) {
            machine.stopMonitoring(nowWallMs);
            machine.seedCheckpoint(nowWallMs, false);
        }
        if (stateStore != null) {
            stateStore.clearActiveRestSession();
        }
        if (pullbackCoordinator != null && stateStore != null) {
            clearPullbackTracker();
        }
        if (usageAccumulator != null) {
            usageAccumulator.restoreObservationAnchor(nowWallMs, "", false);
        }
        staticProtectionIntegrityFailureReason = "";
        if (machine != null) {
            recordCheckpoint(nowWallMs, nowElapsedMs, false, false, "");
            lastAnnouncedState = BlockStateMachine.State.IDLE;
            persistState(true);
        }
    }

    /**
     * Permission/query failures are an integrity boundary: preserve already
     * committed state, discard only the unverified live observation, and make
     * the next healthy cycle start at the current instant.
     */
    private void failClosedForIntegrity(String reason, long nowWallMs, long nowElapsedMs) {
        staticProtectionIntegrityFailureReason = reason == null || reason.isEmpty()
                ? "PROTECTION_INTEGRITY_FAILURE"
                : reason;
        if (usageAccumulator != null && stateStore != null && repository != null) {
            flushPendingUsage(true);
        }
        if (foregroundDetector != null) foregroundDetector.reset();
        if (usageAccumulator != null) usageAccumulator.resetObservation(nowWallMs);
        if (restCheatTracker != null) restCheatTracker.reset();
        staticForegroundPackage = "";
        if (machine != null) {
            machine.seedCheckpoint(nowWallMs, false);
        }
        if (usageAccumulator != null) {
            usageAccumulator.restoreObservationAnchor(nowWallMs, "", false);
        }
        if (machine != null) {
            recordCheckpoint(nowWallMs, nowElapsedMs, false, false, "");
            persistState(true);
        }
        postOverlayAction(() -> {
            overlayController.dismissBlocker();
            overlayController.dismissWarningBar();
            overlayController.dismissGraceCountdown();
        });
    }

    private void syncLiveUsageDiagnostics() {
        if (foregroundDetector == null) return;
        staticLiveUsageQueryFailureCount = foregroundDetector.getLiveUsageQueryFailureCount();
        staticLastLiveUsageQueryFailureClass = foregroundDetector.getLastLiveUsageQueryFailureClass();
        staticLastLiveUsageQuerySucceeded = foregroundDetector.wasLastLiveUsageQuerySuccessful();
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
                staticState,
                staticSessionMs,
                staticGraceUntil,
                staticLimitMinutes,
                staticTargetAppsEmpty
        );
    }

    private void load() {
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(stateStore, System.currentTimeMillis());
        Set<String> loadedTargets = result.config.targetApps;
        Set<String> runtimeTargets = loadedTargets == null
                ? Collections.emptySet()
                : new HashSet<>(loadedTargets);
        targetApps = Collections.unmodifiableSet(runtimeTargets);
        staticRuntimeTargetSnapshot = Collections.unmodifiableSet(runtimeTargets);
        staticRuntimeTargetCount = runtimeTargets.size();
        limitMinutes = result.config.limitMinutes;
        monitoringEnabled = result.config.monitoringEnabled;
        staticLimitMinutes = limitMinutes;
        staticTargetAppsEmpty = runtimeTargets.isEmpty();
        staticAllowEmergencyUnlock = result.config.allowEmergencyUnlock;
        machine = result.machine;
        FlowServiceStateStore.MachineSnapshot machineSnapshot = result.machineSnapshot;
        checkpointWallMs = machineSnapshot.checkpointWallMs;
        checkpointElapsedMs = machineSnapshot.checkpointElapsedMs;
        checkpointTargetActive = machineSnapshot.checkpointTargetActive;
        checkpointInteractionAvailable = machineSnapshot.checkpointInteractionAvailable;
        checkpointForegroundPackage = machineSnapshot.checkpointForegroundPackage;
        checkpointInitialized = isUsableCheckpoint(machineSnapshot, targetApps);
        if (checkpointInitialized) {
            machine.seedCheckpoint(checkpointWallMs, checkpointTargetActive);
            usageAccumulator.restoreObservationAnchor(
                    checkpointWallMs,
                    checkpointForegroundPackage,
                    checkpointTargetActive && checkpointInteractionAvailable
            );
        } else {
            usageAccumulator.resetObservation(System.currentTimeMillis());
        }
        restorePullbackTrackerFromSnapshot(result.pullbackSnapshot);
        lastAnnouncedState = machine.getState();
        publishState();
    }

    private static boolean isUsableCheckpoint(
            FlowServiceStateStore.MachineSnapshot snapshot,
            Set<String> targets
    ) {
        if (snapshot == null
                || snapshot.checkpointWallMs <= 0L
                || snapshot.checkpointElapsedMs <= 0L
                || snapshot.checkpointForegroundPackage == null) {
            return false;
        }
        if (!snapshot.checkpointTargetActive) return true;
        return snapshot.checkpointInteractionAvailable
                && !snapshot.checkpointForegroundPackage.isEmpty()
                && targets != null
                && targets.contains(snapshot.checkpointForegroundPackage);
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
            persistState(true);
            return;
        }
        stateStore.persistBeginRest(decision.startedAt, decision.requiredMs, decision.sessionId);
        machine.beginRest();
        anchorCheckpointToNow(false, false, "");
        persistState(true);
    }

    private void tick(long now, long nowElapsed) {
        boolean targetSetEmpty = targetApps == null || targetApps.isEmpty();
        boolean interactionAvailableNow = monitoringEnabled
                && !targetSetEmpty
                && isInteractionAvailable();
        runtimeTracking.recordTick(
                now,
                monitoringEnabled,
                targetSetEmpty,
                interactionAvailableNow
        );
        staticLastTickAt = now;
        stateStore.writeHeartbeatIfDue(now);
        if (!checkpointInitialized) {
            machine.seedCheckpoint(now, false);
            usageAccumulator.restoreObservationAnchor(now, "", false);
            recordCheckpoint(now, nowElapsed, false, false, "");
        }
        if (!monitoringEnabled) {
            runtimeTracking.recordMonitoringDisabledReturn();
            resetMonitoringLifecycle(now, nowElapsed);
            postOverlayAction(() -> overlayController.dismissBlocker());
            return;
        }
        if (targetSetEmpty) {
            runtimeTracking.recordTargetSetEmptyReturn();
            resetMonitoringLifecycle(now, nowElapsed);
            postOverlayAction(() -> {
                overlayController.dismissBlocker();
                overlayController.dismissWarningBar();
            });
            return;
        }
        if (!interactionAvailableNow) {
            runtimeTracking.recordInteractionUnavailableReturn();
            trackPullbackOutcome(false, 0L, now);
            if (interactionAvailable && machine != null) {
                machine.onScreenOff(now);
            }
            interactionAvailable = false;
            foregroundDetector.reset();
            usageAccumulator.resetObservation(now);
            boolean hadForeground = staticForegroundPackage != null && !staticForegroundPackage.isEmpty();
            staticForegroundPackage = "";
            runtimeTracking.recordForegroundCleared(hadForeground);
            machine.seedCheckpoint(now, false);
            recordCheckpoint(now, nowElapsed, false, false, "");
            persistState();
            postOverlayAction(() -> {
                overlayController.dismissBlocker();
                overlayController.dismissWarningBar();
            });
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
            recordCheckpoint(now, nowElapsed, false, false, "");
            persistState();
        }
        if (permissionManager == null) {
            failClosedForIntegrity("PERMISSION_MANAGER_UNAVAILABLE", now, nowElapsed);
            return;
        }
        ProtectionPrerequisiteGate.Result prerequisites;
        try {
            prerequisites = permissionManager.evaluateCorePrerequisites(
                    monitoringEnabled,
                    targetApps.size()
            );
        } catch (Exception error) {
            String failureClass = error.getClass().getSimpleName();
            failClosedForIntegrity(
                    "PREREQUISITE_CHECK_"
                            + (failureClass == null || failureClass.isEmpty() ? "FAILED" : failureClass),
                    now,
                    nowElapsed
            );
            return;
        }
        if (!prerequisites.isAllowed()) {
            failClosedForIntegrity(prerequisites.reason.name(), now, nowElapsed);
            return;
        }
        long verifiedGapSafeEndWallMs = reconcileHistoricalGap(now, nowElapsed);
        String previousForeground = staticForegroundPackage;
        String foreground = foregroundDetector.detect(now);
        syncLiveUsageDiagnostics();
        if (!foregroundDetector.wasLastLiveUsageQuerySuccessful()) {
            String failureClass = foregroundDetector.getLastLiveUsageQueryFailureClass();
            failClosedForIntegrity(
                    "LIVE_USAGE_QUERY_" + (failureClass == null || failureClass.isEmpty()
                            ? "FAILED"
                            : failureClass),
                    now,
                    nowElapsed
            );
            return;
        }
        staticProtectionIntegrityFailureReason = "";
        boolean foregroundPresent = foreground != null && !foreground.isEmpty();
        boolean foregroundInRuntimeTargets = foregroundPresent && targetApps.contains(foreground);
        boolean foregroundIsSelfPackage = getPackageName().equals(foreground);
        boolean foregroundIsOtherNonTarget = foregroundPresent
                && !foregroundInRuntimeTargets
                && !foregroundIsSelfPackage;
        boolean foregroundChanged = previousForeground == null
                ? foreground != null
                : !previousForeground.equals(foreground);
        runtimeTracking.recordDetector(foregroundPresent, foregroundChanged);
        staticLastUsageEventAt = Math.max(staticLastUsageEventAt, foregroundDetector.getLastUsageEventAt());
        staticForegroundPackage = foreground;
        boolean isTarget = targetClassifier.isTarget(
                foreground,
                targetApps,
                stateStore.isWechatInVideoChannel(),
                stateStore.wechatInVideoChannelAt(),
                now
        );
        runtimeTracking.recordClassifierSignals(
                foregroundPresent,
                foregroundInRuntimeTargets,
                isTarget,
                foregroundIsSelfPackage,
                foregroundIsOtherNonTarget
        );

        long prevObservedAt = usageAccumulator.getLastObservedAt();
        long observedTargetMs = usageAccumulator.observe(isTarget, foreground, now);
        runtimeTracking.recordAccumulator(
                observedTargetMs,
                !usageAccumulator.getLastObservedTargetPackage().isEmpty()
        );
        if (isTarget && observedTargetMs > 0L) usageAccumulator.queue(foreground, observedTargetMs);
        trackPullbackOutcome(isTarget, observedTargetMs, now);

        // 休息期间防作弊：累计在目标应用上的停留时长超过 5 秒即取消休息
        // 用累计而非连续，避免用户每 4 秒切换一次绕过检测
        // 注意：observedTargetMs 在首次切回目标应用时为 0（continuedTarget=false），
        // 所以用 prevObservedAt 独立计算 delta，确保首次切回也能被计入
        BlockStateMachine.State currentState = machine.getState();
        restCheatTracker.observe(
                currentState == BlockStateMachine.State.RESTING,
                isTarget,
                prevObservedAt,
                now
        );
        if (restCheatTracker.triggered()) {
            RestCheatReplay.Decision liveRestCheat = RestCheatReplay.cancelTriggered(
                    machine,
                    restCheatTracker,
                    limitMinutes * 60_000L
            );
            if (liveRestCheat.cancelled) {
                handleRestCheatCancellation(
                        foreground,
                        liveRestCheat.accumulatedMs,
                        true,
                        now,
                        nowElapsed,
                        isTarget,
                        interactionAvailableNow,
                        foreground
                );
                persistState(true);
                flushPendingUsage(false);
                return;
            }
        }

        long machineSessionBeforeMs = machine.getSessionMs();
        BlockStateMachine.State state = verifiedGapSafeEndWallMs > 0L
                ? machine.updateAfterVerifiedGap(
                        isTarget,
                        foreground,
                        now,
                        verifiedGapSafeEndWallMs,
                        limitMinutes * 60_000L
                )
                : machine.update(
                        isTarget,
                        foreground,
                        now,
                        limitMinutes * 60_000L
                );
        long machineSessionAfterMs = machine.getSessionMs();
        runtimeTracking.recordMachineSession(machineSessionBeforeMs, machineSessionAfterMs);
        flushPendingUsage(false);

        boolean stateChanged = state != lastAnnouncedState;
        if (stateChanged) {
            onStateChanged(state, foreground);
            lastAnnouncedState = state;
        }
        if (state == BlockStateMachine.State.BLOCKED && isTarget) {
            String blockedPackage = foreground;
            long sessionMs = machine.getSessionMs();
            boolean allowEmergency = stateStore.allowEmergencyUnlock();
            postOverlayAction(() -> {
                overlayController.showBlocker(blockedPackage, sessionMs, allowEmergency);
                overlayController.dismissWarningBar();
            });
        } else {
            long sessionMs = machine.getSessionMs();
            int currentLimitMinutes = limitMinutes;
            // 渐进式提醒：PERCEPTION / COGNITION 显示顶部浮条
            if (state == BlockStateMachine.State.PERCEPTION && isTarget) {
                postOverlayAction(() -> {
                    overlayController.dismissBlocker();
                    overlayController.showWarningBar(1, sessionMs, currentLimitMinutes);
                });
            } else if (state == BlockStateMachine.State.COGNITION && isTarget) {
                postOverlayAction(() -> {
                    overlayController.dismissBlocker();
                    overlayController.showWarningBar(2, sessionMs, currentLimitMinutes);
                });
            } else if (state == BlockStateMachine.State.GRACE) {
                handleGraceCountdown();
                postOverlayAction(() -> {
                    overlayController.dismissBlocker();
                    overlayController.dismissWarningBar();
                });
            } else {
                postOverlayAction(() -> {
                    overlayController.dismissBlocker();
                    overlayController.dismissWarningBar();
                    overlayController.dismissGraceCountdown();
                });
            }
        }
        // GRACE 状态即使不在目标应用也要检查倒计时
        if (state == BlockStateMachine.State.GRACE) {
            handleGraceCountdown();
        }
        notificationController.updateServiceNotification(snapshot());
        recordCheckpoint(
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime(),
                isTarget,
                interactionAvailableNow,
                foreground
        );
        persistState(stateChanged);
    }

    private long reconcileHistoricalGap(long nowWallMs, long nowElapsedMs) {
        if (!checkpointInitialized) return 0L;

        long gapMs = nowElapsedMs - checkpointElapsedMs;
        if (gapMs <= GAP_RECONCILIATION_THRESHOLD_MS) return 0L;
        staticGapDetectedCount++;
        staticLastGapMs = gapMs;

        long wallGapMs = nowWallMs - checkpointWallMs;
        if (checkpointWallMs <= 0L
                || checkpointElapsedMs <= 0L
                || gapMs < 0L
                || wallGapMs <= 0L
                || Math.abs(wallGapMs - gapMs) > MAX_CLOCK_SKEW_MS) {
            markGapIncomplete(gapMs);
            discardUnreconciledGap(nowWallMs, nowElapsedMs);
            return 0L;
        }

        GapReconciliationWindow.Decision window = GapReconciliationWindow.classify(
                gapMs, checkpointWallMs, nowWallMs
        );
        if (window.kind == GapReconciliationWindow.Kind.SKIPPED_LIVE_WINDOW) {
            // The gap is above the replay threshold but the historical window
            // is not closed yet. Keep every anchor at the checkpoint so the
            // ordinary live path can account for the full clamped delta.
            markGapResult("SKIPPED_LIVE_WINDOW", 0L, 0L);
            return 0L;
        }
        long safeEndWallMs = window.safeEndWallMs;

        staticGapReconciliationCount++;
        ForegroundUsageDetector.GapQuery query = foregroundDetector.queryEventsForReconciliation(
                Math.max(0L, checkpointWallMs - ForegroundUsageDetector.LATE_EVENT_OVERLAP_MS),
                nowWallMs
        );
        if (!query.succeeded) {
            staticLastGapQueryFailureClass = query.failureClass;
            markGapIncomplete(gapMs);
            discardUnreconciledGap(nowWallMs, nowElapsedMs);
            return 0L;
        }

        TargetSessionGapReconciler.Result result = TargetSessionGapReconciler.reconcile(
                new TargetSessionGapReconciler.Input(
                        checkpointWallMs,
                        safeEndWallMs,
                        checkpointForegroundPackage,
                        checkpointTargetActive,
                        checkpointInteractionAvailable,
                        targetApps,
                        query.events
                )
        );
        if (result.status != TargetSessionGapReconciler.Status.SUCCESS) {
            markGapIncomplete(gapMs);
            discardUnreconciledGap(nowWallMs, nowElapsedMs);
            return 0L;
        }

        // Align the machine clock to the exact persisted checkpoint before
        // applying the verified segments. The normal live update remains the
        // authority for the final ten-second tail.
        machine.seedCheckpoint(checkpointWallMs, checkpointTargetActive);
        applyVerifiedGap(result);
        RestCheatReplay.Decision historicalRestCheat = RestCheatReplay.applyVerifiedTargetMs(
                machine,
                restCheatTracker,
                result.historicalTargetMsRecovered,
                limitMinutes * 60_000L
        );
        if (historicalRestCheat.cancelled) {
            handleRestCheatCancellation(
                    historicalRestCheatPackage(result),
                    historicalRestCheat.accumulatedMs,
                    false,
                    0L,
                    0L,
                    false,
                    false,
                    ""
            );
        }
        for (java.util.Map.Entry<String, Long> entry : result.usageMsByTargetPackage.entrySet()) {
            usageAccumulator.queue(entry.getKey(), entry.getValue());
        }
        usageAccumulator.restoreObservationAnchor(
                safeEndWallMs,
                result.finalForegroundPackage,
                result.finalTargetActive && result.finalInteractionAvailable
        );

        long safeEndElapsedMs = Math.max(
                checkpointElapsedMs,
                nowElapsedMs - TargetSessionGapReconciler.LIVE_TAIL_MS
        );
        checkpointWallMs = safeEndWallMs;
        checkpointElapsedMs = safeEndElapsedMs;
        checkpointTargetActive = result.finalTargetActive;
        checkpointInteractionAvailable = result.finalInteractionAvailable;
        checkpointForegroundPackage = result.finalForegroundPackage;
        checkpointInitialized = true;
        markGapResult(
                "SUCCESS",
                result.historicalTargetMsRecovered,
                result.coverageMs
        );
        // Commit the recovered usage and the new checkpoint before the live
        // tail is sampled. If the process dies after reconciliation, the next
        // instance must not replay the same closed interval again.
        flushPendingUsage(true);
        persistState(true);
        return safeEndWallMs;
    }

    private void applyVerifiedGap(TargetSessionGapReconciler.Result result) {
        long limitMs = limitMinutes * 60_000L;
        for (TargetSessionGapReconciler.TimelineSegment segment : result.segments) {
            boolean segmentTarget = segment.targetActive && segment.interactionAvailable;
            BlockStateMachine.State before = machine.getState();
            BlockStateMachine.State atStart = machine.updateVerifiedHistory(
                    segmentTarget,
                    segment.foregroundPackage,
                    segment.startWallMs,
                    limitMs
            );
            announceHistoricalStateChange(before, atStart, segment.foregroundPackage);
            before = machine.getState();
            BlockStateMachine.State after = machine.updateVerifiedHistory(
                    segmentTarget,
                    segment.foregroundPackage,
                    segment.endWallMs,
                    limitMs
            );
            announceHistoricalStateChange(before, after, segment.foregroundPackage);
        }

        BlockStateMachine.State before = machine.getState();
        long replayEnd = result.segments.isEmpty()
                ? machine.getLastCheckAt()
                : result.segments.get(result.segments.size() - 1).endWallMs;
        BlockStateMachine.State after = machine.updateVerifiedHistory(
                result.finalTargetActive && result.finalInteractionAvailable,
                result.finalForegroundPackage,
                replayEnd,
                limitMs
        );
        announceHistoricalStateChange(before, after, result.finalForegroundPackage);
    }

    private String historicalRestCheatPackage(TargetSessionGapReconciler.Result result) {
        if (result != null) {
            for (java.util.Map.Entry<String, Long> entry
                    : result.usageMsByTargetPackage.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0L) {
                    return entry.getKey();
                }
            }
            if (result.finalForegroundPackage != null && !result.finalForegroundPackage.isEmpty()) {
                return result.finalForegroundPackage;
            }
        }
        return "";
    }

    private void handleRestCheatCancellation(
            String packageName,
            long cheatAccumulated,
            boolean anchorLiveCheckpoint,
            long nowWallMs,
            long nowElapsedMs,
            boolean targetActive,
            boolean interactionAvailableNow,
            String foregroundPackage
    ) {
        // Historical replay has already advanced the checkpoint to its safe
        // endpoint. The live path anchors it to the current observation before
        // the forced persist. Both paths clear the active rest session and
        // share the same user-visible/event semantics.
        if (anchorLiveCheckpoint) {
            machine.seedCheckpoint(nowWallMs, targetActive);
            recordCheckpoint(
                    nowWallMs,
                    nowElapsedMs,
                    targetActive,
                    interactionAvailableNow,
                    foregroundPackage
            );
        }
        stateStore.clearActiveRestSession();
        repository.log(
                "rest_cheat",
                packageName == null ? "" : packageName,
                "",
                Math.max(0L, cheatAccumulated) / 1000L,
                ""
        );
        alert("休息已取消", "检测到在休息期间使用目标应用，未完成本次休息。");
        // The live branch returns before the normal state-change announcer;
        // historical replay must preserve that behavior as well.
        lastAnnouncedState = machine.getState();
    }

    private void announceHistoricalStateChange(
            BlockStateMachine.State before,
            BlockStateMachine.State after,
            String foregroundPackage
    ) {
        if (after == before) return;
        onStateChanged(after, foregroundPackage);
        lastAnnouncedState = after;
    }

    private void markGapIncomplete(long gapMs) {
        markGapResult("INCOMPLETE", 0L, 0L);
        staticLastGapMs = gapMs;
    }

    private void markGapResult(String result, long reconciledMs, long coverageMs) {
        staticLastReconciliationResult = result;
        staticLastReconciledMs = reconciledMs;
        staticLastReconciliationCoverageMs = coverageMs;
        if ("SUCCESS".equals(result)) {
            staticHistoricalTargetMsRecovered += reconciledMs;
        }
    }

    private void discardUnreconciledGap(long nowWallMs, long nowElapsedMs) {
        machine.seedCheckpoint(nowWallMs, false);
        usageAccumulator.restoreObservationAnchor(nowWallMs, "", false);
        recordCheckpoint(nowWallMs, nowElapsedMs, false, false, "");
    }

    private void recordCheckpoint(
            long wallMs,
            long elapsedMs,
            boolean targetActive,
            boolean interactionAvailable,
            String foregroundPackage
    ) {
        checkpointWallMs = Math.max(0L, wallMs);
        checkpointElapsedMs = Math.max(0L, elapsedMs);
        checkpointTargetActive = targetActive;
        checkpointInteractionAvailable = interactionAvailable;
        checkpointForegroundPackage = foregroundPackage == null ? "" : foregroundPackage;
        checkpointInitialized = checkpointWallMs > 0L && checkpointElapsedMs > 0L;
    }

    private void anchorCheckpointToNow(boolean targetActive, boolean interactionAvailable, String pkg) {
        long wallMs = System.currentTimeMillis();
        long elapsedMs = SystemClock.elapsedRealtime();
        machine.seedCheckpoint(wallMs, targetActive);
        usageAccumulator.restoreObservationAnchor(wallMs, pkg, targetActive && interactionAvailable);
        recordCheckpoint(wallMs, elapsedMs, targetActive, interactionAvailable, pkg);
    }

    private void handleScreenOff(long now) {
        trackPullbackOutcome(false, 0L, now);
        screenOn = false;
        interactionAvailable = false;
        foregroundDetector.reset();
        usageAccumulator.resetObservation(now);
        staticForegroundPackage = "";
        if (machine != null) {
            machine.onScreenOff(now);
            recordCheckpoint(now, SystemClock.elapsedRealtime(), false, false, "");
            persistState(true);
        }
        flushPendingUsage(true);
        postOverlayAction(() -> {
            overlayController.dismissBlocker();
            overlayController.dismissWarningBar();
        });
    }

    private void handleScreenOn(long now) {
        screenOn = true;
        interactionAvailable = false;
        foregroundDetector.reset();
        foregroundDetector.resetCursor(Math.max(0, now - ForegroundUsageDetector.INITIAL_EVENT_LOOKBACK_MS));
        if (machine != null) {
            machine.onScreenOn(now);
            recordCheckpoint(now, SystemClock.elapsedRealtime(), false, false, "");
            persistState(true);
        }
    }

    /** All service commands and lifecycle transitions enter the engine owner. */
    private void postMonitorCommand(Runnable command) {
        Handler worker = monitorHandler;
        if (worker == null || monitorShutdownRequested) return;
        worker.post(() -> {
            if (!monitorShutdownRequested) command.run();
        });
    }

    /** Overlay/View state is owned by the main Looper, never by the monitor. */
    private void postOverlayAction(Runnable action) {
        if (monitorShutdownRequested) return;
        mainHandler.post(() -> {
            if (!monitorShutdownRequested && overlayController != null) action.run();
        });
    }

    private boolean runEmergencyUnlockOnMonitorAndWait() {
        Handler worker = monitorHandler;
        if (worker == null || monitorShutdownRequested) return false;
        if (Looper.myLooper() == worker.getLooper()) return performEmergencyUnlock();

        AtomicBoolean unlocked = new AtomicBoolean(false);
        CountDownLatch completed = new CountDownLatch(1);
        if (!worker.post(() -> {
            try {
                unlocked.set(performEmergencyUnlock());
            } finally {
                completed.countDown();
            }
        })) {
            return false;
        }
        try {
            return completed.await(2L, TimeUnit.SECONDS) && unlocked.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean performEmergencyUnlock() {
        if (!EmergencyUnlockManager.tryUnlock(FlowForegroundService.this) || machine == null) {
            return false;
        }
        repository.log(
                "emergency_unlock",
                machine.getBlockedPackage(),
                "",
                300,
                ""
        );
        machine.emergencyUnlock(System.currentTimeMillis(), EMERGENCY_GRACE_MS);
        anchorCheckpointToNow(false, false, "");
        persistState(true);
        return true;
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

    private void startMonitorLoop(String reason) {
        if (monitorLoop == null || monitorLoop.isShutdown()) return;
        runtimeTracking.recordMonitorLoopStart(livenessServiceGeneration, reason);
        monitorLoop.start();
    }

    private void stopMonitorLoop(String reason) {
        if (monitorLoop == null || monitorLoop.isShutdown()) return;
        runtimeTracking.recordMonitorLoopStop(livenessServiceGeneration, reason);
        monitorLoop.stop();
    }

    private static String toSafeStartAction(String action) {
        if (action == null || ACTION_START.equals(action)) {
            return MonitorLivenessDiagnostics.ACTION_START;
        }
        if (ACTION_RELOAD.equals(action)) {
            return MonitorLivenessDiagnostics.ACTION_RELOAD;
        }
        if (ACTION_STOP.equals(action)) {
            return MonitorLivenessDiagnostics.ACTION_STOP;
        }
        if (ACTION_BEGIN_REST.equals(action)) {
            return MonitorLivenessDiagnostics.ACTION_BEGIN_REST;
        }
        if (ACTION_COMPLETE_REST.equals(action)) {
            return MonitorLivenessDiagnostics.ACTION_COMPLETE_REST;
        }
        if (ACTION_CANCEL_REST.equals(action)) {
            return MonitorLivenessDiagnostics.ACTION_CANCEL_REST;
        }
        if (ACTION_EMERGENCY.equals(action)) {
            return MonitorLivenessDiagnostics.ACTION_EMERGENCY;
        }
        return MonitorLivenessDiagnostics.ACTION_UNKNOWN;
    }

    private static final class HandlerScheduler implements FlowMonitorLoop.Scheduler {
        private final Handler handler;
        private final long serviceGeneration;

        HandlerScheduler(Handler handler, long serviceGeneration) {
            this.handler = handler;
            this.serviceGeneration = serviceGeneration;
        }

        @Override public void removeCallbacks(Runnable runnable) {
            handler.removeCallbacks(runnable);
            runtimeTracking.cancelPendingCallbackDeadline(serviceGeneration);
        }

        @Override public void post(Runnable runnable) {
            handler.post(runnable);
        }

        @Override public void postDelayed(Runnable runnable, long delayMs) {
            long scheduledElapsedMs = SystemClock.elapsedRealtime();
            boolean armed = handler.postDelayed(runnable, delayMs);
            if (armed) {
                runtimeTracking.recordCallbackScheduled(
                        serviceGeneration,
                        scheduledElapsedMs,
                        scheduledElapsedMs + delayMs
                );
            }
        }
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
        persistState(false);
    }

    private void persistState(boolean force) {
        publishState();
        if (stateStore == null || machine == null || pullbackCoordinator == null) return;
        long nowElapsedMs = SystemClock.elapsedRealtime();
        if (!CheckpointPersistenceGate.shouldPersist(
                lastCheckpointPersistElapsedMs,
                nowElapsedMs,
                force
        )) {
            return;
        }
        stateStore.persist(
                machine,
                pullbackCoordinator.snapshot(),
                new FlowServiceStateStore.Checkpoint(
                        checkpointWallMs,
                        checkpointElapsedMs,
                        checkpointTargetActive,
                        checkpointInteractionAvailable,
                        checkpointForegroundPackage
                )
        );
        lastCheckpointPersistElapsedMs = nowElapsedMs;
    }

    private void publishState() {
        if (machine == null) return;
        staticState = machine.getState();
        staticSessionMs = machine.getSessionMs();
        staticGraceUntil = machine.getGraceUntil();
        staticBlockedPackage = machine.getBlockedPackage();
        staticLimitMinutes = limitMinutes;
        staticTargetAppsEmpty = targetApps == null || targetApps.isEmpty();
    }

    public static BlockStateMachine.State getState() { return staticState; }
    public static long getSessionSeconds() { return staticSessionMs / 1000L; }
    public static long getGraceUntil() { return staticGraceUntil; }
    public static String getBlockedPackage() { return staticBlockedPackage; }
    public static long getLastTickAt() { return staticLastTickAt; }
    public static long getLastUsageEventAt() { return staticLastUsageEventAt; }
    public static String getForegroundPackage() { return staticForegroundPackage; }
    public static boolean isProtectionServiceRuntimeActive() {
        return staticServiceRuntimeActive;
    }
    public static boolean isMonitorThreadAlive() {
        HandlerThread thread = staticMonitorThreadRef;
        return staticServiceRuntimeActive && thread != null && thread.isAlive();
    }
    public static ProtectionRuntimeHealthEvaluator.Result getCurrentProtectionRuntimeHealth() {
        return ProtectionRuntimeHealthEvaluator.evaluate(
                staticServiceRuntimeActive,
                isMonitorThreadAlive(),
                staticLastCompletedMonitorTickElapsedMs,
                SystemClock.elapsedRealtime(),
                staticProtectionIntegrityFailureReason
        );
    }
    public static boolean isProtectionRuntimeHealthy() {
        return getCurrentProtectionRuntimeHealth().isHealthy();
    }
    public static long getLastCompletedMonitorTickElapsedMs() {
        return staticLastCompletedMonitorTickElapsedMs;
    }
    public static long getLastCompletedMonitorTickWallMs() {
        return staticLastCompletedMonitorTickWallMs;
    }
    public static String getProtectionIntegrityFailureReason() {
        return staticProtectionIntegrityFailureReason;
    }
    public static long getLiveUsageQueryFailureCount() {
        return staticLiveUsageQueryFailureCount;
    }
    public static String getLastLiveUsageQueryFailureClass() {
        return staticLastLiveUsageQueryFailureClass;
    }
    public static boolean wasLastLiveUsageQuerySuccessful() {
        return staticLastLiveUsageQuerySucceeded;
    }
    public static JSObject getRuntimeTrackingDiagnostics() {
        return getRuntimeTrackingDiagnostics(null);
    }

    static boolean targetSetsMatch(Set<String> runtimeTargets, Set<String> persistedTargets) {
        return runtimeTargets != null && persistedTargets != null
                && runtimeTargets.equals(persistedTargets);
    }

    public static JSObject getRuntimeTrackingDiagnostics(SharedPreferences persistedPreferences) {
        ProtectionRuntimeHealthEvaluator.Result runtimeHealth =
                getCurrentProtectionRuntimeHealth();
        if (persistedPreferences != null) {
            Set<String> persistedTargets = PreferenceUtils.getMigratedTargetApps(persistedPreferences);
            staticPersistedTargetCount = persistedTargets.size();
            staticRuntimeTargetsMatchPersisted = targetSetsMatch(
                    staticRuntimeTargetSnapshot,
                    persistedTargets
            );
            runtimeTracking.recordTargetSetIntegrity(
                    staticRuntimeTargetCount,
                    staticPersistedTargetCount,
                    staticRuntimeTargetsMatchPersisted
            );
        }

        RuntimeTrackingSnapshot snapshot = runtimeTracking.snapshot();
        JSObject result = new JSObject();
        result.put("tickCount", snapshot.tickCount);
        result.put("detectorNonEmptyTickCount", snapshot.detectorNonEmptyTickCount);
        result.put("targetTrueTickCount", snapshot.targetTrueTickCount);
        result.put("targetFalseTickCount", snapshot.targetFalseTickCount);
        result.put("consecutiveTargetTicks", snapshot.consecutiveTargetTicks);
        result.put("maxConsecutiveTargetTicks", snapshot.maxConsecutiveTargetTicks);
        result.put("lastClassifierIsTarget", snapshot.lastClassifierIsTarget);
        result.put("lastObservedTargetMs", snapshot.lastObservedTargetMs);
        result.put("positiveObservedTargetTickCount", snapshot.positiveObservedTargetTickCount);
        result.put("machineSessionBeforeMs", snapshot.machineSessionBeforeMs);
        result.put("machineSessionAfterMs", snapshot.machineSessionAfterMs);
        result.put("machineSessionIncreaseCount", snapshot.machineSessionIncreaseCount);
        result.put("lastTickDeltaMs", snapshot.lastTickDeltaMs);
        result.put("foregroundChangedCount", snapshot.foregroundChangedCount);
        result.put("lastForegroundPresent", snapshot.lastForegroundPresent);
        result.put("accumulatorLastTargetPresent", snapshot.accumulatorLastTargetPresent);
        result.put("runtimeTargetCount", snapshot.runtimeTargetCount);
        result.put("persistedTargetCount", snapshot.persistedTargetCount);
        result.put("runtimeTargetsMatchPersisted", snapshot.runtimeTargetsMatchPersisted);

        JSObject timing = new JSObject();
        timing.put("lastTickExecutionMs", snapshot.lastTickExecutionMs);
        timing.put("maxTickExecutionMs", snapshot.maxTickExecutionMs);
        timing.put("averageTickExecutionMs", snapshot.averageTickExecutionMs);
        timing.put("lastPostDelayGapMs", snapshot.lastPostDelayGapMs);
        timing.put("maxPostDelayGapMs", snapshot.maxPostDelayGapMs);
        timing.put("averagePostDelayGapMs", snapshot.averagePostDelayGapMs);
        timing.put("postDelayGapOver3000Count", snapshot.postDelayGapOver3000Count);
        timing.put("postDelayGapOver5000Count", snapshot.postDelayGapOver5000Count);
        result.put("timing", timing);

        JSObject reasons = new JSObject();
        reasons.put("foregroundInRuntimeTargetTickCount", snapshot.foregroundInRuntimeTargetTickCount);
        reasons.put("foregroundNotInRuntimeTargetTickCount", snapshot.foregroundNotInRuntimeTargetTickCount);
        reasons.put("classifierFalseForegroundInRuntimeTargetCount", snapshot.classifierFalseForegroundInRuntimeTargetCount);
        reasons.put("classifierFalseForegroundNotInRuntimeTargetCount", snapshot.classifierFalseForegroundNotInRuntimeTargetCount);
        reasons.put("foregroundIsSelfPackageTickCount", snapshot.foregroundIsSelfPackageTickCount);
        reasons.put("foregroundIsOtherNonTargetTickCount", snapshot.foregroundIsOtherNonTargetTickCount);
        reasons.put("monitoringDisabledTickCount", snapshot.monitoringDisabledTickCount);
        reasons.put("targetSetEmptyTickCount", snapshot.targetSetEmptyTickCount);
        reasons.put("interactionUnavailableTickCount", snapshot.interactionUnavailableTickCount);
        result.put("reasonCounters", reasons);
        result.put("gapDetectedCount", staticGapDetectedCount);
        result.put("gapReconciliationCount", staticGapReconciliationCount);
        result.put("lastGapMs", staticLastGapMs);
        result.put("lastReconciledMs", staticLastReconciledMs);
        result.put("lastReconciliationCoverageMs", staticLastReconciliationCoverageMs);
        result.put("lastReconciliationResult", staticLastReconciliationResult);
        result.put("lastGapQueryFailureClass", staticLastGapQueryFailureClass);
        result.put("historicalTargetMsRecovered", staticHistoricalTargetMsRecovered);
        result.put("serviceRuntimeActive", staticServiceRuntimeActive);
        result.put("lastCompletedMonitorTickElapsedMs", staticLastCompletedMonitorTickElapsedMs);
        result.put("lastCompletedMonitorTickWallMs", staticLastCompletedMonitorTickWallMs);
        result.put("monitorThreadAlive", isMonitorThreadAlive());
        result.put("coreRuntimeHealthy", runtimeHealth.isHealthy());
        result.put("coreRuntimeHealthReason", runtimeHealth.reason);
        result.put("coreRuntimeHealthCheckedAtElapsedMs", runtimeHealth.nowElapsedMs);
        result.put("heartbeatFresh", runtimeHealth.heartbeatFresh);
        result.put("monitorLooperIsMain", staticMonitorLooperIsMain);
        result.put("protectionIntegrityFailureReason", staticProtectionIntegrityFailureReason);
        result.put("liveUsageQueryFailureCount", staticLiveUsageQueryFailureCount);
        result.put("lastLiveUsageQueryFailureClass", staticLastLiveUsageQueryFailureClass);
        result.put("lastLiveUsageQuerySucceeded", staticLastLiveUsageQuerySucceeded);

        MonitorLivenessDiagnostics.Snapshot liveness = runtimeTracking.livenessSnapshot();
        JSObject lifecycle = new JSObject();
        lifecycle.put("processPid", liveness.processPid);
        lifecycle.put("processInstanceStartedElapsedMs", liveness.processInstanceStartedElapsedMs);
        lifecycle.put("processInstanceStartedWallMs", liveness.processInstanceStartedWallMs);
        lifecycle.put("serviceInstanceId", liveness.serviceInstanceId);
        lifecycle.put("currentServiceGeneration", liveness.currentServiceGeneration);
        lifecycle.put("serviceCreateCount", liveness.serviceCreateCount);
        lifecycle.put("serviceInstanceStartedElapsedMs", liveness.serviceInstanceStartedElapsedMs);
        lifecycle.put("serviceInstanceStartedWallMs", liveness.serviceInstanceStartedWallMs);
        lifecycle.put("serviceStartCommandCount", liveness.serviceStartCommandCount);
        lifecycle.put("lastStartCommandElapsedMs", liveness.lastStartCommandElapsedMs);
        lifecycle.put("lastStartCommandWallMs", liveness.lastStartCommandWallMs);
        lifecycle.put("lastStartCommandAction", liveness.lastStartCommandAction);
        lifecycle.put("lastStartCommandServiceInstanceId", liveness.lastStartCommandServiceInstanceId);
        lifecycle.put("lastStartCommandServiceGeneration", liveness.lastStartCommandServiceGeneration);
        lifecycle.put("serviceDestroyCount", liveness.serviceDestroyCount);
        lifecycle.put("lastServiceDestroyElapsedMs", liveness.lastServiceDestroyElapsedMs);
        lifecycle.put("lastServiceDestroyWallMs", liveness.lastServiceDestroyWallMs);
        lifecycle.put("lastDestroyedServiceInstanceId", liveness.lastDestroyedServiceInstanceId);
        lifecycle.put("lastDestroyedServiceGeneration", liveness.lastDestroyedServiceGeneration);
        lifecycle.put("serviceTaskRemovedCount", liveness.serviceTaskRemovedCount);
        lifecycle.put("lastTaskRemovedElapsedMs", liveness.lastTaskRemovedElapsedMs);
        lifecycle.put("lastTaskRemovedWallMs", liveness.lastTaskRemovedWallMs);
        lifecycle.put("lastTaskRemovedServiceInstanceId", liveness.lastTaskRemovedServiceInstanceId);
        lifecycle.put("lastTaskRemovedServiceGeneration", liveness.lastTaskRemovedServiceGeneration);
        lifecycle.put("monitorLoopActive", liveness.monitorLoopActive);
        lifecycle.put("monitorLoopShutdown", liveness.monitorLoopShutdown);
        lifecycle.put("monitorLoopStartCount", liveness.monitorLoopStartCount);
        lifecycle.put("monitorLoopStopCount", liveness.monitorLoopStopCount);
        lifecycle.put("monitorLoopShutdownCount", liveness.monitorLoopShutdownCount);
        lifecycle.put("lastMonitorLoopStartReason", liveness.lastMonitorLoopStartReason);
        lifecycle.put("lastMonitorLoopStopReason", liveness.lastMonitorLoopStopReason);
        lifecycle.put("lastMonitorLoopShutdownReason", liveness.lastMonitorLoopShutdownReason);
        HandlerThread currentMonitorThread = staticMonitorThreadRef;
        lifecycle.put("monitorThreadExists", currentMonitorThread != null);
        lifecycle.put(
                "monitorThreadIsAliveNow",
                currentMonitorThread != null && currentMonitorThread.isAlive()
        );
        lifecycle.put(
                "monitorThreadState",
                currentMonitorThread == null
                        ? "NOT_CREATED"
                        : currentMonitorThread.getState().name()
        );
        lifecycle.put("monitorLooperIsMain", staticMonitorLooperIsMain);
        result.put("lifecycle", lifecycle);

        JSObject scheduler = new JSObject();
        scheduler.put("periodMs", FlowMonitorLoop.PERIOD_MS);
        scheduler.put("lastMonitorStartElapsedMs", liveness.lastMonitorStartElapsedMs);
        scheduler.put("lastMonitorStartWallMs", liveness.lastMonitorStartWallMs);
        scheduler.put("lastMonitorEndElapsedMs", liveness.lastMonitorEndElapsedMs);
        scheduler.put("lastMonitorEndWallMs", liveness.lastMonitorEndWallMs);
        scheduler.put("lastCallbackScheduledElapsedMs", liveness.lastCallbackScheduledElapsedMs);
        scheduler.put("scheduledCallbackDueElapsedMs", liveness.scheduledCallbackDueElapsedMs);
        scheduler.put("callbackDeadlinePending", liveness.callbackDeadlinePending);
        scheduler.put("actualMonitorStartElapsedMs", liveness.actualMonitorStartElapsedMs);
        scheduler.put("callbackLatenessMs", liveness.callbackLatenessMs);
        scheduler.put("lastCallbackLatenessMs", liveness.callbackLatenessMs);
        scheduler.put("maxCallbackLatenessMs", liveness.maxCallbackLatenessMs);
        scheduler.put("callbackLatenessOver3000Count", liveness.callbackLatenessOver3000Count);
        scheduler.put("callbackLatenessOver5000Count", liveness.callbackLatenessOver5000Count);
        result.put("scheduler", scheduler);
        result.put("platform", getPlatformDiagnostics());

        JSArray recent = new JSArray();
        for (RecentTickSnapshot tick : snapshot.recentTicks) {
            JSObject row = new JSObject();
            row.put("sequence", tick.sequence);
            row.put("startDeltaMs", tick.startDeltaMs);
            row.put("executionMs", tick.executionMs);
            row.put("postDelayGapMs", tick.postDelayGapMs);
            row.put("monitoringEnabled", tick.monitoringEnabled);
            row.put("targetSetEmpty", tick.targetSetEmpty);
            row.put("interactionAvailable", tick.interactionAvailable);
            row.put("foregroundPresent", tick.foregroundPresent);
            row.put("foregroundInRuntimeTargets", tick.foregroundInRuntimeTargets);
            row.put("classifierEvaluated", tick.classifierEvaluated);
            row.put("classifierIsTarget", tick.classifierIsTarget);
            row.put("machineSessionBeforeMs", tick.machineSessionBeforeMs);
            row.put("machineSessionAfterMs", tick.machineSessionAfterMs);
            row.put("machineSessionDeltaMs", tick.machineSessionDeltaMs);
            recent.put(row);
        }
        result.put("recentTicks", recent);
        return result;
    }

    private static JSObject getPlatformDiagnostics() {
        JSObject result = new JSObject();
        result.put("isIgnoringBatteryOptimizations", "UNAVAILABLE");
        result.put("isBackgroundRestricted", "UNAVAILABLE");
        result.put("appStandbyBucket", "UNAVAILABLE");
        result.put("powerSaveMode", "UNAVAILABLE");

        Context context = staticServiceContext;
        if (context == null) return result;

        PowerManager powerManager = null;
        try {
            powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                try {
                    result.put(
                            "isIgnoringBatteryOptimizations",
                            powerManager.isIgnoringBatteryOptimizations(context.getPackageName())
                    );
                } catch (RuntimeException ignored) { }
                try {
                    result.put("powerSaveMode", powerManager.isPowerSaveMode());
                } catch (RuntimeException ignored) { }
            }
        } catch (RuntimeException ignored) { }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ActivityManager activityManager =
                        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (activityManager != null) {
                    result.put("isBackgroundRestricted", activityManager.isBackgroundRestricted());
                }
            } catch (RuntimeException ignored) { }
            try {
                UsageStatsManager usageStatsManager =
                        (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                if (usageStatsManager != null) {
                    result.put("appStandbyBucket", usageStatsManager.getAppStandbyBucket());
                }
            } catch (RuntimeException ignored) { }
        }
        return result;
    }

    public static int getCurrentLevel() {
        if (staticState == BlockStateMachine.State.PERCEPTION) return 1;
        if (staticState == BlockStateMachine.State.COGNITION) return 2;
        if (staticState == BlockStateMachine.State.BLOCKED) return 3;
        return 0;
    }
    public static long getTotalMinutes() { return staticSessionMs / 60_000L; }

    /**
     * Process-lifetime observations for the native tick path. This state is
     * intentionally not persisted and contains no foreground package history.
     */
    static final class RuntimeTrackingCounters {
        private static final int RECENT_TICK_CAPACITY = 64;
        private final MonitorLivenessDiagnostics liveness;

        private long tickCount;
        private long detectorNonEmptyTickCount;
        private long targetTrueTickCount;
        private long targetFalseTickCount;
        private long consecutiveTargetTicks;
        private long maxConsecutiveTargetTicks;
        private boolean lastClassifierIsTarget;
        private long lastObservedTargetMs;
        private long positiveObservedTargetTickCount;
        private long machineSessionBeforeMs;
        private long machineSessionAfterMs;
        private long machineSessionIncreaseCount;
        private long lastTickDeltaMs;
        private long foregroundChangedCount;
        private boolean lastForegroundPresent;
        private boolean accumulatorLastTargetPresent;
        private long lastTickAt;

        private int runtimeTargetCount;
        private int persistedTargetCount;
        private boolean runtimeTargetsMatchPersisted;

        private long lastTickExecutionMs;
        private long maxTickExecutionMs;
        private long sumTickExecutionMs;
        private long tickExecutionSampleCount;
        private long lastPostDelayGapMs;
        private long maxPostDelayGapMs;
        private long sumPostDelayGapMs;
        private long postDelayGapSampleCount;
        private long postDelayGapOver3000Count;
        private long postDelayGapOver5000Count;
        private long lastMonitorStartElapsed;
        private long previousMonitorEndElapsed;
        private long nextSequence;

        private long foregroundInRuntimeTargetTickCount;
        private long foregroundNotInRuntimeTargetTickCount;
        private long classifierFalseForegroundInRuntimeTargetCount;
        private long classifierFalseForegroundNotInRuntimeTargetCount;
        private long foregroundIsSelfPackageTickCount;
        private long foregroundIsOtherNonTargetTickCount;
        private long monitoringDisabledTickCount;
        private long targetSetEmptyTickCount;
        private long interactionUnavailableTickCount;

        private final RecentTick[] recentTickRing = new RecentTick[RECENT_TICK_CAPACITY];
        private int recentTickSize;
        private int recentTickWriteIndex;
        private RecentTick currentTick;

        RuntimeTrackingCounters() {
            liveness = createLivenessDiagnostics();
        }

        private static MonitorLivenessDiagnostics createLivenessDiagnostics() {
            try {
                return new MonitorLivenessDiagnostics(
                        Process.myPid(),
                        SystemClock.elapsedRealtime(),
                        System.currentTimeMillis()
                );
            } catch (RuntimeException ignored) {
                // Android local JVM tests may not provide framework clock/PID
                // implementations. A real service fills these values in onCreate.
                return new MonitorLivenessDiagnostics(0, 0L, 0L);
            }
        }

        synchronized void initializeProcessIdentity(int pid, long elapsedMs, long wallMs) {
            liveness.initializeProcessIdentity(pid, elapsedMs, wallMs);
        }

        synchronized long recordServiceCreate(long wallMs, long elapsedMs) {
            return liveness.recordServiceCreate(wallMs, elapsedMs);
        }

        void recordServiceStartCommand(long serviceGeneration, String action, long wallMs,
                long elapsedMs) {
            liveness.recordServiceStartCommand(serviceGeneration, action, wallMs, elapsedMs);
        }

        void recordServiceDestroy(long serviceGeneration, long wallMs, long elapsedMs) {
            liveness.recordServiceDestroy(serviceGeneration, wallMs, elapsedMs);
        }

        void recordTaskRemoved(long serviceGeneration, long wallMs, long elapsedMs) {
            liveness.recordTaskRemoved(serviceGeneration, wallMs, elapsedMs);
        }

        void recordMonitorLoopStart(long serviceGeneration, String reason) {
            liveness.recordMonitorLoopStart(serviceGeneration, reason);
        }

        void recordMonitorLoopStop(long serviceGeneration, String reason) {
            liveness.recordMonitorLoopStop(serviceGeneration, reason);
        }

        void recordMonitorLoopShutdown(long serviceGeneration, String reason) {
            liveness.recordMonitorLoopShutdown(serviceGeneration, reason);
        }

        void recordCallbackScheduled(long serviceGeneration, long scheduledElapsedMs,
                long dueElapsedMs) {
            liveness.recordCallbackScheduled(serviceGeneration, scheduledElapsedMs, dueElapsedMs);
        }

        void cancelPendingCallbackDeadline(long serviceGeneration) {
            liveness.cancelPendingCallbackDeadline(serviceGeneration);
        }

        synchronized MonitorLivenessDiagnostics.Snapshot livenessSnapshot() {
            return liveness.snapshot();
        }

        synchronized boolean isCurrentServiceGeneration(long serviceGeneration) {
            return liveness.isCurrentServiceGeneration(serviceGeneration);
        }

        synchronized void recordMonitorStart(long serviceGeneration, long startElapsed,
                long startWall) {
            if (!liveness.recordMonitorStart(serviceGeneration, startElapsed, startWall)) return;
            long startDeltaMs = lastMonitorStartElapsed <= 0L
                    ? 0L
                    : Math.max(0L, startElapsed - lastMonitorStartElapsed);
            long postDelayGapMs = previousMonitorEndElapsed <= 0L
                    ? 0L
                    : Math.max(0L, startElapsed - previousMonitorEndElapsed);
            lastMonitorStartElapsed = startElapsed;
            lastPostDelayGapMs = postDelayGapMs;
            if (previousMonitorEndElapsed > 0L) {
                sumPostDelayGapMs += postDelayGapMs;
                postDelayGapSampleCount++;
                maxPostDelayGapMs = Math.max(maxPostDelayGapMs, postDelayGapMs);
                if (postDelayGapMs > 3_000L) postDelayGapOver3000Count++;
                if (postDelayGapMs > 5_000L) postDelayGapOver5000Count++;
            }
            currentTick = appendRecentTick(startDeltaMs, 0L, postDelayGapMs, startElapsed);
        }

        synchronized void recordMonitorEnd(long serviceGeneration, long endElapsed, long endWall) {
            if (!liveness.recordMonitorEnd(serviceGeneration, endElapsed, endWall)) return;
            if (currentTick == null || currentTick.startElapsed <= 0L) return;
            long executionMs = Math.max(0L, endElapsed - currentTick.startElapsed);
            currentTick.executionMs = executionMs;
            lastTickExecutionMs = executionMs;
            maxTickExecutionMs = Math.max(maxTickExecutionMs, executionMs);
            sumTickExecutionMs += executionMs;
            tickExecutionSampleCount++;
            previousMonitorEndElapsed = endElapsed;
            currentTick = null;
        }

        synchronized void recordTick(long now) {
            recordTick(now, false, false, false);
        }

        synchronized void recordTick(
                long now,
                boolean monitoringEnabled,
                boolean targetSetEmpty,
                boolean interactionAvailable
        ) {
            ensureCurrentTick();
            lastTickDeltaMs = lastTickAt <= 0L ? 0L : Math.max(0L, now - lastTickAt);
            lastTickAt = now;
            tickCount++;
            currentTick.monitoringEnabled = monitoringEnabled;
            currentTick.targetSetEmpty = targetSetEmpty;
            currentTick.interactionAvailable = interactionAvailable;
        }

        synchronized void recordDetector(boolean foregroundPresent, boolean foregroundChanged) {
            ensureCurrentTick();
            if (foregroundPresent) detectorNonEmptyTickCount++;
            if (foregroundChanged) foregroundChangedCount++;
            lastForegroundPresent = foregroundPresent;
            currentTick.foregroundPresent = foregroundPresent;
        }

        synchronized void recordForegroundCleared(boolean wasPresent) {
            ensureCurrentTick();
            if (wasPresent) foregroundChangedCount++;
            lastForegroundPresent = false;
            currentTick.foregroundPresent = false;
            currentTick.foregroundInRuntimeTargets = false;
            currentTick.classifierEvaluated = false;
            currentTick.classifierIsTarget = false;
        }

        synchronized void recordClassifierSignals(
                boolean foregroundPresent,
                boolean foregroundInRuntimeTargets,
                boolean isTarget,
                boolean foregroundIsSelfPackage,
                boolean foregroundIsOtherNonTarget
        ) {
            ensureCurrentTick();
            recordClassifier(isTarget);
            currentTick.foregroundPresent = foregroundPresent;
            currentTick.foregroundInRuntimeTargets = foregroundInRuntimeTargets;
            currentTick.classifierEvaluated = true;
            currentTick.classifierIsTarget = isTarget;
            if (foregroundInRuntimeTargets) {
                foregroundInRuntimeTargetTickCount++;
            } else {
                foregroundNotInRuntimeTargetTickCount++;
            }
            if (!isTarget) {
                if (foregroundInRuntimeTargets) {
                    classifierFalseForegroundInRuntimeTargetCount++;
                } else {
                    classifierFalseForegroundNotInRuntimeTargetCount++;
                }
            }
            if (foregroundIsSelfPackage) foregroundIsSelfPackageTickCount++;
            if (foregroundIsOtherNonTarget) foregroundIsOtherNonTargetTickCount++;
        }

        synchronized void recordClassifier(boolean isTarget) {
            lastClassifierIsTarget = isTarget;
            if (isTarget) {
                targetTrueTickCount++;
                consecutiveTargetTicks++;
                maxConsecutiveTargetTicks = Math.max(
                        maxConsecutiveTargetTicks,
                        consecutiveTargetTicks
                );
            } else {
                targetFalseTickCount++;
                consecutiveTargetTicks = 0L;
            }
            if (currentTick != null) {
                currentTick.classifierEvaluated = true;
                currentTick.classifierIsTarget = isTarget;
            }
        }

        synchronized void recordMonitoringDisabledReturn() {
            monitoringDisabledTickCount++;
        }

        synchronized void recordTargetSetEmptyReturn() {
            targetSetEmptyTickCount++;
        }

        synchronized void recordInteractionUnavailableReturn() {
            interactionUnavailableTickCount++;
        }

        synchronized void recordTargetSetIntegrity(
                int runtimeCount,
                int persistedCount,
                boolean matches
        ) {
            runtimeTargetCount = runtimeCount;
            persistedTargetCount = persistedCount;
            runtimeTargetsMatchPersisted = matches;
        }

        synchronized void recordAccumulator(long observedTargetMs, boolean targetPresent) {
            ensureCurrentTick();
            lastObservedTargetMs = observedTargetMs;
            if (observedTargetMs > 0L) positiveObservedTargetTickCount++;
            accumulatorLastTargetPresent = targetPresent;
        }

        synchronized void recordMachineSession(long beforeMs, long afterMs) {
            ensureCurrentTick();
            machineSessionBeforeMs = beforeMs;
            machineSessionAfterMs = afterMs;
            if (afterMs > beforeMs) machineSessionIncreaseCount++;
            currentTick.machineSessionBeforeMs = beforeMs;
            currentTick.machineSessionAfterMs = afterMs;
            currentTick.machineSessionDeltaMs = afterMs - beforeMs;
        }

        private void ensureCurrentTick() {
            if (currentTick == null) currentTick = appendRecentTick(0L, 0L, 0L, 0L);
        }

        private RecentTick appendRecentTick(
                long startDeltaMs,
                long executionMs,
                long postDelayGapMs,
                long startElapsed
        ) {
            RecentTick tick = new RecentTick(
                    ++nextSequence,
                    startDeltaMs,
                    executionMs,
                    postDelayGapMs,
                    startElapsed
            );
            recentTickRing[recentTickWriteIndex] = tick;
            recentTickWriteIndex = (recentTickWriteIndex + 1) % RECENT_TICK_CAPACITY;
            recentTickSize = Math.min(RECENT_TICK_CAPACITY, recentTickSize + 1);
            return tick;
        }

        synchronized RuntimeTrackingSnapshot snapshot() {
            RecentTickSnapshot[] recent = new RecentTickSnapshot[recentTickSize];
            int firstIndex = recentTickSize == RECENT_TICK_CAPACITY ? recentTickWriteIndex : 0;
            for (int i = 0; i < recentTickSize; i++) {
                RecentTick tick = recentTickRing[(firstIndex + i) % RECENT_TICK_CAPACITY];
                recent[i] = tick.snapshot();
            }
            return new RuntimeTrackingSnapshot(
                    tickCount,
                    detectorNonEmptyTickCount,
                    targetTrueTickCount,
                    targetFalseTickCount,
                    consecutiveTargetTicks,
                    maxConsecutiveTargetTicks,
                    lastClassifierIsTarget,
                    lastObservedTargetMs,
                    positiveObservedTargetTickCount,
                    machineSessionBeforeMs,
                    machineSessionAfterMs,
                    machineSessionIncreaseCount,
                    lastTickDeltaMs,
                    foregroundChangedCount,
                    lastForegroundPresent,
                    accumulatorLastTargetPresent,
                    runtimeTargetCount,
                    persistedTargetCount,
                    runtimeTargetsMatchPersisted,
                    lastTickExecutionMs,
                    maxTickExecutionMs,
                    tickExecutionSampleCount == 0L
                            ? 0D
                            : (double) sumTickExecutionMs / tickExecutionSampleCount,
                    lastPostDelayGapMs,
                    maxPostDelayGapMs,
                    postDelayGapSampleCount == 0L
                            ? 0D
                            : (double) sumPostDelayGapMs / postDelayGapSampleCount,
                    postDelayGapOver3000Count,
                    postDelayGapOver5000Count,
                    foregroundInRuntimeTargetTickCount,
                    foregroundNotInRuntimeTargetTickCount,
                    classifierFalseForegroundInRuntimeTargetCount,
                    classifierFalseForegroundNotInRuntimeTargetCount,
                    foregroundIsSelfPackageTickCount,
                    foregroundIsOtherNonTargetTickCount,
                    monitoringDisabledTickCount,
                    targetSetEmptyTickCount,
                    interactionUnavailableTickCount,
                    recent
            );
        }
    }

    static final class RecentTick {
        final long sequence;
        final long startDeltaMs;
        final long postDelayGapMs;
        final long startElapsed;
        long executionMs;
        boolean monitoringEnabled;
        boolean targetSetEmpty;
        boolean interactionAvailable;
        boolean foregroundPresent;
        boolean foregroundInRuntimeTargets;
        boolean classifierEvaluated;
        boolean classifierIsTarget;
        long machineSessionBeforeMs;
        long machineSessionAfterMs;
        long machineSessionDeltaMs;

        RecentTick(
                long sequence,
                long startDeltaMs,
                long executionMs,
                long postDelayGapMs,
                long startElapsed
        ) {
            this.sequence = sequence;
            this.startDeltaMs = startDeltaMs;
            this.executionMs = executionMs;
            this.postDelayGapMs = postDelayGapMs;
            this.startElapsed = startElapsed;
        }

        RecentTickSnapshot snapshot() {
            return new RecentTickSnapshot(
                    sequence,
                    startDeltaMs,
                    executionMs,
                    postDelayGapMs,
                    monitoringEnabled,
                    targetSetEmpty,
                    interactionAvailable,
                    foregroundPresent,
                    foregroundInRuntimeTargets,
                    classifierEvaluated,
                    classifierIsTarget,
                    machineSessionBeforeMs,
                    machineSessionAfterMs,
                    machineSessionDeltaMs
            );
        }
    }

    static final class RecentTickSnapshot {
        final long sequence;
        final long startDeltaMs;
        final long executionMs;
        final long postDelayGapMs;
        final boolean monitoringEnabled;
        final boolean targetSetEmpty;
        final boolean interactionAvailable;
        final boolean foregroundPresent;
        final boolean foregroundInRuntimeTargets;
        final boolean classifierEvaluated;
        final boolean classifierIsTarget;
        final long machineSessionBeforeMs;
        final long machineSessionAfterMs;
        final long machineSessionDeltaMs;

        RecentTickSnapshot(
                long sequence,
                long startDeltaMs,
                long executionMs,
                long postDelayGapMs,
                boolean monitoringEnabled,
                boolean targetSetEmpty,
                boolean interactionAvailable,
                boolean foregroundPresent,
                boolean foregroundInRuntimeTargets,
                boolean classifierEvaluated,
                boolean classifierIsTarget,
                long machineSessionBeforeMs,
                long machineSessionAfterMs,
                long machineSessionDeltaMs
        ) {
            this.sequence = sequence;
            this.startDeltaMs = startDeltaMs;
            this.executionMs = executionMs;
            this.postDelayGapMs = postDelayGapMs;
            this.monitoringEnabled = monitoringEnabled;
            this.targetSetEmpty = targetSetEmpty;
            this.interactionAvailable = interactionAvailable;
            this.foregroundPresent = foregroundPresent;
            this.foregroundInRuntimeTargets = foregroundInRuntimeTargets;
            this.classifierEvaluated = classifierEvaluated;
            this.classifierIsTarget = classifierIsTarget;
            this.machineSessionBeforeMs = machineSessionBeforeMs;
            this.machineSessionAfterMs = machineSessionAfterMs;
            this.machineSessionDeltaMs = machineSessionDeltaMs;
        }
    }

    static final class RuntimeTrackingSnapshot {
        final long tickCount;
        final long detectorNonEmptyTickCount;
        final long targetTrueTickCount;
        final long targetFalseTickCount;
        final long consecutiveTargetTicks;
        final long maxConsecutiveTargetTicks;
        final boolean lastClassifierIsTarget;
        final long lastObservedTargetMs;
        final long positiveObservedTargetTickCount;
        final long machineSessionBeforeMs;
        final long machineSessionAfterMs;
        final long machineSessionIncreaseCount;
        final long lastTickDeltaMs;
        final long foregroundChangedCount;
        final boolean lastForegroundPresent;
        final boolean accumulatorLastTargetPresent;
        final int runtimeTargetCount;
        final int persistedTargetCount;
        final boolean runtimeTargetsMatchPersisted;
        final long lastTickExecutionMs;
        final long maxTickExecutionMs;
        final double averageTickExecutionMs;
        final long lastPostDelayGapMs;
        final long maxPostDelayGapMs;
        final double averagePostDelayGapMs;
        final long postDelayGapOver3000Count;
        final long postDelayGapOver5000Count;
        final long foregroundInRuntimeTargetTickCount;
        final long foregroundNotInRuntimeTargetTickCount;
        final long classifierFalseForegroundInRuntimeTargetCount;
        final long classifierFalseForegroundNotInRuntimeTargetCount;
        final long foregroundIsSelfPackageTickCount;
        final long foregroundIsOtherNonTargetTickCount;
        final long monitoringDisabledTickCount;
        final long targetSetEmptyTickCount;
        final long interactionUnavailableTickCount;
        final RecentTickSnapshot[] recentTicks;

        RuntimeTrackingSnapshot(
                long tickCount,
                long detectorNonEmptyTickCount,
                long targetTrueTickCount,
                long targetFalseTickCount,
                long consecutiveTargetTicks,
                long maxConsecutiveTargetTicks,
                boolean lastClassifierIsTarget,
                long lastObservedTargetMs,
                long positiveObservedTargetTickCount,
                long machineSessionBeforeMs,
                long machineSessionAfterMs,
                long machineSessionIncreaseCount,
                long lastTickDeltaMs,
                long foregroundChangedCount,
                boolean lastForegroundPresent,
                boolean accumulatorLastTargetPresent,
                int runtimeTargetCount,
                int persistedTargetCount,
                boolean runtimeTargetsMatchPersisted,
                long lastTickExecutionMs,
                long maxTickExecutionMs,
                double averageTickExecutionMs,
                long lastPostDelayGapMs,
                long maxPostDelayGapMs,
                double averagePostDelayGapMs,
                long postDelayGapOver3000Count,
                long postDelayGapOver5000Count,
                long foregroundInRuntimeTargetTickCount,
                long foregroundNotInRuntimeTargetTickCount,
                long classifierFalseForegroundInRuntimeTargetCount,
                long classifierFalseForegroundNotInRuntimeTargetCount,
                long foregroundIsSelfPackageTickCount,
                long foregroundIsOtherNonTargetTickCount,
                long monitoringDisabledTickCount,
                long targetSetEmptyTickCount,
                long interactionUnavailableTickCount,
                RecentTickSnapshot[] recentTicks
        ) {
            this.tickCount = tickCount;
            this.detectorNonEmptyTickCount = detectorNonEmptyTickCount;
            this.targetTrueTickCount = targetTrueTickCount;
            this.targetFalseTickCount = targetFalseTickCount;
            this.consecutiveTargetTicks = consecutiveTargetTicks;
            this.maxConsecutiveTargetTicks = maxConsecutiveTargetTicks;
            this.lastClassifierIsTarget = lastClassifierIsTarget;
            this.lastObservedTargetMs = lastObservedTargetMs;
            this.positiveObservedTargetTickCount = positiveObservedTargetTickCount;
            this.machineSessionBeforeMs = machineSessionBeforeMs;
            this.machineSessionAfterMs = machineSessionAfterMs;
            this.machineSessionIncreaseCount = machineSessionIncreaseCount;
            this.lastTickDeltaMs = lastTickDeltaMs;
            this.foregroundChangedCount = foregroundChangedCount;
            this.lastForegroundPresent = lastForegroundPresent;
            this.accumulatorLastTargetPresent = accumulatorLastTargetPresent;
            this.runtimeTargetCount = runtimeTargetCount;
            this.persistedTargetCount = persistedTargetCount;
            this.runtimeTargetsMatchPersisted = runtimeTargetsMatchPersisted;
            this.lastTickExecutionMs = lastTickExecutionMs;
            this.maxTickExecutionMs = maxTickExecutionMs;
            this.averageTickExecutionMs = averageTickExecutionMs;
            this.lastPostDelayGapMs = lastPostDelayGapMs;
            this.maxPostDelayGapMs = maxPostDelayGapMs;
            this.averagePostDelayGapMs = averagePostDelayGapMs;
            this.postDelayGapOver3000Count = postDelayGapOver3000Count;
            this.postDelayGapOver5000Count = postDelayGapOver5000Count;
            this.foregroundInRuntimeTargetTickCount = foregroundInRuntimeTargetTickCount;
            this.foregroundNotInRuntimeTargetTickCount = foregroundNotInRuntimeTargetTickCount;
            this.classifierFalseForegroundInRuntimeTargetCount = classifierFalseForegroundInRuntimeTargetCount;
            this.classifierFalseForegroundNotInRuntimeTargetCount = classifierFalseForegroundNotInRuntimeTargetCount;
            this.foregroundIsSelfPackageTickCount = foregroundIsSelfPackageTickCount;
            this.foregroundIsOtherNonTargetTickCount = foregroundIsOtherNonTargetTickCount;
            this.monitoringDisabledTickCount = monitoringDisabledTickCount;
            this.targetSetEmptyTickCount = targetSetEmptyTickCount;
            this.interactionUnavailableTickCount = interactionUnavailableTickCount;
            this.recentTicks = recentTicks;
        }
    }

    private void handleGraceCountdown() {
        long graceUntil = machine.getGraceUntil();
        long now = System.currentTimeMillis();
        postOverlayAction(() -> overlayController.showGraceCountdown(graceUntil, now));
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
        runtimeTracking.recordTaskRemoved(
                livenessServiceGeneration,
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime()
        );
        // START_STICKY is the supported recovery path. Scheduling an exact
        // alarm to resurrect a background foreground-service is both brittle
        // on Android 15+ and unsuitable for a user-trust product.
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        staticServiceRuntimeActive = false;
        staticLastCompletedMonitorTickElapsedMs = 0L;
        staticLastCompletedMonitorTickWallMs = 0L;
        if (!serviceDestroyRecorded) {
            serviceDestroyRecorded = true;
            runtimeTracking.recordServiceDestroy(
                    livenessServiceGeneration,
                    System.currentTimeMillis(),
                    SystemClock.elapsedRealtime()
            );
        }
        monitorShutdownRequested = true;
        requestMonitorShutdown();
        overlayController.clearCallbacks();
        overlayController.dismissAll();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    private void requestMonitorShutdown() {
        Handler worker = monitorHandler;
        HandlerThread thread = monitorThread;
        if (worker == null || thread == null) return;
        long serviceGeneration = livenessServiceGeneration;
        runtimeTracking.cancelPendingCallbackDeadline(serviceGeneration);
        worker.removeCallbacksAndMessages(null);
        worker.postAtFrontOfQueue(() -> {
            if (monitorShutdownComplete) return;
            monitorShutdownComplete = true;
            if (monitorLoop != null && !monitorLoop.isShutdown()) {
                runtimeTracking.recordMonitorLoopStop(
                        serviceGeneration,
                        MonitorLivenessDiagnostics.LOOP_REASON_SERVICE_DESTROY
                );
                runtimeTracking.recordMonitorLoopShutdown(
                        serviceGeneration,
                        MonitorLivenessDiagnostics.LOOP_REASON_SERVICE_DESTROY
                );
                monitorLoop.shutdown();
            }
            flushPendingUsage(true);
            if (runtimeTracking.isCurrentServiceGeneration(serviceGeneration)) {
                persistState(true);
            }
            thread.quitSafely();
        });
    }
}
