package com.flowbreak.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Records wall-clock changes even when the foreground service is not running. */
public final class RestClockChangeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !Intent.ACTION_TIME_CHANGED.equals(intent.getAction())) {
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences(
                FlowServiceStateStore.PREFS, Context.MODE_PRIVATE
        );
        RestSessionClock.invalidateActiveSession(preferences);
    }
}
