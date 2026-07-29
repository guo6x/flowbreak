package com.flowbreak.app;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.provider.Telephony;
import android.util.Base64;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(
        name = "NativeFlow",
        permissions = @Permission(
                alias = "notifications",
                strings = Manifest.permission.POST_NOTIFICATIONS
        )
)
public class NativeFlowPlugin extends Plugin {
    private static final String PREFS = "FlowBreakPrefs";
    private static final long UNLOCK_MS = 10 * 60_000L;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    protected void handleOnResume() {
        notifyListeners("permissionsChanged", permissionState(), true);
    }

    @Override
    protected void handleOnDestroy() {
        executor.shutdownNow();
    }

    @PluginMethod public void checkPermissions(PluginCall call) {
        call.resolve(permissionState());
    }

    private JSObject permissionState() {
        Context context = getContext();
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
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
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

    @PluginMethod public void requestUsageStatsPermission(PluginCall call) {
        openSettings(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        call.resolve();
    }

    @PluginMethod public void requestOverlayPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            openSettings(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getContext().getPackageName())
            ));
        }
        call.resolve();
    }

    @PluginMethod public void requestIgnoreBatteryOptimizations(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // Do not request a direct Doze exemption. Google Play limits
                // that flow; users can still explicitly choose FlowBreak from
                // the standard battery-optimization settings screen.
                openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }
        call.resolve();
    }

    @PluginMethod public void requestNotificationPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionForAlias("notifications", call, "checkPermissions");
        } else {
            call.resolve();
        }
    }

    @PluginMethod public void requestAccessibilityPermission(PluginCall call) {
        if ("domestic".equals(BuildConfig.CHANNEL)) {
            openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
        call.resolve();
    }

    /**
     * 跳转厂商自启动管理设置页。Android 原生没有标准 API，各厂商使用私有 Activity 或私有 Action，
     * 此处按 Build.MANUFACTURER 逐一尝试常见入口；全部失败时回退到应用详情页，
     * 让用户手动进入"电池"或"自启动"子项。
     *
     * 注意：小米/华为/荣耀 使用私有 Action；OPPO/vivo/魅族/三星/乐视/华硕/中兴/联想
     * 使用私有 Activity，必须用 setClassName(宿主包名, 类名) 启动，不能 new Intent(类名字符串)
     * 否则会找不到匹配 Activity 并静默落到兜底分支。
     */
    @PluginMethod public void openAutoStartSettings(PluginCall call) {
        String manufacturer = Build.MANUFACTURER == null
                ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        AutoStartTarget[] candidates = autoStartTargets(manufacturer);
        for (AutoStartTarget target : candidates) {
            try {
                getContext().startActivity(target.buildIntent());
                call.resolve();
                return;
            } catch (Exception ignored) { }
        }
        // 全部失败时回退到应用详情页
        try {
            Intent detail = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getContext().getPackageName())
            );
            detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(detail);
            call.resolve();
        } catch (Exception error) {
            call.reject("无法打开自启动设置页，请到系统设置手动允许 FlowBreak 后台运行", error);
        }
    }

    /**
     * 厂商自启动入口描述。action 与 (packageName + className) 二选一：
     * - action 不为空时用 new Intent(action) 启动
     * - packageName + className 不为空时用 setClassName 启动私有 Activity
     */
    private static final class AutoStartTarget {
        final String action;
        final String packageName;
        final String className;

        private AutoStartTarget(String action, String packageName, String className) {
            this.action = action;
            this.packageName = packageName;
            this.className = className;
        }

        static AutoStartTarget action(String action) {
            return new AutoStartTarget(action, null, null);
        }

        static AutoStartTarget component(String packageName, String className) {
            return new AutoStartTarget(null, packageName, className);
        }

        Intent buildIntent() {
            Intent intent;
            if (action != null) {
                intent = new Intent(action);
            } else {
                intent = new Intent();
                intent.setClassName(packageName, className);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }
    }

    private AutoStartTarget[] autoStartTargets(String manufacturer) {
        // 小米 / 红米 / 黑鲨（JoyUI 已并入 MIUI 体系，共享 Action 入口）
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
                || manufacturer.contains("blackshark")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.action("miui.intent.action.OP_AUTO_START"),
                    AutoStartTarget.action("miui.intent.action.POWER_HIDE_MODE_APP_LIST_MANAGER")
            };
        }
        // 华为 EMUI
        if (manufacturer.contains("huawei")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.action("huawei.intent.action.HSM_BOOTAPP_MANAGER"),
                    AutoStartTarget.action("huawei.intent.action.PROTECTED_APPS")
            };
        }
        // 荣耀 MagicOS（独立后入口与 EMUI 不同，先试 hihonor 私有 Action，回退到 EMUI 入口）
        if (manufacturer.contains("honor") || manufacturer.contains("hihonor")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.action("com.hihonor.manager.intent.action.APP_BOOTUP_MANAGER"),
                    AutoStartTarget.action("huawei.intent.action.HSM_BOOTAPP_MANAGER"),
                    AutoStartTarget.action("huawei.intent.action.PROTECTED_APPS")
            };
        }
        // OPPO / 一加 / realme（ColorOS 12+ 宿主包名改为 oplus，老版为 coloros / oppo）
        if (manufacturer.contains("oppo") || manufacturer.contains("realme")
                || manufacturer.contains("oneplus") || manufacturer.contains("oplus")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.oplus.safecenter",
                            "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                    AutoStartTarget.component("com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    AutoStartTarget.component("com.coloros.safecenter",
                            "com.coloros.safecenter.permission.PermissionTopActivity"),
                    AutoStartTarget.component("com.oppo.safe",
                            "com.oppo.safe.permission.permission.TopActivity")
            };
        }
        // vivo / iQOO（OriginOS / FuntouchOS）
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    AutoStartTarget.component("com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                    AutoStartTarget.component("com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManagerActivity")
            };
        }
        // 魅族 Flyme
        if (manufacturer.contains("meizu")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.meizu.safe",
                            "com.meizu.safe.permission.SmartBGActivity"),
                    AutoStartTarget.component("com.meizu.safe",
                            "com.meizu.safe.security.SHOW_APPSEC")
            };
        }
        // 三星 One UI（无"自启动"概念，引导到 Smart Manager / 设备维护，多版本兜底）
        if (manufacturer.contains("samsung")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.samsung.android.lool",
                            "com.samsung.android.lool.SmartManagerDetail"),
                    AutoStartTarget.component("com.samsung.android.sm.battery",
                            "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    AutoStartTarget.component("com.samsung.android.sm",
                            "com.samsung.android.sm.ui.battery.BatteryActivity")
            };
        }
        // 乐视 EUI（含 leeco 别名）
        if (manufacturer.contains("letv") || manufacturer.contains("leeco")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.letv.android.letvsafe",
                            "com.letv.android.letvsafe.permission.PermissionTopActivity")
            };
        }
        // 华硕 ZenUI
        if (manufacturer.contains("asus")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.asus.mobilemanager",
                            "com.asus.mobilemanager.entry.FunctionActivity"),
                    AutoStartTarget.component("com.asus.mobilemanager",
                            "com.asus.mobilemanager.autostart.AutoStartActivity")
            };
        }
        // 中兴 / 努比亚（MyOS / nubia UI）
        if (manufacturer.contains("zte") || manufacturer.contains("nubia")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.zte.heartyservices",
                            "com.zte.heartyservices.startupmanager.BootUpMgrActivity"),
                    AutoStartTarget.action("com.zte.heartyservices.intent.action.STARTUP_MANAGER")
            };
        }
        // 联想 / 摩托罗拉（ZUI；摩托罗拉接近原生，主要靠电池优化白名单）
        if (manufacturer.contains("lenovo") || manufacturer.contains("motorola")) {
            return new AutoStartTarget[]{
                    AutoStartTarget.component("com.lenovo.guardhouse",
                            "com.lenovo.guardhouse.autoboot.AutoBootActivity")
            };
        }
        return new AutoStartTarget[0];
    }

    private void openSettings(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }

    @PluginMethod public void startService(PluginCall call) {
        try {
            SharedPreferences.Editor editor = prefs().edit()
                    .putBoolean("serviceConfigured", true)
                    .putBoolean("monitoringEnabled", call.getBoolean("monitoringEnabled", true));
            if (call.getData().has("limitMinutes")) {
                editor.putInt("limitMinutes", Math.max(1, call.getInt("limitMinutes", 25)));
            }
            JSArray apps = call.getArray("apps");
            if (apps != null) {
                Set<String> filtered = filterTargetApps(toStringSet(apps));
                if (filtered.isEmpty()) {
                    call.reject("至少需要一个有效的受限应用");
                    return;
                }
                editor.putStringSet(PreferenceUtils.PREF_TARGET_APPS, filtered);
            }
            editor.apply();
            startServiceAction(FlowForegroundService.ACTION_START);
            call.resolve();
        } catch (Exception error) {
            call.reject("保护服务启动失败", error);
        }
    }

    @PluginMethod public void stopService(PluginCall call) {
        try {
            prefs().edit().putBoolean("monitoringEnabled", false).apply();
            startServiceAction(FlowForegroundService.ACTION_STOP);
            call.resolve();
        } catch (Exception error) {
            call.reject("保护服务停止失败", error);
        }
    }

    @PluginMethod public void beginRest(PluginCall call) {
        try {
            startServiceAction(FlowForegroundService.ACTION_BEGIN_REST);
            call.resolve();
        } catch (Exception error) {
            call.reject("休息模式启动失败", error);
        }
    }

    @PluginMethod public void cancelRest(PluginCall call) {
        try {
            startServiceAction(FlowForegroundService.ACTION_CANCEL_REST);
            call.resolve();
        } catch (Exception error) {
            call.reject("休息模式退出失败", error);
        }
    }

    @PluginMethod public void getBlockState(PluginCall call) {
        SharedPreferences preferences = prefs();
        JSObject result = new JSObject();
        result.put("state", preferences.getString(
                "blockState", FlowForegroundService.getState().name()
        ));
        result.put("sessionSeconds", preferences.getLong(
                "sessionMs", FlowForegroundService.getSessionSeconds() * 1000L
        ) / 1000L);
        result.put("graceUntil", preferences.getLong(
                "graceUntil", FlowForegroundService.getGraceUntil()
        ));
        result.put("blockedPackage", preferences.getString(
                "blockedPackage", FlowForegroundService.getBlockedPackage()
        ));
        result.put("restStartedAt", preferences.getLong(
                FlowForegroundService.PREF_REST_STARTED_AT, 0L
        ));
        result.put("restRequiredSeconds", preferences.getLong(
                FlowForegroundService.PREF_REST_REQUIRED_MS, 0L
        ) / 1000L);
        call.resolve(result);
    }

    @PluginMethod public void getCurrentFatigueLevel(PluginCall call) {
        JSObject result = new JSObject();
        result.put("level", FlowForegroundService.getCurrentLevel());
        result.put("minutes", FlowForegroundService.getTotalMinutes());
        call.resolve(result);
    }

    @PluginMethod public void completeRestAndUnlock(PluginCall call) {
        String requestedActivity = call.getString("activity", "breathe");
        String activity = "eye".equals(requestedActivity)
                || "stretch".equals(requestedActivity)
                || "breathe".equals(requestedActivity)
                ? requestedActivity : "breathe";
        executor.execute(() -> {
            try {
                SharedPreferences preferences = prefs();
                FlowDao dao = FlowDatabase.get(getContext()).flowDao();
                long now = System.currentTimeMillis();
                long sessionId = preferences.getLong(
                        FlowForegroundService.PREF_REST_SESSION_ID, 0L
                );
                String state = preferences.getString(
                        "blockState", BlockStateMachine.State.IDLE.name()
                );

                // A retry after a successful native commit must be idempotent:
                // return the original grace window instead of awarding points twice.
                if (BlockStateMachine.State.GRACE.name().equals(state)
                        && sessionId > 0L
                        && sessionId == preferences.getLong(
                                FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID, -1L
                        )) {
                    ProgressEntity existing = dao.getProgress();
                    JSObject result = new JSObject();
                    result.put("graceUntil", preferences.getLong(
                            FlowForegroundService.PREF_COMPLETED_REST_GRACE_UNTIL,
                            preferences.getLong("graceUntil", 0L)
                    ));
                    result.put("points", existing == null ? 0 : existing.points);
                    result.put("streak", existing == null ? 0 : existing.streak);
                    result.put("achievement", "");
                    getActivity().runOnUiThread(() -> call.resolve(result));
                    return;
                }

                long startedAt = preferences.getLong(
                        FlowForegroundService.PREF_REST_STARTED_AT, 0L
                );
                long requiredMs = preferences.getLong(
                        FlowForegroundService.PREF_REST_REQUIRED_MS, 0L
                );
                if (!BlockStateMachine.State.RESTING.name().equals(state)
                        || sessionId <= 0L
                        || !RestSessionValidator.isComplete(startedAt, requiredMs, now)) {
                    getActivity().runOnUiThread(() -> call.reject("休息尚未完成，请继续完成本次引导。"));
                    return;
                }

                long duration = requiredMs / 1000L;
                // 幂等保护：用 sessionId 标记 DB 写入是否已完成。
                // 如果 prefs commit 失败导致前端重试，此处可拦截重复的 DB 写入。
                long dbRecorded = preferences.getLong("dbRestRecordedSessionId", 0L);
                ProgressEntity progress;
                boolean firstRest;
                if (sessionId != dbRecorded) {
                    dao.insertEvent(new FlowEventEntity(
                            now, "rest_complete", "", activity, duration, ""
                    ));
                    FlowRepository.get(getContext()).recordRestWithIdempotency(sessionId, duration);
                    progress = dao.getProgress();
                    if (progress == null) progress = new ProgressEntity(0, 0, "", "[]");
                    String today = day(now);
                    String yesterday = day(now - 86_400_000L);
                    if (!today.equals(progress.lastRestDay)) {
                        progress.streak = yesterday.equals(progress.lastRestDay)
                                ? progress.streak + 1 : 1;
                        progress.lastRestDay = today;
                    }
                    progress.points += 10;
                    firstRest = dao.countEventsSince("rest_complete", 0) == 1;
                    if (firstRest) {
                        progress.points += 10;
                        progress.achievementsJson = "[\"health_guardian\"]";
                    }
                    dao.saveProgress(progress);
                    preferences.edit().putLong("dbRestRecordedSessionId", sessionId).commit();
                } else {
                    // 重试场景：DB 已写入过，从 DB 读取当前值用于响应
                    progress = dao.getProgress();
                    if (progress == null) progress = new ProgressEntity(0, 0, "", "[]");
                    firstRest = false;
                }
                long graceUntil = now + UNLOCK_MS;
                boolean committed = preferences.edit()
                        .putString("blockState", BlockStateMachine.State.GRACE.name())
                        .putLong("sessionMs", 0)
                        .putLong("graceUntil", graceUntil)
                        .putString("blockedPackage", "")
                        .remove(FlowForegroundService.PREF_REST_STARTED_AT)
                        .remove(FlowForegroundService.PREF_REST_REQUIRED_MS)
                        .putLong(FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID, sessionId)
                        .putLong(FlowForegroundService.PREF_COMPLETED_REST_GRACE_UNTIL, graceUntil)
                        .putLong(FlowForegroundService.PREF_PULLBACK_SESSION_ID, sessionId)
                        .putLong(FlowForegroundService.PREF_PULLBACK_STARTED_AT, now)
                        .putLong(FlowForegroundService.PREF_PULLBACK_TARGET_MS, 0L)
                        .putLong(FlowForegroundService.PREF_PULLBACK_LEFT_AT, 0L)
                        .putBoolean(FlowForegroundService.PREF_PULLBACK_SAW_TARGET, false)
                        .putBoolean(FlowForegroundService.PREF_PULLBACK_RETURN_REPORTED, false)
                        .putBoolean(FlowForegroundService.PREF_PULLBACK_RESOLVED, false)
                        .putBoolean(FlowForegroundService.PREF_PULLBACK_SUCCESS, false)
                        .commit();
                if (!committed) throw new IllegalStateException("无法保存解锁状态");
                try {
                    startServiceAction(FlowForegroundService.ACTION_COMPLETE_REST);
                } catch (Exception serviceError) {
                    // commit 已成功，状态已持久化到 GRACE。服务下次启动时 load() 会读到正确状态。
                    // 不中断流程，前端仍收到成功响应，避免用户被卡在休息完成按钮上。
                    android.util.Log.w("NativeFlowPlugin",
                            "startService failed after rest commit", serviceError);
                }
                JSObject result = new JSObject();
                result.put("graceUntil", graceUntil);
                result.put("points", progress.points);
                result.put("streak", progress.streak);
                result.put("achievement", firstRest ? "health_guardian" : "");
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("保存休息记录失败", error));
            }
        });
    }

    @PluginMethod public void requestEmergencyUnlock(PluginCall call) {
        boolean isBlocked = BlockStateMachine.State.BLOCKED.name().equals(
                prefs().getString("blockState", BlockStateMachine.State.IDLE.name())
        );
        boolean allowed = isBlocked && EmergencyUnlockManager.tryUnlock(getContext());
        long graceUntil = 0;
        if (allowed) {
            graceUntil = System.currentTimeMillis() + 5 * 60_000L;
            startServiceAction(FlowForegroundService.ACTION_EMERGENCY);
            FlowRepository.get(getContext()).log(
                    "emergency_unlock", FlowForegroundService.getBlockedPackage(), "", 300, ""
            );
        }
        JSObject result = new JSObject();
        result.put("allowed", allowed);
        result.put("graceUntil", graceUntil);
        result.put("remainingToday", EmergencyUnlockManager.remainingToday(getContext()));
        call.resolve(result);
    }

    @PluginMethod public void getLaunchableApps(PluginCall call) {
        executor.execute(() -> {
            try {
                PackageManager pm = getContext().getPackageManager();
                Intent launcher = new Intent(Intent.ACTION_MAIN);
                launcher.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> infos = pm.queryIntentActivities(launcher, 0);
                Set<String> protectedPackages = protectedPackages(pm);
                Map<String, JSObject> unique = new HashMap<>();
                for (ResolveInfo info : infos) {
                    String packageName = info.activityInfo.packageName;
                    if (protectedPackages.contains(packageName) || !isSupportedTargetPackage(packageName)) continue;
                    JSObject app = new JSObject();
                    app.put("packageName", packageName);
                    app.put("label", info.loadLabel(pm).toString());
                    app.put("iconDataUri", drawableDataUri(info.loadIcon(pm)));
                    unique.put(packageName, app);
                }
                List<JSObject> sorted = new ArrayList<>(unique.values());
                Collections.sort(sorted, Comparator.comparing(
                        value -> value.optString("label", ""), String.CASE_INSENSITIVE_ORDER
                ));
                JSArray result = new JSArray();
                for (JSObject app : sorted) result.put(app);
                JSObject response = new JSObject();
                response.put("apps", result);
                getActivity().runOnUiThread(() -> call.resolve(response));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("读取应用列表失败", error));
            }
        });
    }

    @PluginMethod public void saveTargetApps(PluginCall call) {
        JSArray values = call.getArray("packageNames");
        Set<String> filtered = filterTargetApps(toStringSet(values));
        if (filtered.isEmpty()) {
            call.reject("至少选择一个可阻断应用");
            return;
        }
        if (filtered.size() > 30) {
            call.reject("最多选择 30 个应用");
            return;
        }
        prefs().edit()
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, filtered)
                .putBoolean("serviceConfigured", true)
                .apply();
        try {
            startServiceAction(FlowForegroundService.ACTION_RELOAD);
            call.resolve();
        } catch (Exception error) {
            call.reject("保存受限应用失败", error);
        }
    }

    @PluginMethod public void saveSettings(PluginCall call) {
        JSObject data = call.getData();
        SharedPreferences.Editor editor = prefs().edit().putBoolean("serviceConfigured", true);
        if (data.has("limitMinutes")) editor.putInt(
                "limitMinutes", Math.max(1, call.getInt("limitMinutes", 25))
        );
        if (data.has("restDuration")) editor.putInt(
                "restDuration", Math.max(30, call.getInt("restDuration", 180))
        );
        if (data.has("allowEmergencyUnlock")) editor.putBoolean(
                "allowEmergencyUnlock", call.getBoolean("allowEmergencyUnlock", true)
        );
        if (data.has("strongBlockingEnabled") && "domestic".equals(BuildConfig.CHANNEL)) {
            editor.putBoolean(
                    "strongBlockingEnabled", call.getBoolean("strongBlockingEnabled", true)
            );
        }
        if (data.has("monitoringEnabled")) editor.putBoolean(
                "monitoringEnabled", call.getBoolean("monitoringEnabled", true)
        );
        JSArray apps = call.getArray("targetApps");
        if (apps != null) {
            Set<String> filtered = filterTargetApps(toStringSet(apps));
            if (!filtered.isEmpty()) editor.putStringSet(PreferenceUtils.PREF_TARGET_APPS, filtered);
        }
        editor.apply();
        try {
            startServiceAction(FlowForegroundService.ACTION_RELOAD);
            call.resolve();
        } catch (Exception error) {
            call.reject("保存设置失败", error);
        }
    }

    @PluginMethod public void loadSettings(PluginCall call) {
        SharedPreferences preferences = prefs();
        JSArray apps = new JSArray();
        for (String value : PreferenceUtils.getMigratedTargetApps(preferences)) apps.put(value);
        JSObject result = new JSObject();
        result.put("limitMinutes", preferences.getInt("limitMinutes", 25));
        result.put("restDuration", preferences.getInt("restDuration", 180));
        result.put("allowEmergencyUnlock", preferences.getBoolean("allowEmergencyUnlock", true));
        boolean strongDefault = "domestic".equals(BuildConfig.CHANNEL);
        result.put("strongBlockingEnabled", preferences.getBoolean("strongBlockingEnabled", strongDefault));
        result.put("monitoringEnabled", preferences.getBoolean("monitoringEnabled", true));
        result.put("targetApps", apps);
        result.put("channel", BuildConfig.CHANNEL);
        call.resolve(result);
    }

    @PluginMethod public void getUsageStats(PluginCall call) {
        executor.execute(() -> {
            try {
                FlowDao dao = FlowDatabase.get(getContext()).flowDao();
                String today = day(System.currentTimeMillis());
                DailySummaryEntity summary = dao.summaryForDay(today);
                long total = Math.max(
                        dao.totalUsageForDay(today),
                        summary == null ? 0L : summary.legacyScreenSeconds
                );
                JSObject result = new JSObject();
                result.put("screenTimeSeconds", total);
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("读取使用时长失败", error));
            }
        });
    }

    @PluginMethod public void getAppUsageList(PluginCall call) {
        executor.execute(() -> {
            try {
                JSArray apps = new JSArray();
                for (DailyUsageEntity entry : FlowDatabase.get(getContext()).flowDao()
                        .usageForDay(day(System.currentTimeMillis()))) {
                    JSObject item = new JSObject();
                    item.put("packageName", entry.packageName);
                    item.put("totalTimeSeconds", entry.seconds);
                    apps.put(item);
                }
                JSObject result = new JSObject();
                result.put("apps", apps);
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("读取应用使用时长失败", error));
            }
        });
    }

    @PluginMethod public void getCurrentApp(PluginCall call) {
        JSObject result = new JSObject();
        result.put("packageName", FlowForegroundService.getForegroundPackage());
        call.resolve(result);
    }

    @PluginMethod public void getDashboardSummary(PluginCall call) {
        executor.execute(() -> {
            try {
                FlowDao dao = FlowDatabase.get(getContext()).flowDao();
                DailySummaryEntity summary = dao.summaryForDay(day(System.currentTimeMillis()));
                JSObject result = new JSObject();
                result.put("blockCount", summary == null ? 0 : summary.blockCount);
                result.put("restCount", summary == null ? 0 : summary.restCount);
                result.put("pullbackOutcomeCount", summary == null ? 0 : summary.pullbackOutcomeCount);
                result.put("successfulPullbackCount", summary == null ? 0 : summary.successfulPullbackCount);
                result.put("postRestReturnCount", summary == null ? 0 : summary.postRestReturnCount);
                result.put("postRestTargetSeconds", summary == null ? 0L : summary.postRestTargetSeconds);
                result.put("reflectionValue", summary == null ? 0 : summary.reflectionValue);
                long unlockSeconds = summary == null ? 0L : summary.graceSeconds;
                // Kept for older WebView bundles while the UI switches to the
                // more accurate "rest-earned access window" wording.
                result.put("rescuedSeconds", unlockSeconds);
                result.put("unlockSeconds", unlockSeconds);
                ProgressEntity progress = dao.getProgress();
                result.put("points", progress == null ? 0 : progress.points);
                result.put("streak", progress == null ? 0 : progress.streak);
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("读取仪表盘数据失败", error));
            }
        });
    }

    @PluginMethod public void getStatisticsHistory(PluginCall call) {
        int days = Math.max(1, Math.min(90, call.getInt("days", 7)));
        executor.execute(() -> {
            try {
                FlowDao dao = FlowDatabase.get(getContext()).flowDao();
                String firstDay = dayDaysAgo(days - 1);
                Map<String, Long> usageByDay = new HashMap<>();
                for (DailyUsageEntity row : dao.usageSince(firstDay)) {
                    usageByDay.put(
                            row.date,
                            usageByDay.getOrDefault(row.date, 0L) + Math.max(0L, row.seconds)
                    );
                }
                Map<String, DailySummaryEntity> summariesByDay = new HashMap<>();
                for (DailySummaryEntity row : dao.summariesSince(firstDay)) {
                    summariesByDay.put(row.date, row);
                }
                JSArray history = new JSArray();
                for (int daysAgo = days - 1; daysAgo >= 0; daysAgo--) {
                    String date = dayDaysAgo(daysAgo);
                    DailySummaryEntity summary = summariesByDay.get(date);
                    JSObject item = new JSObject();
                    item.put("date", date);
                    item.put("screenTimeSeconds", Math.max(
                            usageByDay.getOrDefault(date, 0L),
                            summary == null ? 0L : summary.legacyScreenSeconds
                    ));
                    item.put("restCount", summary == null ? 0 : summary.restCount);
                    item.put("interventionCount", summary == null ? 0 : summary.interventionCount);
                    item.put("blockCount", summary == null ? 0 : summary.blockCount);
                    item.put("unlockSeconds", summary == null ? 0L : summary.graceSeconds);
                    item.put("pullbackOutcomeCount", summary == null ? 0 : summary.pullbackOutcomeCount);
                    item.put("successfulPullbackCount", summary == null ? 0 : summary.successfulPullbackCount);
                    item.put("postRestReturnCount", summary == null ? 0 : summary.postRestReturnCount);
                    item.put("postRestTargetSeconds", summary == null ? 0L : summary.postRestTargetSeconds);
                    item.put("reflectionValue", summary == null ? 0 : summary.reflectionValue);
                    history.put(item);
                }
                JSObject result = new JSObject();
                result.put("days", history);
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("读取历史统计失败", error));
            }
        });
    }

    @PluginMethod public void getValidationSummary(PluginCall call) {
        int days = Math.max(1, Math.min(90, call.getInt("days", 7)));
        executor.execute(() -> {
            try {
                int rests = 0;
                int outcomes = 0;
                int successes = 0;
                int returns = 0;
                long targetSeconds = 0L;
                int helped = 0;
                int neutral = 0;
                int notHelped = 0;
                for (DailySummaryEntity row : FlowDatabase.get(getContext()).flowDao()
                        .summariesSince(dayDaysAgo(days - 1))) {
                    rests += row.restCount;
                    outcomes += row.pullbackOutcomeCount;
                    successes += row.successfulPullbackCount;
                    returns += row.postRestReturnCount;
                    targetSeconds += row.postRestTargetSeconds;
                    if (row.reflectionValue == 3) helped++;
                    else if (row.reflectionValue == 2) neutral++;
                    else if (row.reflectionValue == 1) notHelped++;
                }
                JSObject result = new JSObject();
                result.put("days", days);
                result.put("restCount", rests);
                result.put("outcomeCount", outcomes);
                result.put("successfulPullbackCount", successes);
                result.put("postRestReturnCount", returns);
                result.put("postRestTargetSeconds", targetSeconds);
                result.put("helpedDays", helped);
                result.put("neutralDays", neutral);
                result.put("notHelpedDays", notHelped);
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("读取效果验证数据失败", error));
            }
        });
    }

    @PluginMethod public void saveDailyReflection(PluginCall call) {
        String value = call.getString("value", "");
        int numeric = "helped".equals(value) ? 3 : "neutral".equals(value) ? 2
                : "not_helped".equals(value) ? 1 : 0;
        if (numeric == 0) {
            call.reject("无效的反馈值");
            return;
        }
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                FlowDatabase.get(getContext()).flowDao().saveReflection(
                        day(now), numeric, now,
                        new FlowEventEntity(now, "daily_reflection", "", "", 0,
                                "{\"value\":" + numeric + "}")
                );
                getActivity().runOnUiThread(call::resolve);
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("保存今日反馈失败", error));
            }
        });
    }

    @PluginMethod public void getDiagnostics(PluginCall call) {
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                SharedPreferences preferences = prefs();
                FlowDao dao = FlowDatabase.get(getContext()).flowDao();
                long heartbeat = Math.max(
                        FlowForegroundService.getLastTickAt(),
                        preferences.getLong("serviceHeartbeatAt", 0L)
                );
                JSObject result = new JSObject();
                result.put("versionName", BuildConfig.VERSION_NAME);
                result.put("versionCode", BuildConfig.VERSION_CODE);
                result.put("channel", BuildConfig.CHANNEL);
                result.put("packageName", getContext().getPackageName());
                result.put("databaseVersion", 3);
                result.put("serviceAlive", heartbeat > 0L && now - heartbeat < 45_000L);
                result.put("serviceHeartbeatAt", heartbeat);
                result.put("lastUsageEventAt", FlowForegroundService.getLastUsageEventAt());
                result.put("state", FlowForegroundService.getState().name());
                result.put("sessionSeconds", FlowForegroundService.getSessionSeconds());
                result.put("graceUntil", FlowForegroundService.getGraceUntil());
                result.put("monitoringEnabled", preferences.getBoolean("monitoringEnabled", true));
                result.put("targetCount", PreferenceUtils.getMigratedTargetApps(preferences).size());
                result.put("eventCount", dao.eventCount());
                result.put("usageRowCount", dao.usageRowCount());
                result.put("latestEventAt", dao.latestEventAt());
                result.put("permissions", permissionState());
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("读取运行诊断失败", error));
            }
        });
    }

    @PluginMethod public void exportDiagnostics(PluginCall call) {
        executor.execute(() -> {
            try {
                JSObject result = new JSObject();
                result.put("json", buildDiagnosticsJson());
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("导出诊断失败", error));
            }
        });
    }

    @PluginMethod public void migrateLegacyData(PluginCall call) {
        if (prefs().getInt("migrationVersion", 0) >= 1) {
            JSObject result = new JSObject();
            result.put("migrated", false);
            result.put("version", 1);
            call.resolve(result);
            return;
        }
        JSObject payload = call.getObject("payload", new JSObject());
        executor.execute(() -> {
            try {
                FlowDatabase database = FlowDatabase.get(getContext());
                FlowDao dao = database.flowDao();
                database.runInTransaction(() -> {
                    ProgressEntity existing = dao.getProgress();
                    int legacyPoints = Math.max(0, payload.optInt("points", 0));
                    int legacyStreak = Math.max(0, payload.optInt("streak", 0));
                    JSONArray legacyAchievements = payload.optJSONArray("achievements");
                    String achievementsJson = legacyAchievements == null ? "[]" : legacyAchievements.toString();
                    if (existing != null) {
                        legacyPoints = Math.max(legacyPoints, existing.points);
                        legacyStreak = Math.max(legacyStreak, existing.streak);
                        if ((legacyAchievements == null || legacyAchievements.length() == 0)
                                && existing.achievementsJson != null
                                && !existing.achievementsJson.isEmpty()) {
                            achievementsJson = existing.achievementsJson;
                        }
                    }
                    dao.saveProgress(new ProgressEntity(legacyPoints, legacyStreak, "", achievementsJson));

                    JSONObject legacyStats = payload.optJSONObject("stats");
                    if (legacyStats != null) {
                        java.util.Iterator<String> dates = legacyStats.keys();
                        while (dates.hasNext()) {
                            String date = dates.next();
                            JSONObject stat = legacyStats.optJSONObject(date);
                            if (stat == null) continue;
                            dao.mergeLegacySummarySafely(
                                    date,
                                    Math.max(0L, stat.optLong("totalScreenTime", 0L)),
                                    Math.max(0, stat.optInt("restCount", 0)),
                                    Math.max(0, stat.optInt("interventionCount", 0))
                            );
                        }
                    }
                    // The source payload is retained as migration metadata so
                    // no legacy field is silently discarded, while current
                    // screens use the normalized Room records above.
                    dao.insertEvent(new FlowEventEntity(
                            System.currentTimeMillis(), "migration", "", "", 0, payload.toString()
                    ));
                });
                if (!prefs().edit().putInt("migrationVersion", 1).commit()) {
                    throw new IllegalStateException("无法保存迁移版本");
                }
                JSObject result = new JSObject();
                result.put("migrated", true);
                result.put("version", 1);
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("迁移失败，可稍后重试", error));
            }
        });
    }

    @PluginMethod public void exportLocalData(PluginCall call) {
        executor.execute(() -> {
            try {
                JSObject result = new JSObject();
                result.put("json", buildExportJson(call.getObject("uiData", new JSObject())));
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("导出数据失败", error));
            }
        });
    }

    @PluginMethod public void shareLocalData(PluginCall call) {
        executor.execute(() -> {
            try {
                File directory = new File(getContext().getCacheDir(), "exports");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Cannot create export directory");
                }
                File file = new File(directory, "flowbreak-export-" + day(System.currentTimeMillis()) + ".json");
                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(buildExportJson(call.getObject("uiData", new JSObject()))
                            .getBytes(StandardCharsets.UTF_8));
                }
                Uri uri = FileProvider.getUriForFile(
                        getContext(),
                        getContext().getPackageName() + ".fileprovider",
                        file
                );
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/json");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser = Intent.createChooser(share, "导出 FlowBreak 数据");
                getActivity().runOnUiThread(() -> {
                    try {
                        getActivity().startActivity(chooser);
                        call.resolve();
                    } catch (Exception error) {
                        call.reject("无法打开系统分享面板", error);
                    }
                });
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("导出数据失败", error));
            }
        });
    }

    @PluginMethod public void shareDiagnostics(PluginCall call) {
        executor.execute(() -> {
            try {
                File directory = new File(getContext().getCacheDir(), "exports");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Cannot create export directory");
                }
                File file = new File(
                        directory,
                        "flowbreak-diagnostics-" + day(System.currentTimeMillis()) + ".json"
                );
                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(buildDiagnosticsJson().getBytes(StandardCharsets.UTF_8));
                }
                Uri uri = FileProvider.getUriForFile(
                        getContext(), getContext().getPackageName() + ".fileprovider", file
                );
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/json");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser = Intent.createChooser(share, "导出 FlowBreak 脱敏诊断");
                getActivity().runOnUiThread(() -> {
                    try {
                        getActivity().startActivity(chooser);
                        call.resolve();
                    } catch (Exception error) {
                        call.reject("无法打开系统分享面板", error);
                    }
                });
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("导出诊断失败", error));
            }
        });
    }

    @PluginMethod public void clearLocalData(PluginCall call) {
        executor.execute(() -> {
            SharedPreferences preferences = prefs();
            try {
                // Tell the service not to flush its in-memory usage buffer
                // while the database clear is queued behind any earlier writes.
                if (!preferences.edit()
                        .putBoolean("dataErasing", true)
                        .putBoolean("monitoringEnabled", false)
                        .putBoolean("serviceConfigured", false)
                        .commit()) {
                    throw new IllegalStateException("无法准备本地数据清除");
                }
                getContext().stopService(new Intent(getContext(), FlowForegroundService.class));
                FlowRepository.get(getContext()).clearAllBlocking();
                deleteRecursively(new File(getContext().getCacheDir(), "exports"));
                deleteRecursively(new File(getContext().getCacheDir(), "crashes"));
                if (!preferences.edit().clear().commit()) {
                    throw new IllegalStateException("无法完成本地数据清除");
                }
                getActivity().runOnUiThread(call::resolve);
            } catch (Exception error) {
                preferences.edit().putBoolean("dataErasing", false).apply();
                getActivity().runOnUiThread(() -> call.reject("清除本地数据失败", error));
            }
        });
    }

    @PluginMethod public void getBuildInfo(PluginCall call) {
        JSObject result = new JSObject();
        result.put("versionName", BuildConfig.VERSION_NAME);
        result.put("versionCode", BuildConfig.VERSION_CODE);
        result.put("channel", BuildConfig.CHANNEL);
        result.put("packageName", getContext().getPackageName());
        call.resolve(result);
    }

    @PluginMethod public void consumePendingNavigation(PluginCall call) {
        SharedPreferences preferences = prefs();
        String path = preferences.getString("pendingNavigation", "");
        preferences.edit().remove("pendingNavigation").apply();
        JSObject result = new JSObject();
        result.put("path", path == null ? "" : path);
        call.resolve(result);
    }

    @PluginMethod public void getCrashLogs(PluginCall call) {
        executor.execute(() -> {
            try {
                File dir = new File(getContext().getCacheDir(), "crashes");
                JSArray logs = new JSArray();
                File[] files = dir.listFiles();
                if (files != null) {
                    java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (File file : files) {
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                            byte[] data = new byte[(int) Math.min(file.length(), 64 * 1024L)];
                            int read = fis.read(data);
                            JSObject entry = new JSObject();
                            entry.put("filename", file.getName());
                            entry.put("timestamp", file.lastModified());
                            entry.put("content", new String(data, 0, Math.max(0, read), StandardCharsets.UTF_8));
                            logs.put(entry);
                        } catch (Exception ignored) { }
                    }
                }
                JSObject result = new JSObject();
                result.put("logs", logs);
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("读取崩溃日志失败", error));
            }
        });
    }

    @PluginMethod public void clearCrashLogs(PluginCall call) {
        executor.execute(() -> {
            try {
                File dir = new File(getContext().getCacheDir(), "crashes");
                File[] files = dir.listFiles();
                if (files != null) for (File file : files) file.delete();
                getActivity().runOnUiThread(call::resolve);
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("清除崩溃日志失败", error));
            }
        });
    }

    private String buildDiagnosticsJson() {
        long now = System.currentTimeMillis();
        SharedPreferences preferences = prefs();
        FlowDao dao = FlowDatabase.get(getContext()).flowDao();
        long heartbeat = Math.max(
                FlowForegroundService.getLastTickAt(),
                preferences.getLong("serviceHeartbeatAt", 0L)
        );
        JSObject root = new JSObject();
        root.put("formatVersion", 1);
        root.put("exportedAt", new Date().toString());
        root.put("versionName", BuildConfig.VERSION_NAME);
        root.put("versionCode", BuildConfig.VERSION_CODE);
        root.put("channel", BuildConfig.CHANNEL);
        root.put("databaseVersion", 3);
        root.put("serviceAlive", heartbeat > 0L && now - heartbeat < 45_000L);
        root.put("serviceHeartbeatAt", heartbeat);
        root.put("lastUsageEventAt", FlowForegroundService.getLastUsageEventAt());
        root.put("state", FlowForegroundService.getState().name());
        root.put("sessionSeconds", FlowForegroundService.getSessionSeconds());
        root.put("graceUntil", FlowForegroundService.getGraceUntil());
        root.put("monitoringEnabled", preferences.getBoolean("monitoringEnabled", true));
        root.put("targetCount", PreferenceUtils.getMigratedTargetApps(preferences).size());
        root.put("eventCount", dao.eventCount());
        root.put("usageRowCount", dao.usageRowCount());
        root.put("latestEventAt", dao.latestEventAt());
        root.put("permissions", permissionState());
        return root.toString();
    }

    private String buildExportJson(JSObject uiData) {
        FlowDao dao = FlowDatabase.get(getContext()).flowDao();
        JSObject root = new JSObject();
        root.put("formatVersion", 2);
        root.put("exportedAt", new Date().toString());
        root.put("channel", BuildConfig.CHANNEL);
        JSObject settings = new JSObject();
        for (Map.Entry<String, ?> entry : prefs().getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Set) {
                JSArray values = new JSArray();
                for (Object item : (Set<?>) value) values.put(String.valueOf(item));
                settings.put(entry.getKey(), values);
            } else {
                settings.put(entry.getKey(), value);
            }
        }
        root.put("settings", settings);
        JSArray usage = new JSArray();
        for (DailyUsageEntity item : dao.allUsage()) {
            JSObject row = new JSObject();
            row.put("date", item.date);
            row.put("packageName", item.packageName);
            row.put("seconds", item.seconds);
            usage.put(row);
        }
        root.put("usage", usage);
        JSArray summaries = new JSArray();
        for (DailySummaryEntity item : dao.allSummaries()) {
            JSObject row = new JSObject();
            row.put("date", item.date);
            row.put("legacyScreenSeconds", item.legacyScreenSeconds);
            row.put("restCount", item.restCount);
            row.put("interventionCount", item.interventionCount);
            row.put("blockCount", item.blockCount);
            row.put("unlockSeconds", item.graceSeconds);
            row.put("pullbackOutcomeCount", item.pullbackOutcomeCount);
            row.put("successfulPullbackCount", item.successfulPullbackCount);
            row.put("postRestReturnCount", item.postRestReturnCount);
            row.put("postRestTargetSeconds", item.postRestTargetSeconds);
            row.put("reflectionValue", item.reflectionValue);
            row.put("reflectionUpdatedAt", item.reflectionUpdatedAt);
            summaries.put(row);
        }
        root.put("dailySummaries", summaries);
        JSArray events = new JSArray();
        for (FlowEventEntity item : dao.allEvents()) {
            JSObject row = new JSObject();
            row.put("id", item.id);
            row.put("timestamp", item.timestamp);
            row.put("type", item.type);
            row.put("packageName", item.packageName);
            row.put("activity", item.activity);
            row.put("durationSeconds", item.durationSeconds);
            row.put("metadata", item.metadata);
            events.put(row);
        }
        root.put("events", events);
        ProgressEntity progress = dao.getProgress();
        JSObject progressJson = new JSObject();
        progressJson.put("points", progress == null ? 0 : progress.points);
        progressJson.put("streak", progress == null ? 0 : progress.streak);
        progressJson.put("lastRestDay", progress == null ? "" : progress.lastRestDay);
        progressJson.put("achievements", progress == null ? "[]" : progress.achievementsJson);
        root.put("progress", progressJson);
        root.put("webCache", uiData == null ? new JSObject() : uiData);
        return root.toString();
    }

    private void startServiceAction(String action) {
        Intent intent = new Intent(getContext(), FlowForegroundService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !FlowForegroundService.ACTION_STOP.equals(action)) {
            ContextCompat.startForegroundService(getContext(), intent);
        } else {
            getContext().startService(intent);
        }
    }

    private Set<String> toStringSet(JSArray array) {
        Set<String> result = new HashSet<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private Set<String> filterTargetApps(Set<String> values) {
        Set<String> protectedValues = protectedPackages(getContext().getPackageManager());
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (!protectedValues.contains(value) && isSupportedTargetPackage(value)) result.add(value);
        }
        return result;
    }

    private boolean isSupportedTargetPackage(String packageName) {
        // UsageStats has no reliable, privacy-preserving signal for the
        // difference between WeChat chat and Video Channels. The Play flavor
        // must not present whole-WeChat blocking as Video Channels support.
        return !("play".equals(BuildConfig.CHANNEL) && "com.tencent.mm".equals(packageName));
    }

    private Set<String> protectedPackages(PackageManager pm) {
        Set<String> result = new HashSet<>();
        result.add(getContext().getPackageName());
        result.add("com.flowbreak.app");
        result.add("com.flowbreak.app.cn");
        result.add("com.android.settings");
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo info : pm.queryIntentActivities(home, 0)) {
            result.add(info.activityInfo.packageName);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String sms = Telephony.Sms.getDefaultSmsPackage(getContext());
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

    private String drawableDataUri(Drawable drawable) {
        int width = Math.max(1, Math.min(96, drawable.getIntrinsicWidth()));
        int height = Math.max(1, Math.min(96, drawable.getIntrinsicHeight()));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, output);
        bitmap.recycle();
        return "data:image/png;base64," + Base64.encodeToString(
                output.toByteArray(), Base64.NO_WRAP
        );
    }

    private boolean isAccessibilityEnabled() {
        if (!"domestic".equals(BuildConfig.CHANNEL)) return false;
        String enabled = Settings.Secure.getString(
                getContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabled == null) return false;
        String component = new ComponentName(
                getContext().getPackageName(),
                "com.flowbreak.app.FlowAccessibilityService"
        ).flattenToString();
        return enabled.toLowerCase(Locale.ROOT).contains(component.toLowerCase(Locale.ROOT));
    }

    private String dayDaysAgo(int daysAgo) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -Math.max(0, daysAgo));
        return day(calendar.getTimeInMillis());
    }

    private void deleteRecursively(File target) {
        if (target == null || !target.exists()) return;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        if (!target.delete() && target.exists()) {
            throw new IllegalStateException("无法删除本地缓存: " + target.getName());
        }
    }

    private String day(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(timestamp));
    }
}
