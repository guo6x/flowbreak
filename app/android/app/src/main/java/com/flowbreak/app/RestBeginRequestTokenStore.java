package com.flowbreak.app;

import android.content.SharedPreferences;

/** Serializes the latest BEGIN token with monitor-owned REST session writes. */
final class RestBeginRequestTokenStore {
    static final Object LOCK = new Object();
    static final String PREF_REQUESTED_AT = "restBeginRequestRequestedAt";
    static final String PREF_STATUS = "restBeginRequestStatus";
    static final String PREF_STATUS_TOKEN = "restBeginRequestStatusToken";
    static final String PREF_ERROR_CODE = "restBeginRequestErrorCode";
    static final long PENDING_RECOVERY_TIMEOUT_MS = 30_000L;
    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_ACCEPTED = "ACCEPTED";
    static final String STATUS_REJECTED = "REJECTED";

    enum PendingRecoveryResult {
        NOT_PENDING,
        DEFERRED,
        ACCEPTED,
        REJECTED,
        WRITE_FAILED
    }

    private RestBeginRequestTokenStore() { }

    static boolean persistLatestRequest(SharedPreferences preferences, String token) {
        return persistLatestRequest(preferences, token, System.currentTimeMillis());
    }

    static boolean persistLatestRequest(
            SharedPreferences preferences,
            String token,
            long requestedAt
    ) {
        if (preferences == null || token == null || token.isEmpty()) return false;
        synchronized (LOCK) {
            return preferences.edit()
                    .putString(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, token)
                    .putString(PREF_STATUS_TOKEN, token)
                    .putString(PREF_STATUS, STATUS_PENDING)
                    .putLong(PREF_REQUESTED_AT, Math.max(0L, requestedAt))
                    .remove(PREF_ERROR_CODE)
                    .commit();
        }
    }

    static boolean isPendingCurrentRequest(SharedPreferences preferences, String token) {
        if (preferences == null || token == null || token.isEmpty()) return false;
        synchronized (LOCK) {
            return isCurrentRequestUnlocked(preferences, token)
                    && token.equals(preferences.getString(PREF_STATUS_TOKEN, ""))
                    && STATUS_PENDING.equals(preferences.getString(PREF_STATUS, ""));
        }
    }

    /**
     * Resolve a PENDING request only after its owning service command has had
     * time to run. A live in-memory owner is never failed by startup recovery;
     * after the recovery window, a valid persisted REST session is accepted
     * and an orphaned request is rejected with an explicit reason.
     */
    static PendingRecoveryResult reconcilePendingRequest(
            SharedPreferences preferences,
            String token,
            String inFlightToken,
            long now,
            boolean validPersistedRestSession,
            String rejectionCode
    ) {
        if (preferences == null || token == null || token.isEmpty()) {
            return PendingRecoveryResult.NOT_PENDING;
        }
        synchronized (LOCK) {
            if (!isCurrentRequestUnlocked(preferences, token)
                    || !token.equals(preferences.getString(PREF_STATUS_TOKEN, ""))
                    || !STATUS_PENDING.equals(preferences.getString(PREF_STATUS, ""))) {
                return PendingRecoveryResult.NOT_PENDING;
            }
            if (token.equals(inFlightToken)) return PendingRecoveryResult.DEFERRED;
            long requestedAt = preferences.getLong(PREF_REQUESTED_AT, 0L);
            if (requestedAt > 0L
                    && (now < requestedAt
                            || now - requestedAt < PENDING_RECOVERY_TIMEOUT_MS)) {
                return PendingRecoveryResult.DEFERRED;
            }
            String status = validPersistedRestSession ? STATUS_ACCEPTED : STATUS_REJECTED;
            String errorCode = validPersistedRestSession
                    ? ""
                    : (rejectionCode == null ? "SERVICE_RESTARTED_BEFORE_REST_START"
                            : rejectionCode);
            SharedPreferences.Editor editor = preferences.edit()
                    .putString(PREF_STATUS_TOKEN, token)
                    .putString(PREF_STATUS, status);
            if (errorCode.isEmpty()) editor.remove(PREF_ERROR_CODE);
            else editor.putString(PREF_ERROR_CODE, errorCode);
            if (!editor.commit()) return PendingRecoveryResult.WRITE_FAILED;
            return validPersistedRestSession
                    ? PendingRecoveryResult.ACCEPTED
                    : PendingRecoveryResult.REJECTED;
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
            return isCurrentRequestUnlocked(preferences, token);
        }
    }

    private static boolean isCurrentRequestUnlocked(SharedPreferences preferences, String token) {
        return RestCancellationGate.shouldAcceptBegin(
                token,
                preferences.getString(
                        FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN,
                        ""
                )
        );
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
