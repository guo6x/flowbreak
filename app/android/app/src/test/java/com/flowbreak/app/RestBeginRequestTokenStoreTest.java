package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import com.getcapacitor.JSObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RestBeginRequestTokenStoreTest {
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        preferences = ApplicationProvider.getApplicationContext()
                .getSharedPreferences("RestBeginRequestTokenStoreTest", 0);
        preferences.edit().clear().commit();
    }

    @Test
    public void latestRequestStartsPendingAndCanBeAccepted() {
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(preferences, "begin-current"));

        assertEquals("begin-current", preferences.getString(
                FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, ""
        ));
        assertEquals("begin-current", preferences.getString(
                RestBeginRequestTokenStore.PREF_STATUS_TOKEN, ""
        ));
        assertEquals(RestBeginRequestTokenStore.STATUS_PENDING, preferences.getString(
                RestBeginRequestTokenStore.PREF_STATUS, ""
        ));
        assertTrue(RestBeginRequestTokenStore.recordAcceptedIfCurrent(
                preferences, "begin-current"
        ));
        assertEquals(RestBeginRequestTokenStore.STATUS_ACCEPTED, preferences.getString(
                RestBeginRequestTokenStore.PREF_STATUS, ""
        ));
        assertEquals("", preferences.getString(RestBeginRequestTokenStore.PREF_ERROR_CODE, ""));
    }

    @Test
    public void rejectionIsTokenBoundAndNewRequestClearsThePriorError() {
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(preferences, "begin-old"));
        assertTrue(RestBeginRequestTokenStore.recordRejectedIfCurrent(
                preferences, "begin-old", "PROTECTION_RUNTIME_UNHEALTHY"
        ));
        assertEquals("PROTECTION_RUNTIME_UNHEALTHY", preferences.getString(
                RestBeginRequestTokenStore.PREF_ERROR_CODE, ""
        ));

        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(preferences, "begin-new"));
        assertFalse(RestBeginRequestTokenStore.recordAcceptedIfCurrent(preferences, "begin-old"));
        assertFalse(RestBeginRequestTokenStore.recordRejectedIfCurrent(
                preferences, "begin-old", "STALE_REJECTION"
        ));
        assertEquals("begin-new", preferences.getString(
                FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, ""
        ));
        assertEquals(RestBeginRequestTokenStore.STATUS_PENDING, preferences.getString(
                RestBeginRequestTokenStore.PREF_STATUS, ""
        ));
        assertEquals("", preferences.getString(RestBeginRequestTokenStore.PREF_ERROR_CODE, ""));

        assertTrue(RestBeginRequestTokenStore.recordRejectedIfCurrent(
                preferences, "begin-new", "PROTECTION_RUNTIME_UNHEALTHY"
        ));
        assertEquals(RestBeginRequestTokenStore.STATUS_REJECTED, preferences.getString(
                RestBeginRequestTokenStore.PREF_STATUS, ""
        ));
    }

    @Test
    public void blockStateSerializesTheCurrentBeginReceipt() throws Exception {
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(preferences, "begin-visible"));
        assertTrue(RestBeginRequestTokenStore.recordRejectedIfCurrent(
                preferences, "begin-visible", "PROTECTION_RUNTIME_UNHEALTHY"
        ));

        JSObject state = new NativeFlowServiceController(
                ApplicationProvider.getApplicationContext()
        ).blockState(preferences);

        assertEquals("begin-visible", state.getString("restBeginRequestToken"));
        assertEquals("begin-visible", state.getString("restBeginRequestStatusToken"));
        assertEquals(RestBeginRequestTokenStore.STATUS_REJECTED,
                state.getString("restBeginRequestStatus"));
        assertEquals("PROTECTION_RUNTIME_UNHEALTHY",
                state.getString("restBeginRequestErrorCode"));
    }
}
