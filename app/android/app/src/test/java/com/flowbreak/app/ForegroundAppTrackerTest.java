package com.flowbreak.app;

import android.app.usage.UsageEvents;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ForegroundAppTrackerTest {
    @Test public void tracksDuplicateResumesAndClearsOnPause() {
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", UsageEvents.Event.ACTIVITY_RESUMED, 100L);
        tracker.accept("com.example.video", UsageEvents.Event.ACTIVITY_RESUMED, 200L);
        assertEquals("com.example.video", tracker.getForegroundPackage());

        tracker.accept("com.example.video", UsageEvents.Event.ACTIVITY_PAUSED, 300L);
        assertEquals("", tracker.getForegroundPackage());
    }

    @Test public void systemForegroundReplacesTargetAndStaleEventsAreIgnored() {
        ForegroundAppTracker tracker = new ForegroundAppTracker();
        tracker.accept("com.example.video", UsageEvents.Event.MOVE_TO_FOREGROUND, 100L);
        tracker.accept("com.android.settings", UsageEvents.Event.MOVE_TO_FOREGROUND, 200L);
        tracker.accept("com.example.video", UsageEvents.Event.ACTIVITY_RESUMED, 150L);
        assertEquals("com.android.settings", tracker.getForegroundPackage());
    }
}
