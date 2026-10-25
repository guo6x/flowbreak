package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.Shadows;

/** Service-level regressions for integrity failure and historical replay boundaries. */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 35)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class FlowForegroundServiceIntegrityRegressionTest {
    private static final String TARGET = "tv.danmaku.bili";
    private static final String SAFE = "com.example.reader";
    private static final long CLOSED_GAP_MS = 30_000L;
    private static final int BOOT_COUNT = 41;

    private Context context;
    private SharedPreferences preferences;
    private UsageStatsManager usageStatsManager;
    private ServiceController<FlowForegroundService> serviceController;
    private FlowForegroundService service;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences(FlowServiceStateStore.PREFS, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        preferences.edit()
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, Collections.singleton(TARGET))
                .putBoolean("monitoringEnabled", true)
                .putInt("limitMinutes", 25)
                .putInt("restDuration", 180)
                .commit();

        Settings.Global.putInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, BOOT_COUNT);
        AppOpsManager appOps = context.getSystemService(AppOpsManager.class);
        Shadows.shadowOf(appOps).setMode(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName(),
                AppOpsManager.MODE_ALLOWED
        );
        ShadowSettings.setCanDrawOverlays(true);
        usageStatsManager = context.getSystemService(UsageStatsManager.class);
        Shadows.shadowOf(usageStatsManager).reset();
    }

    @After public void tearDown() throws Exception {
        if (serviceController != null) {
            Object thread = getField(service, "monitorThread");
            serviceController.destroy();
            if (thread instanceof Thread) ((Thread) thread).join(TimeUnit.SECONDS.toMillis(5L));
        }
        if (usageStatsManager != null) Shadows.shadowOf(usageStatsManager).reset();
        preferences.edit().clear().commit();
    }

    @Test public void integrityFailureCancelsPersistedRestThroughTheService() throws Exception {
        long nowWall = System.currentTimeMillis();
        long nowElapsed = Math.max(1L, SystemClock.elapsedRealtime());
        int currentBootCount = RestSessionClock.currentBootCount(context);
        assertTrue("Robolectric boot count must be available", currentBootCount >= 0);
        FlowServiceStateStore store = new FlowServiceStateStore(context);
        assertTrue(store.persistBeginRest(
                nowWall,
                180_000L,
                901L,
                false,
                true,
                SAFE,
                "",
                Collections.singleton(TARGET),
                nowElapsed,
                currentBootCount
        ));

        createServiceAndAwaitInitialLoad();
        onMonitor(() -> {
            BlockStateMachine machine = (BlockStateMachine) getField(service, "machine");
            assertEquals(BlockStateMachine.State.RESTING, machine.getState());
            Method failClosed = FlowForegroundService.class.getDeclaredMethod(
                    "failClosedForIntegrity", String.class, long.class, long.class
            );
            failClosed.setAccessible(true);
            failClosed.invoke(service, "TEST_INTEGRITY_FAILURE", nowWall + 1L, nowElapsed + 1L);
            return null;
        });

        assertFalse("RESTING must not survive an unverified service interval",
                BlockStateMachine.State.RESTING.name().equals(
                        preferences.getString("blockState", "")));
        assertFalse(preferences.contains(FlowForegroundService.PREF_REST_STARTED_AT));
        assertFalse(preferences.contains(FlowForegroundService.PREF_REST_STARTED_ELAPSED_AT));
        assertFalse(preferences.contains(FlowForegroundService.PREF_REST_STARTED_BOOT_COUNT));
        assertFalse(preferences.contains(FlowForegroundService.PREF_REST_REQUIRED_MS));
        assertFalse(preferences.contains(FlowForegroundService.PREF_REST_SESSION_ID));
        assertFalse(preferences.contains(FlowForegroundService.PREF_REST_TARGET_APPS));
        assertEquals("TEST_INTEGRITY_FAILURE",
                FlowForegroundService.getProtectionIntegrityFailureReason());
    }

    @Test public void persistedTargetOrMonitoringChangeIsNotReadyUntilServiceReload() throws Exception {
        createServiceAndAwaitInitialLoad();
        Set<String> initialTargets = PreferenceUtils.getMigratedTargetApps(preferences);
        FlowForegroundService.RuntimeConfigurationMatch initialMatch =
                FlowForegroundService.compareRuntimeConfiguration(
                        initialTargets, true, preferences.getInt("limitMinutes", 25)
                );
        assertTrue(initialMatch.targetSetMatchesPersisted);
        assertTrue(initialMatch.monitoringEnabledMatchesPersisted);

        preferences.edit()
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, Collections.singleton(SAFE))
                .commit();
        Set<String> changedTargets = PreferenceUtils.getMigratedTargetApps(preferences);
        FlowForegroundService.RuntimeConfigurationMatch staleTargets =
                FlowForegroundService.compareRuntimeConfiguration(
                        changedTargets, true, preferences.getInt("limitMinutes", 25)
                );
        assertFalse("new persisted targets must not be reported as loaded before reload",
                staleTargets.targetSetMatchesPersisted);
        assertTrue(staleTargets.monitoringEnabledMatchesPersisted);
        ProtectionStatusEvaluator.Result targetMismatchStatus =
                evaluateHealthyStatus(staleTargets);
        assertEquals(ProtectionStatusEvaluator.Status.DEGRADED, targetMismatchStatus.status);
        assertEquals("TARGET_SET_MISMATCH", targetMismatchStatus.reason);
        assertFalse(targetMismatchStatus.coreProtectionOperational);

        onMonitor(() -> {
            reloadServiceConfiguration();
            return null;
        });
        FlowForegroundService.RuntimeConfigurationMatch reloadedTargets =
                FlowForegroundService.compareRuntimeConfiguration(
                        changedTargets, true, preferences.getInt("limitMinutes", 25)
                );
        assertTrue(reloadedTargets.targetSetMatchesPersisted);
        assertTrue(reloadedTargets.monitoringEnabledMatchesPersisted);

        preferences.edit().putBoolean("monitoringEnabled", false).commit();
        onMonitor(() -> {
            reloadServiceConfiguration();
            return null;
        });
        preferences.edit().putBoolean("monitoringEnabled", true).commit();
        FlowForegroundService.RuntimeConfigurationMatch staleMonitoring =
                FlowForegroundService.compareRuntimeConfiguration(
                        changedTargets, true, preferences.getInt("limitMinutes", 25)
                );
        assertTrue(staleMonitoring.targetSetMatchesPersisted);
        assertFalse("persisted enabling must not imply the worker has enabled monitoring",
                staleMonitoring.monitoringEnabledMatchesPersisted);
        ProtectionStatusEvaluator.Result monitoringMismatchStatus =
                evaluateHealthyStatus(staleMonitoring);
        assertEquals(ProtectionStatusEvaluator.Status.DEGRADED, monitoringMismatchStatus.status);
        assertEquals("MONITORING_STATE_MISMATCH", monitoringMismatchStatus.reason);
        assertFalse(monitoringMismatchStatus.coreProtectionOperational);

        onMonitor(() -> {
            reloadServiceConfiguration();
            return null;
        });
        FlowForegroundService.RuntimeConfigurationMatch reloadedMonitoring =
                FlowForegroundService.compareRuntimeConfiguration(
                        changedTargets, true, preferences.getInt("limitMinutes", 25)
                );
        assertTrue(reloadedMonitoring.targetSetMatchesPersisted);
        assertTrue(reloadedMonitoring.monitoringEnabledMatchesPersisted);
    }

    @Test public void persistedLimitChangeIsDegradedUntilServiceReload() throws Exception {
        createServiceAndAwaitInitialLoad();
        Set<String> targets = PreferenceUtils.getMigratedTargetApps(preferences);
        int loadedLimitMinutes = preferences.getInt("limitMinutes", 25);
        FlowForegroundService.RuntimeConfigurationMatch initialMatch =
                FlowForegroundService.compareRuntimeConfiguration(
                        targets, true, loadedLimitMinutes
                );
        assertTrue(initialMatch.targetSetMatchesPersisted);
        assertTrue(initialMatch.monitoringEnabledMatchesPersisted);
        assertTrue(initialMatch.runtimeLimitMinutesMatchesPersisted);

        int persistedLimitMinutes = loadedLimitMinutes + 5;
        preferences.edit().putInt("limitMinutes", persistedLimitMinutes).commit();
        FlowForegroundService.RuntimeConfigurationMatch staleLimit =
                FlowForegroundService.compareRuntimeConfiguration(
                        targets, true, persistedLimitMinutes
                );
        assertTrue(staleLimit.targetSetMatchesPersisted);
        assertTrue(staleLimit.monitoringEnabledMatchesPersisted);
        assertFalse("persisted threshold must not be reported as loaded before reload",
                staleLimit.runtimeLimitMinutesMatchesPersisted);
        ProtectionStatusEvaluator.Result staleLimitStatus = evaluateHealthyStatus(staleLimit);
        assertEquals(ProtectionStatusEvaluator.Status.DEGRADED, staleLimitStatus.status);
        assertEquals("LIMIT_MINUTES_MISMATCH", staleLimitStatus.reason);
        assertFalse(staleLimitStatus.coreProtectionOperational);

        onMonitor(() -> {
            reloadServiceConfiguration();
            return null;
        });
        FlowForegroundService.RuntimeConfigurationMatch reloadedLimit =
                FlowForegroundService.compareRuntimeConfiguration(
                        targets, true, persistedLimitMinutes
                );
        assertTrue(reloadedLimit.targetSetMatchesPersisted);
        assertTrue(reloadedLimit.monitoringEnabledMatchesPersisted);
        assertTrue(reloadedLimit.runtimeLimitMinutesMatchesPersisted);
    }

    @Test public void loadingConfigurationInvalidatesCurrentGenerationHeartbeat() throws Exception {
        createServiceAndAwaitInitialLoad();
        long generation = (Long) getField(service, "livenessServiceGeneration");
        Class<?> heartbeatType = Class.forName(
                FlowForegroundService.class.getName() + "$MonitorHeartbeat"
        );
        java.lang.reflect.Constructor<?> constructor = heartbeatType.getDeclaredConstructor(
                long.class, long.class, long.class
        );
        constructor.setAccessible(true);
        Object priorHeartbeat = constructor.newInstance(
                generation,
                Math.max(1L, SystemClock.elapsedRealtime()),
                System.currentTimeMillis()
        );
        setStaticField(FlowForegroundService.class, "staticMonitorHeartbeat", priorHeartbeat);
        assertTrue("fixture must start with a fresh heartbeat",
                FlowForegroundService.getLastCompletedMonitorTickElapsedMs() > 0L);

        onMonitor(() -> {
            reloadServiceConfiguration();
            return null;
        });

        assertEquals("a config reload must await a tick for its new runtime snapshot",
                0L, FlowForegroundService.getLastCompletedMonitorTickElapsedMs());
        assertEquals("HEARTBEAT_MISSING",
                FlowForegroundService.getCurrentProtectionRuntimeHealth().reason);
    }

    @Test public void stoppingMonitoringKeepsRuntimeSnapshotInSyncWithPersistedState()
            throws Exception {
        createServiceAndAwaitInitialLoad();
        onMonitor(() -> {
            Method stop = FlowForegroundService.class.getDeclaredMethod(
                    "stopMonitoring", int.class
            );
            stop.setAccessible(true);
            stop.invoke(service, 1);
            return null;
        });

        Set<String> persistedTargets = PreferenceUtils.getMigratedTargetApps(preferences);
        assertFalse("the stop command should persist monitoring disabled",
                preferences.getBoolean("monitoringEnabled", true));
        FlowForegroundService.RuntimeConfigurationMatch match =
                FlowForegroundService.compareRuntimeConfiguration(
                        persistedTargets, false, preferences.getInt("limitMinutes", 25)
                );
        assertTrue("the actual runtime target snapshot should remain observable",
                match.targetSetMatchesPersisted);
        assertTrue("stop should publish the runtime disabled state it just persisted",
                match.monitoringEnabledMatchesPersisted);
    }

    @Test public void staleServiceGenerationCannotOverwriteReplacementConfigSnapshot()
            throws Exception {
        createServiceAndAwaitInitialLoad();
        ServiceController<FlowForegroundService> staleController = serviceController;
        FlowForegroundService staleService = service;
        long staleGeneration = (Long) getField(staleService, "livenessServiceGeneration");
        Object staleMonitorThread = getField(staleService, "monitorThread");
        try {
            preferences.edit()
                    .putStringSet(
                            PreferenceUtils.PREF_TARGET_APPS,
                            Collections.singleton(SAFE)
                    )
                    .commit();
            ServiceController<FlowForegroundService> replacementController =
                    Robolectric.buildService(FlowForegroundService.class).create();
            serviceController = replacementController;
            service = replacementController.get();
            onMonitor(() -> null);

            long replacementGeneration = (Long) getField(service, "livenessServiceGeneration");
            assertTrue("replacement service must own a newer generation",
                    replacementGeneration > staleGeneration);
            FlowForegroundService.RuntimeConfigurationMatch replacementMatch =
                    FlowForegroundService.compareRuntimeConfiguration(
                            Collections.singleton(SAFE), true,
                            preferences.getInt("limitMinutes", 25)
                    );
            assertTrue("replacement load should publish its current target snapshot",
                    replacementMatch.targetSetMatchesPersisted);
            assertTrue(replacementMatch.monitoringEnabledMatchesPersisted);

            String replacementForegroundPackage = "com.example.replacement";
            onMonitor(() -> {
                setField(service, "machine", new BlockStateMachine(
                        BlockStateMachine.State.BLOCKED, 42_000L, 0L, 1L, SAFE
                ));
                Method publishLoadedState = FlowForegroundService.class.getDeclaredMethod(
                        "publishLoadedStateIfCurrent", String.class
                );
                publishLoadedState.setAccessible(true);
                assertTrue("the current service should publish its state snapshot",
                        (Boolean) publishLoadedState.invoke(service, replacementForegroundPackage));
                return null;
            });
            assertEquals(BlockStateMachine.State.BLOCKED, FlowForegroundService.getState());
            assertEquals(SAFE, FlowForegroundService.getBlockedPackage());
            assertEquals(replacementForegroundPackage,
                    FlowForegroundService.getForegroundPackage());

            Method persistState = FlowForegroundService.class.getDeclaredMethod(
                    "persistState", boolean.class
            );
            persistState.setAccessible(true);
            onMonitor(() -> {
                persistState.invoke(service, true);
                return null;
            });
            assertEquals(BlockStateMachine.State.BLOCKED.name(),
                    preferences.getString("blockState", ""));
            assertEquals(42_000L, preferences.getLong("sessionMs", 0L));

            Method loadStaleService = FlowForegroundService.class.getDeclaredMethod("load");
            loadStaleService.setAccessible(true);
            setField(staleService, "machine", new BlockStateMachine(
                    BlockStateMachine.State.IDLE, 5_000L, 0L, 0L, ""
            ));
            loadStaleService.invoke(staleService);
            persistState.invoke(staleService, true);
            assertEquals("a stale load followed by forced persistence must not replace B's state",
                    BlockStateMachine.State.BLOCKED.name(),
                    preferences.getString("blockState", ""));
            assertEquals(42_000L, preferences.getLong("sessionMs", 0L));
            Method publishLoadedState = FlowForegroundService.class.getDeclaredMethod(
                    "publishLoadedStateIfCurrent", String.class
            );
            publishLoadedState.setAccessible(true);
            assertFalse("a late old-generation load must not publish its local state",
                    (Boolean) publishLoadedState.invoke(staleService, "com.example.stale"));
            assertEquals(BlockStateMachine.State.BLOCKED, FlowForegroundService.getState());
            assertEquals(SAFE, FlowForegroundService.getBlockedPackage());
            assertEquals(replacementForegroundPackage,
                    FlowForegroundService.getForegroundPackage());

            long invalidRestSessionId = 902L;
            long nowWall = System.currentTimeMillis();
            long nowElapsed = Math.max(1L, SystemClock.elapsedRealtime());
            int currentBootCount = RestSessionClock.currentBootCount(context);
            FlowServiceStateStore staleStore =
                    (FlowServiceStateStore) getField(staleService, "stateStore");
            assertTrue("the test should create an invalid persisted REST session",
                    staleStore.persistBeginRest(
                            nowWall,
                            180_000L,
                            invalidRestSessionId,
                            false,
                            true,
                            SAFE,
                            "",
                            Collections.singleton(TARGET),
                            nowElapsed,
                            currentBootCount
                    ));
            preferences.edit()
                    .putLong(
                            FlowForegroundService.PREF_REST_CLOCK_INVALIDATED_SESSION_ID,
                            invalidRestSessionId
                    )
                    .commit();
            setField(staleService, "machine", new BlockStateMachine(
                    BlockStateMachine.State.RESTING, 0L, 0L, 0L, SAFE
            ));
            Method cancelStaleRest = FlowForegroundService.class.getDeclaredMethod(
                    "cancelInvalidRestOnMonitor", long.class
            );
            cancelStaleRest.setAccessible(true);
            assertFalse("an obsolete generation must not commit REST cancellation",
                    (Boolean) cancelStaleRest.invoke(staleService, invalidRestSessionId));
            assertEquals(BlockStateMachine.State.RESTING.name(),
                    preferences.getString("blockState", ""));
            assertEquals(invalidRestSessionId, preferences.getLong(
                    FlowForegroundService.PREF_REST_SESSION_ID, 0L
            ));
            assertEquals(BlockStateMachine.State.BLOCKED, FlowForegroundService.getState());
            assertEquals(SAFE, FlowForegroundService.getBlockedPackage());
            assertEquals(replacementForegroundPackage,
                    FlowForegroundService.getForegroundPackage());

            assertFalse("a late old-generation load must not replace the current snapshot",
                    FlowForegroundService.publishRuntimeProtectionConfigSnapshotIfCurrent(
                            staleGeneration,
                            Collections.singleton(TARGET),
                        true,
                        25,
                        true
                    ));
            FlowForegroundService.RuntimeConfigurationMatch afterStalePublish =
                    FlowForegroundService.compareRuntimeConfiguration(
                            Collections.singleton(SAFE), true,
                            preferences.getInt("limitMinutes", 25)
                    );
            assertTrue("the replacement configuration must remain visible",
                    afterStalePublish.targetSetMatchesPersisted);
            assertTrue(afterStalePublish.monitoringEnabledMatchesPersisted);

            Method stopStaleService = FlowForegroundService.class.getDeclaredMethod(
                    "stopMonitoring", int.class
            );
            stopStaleService.setAccessible(true);
            stopStaleService.invoke(staleService, 2);
            assertTrue("a stale service stop must not disable persisted monitoring",
                    preferences.getBoolean("monitoringEnabled", false));
            FlowForegroundService.RuntimeConfigurationMatch afterStaleStop =
                    FlowForegroundService.compareRuntimeConfiguration(
                            Collections.singleton(SAFE), true,
                            preferences.getInt("limitMinutes", 25)
                    );
            assertTrue(afterStaleStop.targetSetMatchesPersisted);
            assertTrue(afterStaleStop.monitoringEnabledMatchesPersisted);
        } finally {
            staleController.destroy();
            if (serviceController == staleController) serviceController = null;
            if (staleMonitorThread instanceof Thread) {
                ((Thread) staleMonitorThread).join(TimeUnit.SECONDS.toMillis(5L));
            }
        }
    }

    @Test public void staleServiceGenerationDoesNotFlushPendingUsageAfterReplacement()
            throws Exception {
        createServiceAndAwaitInitialLoad();
        ServiceController<FlowForegroundService> staleController = serviceController;
        FlowForegroundService staleService = service;
        Object staleMonitorThread = getField(staleService, "monitorThread");
        UsageAccumulator pendingUsage = new UsageAccumulator();
        pendingUsage.queue(TARGET, 5_000L);
        setField(staleService, "usageAccumulator", pendingUsage);

        try {
            serviceController = Robolectric.buildService(FlowForegroundService.class).create();
            service = serviceController.get();
            onMonitor(() -> null);

            Method flushPendingUsage = FlowForegroundService.class.getDeclaredMethod(
                    "flushPendingUsage", boolean.class
            );
            flushPendingUsage.setAccessible(true);
            flushPendingUsage.invoke(staleService, true);

            assertEquals(
                    "an obsolete service must retain, not enqueue, its pending usage tail",
                    Long.valueOf(5_000L),
                    pendingUsage.pendingSnapshot().get(TARGET)
            );
        } finally {
            staleController.destroy();
            if (staleMonitorThread instanceof Thread) {
                ((Thread) staleMonitorThread).join(TimeUnit.SECONDS.toMillis(5L));
            }
        }
    }

    @Test public void staleServiceGenerationDoesNotRecordBlockTransitionAfterReplacement()
            throws Exception {
        createServiceAndAwaitInitialLoad();
        ServiceController<FlowForegroundService> staleController = serviceController;
        FlowForegroundService staleService = service;
        Object staleMonitorThread = getField(staleService, "monitorThread");

        try {
            serviceController = Robolectric.buildService(FlowForegroundService.class).create();
            service = serviceController.get();
            onMonitor(() -> null);
            FlowRepository repository = (FlowRepository) getField(staleService, "repository");
            int blockAttemptsBefore = countRepositoryEvents(repository, "block_attempt");
            int pullbackReturnsBefore = countRepositoryEvents(repository, "post_rest_return");

            Method onStateChanged = FlowForegroundService.class.getDeclaredMethod(
                    "onStateChanged", BlockStateMachine.State.class, String.class
            );
            onStateChanged.setAccessible(true);
            Method persistState = FlowForegroundService.class.getDeclaredMethod(
                    "persistState", boolean.class
            );
            persistState.setAccessible(true);
            onStateChanged.invoke(staleService, BlockStateMachine.State.BLOCKED, TARGET);
            persistState.invoke(staleService, true);

            long now = System.currentTimeMillis();
            setField(staleService, "machine", new BlockStateMachine(
                    BlockStateMachine.State.GRACE, 0L, now + 600_000L, 0L, ""
            ));
            PullbackSessionCoordinator stalePullback =
                    (PullbackSessionCoordinator) getField(staleService, "pullbackCoordinator");
            stalePullback.restore(new PullbackSessionCoordinator.Snapshot(
                    true, 990_001L, now - 1_000L, 0L, 0L,
                    false, false, false, false
            ));
            Method trackPullbackOutcome = FlowForegroundService.class.getDeclaredMethod(
                    "trackPullbackOutcome", boolean.class, long.class, long.class
            );
            trackPullbackOutcome.setAccessible(true);
            trackPullbackOutcome.invoke(staleService, true, 5_000L, now);

            assertEquals(
                    "an obsolete transition must not enqueue an event without its state commit",
                    blockAttemptsBefore,
                    countRepositoryEvents(repository, "block_attempt")
            );
            assertEquals(
                    "an obsolete pullback edge must not be recorded before its snapshot commit",
                    pullbackReturnsBefore,
                    countRepositoryEvents(repository, "post_rest_return")
            );
            assertFalse("the replacement's pullback snapshot must remain untouched",
                    preferences.getBoolean(FlowForegroundService.PREF_PULLBACK_RETURN_REPORTED, false));
        } finally {
            staleController.destroy();
            if (staleMonitorThread instanceof Thread) {
                ((Thread) staleMonitorThread).join(TimeUnit.SECONDS.toMillis(5L));
            }
        }
    }

    @Test public void staleServiceGenerationCannotRecordEmergencyUnlockAfterReplacement()
            throws Exception {
        createServiceAndAwaitInitialLoad();
        ServiceController<FlowForegroundService> staleController = serviceController;
        FlowForegroundService staleService = service;
        Object staleMonitorThread = getField(staleService, "monitorThread");
        FlowRepository repository = (FlowRepository) getField(staleService, "repository");
        SharedPreferences emergencyPreferences = context.getSharedPreferences(
                "FlowBreakPrefs", Context.MODE_PRIVATE
        );
        Object priorDay = emergencyPreferences.getAll().get("emergencyUnlockDay");
        Object priorWallAt = emergencyPreferences.getAll().get("emergencyUnlockWallAt");
        Object priorMonotonicAt = emergencyPreferences.getAll().get("emergencyUnlockMonotonicAt");
        boolean hadAllowEmergency = emergencyPreferences.contains("allowEmergencyUnlock");
        boolean priorAllowEmergency = emergencyPreferences.getBoolean("allowEmergencyUnlock", true);
        emergencyPreferences.edit()
                .remove("emergencyUnlockDay")
                .remove("emergencyUnlockWallAt")
                .remove("emergencyUnlockMonotonicAt")
                .putBoolean("allowEmergencyUnlock", true)
                .commit();
        long currentGeneration = (Long) getField(staleService, "livenessServiceGeneration");
        Method guardedUnlock = FlowForegroundService.class.getDeclaredMethod(
                "performEmergencyUnlockIfCurrent", long.class
        );
        guardedUnlock.setAccessible(true);
        setField(staleService, "machine", null);
        assertFalse("a missing state machine must not consume the emergency allowance",
                (Boolean) onMonitor(() -> (Boolean) guardedUnlock.invoke(
                        staleService, currentGeneration
                )));
        assertFalse("a no-op unlock must not mark today's allowance as used",
                emergencyPreferences.contains("emergencyUnlockDay"));
        setField(staleService, "machine", new BlockStateMachine(
                BlockStateMachine.State.BLOCKED, 10_000L, 0L, 1L, TARGET
        ));
        int emergencyUnlocksBefore = countRepositoryEvents(repository, "emergency_unlock");

        try {
            serviceController = Robolectric.buildService(FlowForegroundService.class).create();
            service = serviceController.get();
            onMonitor(() -> null);

            Method requestEmergencyUnlock = FlowForegroundService.class.getDeclaredMethod(
                    "requestEmergencyUnlock",
                    FlowOverlayController.EmergencyUnlockCompletion.class
            );
            requestEmergencyUnlock.setAccessible(true);
            CountDownLatch staleRequestCompleted = new CountDownLatch(1);
            AtomicBoolean staleRequestUnlocked = new AtomicBoolean(true);
            requestEmergencyUnlock.invoke(
                    staleService,
                    (FlowOverlayController.EmergencyUnlockCompletion) unlocked -> {
                        staleRequestUnlocked.set(unlocked);
                        staleRequestCompleted.countDown();
                    }
            );
            assertTrue("the stale service should report its rejected request",
                    staleRequestCompleted.await(5L, TimeUnit.SECONDS));
            assertFalse("an obsolete service must reject its queued emergency unlock",
                    staleRequestUnlocked.get());
            assertEquals("a stale service must not log a ghost emergency unlock",
                    emergencyUnlocksBefore,
                    countRepositoryEvents(repository, "emergency_unlock"));
            assertFalse("a rejected stale unlock must not consume today's allowance",
                    emergencyPreferences.contains("emergencyUnlockDay"));
        } finally {
            SharedPreferences.Editor restore = emergencyPreferences.edit()
                    .remove("emergencyUnlockDay")
                    .remove("emergencyUnlockWallAt")
                    .remove("emergencyUnlockMonotonicAt")
                    .remove("allowEmergencyUnlock");
            if (priorDay instanceof String) restore.putString("emergencyUnlockDay", (String) priorDay);
            if (priorWallAt instanceof Long) {
                restore.putLong("emergencyUnlockWallAt", (Long) priorWallAt);
            }
            if (priorMonotonicAt instanceof Long) {
                restore.putLong("emergencyUnlockMonotonicAt", (Long) priorMonotonicAt);
            }
            if (hadAllowEmergency) restore.putBoolean("allowEmergencyUnlock", priorAllowEmergency);
            restore.commit();
            staleController.destroy();
            if (staleMonitorThread instanceof Thread) {
                ((Thread) staleMonitorThread).join(TimeUnit.SECONDS.toMillis(5L));
            }
        }
    }

    @Test public void concurrentEmergencyUnlockRequestsConsumeTheAllowanceOnce()
            throws Exception {
        SharedPreferences emergencyPreferences = context.getSharedPreferences(
                "FlowBreakPrefs", Context.MODE_PRIVATE
        );
        java.util.Map<String, Object> previous = new java.util.HashMap<>();
        String[] keys = {
                "emergencyUnlockDay",
                "emergencyUnlockWallAt",
                "emergencyUnlockMonotonicAt",
                "allowEmergencyUnlock"
        };
        for (String key : keys) {
            if (emergencyPreferences.contains(key)) {
                previous.put(key, emergencyPreferences.getAll().get(key));
            }
        }
        emergencyPreferences.edit()
                .remove("emergencyUnlockDay")
                .remove("emergencyUnlockWallAt")
                .remove("emergencyUnlockMonotonicAt")
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            if (!start.await(5L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent unlock start timed out");
            }
            return EmergencyUnlockManager.tryUnlock(context);
        };
        FutureTask<Boolean> first = new FutureTask<>(attempt);
        FutureTask<Boolean> second = new FutureTask<>(attempt);
        Thread firstThread = new Thread(first, "emergency-unlock-test-1");
        Thread secondThread = new Thread(second, "emergency-unlock-test-2");
        try {
            firstThread.start();
            secondThread.start();
            assertTrue("both unlock requests should be ready",
                    ready.await(5L, TimeUnit.SECONDS));
            start.countDown();
            boolean firstUnlocked = first.get(5L, TimeUnit.SECONDS);
            boolean secondUnlocked = second.get(5L, TimeUnit.SECONDS);
            assertTrue("at most one concurrent request may consume the daily allowance",
                    firstUnlocked ^ secondUnlocked);
            assertEquals(0, EmergencyUnlockManager.remainingToday(context));
        } finally {
            start.countDown();
            firstThread.join(TimeUnit.SECONDS.toMillis(5L));
            secondThread.join(TimeUnit.SECONDS.toMillis(5L));
            restoreEmergencyUnlockPreferences(emergencyPreferences, previous);
        }
    }

    @Test public void serviceConfirmsEmergencyUnlockBeforeClosingBlockerExactlyOnce()
            throws Exception {
        SharedPreferences emergencyPreferences = context.getSharedPreferences(
                "FlowBreakPrefs", Context.MODE_PRIVATE
        );
        java.util.Map<String, Object> previous = new java.util.HashMap<>();
        String[] keys = {
                "emergencyUnlockDay",
                "emergencyUnlockWallAt",
                "emergencyUnlockMonotonicAt",
                "allowEmergencyUnlock"
        };
        for (String key : keys) {
            if (emergencyPreferences.contains(key)) {
                previous.put(key, emergencyPreferences.getAll().get(key));
            }
        }
        emergencyPreferences.edit()
                .remove("emergencyUnlockDay")
                .remove("emergencyUnlockWallAt")
                .remove("emergencyUnlockMonotonicAt")
                .putBoolean("allowEmergencyUnlock", true)
                .putString("blockState", BlockStateMachine.State.BLOCKED.name())
                .putLong("sessionMs", 1_500_000L)
                .putLong("graceUntil", 0L)
                .putString("blockedPackage", TARGET)
                .commit();

        CountDownLatch monitorEntered = new CountDownLatch(1);
        CountDownLatch releaseMonitor = new CountDownLatch(1);
        try {
            createServiceAndAwaitInitialLoad();
            assertEquals(BlockStateMachine.State.BLOCKED, FlowForegroundService.getState());
            FlowRepository repository = (FlowRepository) getField(service, "repository");
            int emergencyUnlocksBefore = countRepositoryEvents(repository, "emergency_unlock");

            FlowOverlayController overlay =
                    (FlowOverlayController) getField(service, "overlayController");
            overlay.showBlocker(TARGET, 1_500_000L, true);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            TextView emergencyAction = (TextView) getField(overlay, "blockerEmergencyAction");
            assertNotNull(emergencyAction);

            android.os.Handler monitorHandler =
                    (android.os.Handler) getField(service, "monitorHandler");
            assertTrue(monitorHandler.post(() -> {
                monitorEntered.countDown();
                try {
                    if (!releaseMonitor.await(10L, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("monitor test gate was not released");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue("test should hold the monitor before unlock confirmation",
                    monitorEntered.await(5L, TimeUnit.SECONDS));

            MotionEvent down = MotionEvent.obtain(
                    0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0
            );
            try {
                assertTrue(emergencyAction.dispatchTouchEvent(down));
            } finally {
                down.recycle();
            }
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(10L, TimeUnit.SECONDS);

            assertNotNull("blocker must remain until the service confirms success",
                    getField(overlay, "blockerView"));
            assertEquals(BlockStateMachine.State.BLOCKED, FlowForegroundService.getState());
            assertEquals(BlockStateMachine.State.BLOCKED.name(),
                    preferences.getString("blockState", ""));
            assertEquals(1, EmergencyUnlockManager.remainingToday(context));
            assertEquals(emergencyUnlocksBefore,
                    countRepositoryEvents(repository, "emergency_unlock"));

            releaseMonitor.countDown();
            onMonitor(() -> null);

            assertEquals("service success must be committed before the UI callback runs",
                    BlockStateMachine.State.GRACE, FlowForegroundService.getState());
            assertEquals(BlockStateMachine.State.GRACE.name(),
                    preferences.getString("blockState", ""));
            assertTrue(preferences.getLong("graceUntil", 0L) > System.currentTimeMillis());
            assertEquals(0, EmergencyUnlockManager.remainingToday(context));
            assertEquals(emergencyUnlocksBefore + 1,
                    countRepositoryEvents(repository, "emergency_unlock"));
            assertNotNull("the blocker waits for the success callback on the main looper",
                    getField(overlay, "blockerView"));

            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNull("the successful service callback should close the blocker",
                    getField(overlay, "blockerView"));

            Method requestEmergencyUnlock = FlowForegroundService.class.getDeclaredMethod(
                    "requestEmergencyUnlock",
                    FlowOverlayController.EmergencyUnlockCompletion.class
            );
            requestEmergencyUnlock.setAccessible(true);
            CountDownLatch duplicateCompleted = new CountDownLatch(1);
            AtomicBoolean duplicateUnlocked = new AtomicBoolean(true);
            requestEmergencyUnlock.invoke(
                    service,
                    (FlowOverlayController.EmergencyUnlockCompletion) unlocked -> {
                        duplicateUnlocked.set(unlocked);
                        duplicateCompleted.countDown();
                    }
            );
            assertTrue("duplicate unlock should complete", duplicateCompleted.await(
                    5L, TimeUnit.SECONDS
            ));
            assertFalse("a second request must not grant another emergency unlock",
                    duplicateUnlocked.get());
            assertEquals(0, EmergencyUnlockManager.remainingToday(context));
            assertEquals(emergencyUnlocksBefore + 1,
                    countRepositoryEvents(repository, "emergency_unlock"));
        } finally {
            releaseMonitor.countDown();
            restoreEmergencyUnlockPreferences(emergencyPreferences, previous);
        }
    }

    @Test public void failedHistoricalGapCannotBeClearedBySameTickLiveQuery() throws Exception {
        createServiceAndAwaitInitialLoad();
        AtomicInteger queries = new AtomicInteger();
        ForegroundUsageDetector detector = newDetector((begin, end) -> {
            if (queries.getAndIncrement() == 0) {
                throw new IllegalStateException("historical query unavailable");
            }
            return usageStatsManager.queryEvents(begin, end);
        });
        long nowWall = System.currentTimeMillis();
        long nowElapsed = Math.max(CLOSED_GAP_MS + 10_000L, SystemClock.elapsedRealtime());
        onMonitor(() -> {
            setField(service, "foregroundDetector", detector);
            setField(service, "machine", new BlockStateMachine(
                    BlockStateMachine.State.IDLE, 0L, 0L, 0L, ""));
            setField(service, "targetApps", Collections.singleton(TARGET));
            setField(service, "limitMinutes", 25);
            setField(service, "monitoringEnabled", true);
            setField(service, "screenOn", true);
            setField(service, "interactionAvailable", true);
            setField(service, "usageAccumulator", new UsageAccumulator());
            setField(service, "checkpointWallMs", nowWall - CLOSED_GAP_MS);
            setField(service, "checkpointElapsedMs", nowElapsed - CLOSED_GAP_MS);
            setField(service, "checkpointTargetActive", false);
            setField(service, "checkpointInteractionAvailable", true);
            setField(service, "checkpointForegroundPackage", SAFE);
            setField(service, "checkpointInitialized", true);

            Method tick = FlowForegroundService.class.getDeclaredMethod(
                    "tick", long.class, long.class
            );
            tick.setAccessible(true);
            assertEquals(Boolean.FALSE, tick.invoke(service, nowWall, nowElapsed));
            return null;
        });

        assertEquals("only the historical query should have run in the failed tick", 1, queries.get());
        assertEquals("HISTORICAL_GAP_RECONCILIATION_INCOMPLETE",
                FlowForegroundService.getProtectionIntegrityFailureReason());
        assertFalse("the live detector must not be sampled in the failed tick",
                detector.wasLastLiveUsageQuerySuccessful());

        // The injected query would succeed on its next call. That later success must not
        // retroactively turn the failed tick into a healthy observation.
        assertEquals("", detector.detect(nowWall));
        assertTrue(detector.wasLastLiveUsageQuerySuccessful());
        assertEquals("HISTORICAL_GAP_RECONCILIATION_INCOMPLETE",
                FlowForegroundService.getProtectionIntegrityFailureReason());
    }

    @Test public void nonRestHistoricalReplayDoesNotExtendPriorRestEvidence() throws Exception {
        createServiceAndAwaitInitialLoad();
        long nowWall = System.currentTimeMillis();
        long nowElapsed = Math.max(CLOSED_GAP_MS + 10_000L, SystemClock.elapsedRealtime());
        long checkpointWall = nowWall - CLOSED_GAP_MS;
        long checkpointElapsed = nowElapsed - CLOSED_GAP_MS;
        ForegroundUsageDetector detector = newDetector(
                (begin, end) -> usageStatsManager.queryEvents(begin, end));
        RestCheatEvidence evidence = (RestCheatEvidence) getStaticField(
                FlowForegroundService.class, "restCheatEvidence");
        evidence.onRestStarted();
        evidence.recordHistoricalReplay(7_000L, 7_000L, nowWall - 1L);
        addUsageEvent(
                TARGET,
                "MainActivity",
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                checkpointWall + 5_000L
        );

        long reconciliationBefore = (Long) getStaticField(
                FlowForegroundService.class, "staticHistoricalTargetMsRecovered");
        onMonitor(() -> {
            setField(service, "foregroundDetector", detector);
            setField(service, "machine", new BlockStateMachine(
                    BlockStateMachine.State.IDLE, 0L, 0L, 0L, ""));
            setField(service, "targetApps", Collections.singleton(TARGET));
            setField(service, "limitMinutes", 25);
            setField(service, "monitoringEnabled", true);
            setField(service, "screenOn", true);
            setField(service, "interactionAvailable", true);
            setField(service, "usageAccumulator", new UsageAccumulator());
            setField(service, "checkpointWallMs", checkpointWall);
            setField(service, "checkpointElapsedMs", checkpointElapsed);
            setField(service, "checkpointTargetActive", false);
            setField(service, "checkpointInteractionAvailable", true);
            setField(service, "checkpointForegroundPackage", SAFE);
            setField(service, "checkpointInitialized", true);
            ((BlockStateMachine) getField(service, "machine")).seedCheckpoint(checkpointWall, false);

            Method tick = FlowForegroundService.class.getDeclaredMethod(
                    "tick", long.class, long.class
            );
            tick.setAccessible(true);
            Boolean tickSucceeded = (Boolean) tick.invoke(service, nowWall, nowElapsed);
            assertTrue("production tick failed closed: "
                    + FlowForegroundService.getProtectionIntegrityFailureReason(), tickSucceeded);
            return null;
        });

        assertEquals("SUCCESS", getStaticField(
                FlowForegroundService.class, "staticLastReconciliationResult"));
        long recoveredHistoricalTargetMs = (Long) getStaticField(
                FlowForegroundService.class, "staticHistoricalTargetMsRecovered");
        assertTrue("the production tick should replay target UsageEvents from the closed gap",
                recoveredHistoricalTargetMs > reconciliationBefore);
        assertEquals(BlockStateMachine.State.IDLE,
                ((BlockStateMachine) getField(service, "machine")).getState());
        RestCheatEvidence.Snapshot after = evidence.snapshot();
        assertEquals("prior REST replay evidence must not grow during ordinary monitoring",
                7_000L, after.sessionHistoricalReplayMs);
        assertEquals("non-REST replay must not overwrite the prior evidence source",
                "HISTORICAL_REPLAY", after.lastSource);
    }

    private void createServiceAndAwaitInitialLoad() throws Exception {
        serviceController = Robolectric.buildService(FlowForegroundService.class).create();
        service = serviceController.get();
        onMonitor(() -> null);
    }

    private void reloadServiceConfiguration() throws Exception {
        Method load = FlowForegroundService.class.getDeclaredMethod("load");
        load.setAccessible(true);
        load.invoke(service);
    }

    private void addUsageEvent(String packageName, String className, int eventType, long at)
            throws Exception {
        UsageEvents.Event event = new UsageEvents.Event();
        setField(event, "mPackage", packageName);
        setField(event, "mClass", className);
        setField(event, "mTimeStamp", at);
        setField(event, "mEventType", eventType);
        Shadows.shadowOf(usageStatsManager).addEvent(event);
    }

    private static ProtectionStatusEvaluator.Result evaluateHealthyStatus(
            FlowForegroundService.RuntimeConfigurationMatch match
    ) {
        return ProtectionStatusEvaluator.evaluate(
                new ProtectionStatusEvaluator.Input(
                        true, true, true, true,
                        match.targetSetMatchesPersisted,
                        match.monitoringEnabledMatchesPersisted,
                        match.runtimeLimitMinutesMatchesPersisted,
                        true, true, true, true,
                        1_000L, 1_000L,
                        false, false, false, ""
                )
        );
    }

    private ForegroundUsageDetector newDetector(ForegroundUsageDetector.UsageEventsQuery query) {
        return new ForegroundUsageDetector(
                context,
                new ForegroundAppTracker(),
                ForegroundUsageDetector.INITIAL_EVENT_LOOKBACK_MS,
                query
        );
    }

    private <T> T onMonitor(Callable<T> operation) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Method post = FlowForegroundService.class.getDeclaredMethod(
                "postMonitorCommand", Runnable.class
        );
        post.setAccessible(true);
        boolean queued = (Boolean) post.invoke(service, (Runnable) () -> {
            try {
                result.set(operation.call());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue("monitor worker should accept the test command", queued);
        assertTrue("monitor worker command should complete", completed.await(10L, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("Monitor test command failed", failure.get());
        return result.get();
    }

    private static Object getStaticField(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStaticField(Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static int countRepositoryEvents(FlowRepository repository, String type)
            throws Exception {
        Field executorField = FlowRepository.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(repository);
        executor.submit(() -> null).get(5L, TimeUnit.SECONDS);
        FutureTask<Integer> query = new FutureTask<>(
                () -> FlowDatabase.get(ApplicationProvider.getApplicationContext())
                        .flowDao().countEventsSince(type, 0L)
        );
        Thread worker = new Thread(query, "service-integrity-room-query");
        worker.setDaemon(true);
        worker.start();
        return query.get(10L, TimeUnit.SECONDS);
    }

    private static void restoreEmergencyUnlockPreferences(
            SharedPreferences preferences,
            java.util.Map<String, Object> previous
    ) {
        SharedPreferences.Editor restore = preferences.edit()
                .remove("emergencyUnlockDay")
                .remove("emergencyUnlockWallAt")
                .remove("emergencyUnlockMonotonicAt")
                .remove("allowEmergencyUnlock");
        for (java.util.Map.Entry<String, Object> entry : previous.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                restore.putString(entry.getKey(), (String) value);
            } else if (value instanceof Long) {
                restore.putLong(entry.getKey(), (Long) value);
            } else if (value instanceof Boolean) {
                restore.putBoolean(entry.getKey(), (Boolean) value);
            }
        }
        restore.commit();
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
