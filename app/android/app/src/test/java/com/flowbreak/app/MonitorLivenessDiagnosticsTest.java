package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.getcapacitor.JSObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MonitorLivenessDiagnosticsTest {
    @Test public void t1_dueCallbackHasZeroLateness() {
        MonitorLivenessDiagnostics diagnostics = new MonitorLivenessDiagnostics(123, 10L, 20L);
        long generation = diagnostics.recordServiceCreate(100L, 1_000L);
        diagnostics.recordCallbackScheduled(generation, 1_000L, 3_000L);
        diagnostics.recordMonitorStart(generation, 3_000L, 30L);

        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(3_000L, snapshot.scheduledCallbackDueElapsedMs);
        assertEquals(3_000L, snapshot.actualMonitorStartElapsedMs);
        assertEquals(0L, snapshot.callbackLatenessMs);
        assertEquals(0L, snapshot.maxCallbackLatenessMs);
        assertEquals(0L, snapshot.callbackLatenessOver3000Count);
        assertEquals(0L, snapshot.callbackLatenessOver5000Count);
    }

    @Test public void t2_callbackJitterIsMeasuredWithoutChangingSchedule() {
        MonitorLivenessDiagnostics diagnostics = new MonitorLivenessDiagnostics(123, 10L, 20L);
        long generation = diagnostics.recordServiceCreate(100L, 1_000L);
        diagnostics.recordCallbackScheduled(generation, 1_000L, 3_000L);
        diagnostics.recordMonitorStart(generation, 3_200L, 30L);

        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(3_200L, snapshot.actualMonitorStartElapsedMs);
        assertEquals(200L, snapshot.callbackLatenessMs);
        assertEquals(200L, snapshot.maxCallbackLatenessMs);
        assertEquals(0L, snapshot.callbackLatenessOver3000Count);
        assertEquals(0L, snapshot.callbackLatenessOver5000Count);
    }

    @Test public void t3_longFreezeCountsOneOverFiveSecondCallback() {
        MonitorLivenessDiagnostics diagnostics = new MonitorLivenessDiagnostics(123, 10L, 20L);
        long generation = diagnostics.recordServiceCreate(100L, 1_000L);
        diagnostics.recordCallbackScheduled(generation, 1_000L, 3_000L);
        diagnostics.recordMonitorStart(generation, 503_000L, 30L);

        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(500_000L, snapshot.callbackLatenessMs);
        assertEquals(500_000L, snapshot.maxCallbackLatenessMs);
        assertEquals(1L, snapshot.callbackLatenessOver3000Count);
        assertEquals(1L, snapshot.callbackLatenessOver5000Count);
        assertFalse(snapshot.callbackDeadlinePending);
    }

    @Test public void t4_stopAndRearmDoNotBecomeAFalseDelayedCallback() {
        MonitorLivenessDiagnostics diagnostics = new MonitorLivenessDiagnostics(123, 10L, 20L);
        long generation = diagnostics.recordServiceCreate(100L, 1_000L);
        diagnostics.recordMonitorLoopStart(
                generation,
                MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START
        );
        diagnostics.recordCallbackScheduled(generation, 1_000L, 3_000L);
        diagnostics.cancelPendingCallbackDeadline(generation);
        diagnostics.recordMonitorLoopStop(
                generation,
                MonitorLivenessDiagnostics.LOOP_REASON_USER_STOP
        );
        diagnostics.recordMonitorLoopStart(
                generation,
                MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START
        );
        diagnostics.recordMonitorStart(generation, 10_000L, 30L);
        diagnostics.recordCallbackScheduled(generation, 11_000L, 13_000L);
        diagnostics.recordMonitorStart(generation, 13_000L, 40L);

        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(2L, snapshot.monitorLoopStartCount);
        assertEquals(1L, snapshot.monitorLoopStopCount);
        assertEquals(0L, snapshot.callbackLatenessMs);
        assertEquals(0L, snapshot.callbackLatenessOver5000Count);
        assertEquals(0L, snapshot.callbackLatenessOver3000Count);
    }

    @Test public void t5_serviceInstanceAndLifecycleCountersAreExplicit() {
        MonitorLivenessDiagnostics diagnostics = new MonitorLivenessDiagnostics(321, 10L, 20L);
        long generation = diagnostics.recordServiceCreate(100L, 1_000L);
        diagnostics.recordServiceStartCommand(
                generation,
                MonitorLivenessDiagnostics.ACTION_RELOAD,
                110L,
                1_010L
        );
        diagnostics.recordTaskRemoved(generation, 120L, 1_020L);
        diagnostics.recordServiceDestroy(generation, 130L, 1_030L);
        diagnostics.recordServiceCreate(200L, 2_000L);

        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals("321-2", snapshot.serviceInstanceId);
        assertEquals(2L, snapshot.serviceCreateCount);
        assertEquals(1L, snapshot.serviceStartCommandCount);
        assertEquals(MonitorLivenessDiagnostics.ACTION_RELOAD, snapshot.lastStartCommandAction);
        assertEquals("321-1", snapshot.lastStartCommandServiceInstanceId);
        assertEquals(1L, snapshot.serviceTaskRemovedCount);
        assertEquals(1L, snapshot.serviceDestroyCount);
        assertEquals(2_000L, snapshot.serviceInstanceStartedElapsedMs);
        assertEquals(321, snapshot.processPid);
        assertEquals(10L, snapshot.processInstanceStartedElapsedMs);
        assertEquals(20L, snapshot.processInstanceStartedWallMs);
        assertEquals("321-1", snapshot.lastDestroyedServiceInstanceId);
        assertEquals("321-1", snapshot.lastTaskRemovedServiceInstanceId);
    }

    @Test public void sameProcessServiceRecreationGetsFreshMonitorState() {
        MonitorLivenessDiagnostics diagnostics = new MonitorLivenessDiagnostics(321, 10L, 20L);
        long generation1 = diagnostics.recordServiceCreate(100L, 1_000L);
        diagnostics.recordMonitorLoopStart(
                generation1,
                MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START
        );
        diagnostics.recordCallbackScheduled(generation1, 1_000L, 3_000L);
        diagnostics.recordMonitorLoopShutdown(
                generation1,
                MonitorLivenessDiagnostics.LOOP_REASON_SERVICE_DESTROY
        );

        MonitorLivenessDiagnostics.Snapshot generation1Snapshot = diagnostics.snapshot();
        assertEquals(1L, generation1Snapshot.currentServiceGeneration);
        assertFalse(generation1Snapshot.monitorLoopActive);
        assertTrue(generation1Snapshot.monitorLoopShutdown);

        long generation2 = diagnostics.recordServiceCreate(200L, 2_000L);
        diagnostics.recordMonitorLoopStart(
                generation2,
                MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START
        );

        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals("321-2", snapshot.serviceInstanceId);
        assertEquals(2L, generation2);
        assertEquals(2L, snapshot.currentServiceGeneration);
        assertEquals(2L, snapshot.serviceCreateCount);
        assertTrue(snapshot.monitorLoopActive);
        assertFalse(snapshot.monitorLoopShutdown);
        assertEquals(1L, snapshot.monitorLoopStartCount);
        assertEquals(0L, snapshot.monitorLoopShutdownCount);
        assertFalse(snapshot.callbackDeadlinePending);
    }

    @Test public void lateOldServiceShutdownDoesNotPoisonNewService() {
        MonitorLivenessDiagnostics diagnostics = new MonitorLivenessDiagnostics(321, 10L, 20L);
        long generation1 = diagnostics.recordServiceCreate(100L, 1_000L);
        diagnostics.recordMonitorLoopStart(
                generation1,
                MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START
        );

        long generation2 = diagnostics.recordServiceCreate(200L, 2_000L);
        diagnostics.recordMonitorLoopStart(
                generation2,
                MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START
        );
        diagnostics.recordCallbackScheduled(generation2, 2_000L, 5_000L);

        diagnostics.recordMonitorLoopStop(
                generation1,
                MonitorLivenessDiagnostics.LOOP_REASON_SERVICE_DESTROY
        );
        diagnostics.recordMonitorLoopShutdown(
                generation1,
                MonitorLivenessDiagnostics.LOOP_REASON_SERVICE_DESTROY
        );
        diagnostics.cancelPendingCallbackDeadline(generation1);

        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(2L, snapshot.currentServiceGeneration);
        assertTrue(snapshot.monitorLoopActive);
        assertFalse(snapshot.monitorLoopShutdown);
        assertTrue(snapshot.callbackDeadlinePending);
        assertEquals(5_000L, snapshot.scheduledCallbackDueElapsedMs);
    }

    @Test public void oldServiceCallbackBookkeepingDoesNotOverwriteNewService() {
        MonitorLivenessDiagnostics diagnostics = new MonitorLivenessDiagnostics(321, 10L, 20L);
        long generation1 = diagnostics.recordServiceCreate(100L, 1_000L);
        diagnostics.recordCallbackScheduled(generation1, 1_000L, 3_000L);

        long generation2 = diagnostics.recordServiceCreate(200L, 2_000L);
        diagnostics.recordMonitorLoopStart(
                generation2,
                MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START
        );
        diagnostics.recordCallbackScheduled(generation2, 2_000L, 5_000L);

        diagnostics.cancelPendingCallbackDeadline(generation1);
        assertFalse(diagnostics.recordMonitorStart(generation1, 100_000L, 100L));

        MonitorLivenessDiagnostics.Snapshot beforeCurrentCallback = diagnostics.snapshot();
        assertTrue(beforeCurrentCallback.callbackDeadlinePending);
        assertEquals(5_000L, beforeCurrentCallback.scheduledCallbackDueElapsedMs);
        assertEquals(2_000L, beforeCurrentCallback.lastCallbackScheduledElapsedMs);
        assertEquals(0L, beforeCurrentCallback.actualMonitorStartElapsedMs);

        assertTrue(diagnostics.recordMonitorStart(generation2, 5_500L, 200L));
        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertFalse(snapshot.callbackDeadlinePending);
        assertEquals(500L, snapshot.callbackLatenessMs);
        assertEquals(500L, snapshot.maxCallbackLatenessMs);
    }

    @Test public void t6_exportContainsDiagnosticsButNoTargetHistoryOrSecrets() {
        JSObject diagnostics = FlowForegroundService.getRuntimeTrackingDiagnostics();

        assertTrue(diagnostics.has("lifecycle"));
        assertTrue(diagnostics.has("scheduler"));
        assertTrue(diagnostics.has("platform"));
        assertFalse(diagnostics.has("tv.danmaku.bili"));
        assertFalse(diagnostics.has("password"));
        assertFalse(diagnostics.has("secret"));
    }
}
