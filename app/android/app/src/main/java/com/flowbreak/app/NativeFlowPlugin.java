package com.flowbreak.app;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(
    name = "NativeFlow",
    permissions = {
        @Permission(
            alias = "notifications",
            strings = { Manifest.permission.POST_NOTIFICATIONS }
        )
    }
)
public class NativeFlowPlugin extends Plugin {
    private static final String TAG = "NativeFlowPlugin";

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        call.resolve(getPermissionsState());
    }

    private JSObject getPermissionsState() {
        Context context = getContext();
        boolean hasUsageStats = false;
        boolean hasOverlay = false;
        boolean isIgnoringBattery = false;
        boolean hasNotification = true;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
                int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(), context.getPackageName());
                hasUsageStats = (mode == AppOpsManager.MODE_ALLOWED);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hasOverlay = Settings.canDrawOverlays(context);
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.getPackageName());
            } else {
                hasOverlay = true;
                isIgnoringBattery = true;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasNotification = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            }
        } catch (Exception e) {
            Log.e(TAG, "Permission check error", e);
        }

        JSObject ret = new JSObject();
        ret.put("hasUsageStats", hasUsageStats);
        ret.put("hasOverlay", hasOverlay);
        ret.put("isIgnoringBattery", isIgnoringBattery);
        ret.put("hasNotification", hasNotification);
        return ret;
    }

    @PluginMethod
    public void requestNotificationPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionForAlias("notifications", call, "checkPermissions");
        } else {
            call.resolve();
        }
    }

    @PluginMethod
    public void requestUsageStatsPermission(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        call.resolve();
    }

    @PluginMethod
    public void requestOverlayPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        }
        call.resolve();
    }

    @PluginMethod
    public void requestIgnoreBatteryOptimizations(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void startService(PluginCall call) {
        Integer limitMinutes = call.getInt("limitMinutes", 30);
        JSArray apps = call.getArray("apps");
        ArrayList<String> appList = new ArrayList<>();
        if (apps != null) {
            try {
                List<Object> list = apps.toList();
                for (Object o : list) {
                    appList.add(String.valueOf(o));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing apps list", e);
            }
        } else {
            // Default apps if none provided
            appList.add("com.ss.android.ugc.aweme"); // 抖音
            appList.add("tv.danmaku.bili"); // B站
            appList.add("com.smile.gifmaker"); // 快手
            appList.add("com.google.android.youtube"); // YouTube
        }

        Intent intent = new Intent(getContext(), FlowForegroundService.class);
        intent.setAction(FlowForegroundService.ACTION_START);
        intent.putExtra("limitMinutes", limitMinutes);
        intent.putStringArrayListExtra("targetApps", appList);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getContext().startForegroundService(intent);
        } else {
            getContext().startService(intent);
        }
        call.resolve();
    }

    @PluginMethod
    public void stopService(PluginCall call) {
        Intent intent = new Intent(getContext(), FlowForegroundService.class);
        intent.setAction(FlowForegroundService.ACTION_STOP);
        getContext().startService(intent);
        call.resolve();
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        notifyListeners("permissionsChanged", getPermissionsState());
    }
}
