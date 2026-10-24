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
    public void freshOrOwnedPendingRequestIsNotRejectedDuringStartupRecovery() {
        long requestedAt = 10_000L;
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(
                preferences, "begin-live", requestedAt
        ));

        assertEquals(RestBeginRequestTokenStore.PendingRecoveryResult.DEFERRED,
                RestBeginRequestTokenStore.reconcilePendingRequest(
                        preferences,
                        "begin-live",
                        "",
                        requestedAt + RestBeginRequestTokenStore.PENDING_RECOVERY_TIMEOUT_MS - 1,
                        false,
                        "SERVICE_RESTARTED_BEFORE_REST_START"
                ));
        assertEquals(RestBeginRequestTokenStore.PendingRecoveryResult.DEFERRED,
                RestBeginRequestTokenStore.reconcilePendingRequest(
                        preferences,
                        "begin-live",
                        "begin-live",
                        requestedAt + RestBeginRequestTokenStore.PENDING_RECOVERY_TIMEOUT_MS * 2,
                        false,
                        "SERVICE_RESTARTED_BEFORE_REST_START"
                ));
        assertEquals(RestBeginRequestTokenStore.STATUS_PENDING, preferences.getString(
                RestBeginRequestTokenStore.PREF_STATUS, ""
        ));
    }

    @Test
    public void orphanedPendingRequestIsAcceptedOnlyWhenAValidRestSessionExists() {
        long requestedAt = 20_000L;
        long expiredAt = requestedAt + RestBeginRequestTokenStore.PENDING_RECOVERY_TIMEOUT_MS;

        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(
                preferences, "begin-restored", requestedAt
        ));
        assertEquals(RestBeginRequestTokenStore.PendingRecoveryResult.ACCEPTED,
                RestBeginRequestTokenStore.reconcilePendingRequest(
                        preferences, "begin-restored", "", expiredAt, true,
                        "SERVICE_RESTARTED_BEFORE_REST_START"
                ));
        assertEquals(RestBeginRequestTokenStore.STATUS_ACCEPTED, preferences.getString(
                RestBeginRequestTokenStore.PREF_STATUS, ""
        ));
        assertEquals("", preferences.getString(RestBeginRequestTokenStore.PREF_ERROR_CODE, ""));

        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(
                preferences, "begin-orphaned", requestedAt
        ));
        assertEquals(RestBeginRequestTokenStore.PendingRecoveryResult.REJECTED,
                RestBeginRequestTokenStore.reconcilePendingRequest(
                        preferences, "begin-orphaned", "", expiredAt, false,
                        "SERVICE_STOPPED_BEFORE_REST_START"
                ));
        assertEquals(RestBeginRequestTokenStore.STATUS_REJECTED, preferences.getString(
                RestBeginRequestTokenStore.PREF_STATUS, ""
        ));
        assertEquals("SERVICE_STOPPED_BEFORE_REST_START", preferences.getString(
                RestBeginRequestTokenStore.PREF_ERROR_CODE, ""
        ));
    }

    @Test
    public void recoveryCannotOverwriteANewerRequestReceipt() {
        long requestedAt = 30_000L;
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(
                preferences, "begin-old", requestedAt
        ));
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(
                preferences, "begin-new", requestedAt
        ));

        assertEquals(RestBeginRequestTokenStore.PendingRecoveryResult.NOT_PENDING,
                RestBeginRequestTokenStore.reconcilePendingRequest(
                        preferences, "begin-old", "", requestedAt + 60_000L, false,
                        "STALE_REJECTION"
                ));
        assertEquals("begin-new", preferences.getString(
                FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, ""
        ));
        assertEquals(RestBeginRequestTokenStore.STATUS_PENDING, preferences.getString(
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
