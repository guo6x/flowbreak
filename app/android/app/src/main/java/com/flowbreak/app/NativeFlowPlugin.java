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
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.SharedPreferences;

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

    @PluginMethod
    public void getCurrentFatigueLevel(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("level", FlowForegroundService.getCurrentLevel());
        ret.put("minutes", FlowForegroundService.getTotalMinutes());
        call.resolve(ret);
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
        notifyListeners("permissionsChanged", getPermissionsState());
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
        notifyListeners("permissionsChanged", getPermissionsState());
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
        notifyListeners("permissionsChanged", getPermissionsState());
    }

    @PluginMethod
    public void startService(PluginCall call) {
        // Read params BEFORE any resolve
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
            // Default apps if none provided — must match DEFAULT_TARGET_APPS in appNames.ts
            appList.add("com.ss.android.ugc.aweme"); // 抖音
            appList.add("tv.danmaku.bili"); // B站
            appList.add("com.smile.gifmaker"); // 快手
            appList.add("com.tencent.mm"); // 微信
            appList.add("com.xingin.xhs"); // 小红书
        }

        // Persist to SharedPreferences for cold-start restore
        getContext().getSharedPreferences("FlowBreakPrefs", Context.MODE_PRIVATE)
                .edit()
                .putInt("limitMinutes", limitMinutes)
                .putStringSet("targetApps", new java.util.HashSet<>(appList))
                .apply();

        Intent intent = new Intent(getContext(), FlowForegroundService.class);
        intent.setAction(FlowForegroundService.ACTION_START);
        intent.putExtra("limitMinutes", limitMinutes);
        intent.putStringArrayListExtra("targetApps", appList);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                getContext().startForegroundService(intent);
            } catch (Exception e) {
                // Android 12+ (API 31) may throw ForegroundServiceStartNotAllowedException
                // if the app is not in the foreground.
                Log.e(TAG, "Failed to start foreground service", e);
            }
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

    @PluginMethod
    public void snoozeService(PluginCall call) {
        Intent intent = new Intent(getContext(), FlowForegroundService.class);
        intent.setAction(FlowForegroundService.ACTION_SNOOZE);
        getContext().startService(intent);
        call.resolve();
    }

    @PluginMethod
    public void getUsageStats(PluginCall call) {
        UsageStatsManager usm = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        // Get target apps from preferences to filter stats
        SharedPreferences prefs = getContext().getSharedPreferences("FlowBreakPrefs", Context.MODE_PRIVATE);
        Set<String> targetApps = prefs.getStringSet("targetApps", new HashSet<>());

        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
        long totalScreenTime = 0;
        if (stats != null) {
            for (UsageStats usageStats : stats) {
                if (targetApps.contains(usageStats.getPackageName())) {
                    totalScreenTime += usageStats.getTotalTimeInForeground();
                }
            }
        }

        JSObject ret = new JSObject();
        ret.put("screenTimeSeconds", totalScreenTime / 1000);
        call.resolve(ret);
    }

    @PluginMethod
    public void getCurrentApp(PluginCall call) {
        UsageStatsManager usm = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time);

        String currentApp = "";
        String currentAppName = "";
        if (stats != null) {
            SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
            for (UsageStats usageStats : stats) {
                sortedMap.put(usageStats.getLastTimeUsed(), usageStats);
            }
            if (!sortedMap.isEmpty()) {
                UsageStats lastUsed = sortedMap.get(sortedMap.lastKey());
                currentApp = lastUsed.getPackageName();
            }
        }

        JSObject ret = new JSObject();
        ret.put("packageName", currentApp);
        call.resolve(ret);
    }

    @PluginMethod
    public void saveSettings(PluginCall call) {
        Integer limitMinutes = call.getInt("limitMinutes", 30);
        JSArray apps = call.getArray("targetApps");
        if (apps == null) {
            apps = call.getArray("apps");
        }

        // Convert JSArray to Set<String> for consistent SharedPreferences format
        Set<String> appSet = new HashSet<>();
        try {
            if (apps != null) {
                for (int i = 0; i < apps.length(); i++) {
                    appSet.add(apps.getString(i));
                }
            }
        } catch (Exception e) {
            // Use default target apps
            appSet.add("com.ss.android.ugc.aweme");
            appSet.add("tv.danmaku.bili");
            appSet.add("com.smile.gifmaker");
            appSet.add("com.tencent.mm");
            appSet.add("com.xingin.xhs");
        }

        SharedPreferences prefs = getContext().getSharedPreferences("FlowBreakPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
            .putInt("limitMinutes", limitMinutes)
            .putStringSet("targetApps", appSet);

        // Persist reminder settings for native service
        try {
            editor.putBoolean("reminderEnabled", call.getBoolean("reminderEnabled", true));
            editor.putInt("reminderIntervalMinutes", call.getInt("reminderIntervalMinutes", 30));
            editor.putInt("reminderStartHour", call.getInt("reminderStartHour", 8));
            editor.putInt("reminderEndHour", call.getInt("reminderEndHour", 22));
        } catch (Exception e) {
            Log.e(TAG, "Error saving reminder settings", e);
        }

        editor.apply();
        call.resolve();
    }

    @PluginMethod
    public void loadSettings(PluginCall call) {
        SharedPreferences prefs = getContext().getSharedPreferences("FlowBreakPrefs", Context.MODE_PRIVATE);
        int limitMinutes = prefs.getInt("limitMinutes", 30);

        // Read as StringSet (consistent with saveSettings and startService)
        Set<String> appSet = prefs.getStringSet("targetApps", new HashSet<>());
        JSArray appsArr = new JSArray();
        for (String pkg : appSet) {
            appsArr.put(pkg);
        }

        JSObject ret = new JSObject();
        ret.put("limitMinutes", limitMinutes);
        ret.put("targetApps", appsArr);
        call.resolve(ret);
    }

    @PluginMethod
    public void getAppUsageList(PluginCall call) {
        UsageStatsManager usm = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
        JSArray result = new JSArray();

        if (stats != null) {
            Collections.sort(stats, (a, b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

            int count = 0;
            for (UsageStats usageStats : stats) {
                if (usageStats.getTotalTimeInForeground() > 0 && count < 10) {
                    JSObject item = new JSObject();
                    item.put("packageName", usageStats.getPackageName());
                    item.put("totalTimeSeconds", usageStats.getTotalTimeInForeground() / 1000);
                    result.put(item);
                    count++;
                }
            }
        }

        JSObject ret = new JSObject();
        ret.put("apps", result);
        call.resolve(ret);
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        notifyListeners("permissionsChanged", getPermissionsState());
    }
}
