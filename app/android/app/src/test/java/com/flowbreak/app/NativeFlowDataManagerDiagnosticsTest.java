package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.getcapacitor.JSObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class NativeFlowDataManagerDiagnosticsTest {
    @Test public void sharedDiagnosticsKeepsSupportSummaryAndOmitsUsageTimeline() {
        JSObject runtimeTracking = new JSObject();
        runtimeTracking.put("lastCompletedMonitorTickWallMs", 1_792_329_600_000L);
        runtimeTracking.put("recentTicks", "recent tick observations");

        JSObject source = new JSObject();
        source.put("versionName", "1.0.0");
        source.put("channel", "domestic");
        source.put("serviceAlive", true);
        source.put("eventCount", 42);
        source.put("targetCount", 2);
        source.put("runtimeTracking", runtimeTracking);
        source.put("lastUsageEventAt", 1_792_329_600_000L);
        source.put("latestEventAt", 1_792_329_600_000L);
        source.put("graceUntil", 1_792_329_600_000L);

        assertTrue(source.has("versionName"));
        assertEquals("1.0.0", source.optString("versionName"));
        JSObject shared = NativeFlowDataManager.shareableDiagnostics(source);

        assertEquals("1.0.0", shared.optString("versionName"));
        assertEquals("domestic", shared.optString("channel"));
        assertTrue(shared.optBoolean("serviceAlive"));
        assertEquals(42, shared.optInt("eventCount"));
        assertEquals(2, shared.optInt("targetCount"));
        assertFalse(shared.has("runtimeTracking"));
        assertFalse(shared.has("lastUsageEventAt"));
        assertFalse(shared.has("latestEventAt"));
        assertFalse(shared.has("graceUntil"));
    }
}
