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
    public static final String PREF_REST_STARTED_ELAPSED_AT = "restStartedElapsedAt";
    public static final String PREF_REST_STARTED_BOOT_COUNT = "restStartedBootCount";
    public static final String PREF_REST_CLOCK_INVALIDATED_SESSION_ID =
            "restClockInvalidatedSessionId";
    public static final String PREF_REST_REQUIRED_MS = "restRequiredMs";
    public static final String PREF_REST_SESSION_ID = "restSessionId";
    public static final String PREF_REST_START_TARGET_ACTIVE = "restStartTargetActive";
    public static final String PREF_REST_START_INTERACTION_AVAILABLE =
            "restStartInteractionAvailable";
    public static final String PREF_REST_START_FOREGROUND_PACKAGE = "restStartForegroundPackage";
    public static final String PREF_REST_TARGET_APPS = "restTargetApps";
    public static final String PREF_REST_TARGET_SESSION_ID = "restTargetSessionId";
    public static final String PREF_REST_SESSION_SEQUENCE = "restSessionSequence";
    public static final String EXTRA_EXPECTED_REST_SESSION_ID = "expectedRestSessionId";
    public static final String EXTRA_REST_BEGIN_REQUEST_TOKEN = "restBeginRequestToken";
    public static final String PREF_REST_BEGIN_REQUEST_TOKEN = "restBeginRequestToken";
    /** Token that originally created the currently persisted REST session. */
    public static final String PREF_REST_SESSION_BEGIN_REQUEST_TOKEN =
            "restSessionBeginRequestToken";
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
    private static volatile FlowForegroundService activeService;
    private static volatile MonitorHeartbeat staticMonitorHeartbeat = MonitorHeartbeat.EMPTY;
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
    private static final RestCheatEvidence restCheatEvidence = new RestCheatEvidence();

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
    private boolean historicalGapIntegrityFailure;
    private long lastCheckpointPersistElapsedMs;
    private long pausedRestObservationAt;
    private boolean pausedRestObservationTargetActive;
    private boolean initialRestRecoveryCaptured;
    private boolean restHistoryReplayPending;
    private boolean restCompletionPending;
    private long activeRestStartedAt;
    private long activeRestStartedElapsedAt;
    private int activeRestStartedBootCount = -1;
    private long activeRestRequiredMs;
    private long activeRestSessionId;
    private boolean activeRestStartTargetActive;
    private boolean activeRestStartInteractionAvailable;
    private String activeRestStartForegroundPackage = "";
    private Set<String> activeRestTargetApps;
    private boolean restCheatTimelineAppliedThisTick;
    private boolean monitoringLifecycleClean;
    private boolean monitoringLifecycleInitialized;
    private final Object restCompletionCallbacksLock = new Object();
    private final Set<RestCompletionCallback> pendingRestCompletionCallbacks = new HashSet<>();
    private final Object restCancellationCallbacksLock = new Object();
    private final Set<TrackedRestCancellationCallback> pendingRestCancellationCallbacks =
            new HashSet<>();
    private volatile String pendingRestBeginRequestToken = "";

    public interface RestCompletionCallback {
        void onSuccess(NativeFlowRestCoordinator.Result result);
        void onDeferred();
        void onRejected(String message, Exception error);
    }

    public interface RestCancellationCallback {
        void onCancelled();
        void onDeferred();
        void onRejected(String message);
    }

    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            if (monitorShutdownRequested) return;
            long serviceGeneration = livenessServiceGeneration;
            long startElapsed = SystemClock.elapsedRealtime();
            long startWall = System.currentTimeMillis();
            if (!runtimeTracking.recordMonitorStart(serviceGeneration, startElapsed, startWall)) {
                return;
            }
            boolean tickVerified = tick(startWall, startElapsed);
            long endElapsed = SystemClock.elapsedRealtime();
            long endWall = System.currentTimeMillis();
            runtimeTracking.recordMonitorEnd(serviceGeneration, endElapsed, endWall);
            // This timestamp is a process-local integrity signal. Publish it
            // atomically with the service generation and only after a tick
            // verifies its live protection inputs. A failed query must not
            // make rest completion appear safe, and a late old-instance
            // callback can never satisfy the current service's health check.
            synchronized (runtimeTracking) {
                if (tickVerified
                        && !monitorShutdownRequested
                        && runtimeTracking.isCurrentServiceGeneration(serviceGeneration)) {
                    staticMonitorHeartbeat = new MonitorHeartbeat(
                            serviceGeneration,
                            endElapsed,
                            endWall
                    );
                }
            }
            if (!monitorShutdownRequested && monitorLoop != null) monitorLoop.scheduleNext();
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
        synchronized (runtimeTracking) {
            staticServiceRuntimeActive = false;
            staticMonitorThreadRef = null;
            staticMonitorHeartbeat = MonitorHeartbeat.EMPTY;
            staticLastTickAt = 0L;
            staticLastUsageEventAt = 0L;
            staticForegroundPackage = "";
            staticProtectionIntegrityFailureReason = "";
            staticLiveUsageQueryFailureCount = 0L;
            staticLastLiveUsageQueryFailureClass = "";
            staticLastLiveUsageQuerySucceeded = false;
            runtimeTracking.initializeProcessIdentity(
                    Process.myPid(),
                    SystemClock.elapsedRealtime(),
                    System.currentTimeMillis()
            );
        }
        Context applicationContext = getApplicationContext();
        staticServiceContext = applicationContext == null ? this : applicationContext;
        synchronized (runtimeTracking) {
            livenessServiceGeneration = runtimeTracking.recordServiceCreate(
                    System.currentTimeMillis(),
                    SystemClock.elapsedRealtime()
            );
        }
        monitorThread = new HandlerThread("FlowBreakMonitor", Process.THREAD_PRIORITY_DEFAULT);
        monitorThread.start();
        monitorHandler = new Handler(monitorThread.getLooper());
        synchronized (runtimeTracking) {
            staticMonitorThreadRef = monitorThread;
        }
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
        synchronized (runtimeTracking) {
            staticServiceRuntimeActive = true;
        }
        activeService = this;
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
        long expectedRestSessionId = intent == null
                ? 0L
                : intent.getLongExtra(EXTRA_EXPECTED_REST_SESSION_ID, 0L);
        String beginRequestToken = intent == null
                ? ""
                : intent.getStringExtra(EXTRA_REST_BEGIN_REQUEST_TOKEN);
        if (ACTION_BEGIN_REST.equals(action) && beginRequestToken != null) {
            pendingRestBeginRequestToken = beginRequestToken;
        }
        postMonitorCommand(() -> handleEngineCommand(
                action,
                expectedRestSessionId,
                beginRequestToken
        ));
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

    private void handleEngineCommand(
            String action,
            long expectedRestSessionId,
            String beginRequestToken
    ) {
        if (ACTION_BEGIN_REST.equals(action)) {
            if (stateStore == null || !RestBeginRequestTokenStore.isCurrentRequest(
                    stateStore.preferences(), beginRequestToken
            )) {
                Log.i("FlowForegroundService", "Ignoring stale BEGIN_REST command");
                notificationController.updateServiceNotification(snapshot());
                startMonitorLoop(MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START);
                return;
            }
            if (machine != null
                    && machine.getState() == BlockStateMachine.State.RESTING
                    && !isPersistedRestClockValid(
                            System.currentTimeMillis(), SystemClock.elapsedRealtime()
                    )) {
                long staleSessionId = stateStore.preferences().getLong(
                        PREF_REST_SESSION_ID, 0L
                );
                if (!cancelInvalidRestOnMonitor(staleSessionId)) {
                    Log.w("FlowForegroundService",
                            "Unable to reset invalid REST clock before new begin");
                    clearPendingBeginRequestTokenIfMatches(beginRequestToken);
                    return;
                }
                load();
            }
            String persistedState = machine == null
                    ? BlockStateMachine.State.IDLE.name()
                    : machine.getState().name();
            if (!isRestEntryAllowedForState(persistedState)) {
                Log.w("FlowForegroundService",
                        "BEGIN_REST rejected: current protection runtime is unhealthy");
                postOverlayAction(() -> overlayController.dismissAll());
                clearPendingBeginRequestTokenIfMatches(beginRequestToken);
            } else {
                beginRestSession(beginRequestToken);
                postOverlayAction(() -> overlayController.dismissBlocker());
                clearPendingBeginRequestTokenIfMatches(beginRequestToken);
            }
        } else if (ACTION_COMPLETE_REST.equals(action)) {
            // Compatibility refresh only; user completion commits via the
            // monitor-owned request after a final history verification.
            load();
            persistState(true);
            postOverlayAction(() -> overlayController.dismissBlocker());
        } else if (ACTION_CANCEL_REST.equals(action)) {
            if (!cancelRestOnMonitor(expectedRestSessionId, beginRequestToken)) {
                Log.i("FlowForegroundService", "Ignoring stale CANCEL_REST command");
            }
        } else if (ACTION_EMERGENCY.equals(action)) {
            machine.emergencyUnlock(System.currentTimeMillis(), EMERGENCY_GRACE_MS);
            stateStore.clearActiveRestSession();
            clearActiveRestWindow();
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

    private boolean cancelRestOnMonitor(long expectedRestSessionId, String beginRequestToken) {
        long currentRestSessionId = stateStore == null
                ? 0L
                : stateStore.preferences().getLong(PREF_REST_SESSION_ID, 0L);
        String activeBeginRequestToken = stateStore == null
                ? ""
                : stateStore.preferences().getString(PREF_REST_BEGIN_REQUEST_TOKEN, "");
        if (machine == null || stateStore == null || !RestCancellationGate.shouldCancel(
                machine.getState(),
                expectedRestSessionId,
                currentRestSessionId,
                beginRequestToken,
                activeBeginRequestToken
        )) {
            return false;
        }
        long nowWallMs = System.currentTimeMillis();
        long nowElapsedMs = SystemClock.elapsedRealtime();
        BlockStateMachine.State resultingState = BlockStateMachine.stateFor(
                machine.getSessionMs(),
                limitMinutes * 60_000L
        );
        FlowServiceStateStore.Checkpoint checkpoint = new FlowServiceStateStore.Checkpoint(
                nowWallMs,
                nowElapsedMs,
                checkpointTargetActive,
                checkpointInteractionAvailable,
                checkpointForegroundPackage
        );
        if (!stateStore.commitRestCancellation(
                expectedRestSessionId,
                beginRequestToken,
                resultingState,
                machine.getSessionMs(),
                machine.getGraceUntil(),
                machine.getLeftTargetsAt(),
                machine.getBlockedPackage(),
                pullbackCoordinator == null ? null : pullbackCoordinator.snapshot(),
                checkpoint
        )) {
            Log.w("FlowForegroundService", "REST cancellation persistence was not committed");
            return false;
        }

        // Publish the in-memory transition only after the matching durable state
        // and session cleanup have committed together.
        machine.cancelRest(limitMinutes * 60_000L);
        clearActiveRestWindow();
        pausedRestObservationAt = 0L;
        pausedRestObservationTargetActive = false;
        machine.seedCheckpoint(nowWallMs, checkpointTargetActive);
        if (usageAccumulator != null) {
            usageAccumulator.restoreObservationAnchor(
                    nowWallMs,
                    checkpointForegroundPackage,
                    checkpointTargetActive && checkpointInteractionAvailable
            );
        }
        recordCheckpoint(
                nowWallMs,
                nowElapsedMs,
                checkpointTargetActive,
                checkpointInteractionAvailable,
                checkpointForegroundPackage
        );
        lastCheckpointPersistElapsedMs = nowElapsedMs;
        monitoringLifecycleClean = !monitoringEnabled
                && machine.getState() == BlockStateMachine.State.IDLE;
        publishState();
        return true;
    }

    /**
     * Reset a REST session whose monotonic clock evidence has become invalid.
     * This transition restores the pre-rest enforcement state and deliberately
     * discards the old foreground checkpoint, which can no longer be mapped to
     * a trusted wall-clock interval.
     */
    private boolean cancelInvalidRestOnMonitor(long expectedRestSessionId) {
        if (machine == null || stateStore == null) return false;
        SharedPreferences preferences = stateStore.preferences();
        String activeBeginToken = preferences.getString(PREF_REST_BEGIN_REQUEST_TOKEN, "");
        String nextBeginToken = nextBeginRequestTokenForCleanup(preferences, activeBeginToken);
        long nowWallMs = System.currentTimeMillis();
        long nowElapsedMs = SystemClock.elapsedRealtime();
        BlockStateMachine.State resultingState = BlockStateMachine.stateFor(
                machine.getSessionMs(), limitMinutes * 60_000L
        );
        FlowServiceStateStore.Checkpoint checkpoint = new FlowServiceStateStore.Checkpoint(
                nowWallMs, nowElapsedMs, false, false, ""
        );
        if (!stateStore.commitInvalidRestCancellation(
                expectedRestSessionId,
                activeBeginToken,
                nextBeginToken,
                resultingState,
                machine.getSessionMs(),
                machine.getGraceUntil(),
                machine.getLeftTargetsAt(),
                machine.getBlockedPackage(),
                pullbackCoordinator == null ? null : pullbackCoordinator.snapshot(),
                checkpoint,
                nowWallMs,
                nowElapsedMs,
                RestSessionClock.currentBootCount(this)
        )) {
            return false;
        }
        machine.cancelRest(limitMinutes * 60_000L);
        clearActiveRestWindow();
        pausedRestObservationAt = 0L;
        pausedRestObservationTargetActive = false;
        machine.seedCheckpoint(nowWallMs, false);
        if (usageAccumulator != null) {
            usageAccumulator.restoreObservationAnchor(nowWallMs, "", false);
        }
        recordCheckpoint(nowWallMs, nowElapsedMs, false, false, "");
        lastCheckpointPersistElapsedMs = nowElapsedMs;
        monitoringLifecycleClean = !monitoringEnabled
                && machine.getState() == BlockStateMachine.State.IDLE;
        lastAnnouncedState = machine.getState();
        publishState();
        return true;
    }

    private void clearPendingBeginRequestTokenIfMatches(String beginRequestToken) {
        if (beginRequestToken != null && beginRequestToken.equals(pendingRestBeginRequestToken)) {
            pendingRestBeginRequestToken = "";
        }
    }

    /**
     * Returns a newer BEGIN token that must survive monitor-owned cleanup.
     *
     * The plugin writes the latest token before queuing the command.  The
     * session-creation token lets us distinguish that newer request from the
     * token that belongs to the REST interval being cancelled, even when the
     * command has not reached onStartCommand yet.
     */
    private String nextBeginRequestTokenForCleanup(
            SharedPreferences preferences,
            String activeBeginToken
    ) {
        String active = activeBeginToken == null ? "" : activeBeginToken;
        String pending = pendingRestBeginRequestToken;
        if (!active.isEmpty() && pending != null && pending.equals(active)) {
            return active;
        }
        String sessionToken = preferences == null
                ? ""
                : preferences.getString(PREF_REST_SESSION_BEGIN_REQUEST_TOKEN, "");
        return !active.isEmpty() && !active.equals(sessionToken) ? active : "";
    }

    private void stopMonitoring(int startId) {
        stopMonitorLoop(MonitorLivenessDiagnostics.LOOP_REASON_USER_STOP);
        monitoringEnabled = false;
        if (!monitoringLifecycleClean
                || machine == null
                || machine.getState() != BlockStateMachine.State.IDLE) {
            resetMonitoringLifecycle(System.currentTimeMillis(), SystemClock.elapsedRealtime());
        }
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
        clearActiveRestWindow();
        if (pullbackCoordinator != null && stateStore != null) {
            clearPullbackTracker();
        }
        if (usageAccumulator != null) {
            usageAccumulator.restoreObservationAnchor(nowWallMs, "", false);
        }
        clearIntegrityFailureIfCurrent();
        pausedRestObservationAt = 0L;
        pausedRestObservationTargetActive = false;
        monitoringLifecycleClean = true;
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
        String normalizedReason = reason == null || reason.isEmpty()
                ? "PROTECTION_INTEGRITY_FAILURE"
                : reason;
        boolean forcePersistence;
        synchronized (runtimeTracking) {
            if (!runtimeTracking.isCurrentServiceGeneration(livenessServiceGeneration)) return;
            forcePersistence = shouldForceIntegrityFailurePersist(
                    staticProtectionIntegrityFailureReason
            );
            staticProtectionIntegrityFailureReason = normalizedReason;
        }
        if (usageAccumulator != null && stateStore != null && repository != null) {
            flushPendingUsage(forcePersistence);
        }
        String foregroundBeforeIntegrityFailure = staticForegroundPackage == null
                ? ""
                : staticForegroundPackage;
        boolean showBlockerAfterFailure = false;
        String blockerPackageAfterFailure = "";
        long blockerSessionAfterFailure = 0L;
        boolean allowEmergencyAfterFailure = false;
        if (foregroundDetector != null) foregroundDetector.resetForIntegrityFailure();
        if (usageAccumulator != null) usageAccumulator.resetObservation(nowWallMs);
        if (restCheatTracker != null) restCheatTracker.reset();
        pausedRestObservationAt = 0L;
        pausedRestObservationTargetActive = false;
        staticForegroundPackage = "";
        if (machine != null) {
            if (shouldCancelRestForIntegrityFailure(machine.getState())) {
                // A failed live query creates a blind interval. An active rest
                // cannot be completed after that interval because the service
                // could not verify that the user stayed away from targets.
                machine.cancelRest(limitMinutes * 60_000L);
                if (stateStore != null) stateStore.clearActiveRestSession();
                clearActiveRestWindow();
                lastAnnouncedState = machine.getState();
            }
            showBlockerAfterFailure = machine.getState() == BlockStateMachine.State.BLOCKED
                    && isRuntimeTargetPackage(foregroundBeforeIntegrityFailure, nowWallMs);
            if (showBlockerAfterFailure) {
                blockerPackageAfterFailure = foregroundBeforeIntegrityFailure;
                blockerSessionAfterFailure = machine.getSessionMs();
                allowEmergencyAfterFailure = stateStore != null
                        && stateStore.allowEmergencyUnlock();
            }
            machine.seedCheckpoint(nowWallMs, false);
        }
        if (usageAccumulator != null) {
            usageAccumulator.restoreObservationAnchor(nowWallMs, "", false);
        }
        if (machine != null) {
            recordCheckpoint(nowWallMs, nowElapsedMs, false, false, "");
            monitoringLifecycleClean = !monitoringEnabled
                    && machine.getState() == BlockStateMachine.State.IDLE;
            persistState(forcePersistence);
        }
        final boolean showBlocker = showBlockerAfterFailure;
        final String blockerPackage = blockerPackageAfterFailure;
        final long blockerSession = blockerSessionAfterFailure;
        final boolean allowEmergency = allowEmergencyAfterFailure;
        postOverlayAction(() -> {
            if (showBlocker) {
                overlayController.showBlocker(
                        blockerPackage,
                        blockerSession,
                        allowEmergency
                );
            } else {
                overlayController.dismissBlocker();
            }
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

    private void clearIntegrityFailureIfCurrent() {
        synchronized (runtimeTracking) {
            if (runtimeTracking.isCurrentServiceGeneration(livenessServiceGeneration)) {
                staticProtectionIntegrityFailureReason = "";
            }
        }
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
        long nowWall = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(
                        stateStore,
                        nowWall,
                        nowElapsed,
                        RestSessionClock.currentBootCount(this),
                        true
                );
        boolean previousMonitoringEnabled = monitoringEnabled;
        boolean lifecycleWasInitialized = monitoringLifecycleInitialized;
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
        if (machineSnapshot.persistedState == BlockStateMachine.State.RESTING
                && !result.restClockValid) {
            if (cancelInvalidRestOnMonitor(machineSnapshot.restSessionId)) {
                // Rebuild from the atomically committed enforcement state. In
                // particular, never replay history or auto-complete the stale
                // REST snapshot that triggered this reset.
                load();
                return;
            }
            clearActiveRestWindow();
            failClosedForIntegrity(
                    "REST_CLOCK_INVALIDATION_CANCEL_FAILED",
                    nowWall,
                    nowElapsed
            );
            return;
        }
        if (machine.getState() == BlockStateMachine.State.RESTING
                && machineSnapshot.restStartedAt > 0L
                && machineSnapshot.restRequiredMs > 0L) {
            setActiveRestWindow(machineSnapshot);
        } else {
            clearActiveRestWindow();
        }
        if (!initialRestRecoveryCaptured) {
            initialRestRecoveryCaptured = true;
            restHistoryReplayPending = machine.getState() == BlockStateMachine.State.RESTING
                    && machineSnapshot.restStartedAt > 0L
                    && result.restClockValid;
            restCompletionPending = result.restCompletionPending;
        } else if (result.restCompletionPending) {
            restCompletionPending = true;
        }
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
        if (!lifecycleWasInitialized) {
            monitoringLifecycleClean = !monitoringEnabled
                    && machine.getState() == BlockStateMachine.State.IDLE;
        } else if (previousMonitoringEnabled != monitoringEnabled) {
            // A real config transition needs one fresh cleanup decision. A
            // repeated reload in the same paused mode must not force a write
            // on every monitor tick.
            monitoringLifecycleClean = false;
        } else if (!monitoringEnabled
                && machine.getState() != BlockStateMachine.State.RESTING
                && machine.getState() != BlockStateMachine.State.IDLE) {
            monitoringLifecycleClean = false;
        }
        monitoringLifecycleInitialized = true;
        pausedRestObservationAt = 0L;
        pausedRestObservationTargetActive = false;
        lastAnnouncedState = machine.getState();
        publishState();
    }

    private boolean isPersistedRestClockValid(long nowWallMs, long nowElapsedMs) {
        if (stateStore == null) return false;
        SharedPreferences preferences = stateStore.preferences();
        long restSessionId = preferences.getLong(PREF_REST_SESSION_ID, 0L);
        return RestSessionClock.isValid(
                restSessionId,
                preferences.getLong(PREF_REST_CLOCK_INVALIDATED_SESSION_ID, 0L),
                preferences.getLong(PREF_REST_STARTED_AT, 0L),
                preferences.getLong(PREF_REST_STARTED_ELAPSED_AT, 0L),
                preferences.getInt(PREF_REST_STARTED_BOOT_COUNT, -1),
                nowWallMs,
                nowElapsedMs,
                RestSessionClock.currentBootCount(this)
        );
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

    private void beginRestSession(String beginRequestToken) {
        long existingStartedAt = stateStore.preferences().getLong(PREF_REST_STARTED_AT, 0L);
        boolean alreadyResting = machine.getState() == BlockStateMachine.State.RESTING;
        long nowElapsed = SystemClock.elapsedRealtime();
        long now = System.currentTimeMillis();
        int bootCount = RestSessionClock.currentBootCount(this);
        long nextSessionId = nextRestSessionId(now);
        if (nextSessionId <= 0L) {
            Log.w("FlowForegroundService", "Unable to allocate a unique REST session id");
            return;
        }
        RestSessionManager.BeginRestDecision decision = RestSessionManager.prepareBeginRest(
                alreadyResting,
                existingStartedAt,
                stateStore.restDurationSeconds(),
                now,
                nextSessionId
        );
        if (decision == null) {
            // React can remount after an orientation or WebView recreation.
            // Bind the newest accepted request to the same session rather than
            // granting a fresh timer, so its page can cancel the real session.
            if (!stateStore.bindActiveRestBeginRequestToken(beginRequestToken)) {
                Log.i("FlowForegroundService", "Ignoring stale duplicate BEGIN_REST request");
                return;
            }
            persistState(true);
            return;
        }
        String startForegroundPackage = staticForegroundPackage == null
                ? ""
                : staticForegroundPackage;
        Set<String> configuredTargets = stateStore.loadConfig().targetApps;
        Set<String> sessionTargetApps = configuredTargets == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(configuredTargets));
        boolean startInteractionAvailable = isInteractionAvailable();
        boolean startTargetActive = targetClassifier != null
                && targetClassifier.isTarget(
                        startForegroundPackage,
                        sessionTargetApps,
                        stateStore.isWechatInVideoChannel(),
                        stateStore.wechatInVideoChannelAt(),
                        now
                );
        boolean persisted = stateStore.persistBeginRest(
                decision.startedAt,
                decision.requiredMs,
                decision.sessionId,
                startTargetActive,
                startInteractionAvailable,
                startForegroundPackage,
                beginRequestToken,
                sessionTargetApps,
                nowElapsed,
                bootCount
        );
        if (!persisted) {
            Log.i("FlowForegroundService", "Ignoring stale BEGIN_REST session commit");
            return;
        }
        if (restCheatTracker != null) restCheatTracker.reset();
        machine.beginRest();
        setActiveRestWindow(
                decision.startedAt,
                decision.requiredMs,
                decision.sessionId,
                startTargetActive,
                startInteractionAvailable,
                startForegroundPackage,
                sessionTargetApps,
                nowElapsed,
                bootCount
        );
        restHistoryReplayPending = false;
        restCompletionPending = false;
        restCheatTimelineAppliedThisTick = false;
        restCheatEvidence.onRestStarted();
        pausedRestObservationAt = 0L;
        pausedRestObservationTargetActive = false;
        monitoringLifecycleClean = false;
        anchorCheckpointToNow(
                startTargetActive,
                startInteractionAvailable,
                startForegroundPackage
        );
        persistState(true);
    }

    /**
     * Allocates a persisted, strictly increasing REST session identity.
     * Wall-clock changes can move backwards; the sequence prevents an old
     * page's completion request from matching a later session by accident.
     */
    private long nextRestSessionId(long nowWallMs) {
        if (stateStore == null) return 0L;
        synchronized (RestSessionClock.LOCK) {
            SharedPreferences preferences = stateStore.preferences();
            long previous = preferences.getLong(PREF_REST_SESSION_SEQUENCE, 0L);
            long candidate = Math.max(1L, nowWallMs);
            if (candidate <= previous) {
                if (previous == Long.MAX_VALUE) return 0L;
                candidate = previous + 1L;
            }
            return preferences.edit()
                    .putLong(PREF_REST_SESSION_SEQUENCE, candidate)
                    .commit()
                    ? candidate
                    : 0L;
        }
    }

    private boolean tick(long now, long nowElapsed) {
        restCheatTimelineAppliedThisTick = false;
        boolean targetSetEmpty = targetApps == null || targetApps.isEmpty();
        boolean pausedResting = shouldRunPausedRestIntegrityTick(
                monitoringEnabled,
                machine == null ? null : machine.getState()
        );
        boolean interactionAvailableNow = !targetSetEmpty
                && (monitoringEnabled || pausedResting)
                && isInteractionAvailable();
        runtimeTracking.recordTick(
                now,
                monitoringEnabled,
                targetSetEmpty,
                interactionAvailableNow
        );
        staticLastTickAt = now;
        stateStore.writeHeartbeatIfDue(now);
        if (machine != null
                && machine.getState() == BlockStateMachine.State.RESTING
                && !isPersistedRestClockValid(now, nowElapsed)) {
            long invalidSessionId = stateStore.preferences().getLong(PREF_REST_SESSION_ID, 0L);
            if (cancelInvalidRestOnMonitor(invalidSessionId)) {
                notificationController.updateServiceNotification(snapshot());
                BlockStateMachine.State restoredState = machine.getState();
                String restoredForeground = staticForegroundPackage == null
                        ? "" : staticForegroundPackage;
                boolean restoredTarget = targetClassifier != null
                        && targetClassifier.isTarget(
                                restoredForeground,
                                targetApps,
                                stateStore.isWechatInVideoChannel(),
                                stateStore.wechatInVideoChannelAt(),
                                now
                        );
                if (restoredState == BlockStateMachine.State.BLOCKED && restoredTarget) {
                    long sessionMs = machine.getSessionMs();
                    boolean allowEmergency = stateStore.allowEmergencyUnlock();
                    postOverlayAction(() -> overlayController.showBlocker(
                            restoredForeground, sessionMs, allowEmergency
                    ));
                } else {
                    postOverlayAction(() -> overlayController.dismissBlocker());
                }
                return true;
            }
            failClosedForIntegrity("REST_CLOCK_INVALIDATION_CANCEL_FAILED", now, nowElapsed);
            return false;
        }
        if (!checkpointInitialized) {
            machine.seedCheckpoint(now, false);
            usageAccumulator.restoreObservationAnchor(now, "", false);
            recordCheckpoint(now, nowElapsed, false, false, "");
        }
        if (!monitoringEnabled) {
            runtimeTracking.recordMonitoringDisabledReturn();
            if (pausedResting) {
                return tickPausedRestIntegrity(
                        now,
                        nowElapsed,
                        targetSetEmpty,
                        interactionAvailableNow
                );
            }
            if (shouldResetMonitoringLifecycle(
                    monitoringLifecycleClean,
                    machine == null ? null : machine.getState())) {
                resetMonitoringLifecycle(now, nowElapsed);
            } else {
                // A manually started rest session is independent from the
                // monitoring switch. Keep its state and persisted session
                // until the owner completes or cancels it.
                notificationController.updateServiceNotification(snapshot());
            }
            postOverlayAction(() -> overlayController.dismissBlocker());
            return true;
        }
        if (targetSetEmpty) {
            runtimeTracking.recordTargetSetEmptyReturn();
            if (shouldResetMonitoringLifecycle(
                    monitoringLifecycleClean,
                    machine == null ? null : machine.getState())) {
                resetMonitoringLifecycle(now, nowElapsed);
            } else {
                // Manual rest is independent from the configured target set.
                // Keep an active RESTING session until the owner completes or
                // cancels it, just as when monitoring is manually paused.
                notificationController.updateServiceNotification(snapshot());
            }
            postOverlayAction(() -> {
                overlayController.dismissBlocker();
                overlayController.dismissWarningBar();
            });
            return true;
        }
        // The canonical paused marker is only valid until a normal configured
        // monitoring cycle becomes active again. Otherwise a later target-set
        // loss could incorrectly preserve a stale non-IDLE machine state.
        monitoringLifecycleClean = false;
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
            return true;
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
            return false;
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
            return false;
        }
        if (!prerequisites.isAllowed()) {
            failClosedForIntegrity(prerequisites.reason.name(), now, nowElapsed);
            return false;
        }
        RestHistoryReplayOutcome restHistoryOutcome = replayRestWindowHistory(now, nowElapsed);
        if (restHistoryOutcome == RestHistoryReplayOutcome.FAILED) return false;
        long verifiedGapSafeEndWallMs = reconcileHistoricalGap(
                now,
                nowElapsed,
                restHistoryOutcome != RestHistoryReplayOutcome.NOT_REQUIRED
        );
        if (historicalGapIntegrityFailure) {
            // An incomplete historical interval is an integrity failure, not
            // a successful no-op. Do not clear it with a later live query in
            // this same tick; fail closed before foreground classification.
            failClosedForIntegrity(
                    "HISTORICAL_GAP_RECONCILIATION_INCOMPLETE",
                    now,
                    nowElapsed
            );
            return false;
        }
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
            return false;
        }
        clearIntegrityFailureIfCurrent();
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
        boolean isRestTarget = isActiveRestTarget(foreground, now);
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
                isRestTarget,
                restCheatTimelineAppliedThisTick ? now : prevObservedAt,
                now
        );
        restCheatTimelineAppliedThisTick = false;
        if (currentState == BlockStateMachine.State.RESTING) {
            restCheatEvidence.recordLive(
                    false,
                    foregroundPresent,
                    foregroundInRuntimeTargets,
                    isTarget,
                    restCheatTracker.accumulatedMs(),
                    now
            );
        }
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
                return true;
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
        return true;
    }

    /**
     * Verifies a manually started REST session while the normal monitor is
     * paused. This deliberately does not run the normal state-machine or
     * usage-accounting path: it only supplies a trustworthy foreground
     * observation to the rest anti-cheat tracker.
     */
    private boolean tickPausedRestIntegrity(
            long now,
            long nowElapsed,
            boolean targetSetEmpty,
            boolean interactionAvailableNow
    ) {
        if (isPausedRestSafeNonTargetInterval(targetSetEmpty, interactionAvailableNow)) {
            handlePausedRestSafeNonTargetInterval(now, nowElapsed);
            return true;
        }
        if (permissionManager == null) {
            failClosedForIntegrity(
                    "PAUSED_REST_PERMISSION_MANAGER_UNAVAILABLE",
                    now,
                    nowElapsed
            );
            return false;
        }

        boolean hasUsageStats;
        try {
            hasUsageStats = permissionManager.hasUsageStats();
        } catch (Exception error) {
            failClosedForIntegrity(
                    "PAUSED_REST_USAGE_ACCESS_CHECK_" + safeExceptionName(error),
                    now,
                    nowElapsed
            );
            return false;
        }
        if (!hasUsageStats) {
            failClosedForIntegrity(
                    "PAUSED_REST_USAGE_ACCESS_MISSING",
                    now,
                    nowElapsed
            );
            return false;
        }
        if (foregroundDetector == null
                || targetClassifier == null
                || stateStore == null
                || restCheatTracker == null) {
            failClosedForIntegrity(
                    "PAUSED_REST_DETECTOR_UNAVAILABLE",
                    now,
                    nowElapsed
            );
            return false;
        }

        RestHistoryReplayOutcome restHistoryOutcome = replayRestWindowHistory(now, nowElapsed);
        if (restHistoryOutcome == RestHistoryReplayOutcome.FAILED) return false;
        if (restHistoryOutcome == RestHistoryReplayOutcome.CANCELLED
                || restHistoryOutcome == RestHistoryReplayOutcome.COMPLETED) {
            return true;
        }

        long previousPausedObservationAt = pausedRestObservationAt;
        boolean previousPausedTargetActive = pausedRestObservationTargetActive;
        String previousPausedForeground = staticForegroundPackage == null
                ? ""
                : staticForegroundPackage;
        String foreground;
        try {
            foreground = foregroundDetector.detect(now);
            syncLiveUsageDiagnostics();
        } catch (Exception error) {
            failClosedForIntegrity(
                    "PAUSED_REST_DETECTOR_" + safeExceptionName(error),
                    now,
                    nowElapsed
            );
            return false;
        }
        if (!isPausedRestIntegrityObservationTrustworthy(
                hasUsageStats,
                !targetSetEmpty,
                interactionAvailableNow,
                foregroundDetector.wasLastLiveUsageQuerySuccessful()
        )) {
            String failureClass = foregroundDetector.getLastLiveUsageQueryFailureClass();
            failClosedForIntegrity(
                    "PAUSED_REST_LIVE_USAGE_QUERY_"
                            + (failureClass == null || failureClass.isEmpty()
                            ? "FAILED"
                            : failureClass),
                    now,
                    nowElapsed
            );
            return false;
        }

        clearIntegrityFailureIfCurrent();
        foreground = foreground == null ? "" : foreground;
        staticLastUsageEventAt = Math.max(staticLastUsageEventAt, foregroundDetector.getLastUsageEventAt());
        staticForegroundPackage = foreground;
        boolean isTarget = targetClassifier.isTarget(
                foreground,
                targetApps,
                stateStore.isWechatInVideoChannel(),
                stateStore.wechatInVideoChannelAt(),
                now
        );
        boolean isRestTarget = isActiveRestTarget(foreground, now);
        if (restHistoryOutcome == RestHistoryReplayOutcome.NOT_REQUIRED
                && previousPausedObservationAt > 0L
                && now - previousPausedObservationAt > GAP_RECONCILIATION_THRESHOLD_MS) {
            if (!replayRestCheatGap(
                    previousPausedObservationAt,
                    now,
                    previousPausedForeground,
                    previousPausedTargetActive,
                    true,
                    nowElapsed
            )) {
                failClosedForIntegrity(
                        "PAUSED_REST_GAP_RECONCILIATION_INCOMPLETE",
                        now,
                        nowElapsed
                );
                return false;
            }
            if (machine.getState() != BlockStateMachine.State.RESTING) {
                persistState(true);
                notificationController.updateServiceNotification(snapshot());
                postOverlayAction(() -> {
                    overlayController.dismissBlocker();
                    overlayController.dismissWarningBar();
                    overlayController.dismissGraceCountdown();
                });
                return true;
            }
        }
        restCheatTracker.observe(
                true,
                isRestTarget,
                restCheatTimelineAppliedThisTick ? now : previousPausedObservationAt,
                now
        );
        restCheatTimelineAppliedThisTick = false;
        restCheatEvidence.recordLive(
                true,
                !foreground.isEmpty(),
                targetApps.contains(foreground),
                isTarget,
                restCheatTracker.accumulatedMs(),
                now
        );
        pausedRestObservationAt = now;
        pausedRestObservationTargetActive = isRestTarget;

        if (restCheatTracker.triggered()) {
            RestCheatReplay.Decision liveRestCheat = RestCheatReplay.cancelTriggered(
                    machine,
                    restCheatTracker,
                    limitMinutes * 60_000L
            );
            if (liveRestCheat.cancelled) {
                machine.seedCheckpoint(now, false);
                usageAccumulator.restoreObservationAnchor(now, "", false);
                recordCheckpoint(now, nowElapsed, false, false, "");
                handleRestCheatCancellation(
                        foreground,
                        liveRestCheat.accumulatedMs,
                        false,
                        0L,
                        0L,
                        false,
                        false,
                        ""
                );
                pausedRestObservationAt = 0L;
                pausedRestObservationTargetActive = false;
                monitoringLifecycleClean = !monitoringEnabled
                        && machine.getState() == BlockStateMachine.State.IDLE;
                persistState(true);
                notificationController.updateServiceNotification(snapshot());
                postOverlayAction(() -> {
                    overlayController.dismissBlocker();
                    overlayController.dismissWarningBar();
                    overlayController.dismissGraceCountdown();
                });
                return true;
            }
        }

        // A valid paused tick refreshes the process-local monitor heartbeat,
        // but never increments normal session or usage accounting.
        notificationController.updateServiceNotification(snapshot());
        postOverlayAction(() -> overlayController.dismissBlocker());
        return true;
    }

    /**
     * Keeps a paused manual REST valid when target-app cheating is impossible.
     * This path intentionally avoids integrity-failure handling: it must not
     * cancel REST or reset the cumulative anti-cheat tracker.
     */
    private void handlePausedRestSafeNonTargetInterval(long now, long nowElapsed) {
        if (foregroundDetector != null) foregroundDetector.reset();
        if (usageAccumulator != null) {
            usageAccumulator.restoreObservationAnchor(now, "", false);
        }
        staticForegroundPackage = "";
        pausedRestObservationAt = now;
        pausedRestObservationTargetActive = false;
        notificationController.updateServiceNotification(snapshot());
        postOverlayAction(() -> {
            overlayController.dismissBlocker();
            overlayController.dismissWarningBar();
            overlayController.dismissGraceCountdown();
        });
    }

    private static String safeExceptionName(Exception error) {
        if (error == null || error.getClass() == null) return "FAILED";
        String name = error.getClass().getSimpleName();
        return name == null || name.isEmpty() ? "FAILED" : name;
    }

    private long reconcileHistoricalGap(
            long nowWallMs,
            long nowElapsedMs,
            boolean restWindowHistoryReplayed
    ) {
        historicalGapIntegrityFailure = false;
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
            if (!restWindowHistoryReplayed
                    && machine.getState() == BlockStateMachine.State.RESTING) {
                if (!replayRestCheatGap(
                        checkpointWallMs,
                        nowWallMs,
                        checkpointForegroundPackage,
                        checkpointTargetActive,
                        checkpointInteractionAvailable,
                        nowElapsedMs
                )) {
                    markGapIncomplete(gapMs);
                    return 0L;
                }
            }
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
        if (machine.getState() == BlockStateMachine.State.RESTING
                && !isPersistedRestClockValid(nowWallMs, nowElapsedMs)) {
            cancelInvalidRestOnMonitor(activeRestSessionId);
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
        if (!restWindowHistoryReplayed && machine.getState() == BlockStateMachine.State.RESTING) {
            if (!hasActiveRestTargetSnapshot()) {
                markGapIncomplete(gapMs);
                discardUnreconciledGap(nowWallMs, nowElapsedMs);
                return 0L;
            }
            long restEndWallMs = activeRestWindowEnd();
            boolean restCheckpointTargetActive = isActiveRestTarget(
                    checkpointForegroundPackage,
                    checkpointWallMs
            );
            TargetSessionGapReconciler.Result restResult =
                    TargetSessionGapReconciler.reconcile(
                            new TargetSessionGapReconciler.Input(
                                    checkpointWallMs,
                                    safeEndWallMs,
                                    checkpointForegroundPackage,
                                    restCheckpointTargetActive,
                                    checkpointInteractionAvailable,
                                    activeRestTargetApps,
                                    query.events
                            )
                    );
            long restTargetMs = RestCheatReplay.verifiedTargetMsDuring(
                    restResult,
                    activeRestStartedAt,
                    restEndWallMs
            );
            if (restTargetMs < 0L) {
                markGapIncomplete(gapMs);
                discardUnreconciledGap(nowWallMs, nowElapsedMs);
                return 0L;
            }
            RestCheatReplay.Decision historicalRestCheat = RestCheatReplay.applyVerifiedTargetMs(
                    machine,
                    restCheatTracker,
                    restTargetMs,
                    limitMinutes * 60_000L
            );
            if (RestCheatEvidence.shouldRecordHistoricalReplay(true, restTargetMs)) {
                restCheatEvidence.recordHistoricalReplay(
                        restTargetMs,
                        historicalRestCheat.accumulatedMs,
                        nowWallMs
                );
            }
            if (historicalRestCheat.cancelled) {
                handleRestCheatCancellation(
                        historicalRestCheatPackage(restResult),
                        historicalRestCheat.accumulatedMs,
                        false,
                        0L,
                        0L,
                        false,
                        false,
                        ""
                );
            }
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

    /**
     * Reconstructs the REST-only slice of a gap that is still inside the
     * protected live tail. This does not move monitor checkpoints or usage
     * accounting anchors; it only feeds independently verified cheat time.
     */
    private boolean replayRestCheatGap(
            long gapStartWallMs,
            long gapEndWallMs,
            String startingForegroundPackage,
            boolean startingTargetActive,
            boolean startingInteractionAvailable,
            long nowElapsedMs
    ) {
        if (!hasActiveRestTargetSnapshot()) return false;
        if (!isPersistedRestClockValid(System.currentTimeMillis(), nowElapsedMs)) {
            cancelInvalidRestOnMonitor(activeRestSessionId);
            return false;
        }
        long restWindowEndWallMs = activeRestWindowEnd();
        if (activeRestStartedAt <= 0L
                || restWindowEndWallMs <= activeRestStartedAt
                || gapEndWallMs <= gapStartWallMs) {
            return false;
        }

        ForegroundUsageDetector.GapQuery query = foregroundDetector.queryEventsForReconciliation(
                Math.max(0L, gapStartWallMs - ForegroundUsageDetector.LATE_EVENT_OVERLAP_MS),
                gapEndWallMs
        );
        if (!query.succeeded) {
            staticLastGapQueryFailureClass = query.failureClass;
            return false;
        }
        if (!isPersistedRestClockValid(
                System.currentTimeMillis(), SystemClock.elapsedRealtime()
        )) {
            cancelInvalidRestOnMonitor(activeRestSessionId);
            return false;
        }
        TargetSessionGapReconciler.Result result = TargetSessionGapReconciler.reconcile(
                new TargetSessionGapReconciler.Input(
                        gapStartWallMs,
                        gapEndWallMs,
                        startingForegroundPackage,
                        isActiveRestTarget(startingForegroundPackage, gapStartWallMs),
                        startingInteractionAvailable,
                        activeRestTargetApps,
                        query.events
                )
        );
        long verifiedTargetMs = RestCheatReplay.verifiedTargetMsDuring(
                result,
                activeRestStartedAt,
                restWindowEndWallMs
        );
        if (verifiedTargetMs < 0L) return false;

        RestCheatReplay.Decision decision = RestCheatReplay.applyVerifiedTargetMs(
                machine,
                restCheatTracker,
                verifiedTargetMs,
                limitMinutes * 60_000L
        );
        restCheatTimelineAppliedThisTick = true;
        if (decision.cancelled) {
            handleRestCheatCancellation(
                    historicalRestCheatPackage(result),
                    decision.accumulatedMs,
                    false,
                    0L,
                    0L,
                    false,
                    false,
                    ""
            );
        }
        return true;
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
        SharedPreferences preferences = stateStore.preferences();
        long restSessionId = preferences.getLong(PREF_REST_SESSION_ID, 0L);
        String activeBeginToken = preferences.getString(PREF_REST_BEGIN_REQUEST_TOKEN, "");
        String nextBeginToken = nextBeginRequestTokenForCleanup(preferences, activeBeginToken);
        FlowServiceStateStore.Checkpoint checkpoint = anchorLiveCheckpoint
                ? new FlowServiceStateStore.Checkpoint(
                        nowWallMs,
                        nowElapsedMs,
                        targetActive,
                        interactionAvailableNow,
                        foregroundPackage
                )
                : null;
        if (!stateStore.commitAutomaticRestCancellation(
                restSessionId,
                activeBeginToken,
                nextBeginToken,
                machine.getState(),
                machine.getSessionMs(),
                machine.getGraceUntil(),
                machine.getLeftTargetsAt(),
                machine.getBlockedPackage(),
                pullbackCoordinator == null ? null : pullbackCoordinator.snapshot(),
                checkpoint
        )) {
            Log.w("FlowForegroundService", "REST cheat cancellation persistence was not committed");
            load();
            return;
        }
        clearActiveRestWindow();
        restCheatEvidence.recordCancellation(cheatAccumulated, System.currentTimeMillis());
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
        pausedRestObservationAt = 0L;
        pausedRestObservationTargetActive = false;
        monitoringLifecycleClean = !monitoringEnabled
                && machine.getState() == BlockStateMachine.State.IDLE;
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
        historicalGapIntegrityFailure = true;
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

    private enum RestHistoryReplayOutcome {
        NOT_REQUIRED,
        REPLAYED,
        CANCELLED,
        COMPLETED,
        FAILED
    }

    private void setActiveRestWindow(FlowServiceStateStore.MachineSnapshot snapshot) {
        setActiveRestWindow(
                snapshot.restStartedAt,
                snapshot.restRequiredMs,
                snapshot.restSessionId,
                snapshot.restStartTargetActive,
                snapshot.restStartInteractionAvailable,
                snapshot.restStartForegroundPackage,
                snapshot.restTargetApps,
                snapshot.restStartedElapsedAt,
                snapshot.restStartedBootCount
        );
    }

    private void setActiveRestWindow(
            long startedAt,
            long requiredMs,
            long sessionId,
            boolean startTargetActive,
            boolean startInteractionAvailable,
            String startForegroundPackage,
            Set<String> restTargetApps,
            long startedAtElapsed,
            int startedBootCount
    ) {
        activeRestStartedAt = Math.max(0L, startedAt);
        activeRestStartedElapsedAt = Math.max(0L, startedAtElapsed);
        activeRestStartedBootCount = startedBootCount;
        activeRestRequiredMs = Math.max(0L, requiredMs);
        activeRestSessionId = Math.max(0L, sessionId);
        activeRestStartTargetActive = startTargetActive;
        activeRestStartInteractionAvailable = startInteractionAvailable;
        activeRestStartForegroundPackage = startForegroundPackage == null
                ? ""
                : startForegroundPackage;
        activeRestTargetApps = restTargetApps == null
                ? null
                : Collections.unmodifiableSet(new HashSet<>(restTargetApps));
    }

    private boolean hasActiveRestTargetSnapshot() {
        return activeRestSessionId > 0L
                && activeRestTargetApps != null;
    }

    private boolean isActiveRestTarget(String packageName, long nowWallMs) {
        return hasActiveRestTargetSnapshot()
                && targetClassifier != null
                && stateStore != null
                && targetClassifier.isTarget(
                        packageName,
                        activeRestTargetApps,
                        stateStore.isWechatInVideoChannel(),
                        stateStore.wechatInVideoChannelAt(),
                        nowWallMs
                );
    }

    /**
     * Reconcile the blocker after a fail-closed reset using the last trusted
     * foreground package. A failed UsageEvents read must not hide an already
     * required blocker until the next monitor tick.
     */
    private boolean isRuntimeTargetPackage(String packageName, long nowWallMs) {
        if (packageName == null || packageName.isEmpty()
                || targetClassifier == null
                || targetApps == null
                || targetApps.isEmpty()
                || stateStore == null) {
            return false;
        }
        try {
            return targetClassifier.isTarget(
                    packageName,
                    targetApps,
                    stateStore.isWechatInVideoChannel(),
                    stateStore.wechatInVideoChannelAt(),
                    nowWallMs
            );
        } catch (Exception ignored) {
            return targetApps.contains(packageName);
        }
    }

    private void clearActiveRestWindow() {
        activeRestStartedAt = 0L;
        activeRestStartedElapsedAt = 0L;
        activeRestStartedBootCount = -1;
        activeRestRequiredMs = 0L;
        activeRestSessionId = 0L;
        activeRestStartTargetActive = false;
        activeRestStartInteractionAvailable = false;
        activeRestStartForegroundPackage = "";
        activeRestTargetApps = null;
        restHistoryReplayPending = false;
        restCompletionPending = false;
        restCheatTimelineAppliedThisTick = false;
        if (restCheatTracker != null) restCheatTracker.reset();
    }

    private long activeRestWindowEnd() {
        if (activeRestStartedAt <= 0L
                || activeRestStartedElapsedAt <= 0L
                || activeRestRequiredMs <= 0L) return 0L;
        return RestSessionClock.wallTimeAtElapsed(
                activeRestStartedAt,
                activeRestStartedElapsedAt,
                safeRestElapsedEnd()
        );
    }

    private long safeRestElapsedEnd() {
        if (activeRestStartedElapsedAt <= 0L || activeRestRequiredMs <= 0L) return 0L;
        return Long.MAX_VALUE - activeRestStartedElapsedAt < activeRestRequiredMs
                ? Long.MAX_VALUE
                : activeRestStartedElapsedAt + activeRestRequiredMs;
    }

    /**
     * Replays the persisted REST interval once after a service/process restart.
     * An expired REST is not committed to GRACE until this history is verified.
     */
    private RestHistoryReplayOutcome replayRestWindowHistory(long nowWallMs, long nowElapsedMs) {
        if (!restHistoryReplayPending) return RestHistoryReplayOutcome.NOT_REQUIRED;
        restHistoryReplayPending = false;

        if (!isPersistedRestClockValid(nowWallMs, nowElapsedMs)) {
            if (cancelInvalidRestOnMonitor(activeRestSessionId)) {
                return RestHistoryReplayOutcome.CANCELLED;
            }
            failClosedForIntegrity(
                    "REST_CLOCK_INVALIDATION_CANCEL_FAILED",
                    nowWallMs,
                    nowElapsedMs
            );
            return RestHistoryReplayOutcome.FAILED;
        }

        if (!hasActiveRestTargetSnapshot()) {
            failClosedForIntegrity(
                    "REST_TARGET_SNAPSHOT_MISSING",
                    nowWallMs,
                    nowElapsedMs
            );
            return RestHistoryReplayOutcome.FAILED;
        }

        long restWindowEndWallMs = activeRestWindowEnd();
        long verifiedEndElapsedMs = Math.min(
                nowElapsedMs,
                safeRestElapsedEnd()
        );
        long verifiedEndWallMs = RestSessionClock.wallTimeAtElapsed(
                activeRestStartedAt,
                activeRestStartedElapsedAt,
                verifiedEndElapsedMs
        );
        if (activeRestStartedAt <= 0L
                || activeRestStartedElapsedAt <= 0L
                || activeRestRequiredMs <= 0L
                || restWindowEndWallMs <= activeRestStartedAt
                || verifiedEndElapsedMs <= activeRestStartedElapsedAt
                || verifiedEndWallMs <= activeRestStartedAt
                || !activeRestStartInteractionAvailable) {
            failClosedForIntegrity(
                    "REST_HISTORY_RECONCILIATION_INCOMPLETE",
                    nowWallMs,
                    nowElapsedMs
            );
            return RestHistoryReplayOutcome.FAILED;
        }

        ForegroundUsageDetector.GapQuery query = foregroundDetector.queryEventsForReconciliation(
                Math.max(
                        0L,
                        activeRestStartedAt - ForegroundUsageDetector.LATE_EVENT_OVERLAP_MS
                ),
                verifiedEndWallMs
        );
        if (!query.succeeded) {
            failClosedForIntegrity(
                    "REST_HISTORY_USAGE_QUERY_" + query.failureClass,
                    nowWallMs,
                    nowElapsedMs
            );
            return RestHistoryReplayOutcome.FAILED;
        }

        long afterQueryWallMs = System.currentTimeMillis();
        long afterQueryElapsedMs = SystemClock.elapsedRealtime();
        if (!isPersistedRestClockValid(afterQueryWallMs, afterQueryElapsedMs)) {
            if (cancelInvalidRestOnMonitor(activeRestSessionId)) {
                return RestHistoryReplayOutcome.CANCELLED;
            }
            failClosedForIntegrity(
                    "REST_CLOCK_INVALIDATION_CANCEL_FAILED",
                    afterQueryWallMs,
                    afterQueryElapsedMs
            );
            return RestHistoryReplayOutcome.FAILED;
        }

        TargetSessionGapReconciler.Result result = TargetSessionGapReconciler.reconcile(
                new TargetSessionGapReconciler.Input(
                        activeRestStartedAt,
                        verifiedEndWallMs,
                        activeRestStartForegroundPackage,
                        activeRestStartTargetActive,
                        activeRestStartInteractionAvailable,
                        activeRestTargetApps,
                        query.events
                )
        );
        long verifiedTargetMs = RestCheatReplay.verifiedTargetMsDuring(
                result,
                activeRestStartedAt,
                verifiedEndWallMs
        );
        if (verifiedTargetMs < 0L) {
            failClosedForIntegrity(
                    "REST_HISTORY_RECONCILIATION_INCOMPLETE",
                    nowWallMs,
                    nowElapsedMs
            );
            return RestHistoryReplayOutcome.FAILED;
        }

        RestCheatReplay.Decision decision = RestCheatReplay.applyVerifiedTargetMs(
                machine,
                restCheatTracker,
                verifiedTargetMs,
                limitMinutes * 60_000L
        );
        restCheatTimelineAppliedThisTick = true;
        if (decision.cancelled) {
            handleRestCheatCancellation(
                    historicalRestCheatPackage(result),
                    decision.accumulatedMs,
                    false,
                    0L,
                    0L,
                    false,
                    false,
                    ""
            );
            persistState(true);
            notificationController.updateServiceNotification(snapshot());
            return RestHistoryReplayOutcome.CANCELLED;
        }

        if (restCompletionPending
                && RestSessionValidator.isComplete(
                        activeRestStartedElapsedAt,
                        activeRestRequiredMs,
                        afterQueryElapsedMs
                )) {
            long graceUntil = afterQueryWallMs + FlowServiceStateStore.GRACE_MS;
            if (!stateStore.persistAutoCompletedRest(
                    graceUntil,
                    activeRestSessionId,
                    afterQueryWallMs,
                    afterQueryElapsedMs,
                    RestSessionClock.currentBootCount(this)
            )) {
                failClosedForIntegrity(
                        "REST_AUTO_COMPLETION_PERSIST_FAILED",
                        nowWallMs,
                        nowElapsedMs
                );
                return RestHistoryReplayOutcome.FAILED;
            }
            machine.completeRest(afterQueryWallMs, FlowServiceStateStore.GRACE_MS);
            if (restCheatTracker != null) restCheatTracker.reset();
            clearActiveRestWindow();
            pausedRestObservationAt = 0L;
            pausedRestObservationTargetActive = false;
            lastAnnouncedState = machine.getState();
            persistState(true);
            notificationController.updateServiceNotification(snapshot());
            return RestHistoryReplayOutcome.COMPLETED;
        }
        return RestHistoryReplayOutcome.REPLAYED;
    }

    private void handleScreenOff(long now) {
        trackPullbackOutcome(false, 0L, now);
        screenOn = false;
        interactionAvailable = false;
        foregroundDetector.reset();
        usageAccumulator.resetObservation(now);
        pausedRestObservationAt = 0L;
        pausedRestObservationTargetActive = false;
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
        pausedRestObservationAt = 0L;
        pausedRestObservationTargetActive = false;
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

    /**
     * Queues REST completion on the sole state-machine owner. Usage history,
     * cheat cancellation, and the reward/GRACE commit therefore cannot race a
     * monitor tick or a matching CANCEL_REST command.
     */
    public static boolean requestRestCompletion(
            long expectedSessionId,
            String activity,
            NativeFlowRestCoordinator coordinator,
            RestCompletionCallback callback
    ) {
        FlowForegroundService service = activeService;
        if (service == null || service.monitorShutdownRequested || callback == null) return false;
        Handler worker = service.monitorHandler;
        if (worker == null || !service.isProtectionServiceRuntimeActive()) return false;
        RestCompletionCallback trackedCallback = service.trackRestCompletionCallback(callback);
        synchronized (service.restCompletionCallbacksLock) {
            if (service.monitorShutdownRequested || activeService != service) return false;
            service.pendingRestCompletionCallbacks.add(trackedCallback);
        }
        boolean queued = worker.post(() -> {
            if (service.monitorShutdownRequested || activeService != service) {
                trackedCallback.onDeferred();
                return;
            }
            service.completeRestOnMonitor(
                    expectedSessionId,
                    activity,
                    coordinator,
                    trackedCallback
            );
        });
        if (!queued) trackedCallback.onDeferred();
        // Once registered, every path completes the callback asynchronously
        // or defers it, so the caller must not reject it a second time.
        return true;
    }

    /**
     * Queues REST cancellation on the sole state-machine owner. The caller is
     * notified only after the monitor has either changed the REST state or
     * rejected the request as stale; posting an intent alone is not success.
     */
    public static boolean requestRestCancellation(
            long expectedRestSessionId,
            String beginRequestToken,
            RestCancellationCallback callback
    ) {
        FlowForegroundService service = activeService;
        if (service == null || service.monitorShutdownRequested || callback == null) return false;
        Handler worker = service.monitorHandler;
        if (worker == null || !service.isProtectionServiceRuntimeActive()) return false;

        TrackedRestCancellationCallback trackedCallback =
                service.trackRestCancellationCallback(callback);
        synchronized (service.restCancellationCallbacksLock) {
            if (service.monitorShutdownRequested || activeService != service) return false;
            service.pendingRestCancellationCallbacks.add(trackedCallback);
        }
        boolean queued = worker.post(() -> {
            if (!trackedCallback.start()) return;
            if (service.monitorShutdownRequested || activeService != service) {
                trackedCallback.onDeferred();
                return;
            }
            // cancelRestOnMonitor acquires the shared clock/token locks in a
            // single canonical order. Do not hold the token lock here or a
            // concurrent BEGIN could invert that order.
            boolean cancelled = service.cancelRestOnMonitor(
                    expectedRestSessionId,
                    beginRequestToken
            );
            if (!cancelled) {
                trackedCallback.onRejected(
                        "休息状态或请求已变化，未退出休息模式，请返回后重试。"
                );
                return;
            }
            service.notificationController.updateServiceNotification(service.snapshot());
            service.startMonitorLoop(MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START);
            trackedCallback.onCancelled();
        });
        if (!queued) trackedCallback.onDeferred();
        // After registration, every path delivers one result or a deferred
        // outcome. Returning true prevents the caller from rejecting twice.
        return true;
    }

    private TrackedRestCancellationCallback trackRestCancellationCallback(
            RestCancellationCallback delegate
    ) {
        return new TrackedRestCancellationCallback(delegate);
    }

    /** Linearizes monitor execution against service-destroy deferral. */
    private final class TrackedRestCancellationCallback implements RestCancellationCallback {
        private final RestCancellationCallback delegate;
        private final RestCancellationCallbackGate lifecycle =
                new RestCancellationCallbackGate();

        TrackedRestCancellationCallback(RestCancellationCallback delegate) {
            this.delegate = delegate;
        }

        boolean start() {
            return lifecycle.start();
        }

        void deferIfPending() {
            if (!lifecycle.deferIfPending()) return;
            deliver(delegate::onDeferred);
        }

        private void finish(Runnable notification) {
            if (!lifecycle.finish()) return;
            deliver(notification);
        }

        private void deliver(Runnable notification) {
            synchronized (restCancellationCallbacksLock) {
                pendingRestCancellationCallbacks.remove(this);
            }
            notification.run();
        }

        @Override public void onCancelled() {
            finish(delegate::onCancelled);
        }

        @Override public void onDeferred() {
            finish(delegate::onDeferred);
        }

        @Override public void onRejected(String message) {
            finish(() -> delegate.onRejected(message));
        }
    }

    private RestCompletionCallback trackRestCompletionCallback(
            RestCompletionCallback delegate
    ) {
        AtomicBoolean delivered = new AtomicBoolean(false);
        return new RestCompletionCallback() {
            private void finish(Runnable notification) {
                if (!delivered.compareAndSet(false, true)) return;
                synchronized (restCompletionCallbacksLock) {
                    pendingRestCompletionCallbacks.remove(this);
                }
                notification.run();
            }

            @Override public void onSuccess(NativeFlowRestCoordinator.Result result) {
                finish(() -> delegate.onSuccess(result));
            }

            @Override public void onDeferred() {
                finish(delegate::onDeferred);
            }

            @Override public void onRejected(String message, Exception error) {
                finish(() -> delegate.onRejected(message, error));
            }
        };
    }

    private void completeRestOnMonitor(
            long expectedSessionId,
            String activity,
            NativeFlowRestCoordinator coordinator,
            RestCompletionCallback callback
    ) {
        SharedPreferences preferences = stateStore.preferences();
        String persistedState = preferences.getString(
                "blockState", BlockStateMachine.State.IDLE.name()
        );
        long activeSessionId = preferences.getLong(PREF_REST_SESSION_ID, 0L);
        long restStartedAt = preferences.getLong(PREF_REST_STARTED_AT, 0L);
        long restStartedElapsedAt = preferences.getLong(PREF_REST_STARTED_ELAPSED_AT, 0L);
        int restStartedBootCount = preferences.getInt(PREF_REST_STARTED_BOOT_COUNT, -1);
        long restRequiredMs = preferences.getLong(PREF_REST_REQUIRED_MS, 0L);
        long completionRequestElapsedMs = SystemClock.elapsedRealtime();
        long nowWallMs = System.currentTimeMillis();

        RestCompletionHistoryGate.Decision preliminary = evaluateRestHistoryGate(
                preferences,
                expectedSessionId,
                nowWallMs,
                completionRequestElapsedMs,
                false,
                -1L,
                0L
        );
        if (preliminary == RestCompletionHistoryGate.Decision.SESSION_MISMATCH) {
            callback.onRejected("休息会话已变化，请返回后重试。", null);
            return;
        }
        if (BlockStateMachine.State.RESTING.name().equals(persistedState)
                && !isPersistedRestClockValid(nowWallMs, completionRequestElapsedMs)) {
            rejectInvalidRestCompletion(activeSessionId, callback);
            return;
        }
        if (preliminary == RestCompletionHistoryGate.Decision.NOT_READY) {
            callback.onRejected("休息尚未完成，请继续完成本次引导。",
                    new NativeFlowRestCoordinator.RestPendingException());
            return;
        }

        // A retry after a successful commit is idempotent and needs no second
        // history query. No new points or session can be created here.
        if (preliminary == RestCompletionHistoryGate.Decision.ALLOW) {
            completeAfterVerifiedHistory(
                    preferences, expectedSessionId, coordinator, activity, callback);
            return;
        }

        ProtectionRuntimeHealthEvaluator.Result runtimeHealth =
                getCurrentProtectionRuntimeHealth();
        if (RestCompletionIntegrityGate.evaluate(persistedState, runtimeHealth)
                        != RestCompletionIntegrityGate.Decision.ALLOW
                || !runtimeHealth.serviceRuntimeActive
                || machine == null
                || machine.getState() != BlockStateMachine.State.RESTING
                || activeRestSessionId != expectedSessionId
                || activeRestStartedAt != restStartedAt
                || activeRestStartedElapsedAt != restStartedElapsedAt
                || activeRestStartedBootCount != restStartedBootCount
                || activeRestRequiredMs != restRequiredMs
                || foregroundDetector == null
                || targetApps == null
                || !hasActiveRestTargetSnapshot()) {
            callback.onDeferred();
            return;
        }

        TargetSessionGapReconciler.Result history = null;
        long verifiedTargetMs = -1L;
        boolean historyComplete = false;
        long queryEndElapsedMs = completionRequestElapsedMs;
        long queryEndWallMs = RestSessionClock.wallTimeAtElapsed(
                restStartedAt,
                restStartedElapsedAt,
                queryEndElapsedMs
        );
        try {
            if (queryEndWallMs <= restStartedAt
                    || !isPersistedRestClockValid(nowWallMs, completionRequestElapsedMs)) {
                rejectInvalidRestCompletion(activeSessionId, callback);
                return;
            }
            ForegroundUsageDetector.GapQuery query = foregroundDetector
                    .queryEventsForReconciliation(
                            Math.max(
                                    0L,
                                    restStartedAt - ForegroundUsageDetector.LATE_EVENT_OVERLAP_MS
                            ),
                            queryEndWallMs
                    );
            if (query.succeeded && activeRestStartInteractionAvailable) {
                history = TargetSessionGapReconciler.reconcile(
                        new TargetSessionGapReconciler.Input(
                                restStartedAt,
                                queryEndWallMs,
                                activeRestStartForegroundPackage,
                                activeRestStartTargetActive,
                                activeRestStartInteractionAvailable,
                                activeRestTargetApps,
                                query.events
                        )
                );
                verifiedTargetMs = RestCheatReplay.verifiedTargetMsDuring(
                        history,
                        restStartedAt,
                        queryEndWallMs
                );
                historyComplete = history != null
                        && history.status == TargetSessionGapReconciler.Status.SUCCESS
                        && history.coverageMs == queryEndWallMs - restStartedAt
                        && verifiedTargetMs >= 0L;
            }
        } catch (Exception error) {
            Log.w("FlowForegroundService", "REST completion history query failed", error);
        }

        long verificationWallMs = System.currentTimeMillis();
        long verificationElapsedMs = SystemClock.elapsedRealtime();
        if (!isPersistedRestClockValid(verificationWallMs, verificationElapsedMs)) {
            rejectInvalidRestCompletion(activeSessionId, callback);
            return;
        }
        long unverifiedTailMs = Math.max(0L, verificationElapsedMs - queryEndElapsedMs);
        RestCompletionHistoryGate.Decision decision = evaluateRestHistoryGate(
                preferences,
                expectedSessionId,
                verificationWallMs,
                verificationElapsedMs,
                historyComplete,
                verifiedTargetMs,
                unverifiedTailMs
        );
        if (stopCompletionForDecision(decision, history, verifiedTargetMs, callback)) return;

        // Re-check liveness after the potentially slow UsageEvents query. The
        // Handler still owns all transitions, and no reward is written if the
        // worker became unhealthy while evidence was collected.
        ProtectionRuntimeHealthEvaluator.Result finalHealth =
                getCurrentProtectionRuntimeHealth();
        if (RestCompletionIntegrityGate.evaluate(
                        preferences.getString("blockState", BlockStateMachine.State.IDLE.name()),
                        finalHealth
                ) != RestCompletionIntegrityGate.Decision.ALLOW
                || !finalHealth.serviceRuntimeActive
                || monitorShutdownRequested
                || activeService != this) {
            callback.onDeferred();
            return;
        }
        long commitGateWallMs = System.currentTimeMillis();
        long commitGateElapsedMs = SystemClock.elapsedRealtime();
        if (!isPersistedRestClockValid(commitGateWallMs, commitGateElapsedMs)) {
            rejectInvalidRestCompletion(activeSessionId, callback);
            return;
        }
        RestCompletionHistoryGate.Decision commitDecision = evaluateRestHistoryGate(
                preferences,
                expectedSessionId,
                commitGateWallMs,
                commitGateElapsedMs,
                historyComplete,
                verifiedTargetMs,
                Math.max(0L, commitGateElapsedMs - queryEndElapsedMs)
        );
        if (stopCompletionForDecision(
                commitDecision,
                history,
                verifiedTargetMs,
                callback
        )) return;
        completeAfterVerifiedHistory(
                preferences, expectedSessionId, coordinator, activity, callback);
    }

    private void rejectInvalidRestCompletion(
            long expectedSessionId,
            RestCompletionCallback callback
    ) {
        long nowWallMs = System.currentTimeMillis();
        long nowElapsedMs = SystemClock.elapsedRealtime();
        if (!cancelInvalidRestOnMonitor(expectedSessionId)) {
            failClosedForIntegrity(
                    "REST_CLOCK_INVALIDATION_CANCEL_FAILED",
                    nowWallMs,
                    nowElapsedMs
            );
            callback.onDeferred();
            return;
        }
        callback.onRejected(
                "休息计时已失效，保护状态已恢复；请重新开始休息。",
                new NativeFlowRestCoordinator.RestPendingException()
        );
    }

    private RestCompletionHistoryGate.Decision evaluateRestHistoryGate(
            SharedPreferences preferences,
            long expectedSessionId,
            long nowWallMs,
            long nowElapsedMs,
            boolean historyComplete,
            long verifiedTargetMs,
            long unverifiedTailMs
    ) {
        return RestCompletionHistoryGate.evaluate(
                preferences.getString("blockState", BlockStateMachine.State.IDLE.name()),
                expectedSessionId,
                preferences.getLong(PREF_REST_SESSION_ID, 0L),
                preferences.getLong(PREF_COMPLETED_REST_SESSION_ID, -1L),
                preferences.getLong(PREF_REST_STARTED_AT, 0L),
                preferences.getLong(PREF_REST_STARTED_ELAPSED_AT, 0L),
                preferences.getInt(PREF_REST_STARTED_BOOT_COUNT, -1),
                preferences.getLong(PREF_REST_REQUIRED_MS, 0L),
                nowWallMs,
                nowElapsedMs,
                RestSessionClock.currentBootCount(this),
                preferences.getLong(PREF_REST_CLOCK_INVALIDATED_SESSION_ID, 0L),
                historyComplete,
                verifiedTargetMs,
                unverifiedTailMs
        );
    }

    private boolean stopCompletionForDecision(
            RestCompletionHistoryGate.Decision decision,
            TargetSessionGapReconciler.Result history,
            long verifiedTargetMs,
            RestCompletionCallback callback
    ) {
        switch (decision) {
            case ALLOW:
                return false;
            case DEFER:
                callback.onDeferred();
                return true;
            case SESSION_MISMATCH:
                callback.onRejected("休息会话已变化，请返回后重试。", null);
                return true;
            case NOT_READY:
                callback.onRejected(
                        "休息尚未完成，请继续完成本次引导。",
                        new NativeFlowRestCoordinator.RestPendingException()
                );
                return true;
            case CHEATED:
                if (machine == null || machine.getState() != BlockStateMachine.State.RESTING) {
                    callback.onDeferred();
                    return true;
                }
                machine.cancelRest(limitMinutes * 60_000L);
                handleRestCheatCancellation(
                        historicalRestCheatPackage(history),
                        verifiedTargetMs,
                        false,
                        0L,
                        0L,
                        false,
                        false,
                        ""
                );
                persistState(true);
                notificationController.updateServiceNotification(snapshot());
                callback.onRejected(
                        "检测到休息期间使用目标应用，本次休息已取消。", null
                );
                return true;
            default:
                callback.onDeferred();
                return true;
        }
    }

    private void completeAfterVerifiedHistory(
            SharedPreferences preferences,
            long expectedSessionId,
            NativeFlowRestCoordinator coordinator,
            String activity,
            RestCompletionCallback callback
    ) {
        try {
            NativeFlowRestCoordinator.Result result = coordinator.complete(
                    preferences, activity, expectedSessionId);
            if (result.needServiceRefresh) {
                try {
                    load();
                    persistState(true);
                    notificationController.updateServiceNotification(snapshot());
                    postOverlayAction(() -> overlayController.dismissBlocker());
                } catch (Exception refreshError) {
                    // The committed GRACE/DB state is authoritative. A refresh
                    // failure must not turn a successful settlement into a
                    // second attempt that awards points again.
                    Log.w("FlowForegroundService",
                            "Service refresh failed after REST completion", refreshError);
                }
            }
            callback.onSuccess(result);
        } catch (NativeFlowRestCoordinator.RestPendingException pending) {
            callback.onRejected("休息尚未完成，请继续完成本次引导。", pending);
        } catch (Exception error) {
            callback.onRejected("保存休息记录失败", error);
        }
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

    private static final class MonitorHeartbeat {
        private static final MonitorHeartbeat EMPTY = new MonitorHeartbeat(0L, 0L, 0L);

        private final long serviceGeneration;
        private final long completedElapsedMs;
        private final long completedWallMs;

        private MonitorHeartbeat(long serviceGeneration, long completedElapsedMs,
                long completedWallMs) {
            this.serviceGeneration = serviceGeneration;
            this.completedElapsedMs = completedElapsedMs;
            this.completedWallMs = completedWallMs;
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
        synchronized (runtimeTracking) {
            return staticServiceRuntimeActive;
        }
    }
    public static boolean isMonitorThreadAlive() {
        synchronized (runtimeTracking) {
            HandlerThread thread = staticMonitorThreadRef;
            return staticServiceRuntimeActive && thread != null && thread.isAlive();
        }
    }
    public static ProtectionRuntimeHealthEvaluator.Result getCurrentProtectionRuntimeHealth() {
        synchronized (runtimeTracking) {
            long capturedServiceGeneration = runtimeTracking.currentServiceGeneration();
            MonitorHeartbeat heartbeat = currentMonitorHeartbeatLocked(capturedServiceGeneration);
            boolean serviceRuntimeActive = staticServiceRuntimeActive;
            HandlerThread monitorThread = staticMonitorThreadRef;
            boolean monitorThreadAlive = serviceRuntimeActive
                    && monitorThread != null
                    && monitorThread.isAlive();
            long nowElapsedMs = SystemClock.elapsedRealtime();
            String integrityFailureReason = staticProtectionIntegrityFailureReason;
            return evaluateRuntimeHealthSnapshot(
                    capturedServiceGeneration,
                    runtimeTracking.currentServiceGeneration(),
                    serviceRuntimeActive,
                    monitorThreadAlive,
                    heartbeat.serviceGeneration,
                    heartbeat.completedElapsedMs,
                    nowElapsedMs,
                    integrityFailureReason
            );
        }
    }
    public static boolean isProtectionRuntimeHealthy() {
        return getCurrentProtectionRuntimeHealth().isHealthy();
    }
    /**
     * Prevent a stale persisted BLOCKED state from being converted into a
     * rest session after the current protection runtime loses liveness.
     * Non-BLOCKED states retain the existing manual/restoration semantics.
     */
    public static boolean isRestEntryAllowedForState(String persistedState) {
        return BlockedRestEntryGate.isAllowed(
                persistedState,
                isProtectionRuntimeHealthy()
        );
    }
    static boolean shouldResetMonitoringLifecycleForDisabledTick(
            BlockStateMachine.State currentState
    ) {
        return shouldResetMonitoringLifecycleForUnavailableTick(currentState);
    }
    static boolean shouldResetMonitoringLifecycle(
            boolean lifecycleClean,
            BlockStateMachine.State currentState
    ) {
        return !lifecycleClean
                && shouldResetMonitoringLifecycleForUnavailableTick(currentState);
    }
    static boolean shouldForceIntegrityFailurePersist(String previousFailureReason) {
        return previousFailureReason == null || previousFailureReason.isEmpty();
    }
    static boolean shouldResetMonitoringLifecycleForUnavailableTick(
            BlockStateMachine.State currentState
    ) {
        return currentState == null
                || currentState != BlockStateMachine.State.RESTING;
    }
    static boolean shouldRunPausedRestIntegrityTick(
            boolean monitoringEnabled,
            BlockStateMachine.State currentState
    ) {
        return !monitoringEnabled && currentState == BlockStateMachine.State.RESTING;
    }
    static boolean isPausedRestIntegrityObservationTrustworthy(
            boolean hasUsageStats,
            boolean hasTargets,
            boolean interactionAvailable,
            boolean usageQuerySucceeded
    ) {
        return hasUsageStats
                && hasTargets
                && interactionAvailable
                && usageQuerySucceeded;
    }
    static boolean isPausedRestSafeNonTargetInterval(
            boolean targetSetEmpty,
            boolean interactionAvailable
    ) {
        return targetSetEmpty || !interactionAvailable;
    }
    static boolean shouldCancelRestForIntegrityFailure(
            BlockStateMachine.State currentState
    ) {
        return currentState == BlockStateMachine.State.RESTING;
    }
    public static long getLastCompletedMonitorTickElapsedMs() {
        return currentMonitorHeartbeat().completedElapsedMs;
    }
    public static long getLastCompletedMonitorTickWallMs() {
        return currentMonitorHeartbeat().completedWallMs;
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

    private static MonitorHeartbeat currentMonitorHeartbeat() {
        synchronized (runtimeTracking) {
            return currentMonitorHeartbeatLocked(runtimeTracking.currentServiceGeneration());
        }
    }

    private static MonitorHeartbeat currentMonitorHeartbeatLocked(long currentServiceGeneration) {
        MonitorHeartbeat heartbeat = staticMonitorHeartbeat;
        if (heartbeatElapsedForCurrentService(
                heartbeat.serviceGeneration,
                currentServiceGeneration,
                heartbeat.completedElapsedMs
        ) <= 0L) {
            return MonitorHeartbeat.EMPTY;
        }
        return heartbeat;
    }

    static ProtectionRuntimeHealthEvaluator.Result evaluateRuntimeHealthSnapshot(
            long capturedServiceGeneration,
            long observedServiceGeneration,
            boolean serviceRuntimeActive,
            boolean monitorThreadAlive,
            long heartbeatServiceGeneration,
            long heartbeatElapsedMs,
            long nowElapsedMs,
            String integrityFailureReason
    ) {
        if (!isServiceGenerationStable(
                capturedServiceGeneration,
                observedServiceGeneration
        )) {
            return ProtectionRuntimeHealthEvaluator.evaluate(
                    false,
                    false,
                    0L,
                    nowElapsedMs,
                    "SERVICE_GENERATION_CHANGED"
            );
        }
        return ProtectionRuntimeHealthEvaluator.evaluate(
                serviceRuntimeActive,
                monitorThreadAlive,
                heartbeatElapsedForCurrentService(
                        heartbeatServiceGeneration,
                        capturedServiceGeneration,
                        heartbeatElapsedMs
                ),
                nowElapsedMs,
                integrityFailureReason
        );
    }

    static boolean isServiceGenerationStable(
            long capturedServiceGeneration,
            long observedServiceGeneration
    ) {
        return capturedServiceGeneration == observedServiceGeneration;
    }

    static long heartbeatElapsedForCurrentService(
            long heartbeatServiceGeneration,
            long currentServiceGeneration,
            long heartbeatElapsedMs
    ) {
        return heartbeatServiceGeneration > 0L
                && heartbeatServiceGeneration == currentServiceGeneration
                ? heartbeatElapsedMs
                : 0L;
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
        MonitorLivenessDiagnostics.Snapshot liveness = runtimeTracking.livenessSnapshot();
        MonitorHeartbeat heartbeat = currentMonitorHeartbeat();
        MonitorHeartbeat rawHeartbeat = staticMonitorHeartbeat;
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
        result.put("lastCompletedMonitorTickElapsedMs", heartbeat.completedElapsedMs);
        result.put("lastCompletedMonitorTickWallMs", heartbeat.completedWallMs);
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
        lifecycle.put("lastCompletedMonitorTickServiceGeneration", rawHeartbeat.serviceGeneration);
        lifecycle.put(
                "heartbeatGenerationMatchesCurrent",
                heartbeatElapsedForCurrentService(
                        rawHeartbeat.serviceGeneration,
                        liveness.currentServiceGeneration,
                        rawHeartbeat.completedElapsedMs
                ) > 0L
        );
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

        synchronized long currentServiceGeneration() {
            return liveness.currentServiceGeneration();
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

        synchronized void deactivateServiceGeneration(long serviceGeneration) {
            if (!liveness.isCurrentServiceGeneration(serviceGeneration)) return;
            staticServiceRuntimeActive = false;
            staticMonitorThreadRef = null;
            staticMonitorHeartbeat = MonitorHeartbeat.EMPTY;
        }

        synchronized boolean recordMonitorStart(long serviceGeneration, long startElapsed,
                long startWall) {
            if (!liveness.recordMonitorStart(serviceGeneration, startElapsed, startWall)) {
                return false;
            }
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
            return true;
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
        String persistedState = stateStore == null
                ? staticState.name()
                : stateStore.preferences().getString(
                        "blockState",
                        BlockStateMachine.State.IDLE.name()
                );
        if (!isRestEntryAllowedForState(persistedState)) {
            Log.w("FlowForegroundService",
                    "openRest rejected: current protection runtime is unhealthy");
            overlayController.dismissAll();
            return;
        }
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
        monitorShutdownRequested = true;
        if (activeService == this) activeService = null;
        deferPendingRestCompletionCallbacks();
        deferPendingRestCancellationCallbacks();
        runtimeTracking.deactivateServiceGeneration(livenessServiceGeneration);
        if (!serviceDestroyRecorded) {
            serviceDestroyRecorded = true;
            runtimeTracking.recordServiceDestroy(
                    livenessServiceGeneration,
                    System.currentTimeMillis(),
                    SystemClock.elapsedRealtime()
            );
        }
        requestMonitorShutdown();
        overlayController.clearCallbacks();
        overlayController.dismissAll();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    private void deferPendingRestCompletionCallbacks() {
        RestCompletionCallback[] pending;
        synchronized (restCompletionCallbacksLock) {
            pending = pendingRestCompletionCallbacks.toArray(new RestCompletionCallback[0]);
        }
        for (RestCompletionCallback callback : pending) {
            callback.onDeferred();
        }
    }

    private void deferPendingRestCancellationCallbacks() {
        TrackedRestCancellationCallback[] pending;
        synchronized (restCancellationCallbacksLock) {
            pending = pendingRestCancellationCallbacks.toArray(
                    new TrackedRestCancellationCallback[0]
            );
        }
        for (TrackedRestCancellationCallback callback : pending) {
            callback.deferIfPending();
        }
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
