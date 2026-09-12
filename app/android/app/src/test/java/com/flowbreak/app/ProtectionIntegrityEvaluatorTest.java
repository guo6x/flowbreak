package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProtectionIntegrityEvaluatorTest {
    @Test public void corePrerequisitesRejectMissingUsageAccessBeforeOverlay() {
        assertEquals(
                ProtectionPrerequisiteGate.Reason.USAGE_ACCESS_MISSING,
                ProtectionPrerequisiteGate.evaluate(true, true, true, false, true).reason
        );
    }

    @Test public void corePrerequisitesRejectMissingOverlay() {
        assertEquals(
                ProtectionPrerequisiteGate.Reason.OVERLAY_MISSING,
                ProtectionPrerequisiteGate.evaluate(true, true, true, true, false).reason
        );
    }

    @Test public void startPrerequisitesRejectUnsupportedDisabledAndUnconfigured() {
        assertEquals(
                ProtectionPrerequisiteGate.Reason.UNSUPPORTED_DEVICE,
                ProtectionPrerequisiteGate.evaluate(false, true, true, true, true).reason
        );
        assertEquals(
                ProtectionPrerequisiteGate.Reason.MONITORING_DISABLED,
                ProtectionPrerequisiteGate.evaluate(true, false, true, true, true).reason
        );
        assertEquals(
                ProtectionPrerequisiteGate.Reason.NO_TARGETS,
                ProtectionPrerequisiteGate.evaluate(true, true, false, true, true).reason
        );
    }

    @Test public void activeRequiresCurrentRuntimeThreadAndHeartbeat() {
        ProtectionStatusEvaluator.Result result = ProtectionStatusEvaluator.evaluate(
                input(true, true, true, true, true, true, true, false, false, "")
        );
        assertEquals(ProtectionStatusEvaluator.Status.STARTING_OR_UNCONFIRMED, result.status);
        assertEquals("MONITOR_THREAD_NOT_ALIVE", result.reason);
        assertFalse(result.coreProtectionOperational);

        result = ProtectionStatusEvaluator.evaluate(
                input(true, true, true, true, true, true, false, true, true, "")
        );
        assertEquals(ProtectionStatusEvaluator.Status.STARTING_OR_UNCONFIRMED, result.status);
        assertEquals("SERVICE_NOT_RUNNING", result.reason);

        result = ProtectionStatusEvaluator.evaluate(
                input(true, true, true, true, true, true, true, false, true, "")
        );
        assertEquals(ProtectionStatusEvaluator.Status.STARTING_OR_UNCONFIRMED, result.status);
        assertEquals("MONITOR_THREAD_NOT_ALIVE", result.reason);

        result = ProtectionStatusEvaluator.evaluate(
                input(true, true, true, true, true, true, true, true, false, "")
        );
        assertEquals(ProtectionStatusEvaluator.Status.STARTING_OR_UNCONFIRMED, result.status);
        assertEquals("HEARTBEAT_STALE", result.reason);
    }

    @Test public void activeMeansCoreRuntimeIsCurrentlyOperational() {
        ProtectionStatusEvaluator.Result result = ProtectionStatusEvaluator.evaluate(
                input(true, true, true, true, true, true, true, true, true, "")
        );
        assertEquals(ProtectionStatusEvaluator.Status.ACTIVE, result.status);
        assertTrue(result.coreProtectionOperational);
        assertTrue(result.strongBlockingOperational);
    }

    @Test public void runtimeHealthUsesElapsedRealtimeAndRejectsStaleOrFutureTicks() {
        ProtectionRuntimeHealthEvaluator.Result fresh =
                ProtectionRuntimeHealthEvaluator.evaluate(true, true, 1_000L, 1_000L, "");
        assertTrue(fresh.isHealthy());
        assertTrue(fresh.heartbeatFresh);

        ProtectionRuntimeHealthEvaluator.Result stale =
                ProtectionRuntimeHealthEvaluator.evaluate(
                        true,
                        true,
                        1_000L,
                        1_000L + ProtectionRuntimeHealthEvaluator.SERVICE_HEARTBEAT_STALE_MS,
                        ""
                );
        assertFalse(stale.isHealthy());
        assertEquals("HEARTBEAT_STALE", stale.reason);

        ProtectionRuntimeHealthEvaluator.Result future =
                ProtectionRuntimeHealthEvaluator.evaluate(true, true, 2_000L, 1_000L, "");
        assertFalse(future.isHealthy());
        assertEquals("HEARTBEAT_FROM_FUTURE", future.reason);
    }

    @Test public void oldServiceHeartbeatCannotSatisfyCurrentRuntimeHealth() {
        long oldServiceHeartbeat = FlowForegroundService.heartbeatElapsedForCurrentService(
                1L,
                2L,
                1_000L
        );
        assertEquals(0L, oldServiceHeartbeat);
        assertTrue(FlowForegroundService.heartbeatElapsedForCurrentService(
                2L,
                2L,
                1_000L
        ) > 0L);

        ProtectionRuntimeHealthEvaluator.Result result =
                ProtectionRuntimeHealthEvaluator.evaluate(
                        true,
                        true,
                        oldServiceHeartbeat,
                        1_000L,
                        ""
                );
        assertFalse(result.isHealthy());
        assertEquals("HEARTBEAT_MISSING", result.reason);
    }

    @Test public void runtimeHealthRejectsGenerationChangeAfterInputsAreRead() {
        ProtectionRuntimeHealthEvaluator.Result result =
                FlowForegroundService.evaluateRuntimeHealthSnapshot(
                        1L,
                        2L,
                        true,
                        true,
                        1L,
                        1_000L,
                        1_000L,
                        ""
                );
        assertFalse(result.isHealthy());
        assertEquals("SERVICE_GENERATION_CHANGED", result.integrityFailureReason);
    }

    @Test public void missingCorePermissionDegradesCoreProtection() {
        ProtectionStatusEvaluator.Result result = ProtectionStatusEvaluator.evaluate(
                input(true, true, true, true, false, true, true, true, true, "")
        );
        assertEquals(ProtectionStatusEvaluator.Status.DEGRADED, result.status);
        assertEquals("USAGE_ACCESS_MISSING", result.reason);
        assertFalse(result.coreProtectionOperational);

        result = ProtectionStatusEvaluator.evaluate(
                input(true, true, true, true, true, false, true, true, true, "")
        );
        assertEquals(ProtectionStatusEvaluator.Status.DEGRADED, result.status);
        assertEquals("OVERLAY_MISSING", result.reason);
        assertFalse(result.coreProtectionOperational);
    }

    @Test public void missingAccessibilityDegradesStrongBlockingOnly() {
        ProtectionStatusEvaluator.Result result = ProtectionStatusEvaluator.evaluate(
                new ProtectionStatusEvaluator.Input(
                        true, true, true, true, true, true,
                        true, true, 1_000L, 1_000L,
                        true, false, false, ""
                )
        );
        assertEquals(ProtectionStatusEvaluator.Status.DEGRADED, result.status);
        assertEquals("ACCESSIBILITY_MISSING", result.reason);
        assertTrue(result.coreProtectionOperational);
        assertTrue(result.strongBlockingRequested);
        assertFalse(result.strongBlockingOperational);
        assertEquals("ACCESSIBILITY_MISSING", result.strongBlockingDegradedReason);
    }

    @Test public void strongBlockingReportsCoreFailureSeparately() {
        ProtectionStatusEvaluator.Result result = ProtectionStatusEvaluator.evaluate(
                new ProtectionStatusEvaluator.Input(
                        true, true, true, true, true, true,
                        false, true, 1_000L, 1_000L,
                        true, true, true, ""
                )
        );
        assertEquals(ProtectionStatusEvaluator.Status.STARTING_OR_UNCONFIRMED, result.status);
        assertEquals("SERVICE_NOT_RUNNING", result.reason);
        assertTrue(result.strongBlockingRequested);
        assertFalse(result.strongBlockingOperational);
        assertEquals("SERVICE_NOT_RUNNING", result.strongBlockingDegradedReason);
    }

    @Test public void settingsEnabledButDisconnectedDegradesOnlyStrongBlocking() {
        ProtectionStatusEvaluator.Result result = ProtectionStatusEvaluator.evaluate(
                new ProtectionStatusEvaluator.Input(
                        true, true, true, true, true, true,
                        true, true, 1_000L, 1_000L,
                        true, true, false, ""
                )
        );
        assertEquals(ProtectionStatusEvaluator.Status.DEGRADED, result.status);
        assertEquals("ACCESSIBILITY_SERVICE_NOT_CONNECTED", result.reason);
        assertTrue(result.coreProtectionOperational);
        assertFalse(result.strongBlockingOperational);
        assertEquals(
                "ACCESSIBILITY_SERVICE_NOT_CONNECTED",
                result.strongBlockingDegradedReason
        );
    }

    @Test public void connectedAccessibilityMakesStrongBlockingOperational() {
        ProtectionStatusEvaluator.Result result = ProtectionStatusEvaluator.evaluate(
                new ProtectionStatusEvaluator.Input(
                        true, true, true, true, true, true,
                        true, true, 1_000L, 1_000L,
                        true, true, true, ""
                )
        );
        assertEquals(ProtectionStatusEvaluator.Status.ACTIVE, result.status);
        assertTrue(result.coreProtectionOperational);
        assertTrue(result.strongBlockingOperational);
    }

    @Test public void unsupportedAndPausedRemainDistinct() {
        ProtectionStatusEvaluator.Result unsupported = ProtectionStatusEvaluator.evaluate(
                input(true, true, false, true, true, true, true, true, true, "")
        );
        assertEquals(ProtectionStatusEvaluator.Status.UNSUPPORTED, unsupported.status);
        assertFalse(unsupported.coreProtectionOperational);

        ProtectionStatusEvaluator.Result paused = ProtectionStatusEvaluator.evaluate(
                input(true, false, true, true, true, true, false, false, false, "")
        );
        assertEquals(ProtectionStatusEvaluator.Status.PAUSED, paused.status);
        assertEquals("MONITORING_DISABLED", paused.reason);
    }

    @Test public void staleIntegrityReasonCannotPresentCoreAsActive() {
        ProtectionStatusEvaluator.Result result = ProtectionStatusEvaluator.evaluate(
                input(true, true, true, true, true, true, true, true, true, "LIVE_USAGE_QUERY_SECURITY")
        );
        assertEquals(ProtectionStatusEvaluator.Status.DEGRADED, result.status);
        assertEquals("LIVE_USAGE_QUERY_SECURITY", result.reason);
        assertFalse(result.coreProtectionOperational);
    }

    private static ProtectionStatusEvaluator.Input input(
            boolean configured,
            boolean monitoringEnabled,
            boolean supported,
            boolean targets,
            boolean usage,
            boolean overlay,
            boolean service,
            boolean thread,
            boolean heartbeat,
            String integrityReason
    ) {
        return new ProtectionStatusEvaluator.Input(
                configured,
                monitoringEnabled,
                supported,
                targets,
                usage,
                overlay,
                service,
                thread,
                1_000L,
                heartbeat ? 1_000L : 46_000L,
                false,
                false,
                false,
                integrityReason
        );
    }
}
