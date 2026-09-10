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
        diagnostics.recordCallbackScheduled(1_000L, 3_000L);
        diagnostics.recordMonitorStart(3_000L, 30L);

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
        diagnostics.recordCallbackScheduled(1_000L, 3_000L);
        diagnostics.recordMonitorStart(3_200L, 30L);

        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(3_200L, snapshot.actualMonitorStartElapsedMs);
        assertEquals(200L, snapshot.callbackLatenessMs);
        assertEquals(200L, snapshot.maxCallbackLatenessMs);
        assertEquals(0L, snapshot.callbackLatenessOver3000Count);
        assertEquals(0L, snapshot.callbackLatenessOver5000Count);
    }

    @Test public void t3_longFreezeCountsOneOverFiveSecondCallback() {
        MonitorLivenessDiagnostics diagnostics = new MonitorLivenessDiagnostics(123, 10L, 20L);
        diagnostics.recordCallbackScheduled(1_000L, 3_000L);
        diagnostics.recordMonitorStart(503_000L, 30L);

        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(500_000L, snapshot.callbackLatenessMs);
        assertEquals(500_000L, snapshot.maxCallbackLatenessMs);
        assertEquals(1L, snapshot.callbackLatenessOver3000Count);
        assertEquals(1L, snapshot.callbackLatenessOver5000Count);
        assertFalse(snapshot.callbackDeadlinePending);
    }

    @Test public void t4_stopAndRearmDoNotBecomeAFalseDelayedCallback() {
        MonitorLivenessDiagnostics diagnostics = new MonitorLivenessDiagnostics(123, 10L, 20L);
        diagnostics.recordMonitorLoopStart(MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START);
        diagnostics.recordCallbackScheduled(1_000L, 3_000L);
        diagnostics.cancelPendingCallbackDeadline();
        diagnostics.recordMonitorLoopStop(MonitorLivenessDiagnostics.LOOP_REASON_USER_STOP);
        diagnostics.recordMonitorLoopStart(MonitorLivenessDiagnostics.LOOP_REASON_ENGINE_COMMAND_START);
        diagnostics.recordMonitorStart(10_000L, 30L);
        diagnostics.recordCallbackScheduled(11_000L, 13_000L);
        diagnostics.recordMonitorStart(13_000L, 40L);

        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(2L, snapshot.monitorLoopStartCount);
        assertEquals(1L, snapshot.monitorLoopStopCount);
        assertEquals(0L, snapshot.callbackLatenessMs);
        assertEquals(0L, snapshot.callbackLatenessOver5000Count);
        assertEquals(0L, snapshot.callbackLatenessOver3000Count);
    }

    @Test public void t5_serviceInstanceAndLifecycleCountersAreExplicit() {
        MonitorLivenessDiagnostics diagnostics = new MonitorLivenessDiagnostics(321, 10L, 20L);
        diagnostics.recordServiceCreate(100L, 1_000L);
        diagnostics.recordServiceStartCommand(MonitorLivenessDiagnostics.ACTION_RELOAD, 110L, 1_010L);
        diagnostics.recordTaskRemoved(120L, 1_020L);
        diagnostics.recordServiceDestroy(130L, 1_030L);
        diagnostics.recordServiceCreate(200L, 2_000L);

        MonitorLivenessDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals("321-2", snapshot.serviceInstanceId);
        assertEquals(2L, snapshot.serviceCreateCount);
        assertEquals(1L, snapshot.serviceStartCommandCount);
        assertEquals(MonitorLivenessDiagnostics.ACTION_RELOAD, snapshot.lastStartCommandAction);
        assertEquals(1L, snapshot.serviceTaskRemovedCount);
        assertEquals(1L, snapshot.serviceDestroyCount);
        assertEquals(2_000L, snapshot.serviceInstanceStartedElapsedMs);
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
