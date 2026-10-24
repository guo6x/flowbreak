package com.flowbreak.app;

import android.content.SharedPreferences;

/** Serializes the latest BEGIN token with monitor-owned REST session writes. */
final class RestBeginRequestTokenStore {
    static final Object LOCK = new Object();

    private RestBeginRequestTokenStore() { }

    static boolean persistLatestRequest(SharedPreferences preferences, String token) {
        if (preferences == null || token == null || token.isEmpty()) return false;
        synchronized (LOCK) {
            return preferences.edit()
                    .putString(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, token)
                    .commit();
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
