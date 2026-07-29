package com.flowbreak.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            SharedPreferences prefs = context.getSharedPreferences(
                    "FlowBreakPrefs",
                    Context.MODE_PRIVATE
            );
            boolean configured = prefs.getBoolean("serviceConfigured", false);
            boolean monitoringEnabled = prefs.getBoolean("monitoringEnabled", true);
            if (!configured || !monitoringEnabled) {
                Log.d("BootReceiver", "Protection is not configured or disabled, restart skipped");
                return;
            }

            Log.d("BootReceiver", "Boot completed, starting FlowForegroundService");
            try {
                Intent serviceIntent = new Intent(context, FlowForegroundService.class);
                serviceIntent.setAction(FlowForegroundService.ACTION_START);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (RuntimeException error) {
                // Android can reject a background foreground-service start on
                // some OEM builds. Keep the persisted configuration and let
                // the next user launch resume protection instead of looping.
                Log.w("BootReceiver", "Unable to restart protection after boot", error);
            }
        }
    }
}
