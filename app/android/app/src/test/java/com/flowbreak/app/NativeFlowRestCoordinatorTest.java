package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.SQLiteMode;
import org.robolectric.RobolectricTestRunner;

/**
 * 验证休息协调器的 activity 规范化和完成态幂等重试。
 */
@RunWith(RobolectricTestRunner.class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
public class NativeFlowRestCoordinatorTest {
    private static final long ROOM_WORKER_TIMEOUT_SECONDS = 30L;

    @Test public void eyeActivityIsPreserved() {
        assertEquals("eye", NativeFlowRestCoordinator.normalizeActivity("eye"));
    }

    @Test public void stretchActivityIsPreserved() {
        assertEquals("stretch", NativeFlowRestCoordinator.normalizeActivity("stretch"));
    }

    @Test public void breatheActivityIsPreserved() {
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity("breathe"));
    }

    @Test public void unknownActivityFallsBackToBreathe() {
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity("unknown"));
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity(""));
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity("walking"));
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity("EYE"));
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity(null));
    }

    @Test public void legacyRestWithoutTargetSnapshotCannotBeCompletedDirectly()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(
                FlowServiceStateStore.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        long now = System.currentTimeMillis();
        long sessionId = 8_008L;
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong(FlowForegroundService.PREF_REST_STARTED_AT, now - 180_000L)
                .putLong(FlowForegroundService.PREF_REST_REQUIRED_MS, 180_000L)
                .putLong(FlowForegroundService.PREF_REST_SESSION_ID, sessionId)
                .commit();

        NativeFlowRestCoordinator coordinator = new NativeFlowRestCoordinator(context);
        try {
            runOnWorker(() -> coordinator.complete(prefs, "breathe", sessionId));
            fail("a legacy session without target provenance must not complete");
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause()
                    instanceof NativeFlowRestCoordinator.RestPendingException);
        }

        assertEquals(BlockStateMachine.State.RESTING.name(), prefs.getString("blockState", ""));
        assertFalse(prefs.contains(FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID));
    }

    @Test public void malformedTargetSnapshotCannotBeCompletedDirectly()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(
                FlowServiceStateStore.PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        long now = System.currentTimeMillis();
        long sessionId = 8_009L;
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong(FlowForegroundService.PREF_REST_STARTED_AT, now - 180_000L)
                .putLong(FlowForegroundService.PREF_REST_REQUIRED_MS, 180_000L)
                .putLong(FlowForegroundService.PREF_REST_SESSION_ID, sessionId)
                .putString(FlowForegroundService.PREF_REST_TARGET_APPS, "malformed")
                .putLong(FlowForegroundService.PREF_REST_TARGET_SESSION_ID, sessionId)
                .commit();

        NativeFlowRestCoordinator coordinator = new NativeFlowRestCoordinator(context);
        try {
            runOnWorker(() -> coordinator.complete(prefs, "breathe", sessionId));
            fail("a malformed target snapshot must not authorize completion");
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause()
                    instanceof NativeFlowRestCoordinator.RestPendingException);
        }

        assertEquals(BlockStateMachine.State.RESTING.name(), prefs.getString("blockState", ""));
        assertFalse(prefs.contains(FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID));
    }

    @Test public void committedSessionRetryReturnsExistingResultWithoutRepeatingEffects()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        FlowDao dao = FlowDatabase.get(context).flowDao();
        SharedPreferences prefs = context.getSharedPreferences(
                FlowServiceStateStore.PREFS, Context.MODE_PRIVATE);
        AtomicReference<String> fixtureStage = new AtomicReference<>("starting Room fixture");
        runOnWorker(() -> {
            fixtureStage.set("clearAll");
            dao.clearAll();
            long now = System.currentTimeMillis();
            String today = NativeFlowStatisticsService.day(now);
            fixtureStage.set("insertEvent");
            dao.insertEvent(new FlowEventEntity(
                    now, "rest_complete", "", "breathe", 60L, ""));
            fixtureStage.set("recordRest");
            dao.recordRest(today, 60L);
            fixtureStage.set("saveProgress");
            dao.saveProgress(new ProgressEntity(
                    20, 1, today, "[\"health_guardian\"]"));
            return null;
        }, fixtureStage);
        prefs.edit().clear().commit();

        long sessionId = System.currentTimeMillis();
        long committedGraceUntil = System.currentTimeMillis() + 10 * 60_000L;
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.GRACE.name())
                .putLong("graceUntil", committedGraceUntil)
                .putLong(FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID, sessionId)
                .putLong(FlowForegroundService.PREF_COMPLETED_REST_GRACE_UNTIL,
                        committedGraceUntil)
                .commit();

        NativeFlowRestCoordinator coordinator = new NativeFlowRestCoordinator(context);
        try {
            runOnWorker(() -> coordinator.complete(prefs, "breathe", sessionId + 1L));
            fail("a different expected session must not reuse this completed result");
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause()
                    instanceof NativeFlowRestCoordinator.RestPendingException);
        }
        NativeFlowRestCoordinator.Result retry = runOnWorker(
                () -> coordinator.complete(prefs, "stretch", sessionId));

        assertFalse(retry.needServiceRefresh);
        assertEquals(0L, prefs.getLong(FlowForegroundService.PREF_REST_SESSION_ID, 0L));
        assertEquals(sessionId, prefs.getLong(
                FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID, -1L));
        assertTrue(committedGraceUntil > System.currentTimeMillis());
        assertEquals(committedGraceUntil, retry.response.getLong("graceUntil"));
        assertEquals(20, retry.response.getInt("points"));
        assertEquals(1, retry.response.getInt("streak"));
        assertEquals("", retry.response.getString("achievement"));
        int restCompleteEventCount = runOnWorker(
                () -> dao.countEventsSince("rest_complete", 0L)
        );
        assertEquals(1, restCompleteEventCount);
        ProgressEntity progressAfterRetry = runOnWorker(dao::getProgress);
        assertNotNull(progressAfterRetry);
        assertEquals(20, progressAfterRetry.points);
        assertEquals(1, progressAfterRetry.streak);
        DailySummaryEntity summary = runOnWorker(() -> dao.summaryForDay(
                NativeFlowStatisticsService.day(System.currentTimeMillis())));
        assertNotNull(summary);
        assertEquals(1, summary.restCount);
        assertEquals(0L, prefs.getLong("dbRestRecordedSessionId", 0L));
    }

    private static <T> T runOnWorker(Callable<T> callable) throws Exception {
        return runOnWorker(callable, new AtomicReference<>("worker operation"));
    }

    private static <T> T runOnWorker(
            Callable<T> callable,
            AtomicReference<String> stage
    ) throws Exception {
        FutureTask<T> task = new FutureTask<>(callable);
        Thread worker = new Thread(task, "rest-coordinator-test");
        worker.setDaemon(true);
        worker.start();
        try {
            return task.get(ROOM_WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            StringBuilder workerStack = new StringBuilder();
            for (StackTraceElement frame : worker.getStackTrace()) {
                workerStack.append("\n  at ").append(frame);
            }
            task.cancel(true);
            worker.interrupt();
            throw new AssertionError(
                    "Room worker timed out during " + stage.get()
                            + "; worker stack:" + workerStack,
                    timeout
            );
        }
    }
}
