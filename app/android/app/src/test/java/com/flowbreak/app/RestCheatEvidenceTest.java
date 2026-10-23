package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Non-sensitive evidence only; these tests never mutate the anti-cheat tracker. */
public class RestCheatEvidenceTest {
    @Test public void pausedRestTargetObservationExposesThresholdAndSource() {
        RestCheatEvidence evidence = new RestCheatEvidence();
        evidence.onRestStarted();
        evidence.recordLive(true, true, true, true, 2_000L, 10_000L);
        evidence.recordLive(true, true, true, true, 6_000L, 14_000L);

        RestCheatEvidence.Snapshot snapshot = evidence.snapshot();
        assertEquals("PAUSED_REST", snapshot.lastSource);
        assertEquals(2L, snapshot.sessionObservations);
        assertEquals(2L, snapshot.sessionPausedObservations);
        assertEquals(2L, snapshot.sessionTargetObservations);
        assertTrue(snapshot.lastForegroundPresent);
        assertTrue(snapshot.lastForegroundInTargets);
        assertTrue(snapshot.lastIsTarget);
        assertEquals(6_000L, snapshot.lastAccumulatedMs);
        assertEquals(6_000L, snapshot.maxAccumulatedMs);
        assertEquals(0L, snapshot.cancellationCount);
    }

    @Test public void nonTargetGapDoesNotErasePreviouslyVerifiedCumulativeEvidence() {
        RestCheatEvidence evidence = new RestCheatEvidence();
        evidence.onRestStarted();
        evidence.recordLive(false, true, true, true, 3_000L, 1_000L);
        evidence.recordLive(false, true, false, false, 3_000L, 3_000L);
        evidence.recordLive(false, true, true, true, 5_000L, 5_000L);

        RestCheatEvidence.Snapshot snapshot = evidence.snapshot();
        assertEquals(3L, snapshot.sessionObservations);
        assertEquals(2L, snapshot.sessionTargetObservations);
        assertEquals(0L, snapshot.sessionPausedObservations);
        assertEquals(5_000L, snapshot.maxAccumulatedMs);
        assertEquals("ACTIVE_REST", snapshot.lastSource);
    }

    @Test public void cancellationEvidenceSurvivesTrackerResetAndNewRestSession() {
        RestCheatEvidence evidence = new RestCheatEvidence();
        evidence.onRestStarted();
        evidence.recordLive(true, true, true, true, 5_500L, 5_000L);
        evidence.recordCancellation(5_500L, 6_000L);

        RestCheatEvidence.Snapshot cancelled = evidence.snapshot();
        assertEquals(1L, cancelled.cancellationCount);
        assertEquals(5_500L, cancelled.lastCancelledAccumulatedMs);
        assertEquals(6_000L, cancelled.lastCancellationAt);

        evidence.onRestStarted();
        RestCheatEvidence.Snapshot nextSession = evidence.snapshot();
        assertEquals(0L, nextSession.sessionObservations);
        assertEquals(0L, nextSession.lastAccumulatedMs);
        assertEquals(0L, nextSession.maxAccumulatedMs);
        assertEquals("AWAITING_NATIVE_OBSERVATION", nextSession.lastSource);
        assertFalse(nextSession.lastIsTarget);
        assertEquals(1L, nextSession.cancellationCount);
        assertEquals(5_500L, nextSession.lastCancelledAccumulatedMs);
    }

    @Test public void historicalTargetOutsideRestIsNotAcceptedAsCheatEvidence() {
        assertFalse(RestCheatEvidence.shouldRecordHistoricalReplay(false, 10_000L));
        assertFalse(RestCheatEvidence.shouldRecordHistoricalReplay(false, 0L));
        assertFalse(RestCheatEvidence.shouldRecordHistoricalReplay(true, 0L));
        assertTrue(RestCheatEvidence.shouldRecordHistoricalReplay(true, 5_000L));

        RestCheatEvidence evidence = new RestCheatEvidence();
        evidence.onRestStarted();
        long ordinaryTargetMs = 10_000L;
        if (RestCheatEvidence.shouldRecordHistoricalReplay(false, ordinaryTargetMs)) {
            evidence.recordHistoricalReplay(ordinaryTargetMs, ordinaryTargetMs, 6_000L);
        }
        RestCheatEvidence.Snapshot snapshot = evidence.snapshot();
        assertEquals(0L, snapshot.sessionHistoricalReplayMs);
        assertEquals(0L, snapshot.maxAccumulatedMs);
        assertEquals("AWAITING_NATIVE_OBSERVATION", snapshot.lastSource);
    }

    @Test public void historicalReplayRecordsOnlyVerifiedMillisAndSnapshotIsImmutable() {
        RestCheatEvidence evidence = new RestCheatEvidence();
        evidence.onRestStarted();
        evidence.recordHistoricalReplay(4_000L, 4_000L, 3_000L);
        RestCheatEvidence.Snapshot previous = evidence.snapshot();
        evidence.recordHistoricalReplay(2_000L, 6_000L, 4_000L);
        RestCheatEvidence.Snapshot current = evidence.snapshot();

        assertEquals(4_000L, previous.sessionHistoricalReplayMs);
        assertEquals(6_000L, current.sessionHistoricalReplayMs);
        assertEquals(6_000L, current.maxAccumulatedMs);
        assertEquals("HISTORICAL_REPLAY", current.lastSource);
    }
}
