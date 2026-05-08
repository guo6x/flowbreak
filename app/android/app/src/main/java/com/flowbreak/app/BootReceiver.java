package com.flowbreak.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("BootReceiver", "Boot completed, starting FlowForegroundService");
            Intent serviceIntent = new Intent(context, FlowForegroundService.class);
            serviceIntent.setAction(FlowForegroundService.ACTION_START);
            // Note: We might want to persist the user's settings (limitMinutes, targetApps) 
            // in SharedPreferences to restore them here.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
