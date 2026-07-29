package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RestSessionValidatorTest {
    @Test public void requiresTheFullNativeRestDuration() {
        assertFalse(RestSessionValidator.isComplete(10_000L, 180_000L, 189_999L));
        assertTrue(RestSessionValidator.isComplete(10_000L, 180_000L, 190_000L));
        assertTrue(RestSessionValidator.isComplete(10_000L, 180_000L, 190_001L));
    }

    @Test public void rejectsMissingOrFutureStartTimes() {
        assertFalse(RestSessionValidator.isComplete(0L, 60_000L, 70_000L));
        assertFalse(RestSessionValidator.isComplete(70_000L, 60_000L, 60_000L));
    }
}
