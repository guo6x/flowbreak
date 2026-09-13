package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class RestCompletionIntegrityGateTest {
    private static final String RESTING = BlockStateMachine.State.RESTING.name();
    private static final String BLOCKED = BlockStateMachine.State.BLOCKED.name();

    @Test
    public void restCompletionDeferredWhileActiveMonitorIsStale() throws Exception {
        AtomicInteger dbEvents = new AtomicInteger();
        AtomicInteger pointsWrites = new AtomicInteger();
        AtomicInteger graceCommits = new AtomicInteger();

        RestCompletionIntegrityGate.Attempt<String> attempt = RestCompletionIntegrityGate.execute(
                RESTING,
                true,
                1_000L,
                501_000L,
                () -> {
                    dbEvents.incrementAndGet();
                    pointsWrites.incrementAndGet();
                    graceCommits.incrementAndGet();
                    return "completed";
                }
        );

        assertTrue(attempt.deferred());
        assertEquals(RestCompletionIntegrityGate.Decision.DEFER, attempt.decision);
        assertEquals(0, dbEvents.get());
        assertEquals(0, pointsWrites.get());
        assertEquals(0, graceCommits.get());
        assertEquals(null, attempt.value);
    }

    @Test
    public void freshActiveMonitorAllowsCompletion() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RestCompletionIntegrityGate.Attempt<String> attempt = RestCompletionIntegrityGate.execute(
                RESTING,
                true,
                10_000L,
                12_000L,
                () -> {
                    calls.incrementAndGet();
                    return "completed";
                }
        );

        assertFalse(attempt.deferred());
        assertEquals(RestCompletionIntegrityGate.Decision.ALLOW, attempt.decision);
        assertEquals(1, calls.get());
        assertEquals("completed", attempt.value);
    }

    @Test
    public void inactiveServicePreservesExistingRecoveryBehavior() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RestCompletionIntegrityGate.Attempt<String> attempt = RestCompletionIntegrityGate.execute(
                RESTING,
                false,
                0L,
                500_000L,
                () -> {
                    calls.incrementAndGet();
                    return "recovered";
                }
        );

        assertEquals(RestCompletionIntegrityGate.Decision.ALLOW, attempt.decision);
        assertEquals(1, calls.get());
        assertEquals("recovered", attempt.value);
    }

    @Test
    public void nonRestingStateKeepsExistingValidationPath() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RestCompletionIntegrityGate.Attempt<String> attempt = RestCompletionIntegrityGate.execute(
                BLOCKED,
                true,
                1_000L,
                501_000L,
                () -> {
                    calls.incrementAndGet();
                    return "validated-by-existing-path";
                }
        );

        assertEquals(RestCompletionIntegrityGate.Decision.ALLOW, attempt.decision);
        assertEquals(1, calls.get());
        assertEquals("validated-by-existing-path", attempt.value);
    }

    @Test
    public void staleGraceOverwriteRaceIsClosedBeforeCommit() throws Exception {
        AtomicInteger graceCommits = new AtomicInteger();

        RestCompletionIntegrityGate.Attempt<Void> attempt = RestCompletionIntegrityGate.execute(
                RESTING,
                true,
                100_000L,
                100_000L + RestCheatTracker.CHEAT_THRESHOLD_MS + 1L,
                () -> {
                    graceCommits.incrementAndGet();
                    return null;
                }
        );

        assertEquals(RestCompletionIntegrityGate.Decision.DEFER, attempt.decision);
        assertEquals(0, graceCommits.get());
    }

    @Test
    public void activeIntegrityFailureDefersEvenWhenHeartbeatIsFresh() throws Exception {
        AtomicInteger completions = new AtomicInteger();
        ProtectionRuntimeHealthEvaluator.Result runtimeHealth =
                ProtectionRuntimeHealthEvaluator.evaluate(
                        true,
                        true,
                        10_000L,
                        12_000L,
                        "LIVE_USAGE_QUERY_SECURITY"
                );

        RestCompletionIntegrityGate.Attempt<String> attempt =
                RestCompletionIntegrityGate.execute(
                        RESTING,
                        runtimeHealth,
                        () -> {
                            completions.incrementAndGet();
                            return "must-not-commit";
                        }
                );

        assertEquals(RestCompletionIntegrityGate.Decision.DEFER, attempt.decision);
        assertEquals(0, completions.get());
        assertEquals(null, attempt.value);
    }

    @Test
    public void missingRuntimeHealthSnapshotDefersRestCompletion() throws Exception {
        AtomicInteger completions = new AtomicInteger();
        RestCompletionIntegrityGate.Attempt<String> attempt =
                RestCompletionIntegrityGate.execute(
                        RESTING,
                        null,
                        () -> {
                            completions.incrementAndGet();
                            return "must-not-commit";
                        }
                );

        assertEquals(RestCompletionIntegrityGate.Decision.DEFER, attempt.decision);
        assertEquals(0, completions.get());
        assertEquals(null, attempt.value);
    }

    @Test
    public void currentHealthyRuntimeAllowsCompletionThroughSnapshotGate() throws Exception {
        AtomicInteger completions = new AtomicInteger();
        ProtectionRuntimeHealthEvaluator.Result runtimeHealth =
                ProtectionRuntimeHealthEvaluator.evaluate(
                        true,
                        true,
                        10_000L,
                        12_000L,
                        ""
                );

        RestCompletionIntegrityGate.Attempt<String> attempt =
                RestCompletionIntegrityGate.execute(
                        RESTING,
                        runtimeHealth,
                        () -> {
                            completions.incrementAndGet();
                            return "completed";
                        }
                );

        assertEquals(RestCompletionIntegrityGate.Decision.ALLOW, attempt.decision);
        assertEquals(1, completions.get());
        assertEquals("completed", attempt.value);
    }
}
