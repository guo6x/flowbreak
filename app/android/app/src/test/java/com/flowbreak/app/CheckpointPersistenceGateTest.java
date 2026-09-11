package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CheckpointPersistenceGateTest {
    @Test public void firstPersistenceIsAllowed() {
        assertTrue(CheckpointPersistenceGate.shouldPersist(0L, 2_000L, false));
    }

    @Test public void persistenceBeforeIntervalIsDenied() {
        assertFalse(CheckpointPersistenceGate.shouldPersist(10_000L, 19_999L, false));
    }

    @Test public void persistenceAtIntervalIsAllowed() {
        assertTrue(CheckpointPersistenceGate.shouldPersist(10_000L, 20_000L, false));
    }

    @Test public void forcePersistenceIsAllowedBeforeInterval() {
        assertTrue(CheckpointPersistenceGate.shouldPersist(10_000L, 10_001L, true));
    }

    @Test public void clockRegressionAllowsConservativePersistence() {
        assertTrue(CheckpointPersistenceGate.shouldPersist(20_000L, 19_999L, false));
    }

    @Test public void healthyTwoSecondTicksDoNotPersistEveryTick() {
        long lastPersist = 0L;
        int durableWrites = 0;
        for (long now = 2_000L; now <= 22_000L; now += 2_000L) {
            if (CheckpointPersistenceGate.shouldPersist(lastPersist, now, false)) {
                durableWrites++;
                lastPersist = now;
            }
        }

        assertEquals(3, durableWrites);
        assertTrue(durableWrites < 11);
    }
}
