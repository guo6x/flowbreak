package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.shadows.ShadowSettings;

/** Service-level evidence regressions for both persisted REST history replay paths. */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 35)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class FlowForegroundServiceRestReplayEvidenceRegressionTest {
    private static final String TARGET = "tv.danmaku.bili";
    private static final String SAFE = "com.example.reader";
    private static final long REST_REQUIRED_MS = 180_000L;
    private static final long VERIFIED_TARGET_MS = 4_000L;
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

    @Test public void activeRestGapReplayRecordsVerifiedHistoricalEvidence() throws Exception {
        RestWindow rest = persistActiveRest(901L);
        addUsageEvent(TARGET, UsageEvents.Event.ACTIVITY_RESUMED, rest.startedAtWallMs + 1_000L);
        addUsageEvent(SAFE, UsageEvents.Event.ACTIVITY_RESUMED, rest.startedAtWallMs + 5_000L);
        createServiceAndAwaitInitialLoad();
        RestCheatEvidence evidence = resetSessionEvidence();

        assertTrue("loaded service must have the persisted active REST target snapshot",
                onMonitor(() -> (Boolean) invoke(service, "hasActiveRestTargetSnapshot")));
        assertEquals(BlockStateMachine.State.RESTING,
                onMonitor(() -> ((BlockStateMachine) getField(service, "machine")).getState()));

        Boolean replayed = onMonitor(() -> (Boolean) invoke(
                service,
                "replayRestCheatGap",
                new Class<?>[]{long.class, long.class, String.class,
                        boolean.class, boolean.class, long.class},
                rest.startedAtWallMs,
                rest.startedAtWallMs + 10_000L,
                SAFE,
                false,
                true,
                SystemClock.elapsedRealtime()
        ));

        assertTrue("the persisted REST gap should reconcile successfully", replayed);
        RestCheatEvidence.Snapshot snapshot = evidence.snapshot();
        assertEquals("HISTORICAL_REPLAY", snapshot.lastSource);
        assertEquals(VERIFIED_TARGET_MS, snapshot.sessionHistoricalReplayMs);
        assertEquals(VERIFIED_TARGET_MS, snapshot.lastAccumulatedMs);
    }

    @Test public void pendingRestWindowReplayRecordsVerifiedHistoricalEvidence() throws Exception {
        RestWindow rest = persistActiveRest(902L);
        addUsageEvent(TARGET, UsageEvents.Event.ACTIVITY_RESUMED, rest.startedAtWallMs + 1_000L);
        addUsageEvent(SAFE, UsageEvents.Event.ACTIVITY_RESUMED, rest.startedAtWallMs + 5_000L);
        createServiceAndAwaitInitialLoad();
        RestCheatEvidence evidence = resetSessionEvidence();

        assertTrue("valid restored REST must schedule its historical replay",
                (Boolean) getField(service, "restHistoryReplayPending"));
        assertEquals(BlockStateMachine.State.RESTING,
                onMonitor(() -> ((BlockStateMachine) getField(service, "machine")).getState()));

        Object outcome = onMonitor(() -> invoke(
                service,
                "replayRestWindowHistory",
                new Class<?>[]{long.class, long.class},
                System.currentTimeMillis(),
                SystemClock.elapsedRealtime()
        ));

        assertEquals("REPLAYED", outcome.toString());
        RestCheatEvidence.Snapshot snapshot = evidence.snapshot();
        assertEquals("HISTORICAL_REPLAY", snapshot.lastSource);
        assertEquals(VERIFIED_TARGET_MS, snapshot.sessionHistoricalReplayMs);
        assertEquals(VERIFIED_TARGET_MS, snapshot.lastAccumulatedMs);
    }

    private RestWindow persistActiveRest(long sessionId) {
        // Advance the Robolectric clock so the persisted monotonic anchor remains
        // positive even when the test runner starts elapsedRealtime near zero.
        ShadowSystemClock.advanceBy(30_000L, TimeUnit.MILLISECONDS);
        long nowWallMs = System.currentTimeMillis();
        long nowElapsedMs = SystemClock.elapsedRealtime();
        long startedAtWallMs = nowWallMs - 15_000L;
        long startedAtElapsedMs = nowElapsedMs - 15_000L;
        int currentBootCount = RestSessionClock.currentBootCount(context);
        assertTrue("Robolectric boot count must be available", currentBootCount >= 0);

        FlowServiceStateStore store = new FlowServiceStateStore(context);
        assertTrue("test fixture must persist a valid REST session", store.persistBeginRest(
                startedAtWallMs,
                REST_REQUIRED_MS,
                sessionId,
                false,
                true,
                SAFE,
                "",
                Collections.singleton(TARGET),
                startedAtElapsedMs,
                currentBootCount
        ));
        return new RestWindow(startedAtWallMs);
    }

    private void createServiceAndAwaitInitialLoad() throws Exception {
        serviceController = Robolectric.buildService(FlowForegroundService.class).create();
        service = serviceController.get();
        onMonitor(() -> null);
    }

    private RestCheatEvidence resetSessionEvidence() throws Exception {
        Field field = FlowForegroundService.class.getDeclaredField("restCheatEvidence");
        field.setAccessible(true);
        RestCheatEvidence evidence = (RestCheatEvidence) field.get(null);
        evidence.onRestStarted();
        assertEquals("AWAITING_NATIVE_OBSERVATION", evidence.snapshot().lastSource);
        return evidence;
    }

    private void addUsageEvent(String packageName, int eventType, long at) throws Exception {
        UsageEvents.Event event = new UsageEvents.Event();
        setField(event, "mPackage", packageName);
        setField(event, "mClass", "MainActivity");
        setField(event, "mTimeStamp", at);
        setField(event, "mEventType", eventType);
        Shadows.shadowOf(usageStatsManager).addEvent(event);
    }

    private static Object invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invoke(
            Object target,
            String name,
            Class<?>[] parameterTypes,
            Object... arguments
    ) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, arguments);
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
        if (failure.get() != null) {
            throw new AssertionError("Monitor test command failed", failure.get());
        }
        return result.get();
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RestWindow {
        final long startedAtWallMs;

        RestWindow(long startedAtWallMs) {
            this.startedAtWallMs = startedAtWallMs;
        }
    }
}
