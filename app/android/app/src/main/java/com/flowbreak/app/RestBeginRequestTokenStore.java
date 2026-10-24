package com.flowbreak.app;

import android.content.SharedPreferences;

/** Serializes the latest BEGIN token with monitor-owned REST session writes. */
final class RestBeginRequestTokenStore {
    static final Object LOCK = new Object();
    static final String PREF_STATUS = "restBeginRequestStatus";
    static final String PREF_STATUS_TOKEN = "restBeginRequestStatusToken";
    static final String PREF_ERROR_CODE = "restBeginRequestErrorCode";
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_ACCEPTED = "ACCEPTED";
    static final String STATUS_REJECTED = "REJECTED";

    private RestBeginRequestTokenStore() { }

    static boolean persistLatestRequest(SharedPreferences preferences, String token) {
        if (preferences == null || token == null || token.isEmpty()) return false;
        synchronized (LOCK) {
            return preferences.edit()
                    .putString(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, token)
                    .putString(PREF_STATUS_TOKEN, token)
                    .putString(PREF_STATUS, STATUS_PENDING)
                    .remove(PREF_ERROR_CODE)
                    .commit();
        }
    }

    static boolean recordAcceptedIfCurrent(SharedPreferences preferences, String token) {
        return recordStatusIfCurrent(preferences, token, STATUS_ACCEPTED, "");
    }

    static boolean recordRejectedIfCurrent(
            SharedPreferences preferences,
            String token,
            String errorCode
    ) {
        String normalizedCode = errorCode == null ? "UNKNOWN" : errorCode;
        return recordStatusIfCurrent(preferences, token, STATUS_REJECTED, normalizedCode);
    }

    private static boolean recordStatusIfCurrent(
            SharedPreferences preferences,
            String token,
            String status,
            String errorCode
    ) {
        if (preferences == null || token == null || token.isEmpty()) return false;
        synchronized (LOCK) {
            if (!isCurrentRequest(preferences, token)) return false;
            SharedPreferences.Editor editor = preferences.edit()
                    .putString(PREF_STATUS_TOKEN, token)
                    .putString(PREF_STATUS, status);
            if (errorCode == null || errorCode.isEmpty()) editor.remove(PREF_ERROR_CODE);
            else editor.putString(PREF_ERROR_CODE, errorCode);
            return editor.commit();
        }
    }

    static boolean isCurrentRequest(SharedPreferences preferences, String token) {
        if (preferences == null) return false;
        synchronized (LOCK) {
            return RestCancellationGate.shouldAcceptBegin(
                    token,
                    preferences.getString(
                            FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN,
                            ""
                    )
            );
        }
    }

    static boolean bindExistingSession(SharedPreferences preferences, String token) {
        synchronized (LOCK) {
            if (!isCurrentRequest(preferences, token)) return false;
            return preferences.edit()
                    .putString(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, token)
                    .commit();
        }
    }

    static boolean applySessionIfCurrent(
            SharedPreferences preferences,
            String token,
            SharedPreferences.Editor sessionEditor
    ) {
        synchronized (LOCK) {
            if (!isCurrentRequest(preferences, token)) return false;
            sessionEditor
                    .putString(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, token)
                    .apply();
            return true;
        }
    }
}
