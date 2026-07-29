package com.flowbreak.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.provider.Telephony;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 集中处理权限状态判断、系统设置页跳转和厂商自启动入口解析。
 *
 * 不持有 Activity，不调用 PluginCall.resolve/reject。
 * 需要 UI 跳转的 Intent 由调用方在 UI 线程启动。
 */
public final class NativeFlowPermissionManager {
    private final Context context;

    public NativeFlowPermissionManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 与原 NativeFlowPlugin.permissionState() 字段、顺序、语义完全一致。 */
    public JSObject permissionState() {
        JSObject result = new JSObject();
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        boolean usage = ops != null && ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.getPackageName()
        ) == AppOpsManager.MODE_ALLOWED;
        boolean overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(context);
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean battery = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || (power != null && power.isIgnoringBatteryOptimizations(context.getPackageName()));
        boolean notification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        result.put("hasUsageStats", usage);
        result.put("hasOverlay", overlay);
        result.put("isIgnoringBattery", battery);
        result.put("hasNotification", notification);
        result.put("hasAccessibility", isAccessibilityEnabled());
        result.put("isDomestic", "domestic".equals(BuildConfig.CHANNEL));
        result.put("channel", BuildConfig.CHANNEL);
        result.put("manufacturer", Build.MANUFACTURER == null
                ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT));
        return result;
    }

    public Intent usageStatsSettingsIntent() {
        return withNewTask(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    public Intent overlaySettingsIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        return withNewTask(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName())
        ));
    }

    public Intent batteryOptimizationSettingsIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        return withNewTask(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }

    /** 国内版无障碍设置 Intent；Play 渠道返回 null。 */
    public Intent accessibilitySettingsIntent() {
        if (!"domestic".equals(BuildConfig.CHANNEL)) return null;
        return withNewTask(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    /**
     * 返回厂商自启动候选 Intent 列表。未知厂商返回空列表。
     * 调用方需逐个 try startActivity，失败后回退到应用详情页。
     */
    public List<Intent> autoStartCandidateIntents() {
        String manufacturer = Build.MANUFACTURER == null
                ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        AutoStartTarget[] targets = AutoStartTarget.forManufacturer(manufacturer);
        List<Intent> intents = new java.util.ArrayList<>(targets.length);
        for (AutoStartTarget target : targets) intents.add(target.buildIntent());
        return intents;
    }

    /** 全部候选失败时的应用详情页兜底 Intent。 */
    public Intent appDetailFallbackIntent() {
        Intent detail = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.getPackageName())
        );
        detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return detail;
    }

    public boolean isAccessibilityEnabled() {
        if (!"domestic".equals(BuildConfig.CHANNEL)) return false;
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabled == null) return false;
        String component = new ComponentName(
                context.getPackageName(),
                "com.flowbreak.app.FlowAccessibilityService"
        ).flattenToString();
        return enabled.toLowerCase(Locale.ROOT).contains(component.toLowerCase(Locale.ROOT));
    }

    /**
     * 构建受保护包名集合：自身、自身渠道包、设置、桌面、短信、拨号、相机。
     * 与原 NativeFlowPlugin.protectedPackages() 行为一致。
     */
    public Set<String> protectedPackages(PackageManager pm) {
        Set<String> result = new HashSet<>();
        result.add(context.getPackageName());
        result.add("com.flowbreak.app");
        result.add("com.flowbreak.app.cn");
        result.add("com.android.settings");
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo info : pm.queryIntentActivities(home, 0)) {
            result.add(info.activityInfo.packageName);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String sms = Telephony.Sms.getDefaultSmsPackage(context);
            if (sms != null) result.add(sms);
        }
        Intent dial = new Intent(Intent.ACTION_DIAL);
        for (ResolveInfo info : pm.queryIntentActivities(dial, 0)) {
            result.add(info.activityInfo.packageName);
        }
        Intent camera = new Intent("android.media.action.IMAGE_CAPTURE");
        for (ResolveInfo info : pm.queryIntentActivities(camera, 0)) {
            result.add(info.activityInfo.packageName);
        }
        return result;
    }

    private static Intent withNewTask(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
