package com.flowbreak.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(
        name = "NativeFlow",
        permissions = @Permission(
                alias = "notifications",
                strings = Manifest.permission.POST_NOTIFICATIONS
        )
)
public class NativeFlowPlugin extends Plugin {
    private static final String PREFS = "FlowBreakPrefs";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private NativeFlowPermissionManager permissions;
    private NativeFlowServiceController serviceController;
    private NativeFlowAppCatalog appCatalog;
    private NativeFlowStatisticsService statisticsService;
    private NativeFlowDataManager dataManager;
    private NativeFlowRestCoordinator restCoordinator;

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public void load() {
        super.load();
        Context appContext = getContext().getApplicationContext();
        permissions = new NativeFlowPermissionManager(appContext);
        serviceController = new NativeFlowServiceController(appContext);
        appCatalog = new NativeFlowAppCatalog(appContext, permissions);
        statisticsService = new NativeFlowStatisticsService(appContext);
        dataManager = new NativeFlowDataManager(appContext);
        restCoordinator = new NativeFlowRestCoordinator(appContext);
    }

    @Override
    protected void handleOnResume() {
        notifyListeners("permissionsChanged", permissions.permissionState(), true);
    }

    @Override
    protected void handleOnDestroy() {
        executor.shutdownNow();
    }

    // ==================== Permissions ====================

    @PluginMethod public void checkPermissions(PluginCall call) {
        call.resolve(permissions.permissionState());
    }

    @PluginMethod public void requestUsageStatsPermission(PluginCall call) {
        openSettings(permissions.usageStatsSettingsIntent());
        call.resolve();
    }

    @PluginMethod public void requestOverlayPermission(PluginCall call) {
        Intent intent = permissions.overlaySettingsIntent();
        if (intent != null) openSettings(intent);
        call.resolve();
    }

    @PluginMethod public void requestIgnoreBatteryOptimizations(PluginCall call) {
        Intent intent = permissions.batteryOptimizationSettingsIntent();
        if (intent != null) {
            try {
                openSettings(intent);
            } catch (Exception ignored) {
                openSettings(permissions.batteryOptimizationSettingsIntent());
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
        Intent intent = permissions.accessibilitySettingsIntent();
        if (intent != null) openSettings(intent);
        call.resolve();
    }

    @PluginMethod public void openAutoStartSettings(PluginCall call) {
        for (Intent candidate : permissions.autoStartCandidateIntents()) {
            try {
                getContext().startActivity(candidate);
                call.resolve();
                return;
            } catch (Exception ignored) { }
        }
        try {
            getContext().startActivity(permissions.appDetailFallbackIntent());
            call.resolve();
        } catch (Exception error) {
            call.reject("无法打开自启动设置页，请到系统设置手动允许 FlowBreak 后台运行", error);
        }
    }

    // ==================== Service Control ====================

    @PluginMethod public void startService(PluginCall call) {
        try {
            SharedPreferences.Editor editor = prefs().edit()
                    .putBoolean("serviceConfigured", true)
                    .putBoolean("monitoringEnabled", call.getBoolean("monitoringEnabled", true));
            if (call.getData().has("limitMinutes")) {
                editor.putInt("limitMinutes", Math.max(1, call.getInt("limitMinutes", 25)));
            }
            com.getcapacitor.JSArray apps = call.getArray("apps");
            if (apps != null) {
                Set<String> filtered = appCatalog.filterTargetApps(
                        NativeFlowAppCatalog.toStringSet(apps)
                );
                if (filtered.isEmpty()) {
                    call.reject("至少需要一个有效的受限应用");
                    return;
                }
                editor.putStringSet(PreferenceUtils.PREF_TARGET_APPS, filtered);
            }
            editor.apply();
            serviceController.sendAction(FlowForegroundService.ACTION_START);
            call.resolve();
        } catch (Exception error) {
            call.reject("保护服务启动失败", error);
        }
    }

    @PluginMethod public void stopService(PluginCall call) {
        try {
            prefs().edit().putBoolean("monitoringEnabled", false).apply();
            serviceController.sendAction(FlowForegroundService.ACTION_STOP);
            call.resolve();
        } catch (Exception error) {
            call.reject("保护服务停止失败", error);
        }
    }

    @PluginMethod public void beginRest(PluginCall call) {
        try {
            serviceController.sendAction(FlowForegroundService.ACTION_BEGIN_REST);
            call.resolve();
        } catch (Exception error) {
            call.reject("休息模式启动失败", error);
        }
    }

    @PluginMethod public void cancelRest(PluginCall call) {
        try {
            serviceController.sendAction(FlowForegroundService.ACTION_CANCEL_REST);
            call.resolve();
        } catch (Exception error) {
            call.reject("休息模式退出失败", error);
        }
    }

    @PluginMethod public void getBlockState(PluginCall call) {
        call.resolve(serviceController.blockState(prefs()));
    }

    @PluginMethod public void getCurrentFatigueLevel(PluginCall call) {
        call.resolve(serviceController.fatigueLevel());
    }

    @PluginMethod public void getCurrentApp(PluginCall call) {
        call.resolve(serviceController.currentApp());
    }

    // ==================== Rest Coordination ====================

    @PluginMethod public void completeRestAndUnlock(PluginCall call) {
        String requestedActivity = call.getString("activity", "breathe");
        executor.execute(() -> {
            try {
                NativeFlowRestCoordinator.Result outcome =
                        restCoordinator.complete(prefs(), requestedActivity);
                if (outcome.needServiceRefresh) {
                    try {
                        serviceController.sendAction(FlowForegroundService.ACTION_COMPLETE_REST);
                    } catch (Exception serviceError) {
                        // commit 已成功，状态已持久化到 GRACE。服务下次启动时 load() 会读到正确状态。
                        // 不中断流程，前端仍收到成功响应，避免用户被卡在休息完成按钮上。
                        android.util.Log.w("NativeFlowPlugin",
                                "startService failed after rest commit", serviceError);
                    }
                }
                getActivity().runOnUiThread(() -> call.resolve(outcome.response));
            } catch (NativeFlowRestCoordinator.RestPendingException pending) {
                getActivity().runOnUiThread(() -> call.reject("休息尚未完成，请继续完成本次引导。"));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("保存休息记录失败", error));
            }
        });
    }

    @PluginMethod public void requestEmergencyUnlock(PluginCall call) {
        JSObject result = restCoordinator.requestEmergencyUnlock(
                prefs(), FlowRepository.get(getContext())
        );
        if (result.optBool("allowed")) {
            try {
                serviceController.sendAction(FlowForegroundService.ACTION_EMERGENCY);
            } catch (Exception ignored) { }
        }
        call.resolve(result);
    }

    // ==================== App Catalog ====================

    @PluginMethod public void getLaunchableApps(PluginCall call) {
        execute(call, "读取应用列表失败", () -> appCatalog.launchableApps());
    }

    @PluginMethod public void saveTargetApps(PluginCall call) {
        Set<String> filtered = appCatalog.filterTargetApps(
                NativeFlowAppCatalog.toStringSet(call.getArray("packageNames"))
        );
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
            serviceController.sendAction(FlowForegroundService.ACTION_RELOAD);
            call.resolve();
        } catch (Exception error) {
            call.reject("保存受限应用失败", error);
        }
    }

    // ==================== Settings ====================

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
        com.getcapacitor.JSArray apps = call.getArray("targetApps");
        if (apps != null) {
            Set<String> filtered = appCatalog.filterTargetApps(
                    NativeFlowAppCatalog.toStringSet(apps)
            );
            if (!filtered.isEmpty()) editor.putStringSet(PreferenceUtils.PREF_TARGET_APPS, filtered);
        }
        editor.apply();
        try {
            serviceController.sendAction(FlowForegroundService.ACTION_RELOAD);
            call.resolve();
        } catch (Exception error) {
            call.reject("保存设置失败", error);
        }
    }

    @PluginMethod public void loadSettings(PluginCall call) {
        SharedPreferences preferences = prefs();
        com.getcapacitor.JSArray apps = new com.getcapacitor.JSArray();
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

    // ==================== Statistics ====================

    @PluginMethod public void getUsageStats(PluginCall call) {
        execute(call, "读取使用时长失败", statisticsService::usageStats);
    }

    @PluginMethod public void getAppUsageList(PluginCall call) {
        execute(call, "读取应用使用时长失败", statisticsService::appUsageList);
    }

    @PluginMethod public void getDashboardSummary(PluginCall call) {
        execute(call, "读取仪表盘数据失败", statisticsService::dashboardSummary);
    }

    @PluginMethod public void getStatisticsHistory(PluginCall call) {
        int days = call.getInt("days", 7);
        execute(call, "读取历史统计失败", () -> statisticsService.statisticsHistory(days));
    }

    @PluginMethod public void getValidationSummary(PluginCall call) {
        int days = call.getInt("days", 7);
        execute(call, "读取效果验证数据失败", () -> statisticsService.validationSummary(days));
    }

    @PluginMethod public void saveDailyReflection(PluginCall call) {
        String value = call.getString("value", "");
        JSObject result = statisticsService.saveDailyReflection(value);
        if (result == null) {
            call.reject("无效的反馈值");
            return;
        }
        execute(call, "保存今日反馈失败", () -> result);
    }

    // ==================== Data / Diagnostics ====================

    @PluginMethod public void getDiagnostics(PluginCall call) {
        execute(call, "读取运行诊断失败", () -> {
            JSObject result = dataManager.diagnostics(prefs());
            result.put("permissions", permissions.permissionState());
            return result;
        });
    }

    @PluginMethod public void exportDiagnostics(PluginCall call) {
        execute(call, "导出诊断失败", () -> {
            JSObject result = new JSObject();
            result.put("json", dataManager.diagnosticsJson(permissions, prefs()));
            return result;
        });
    }

    @PluginMethod public void shareDiagnostics(PluginCall call) {
        executeShare(call, "导出诊断失败", "无法打开系统分享面板", "导出 FlowBreak 脱敏诊断",
                () -> {
                    String content = dataManager.diagnosticsJson(permissions, prefs());
                    String fileName = "flowbreak-diagnostics-"
                            + NativeFlowStatisticsService.day(System.currentTimeMillis()) + ".json";
                    Uri uri = dataManager.writeExportFile(fileName, content);
                    return dataManager.buildShareIntent(uri, "导出 FlowBreak 脱敏诊断");
                });
    }

    @PluginMethod public void migrateLegacyData(PluginCall call) {
        JSObject payload = call.getObject("payload", new JSObject());
        execute(call, "迁移失败，可稍后重试", () -> dataManager.migrateLegacyData(payload, prefs()));
    }

    @PluginMethod public void exportLocalData(PluginCall call) {
        JSObject uiData = call.getObject("uiData", new JSObject());
        execute(call, "导出数据失败", () -> {
            JSObject result = new JSObject();
            result.put("json", dataManager.exportJson(uiData, prefs()));
            return result;
        });
    }

    @PluginMethod public void shareLocalData(PluginCall call) {
        JSObject uiData = call.getObject("uiData", new JSObject());
        executeShare(call, "导出数据失败", "无法打开系统分享面板", "导出 FlowBreak 数据",
                () -> {
                    String content = dataManager.exportJson(uiData, prefs());
                    String fileName = "flowbreak-export-"
                            + NativeFlowStatisticsService.day(System.currentTimeMillis()) + ".json";
                    Uri uri = dataManager.writeExportFile(fileName, content);
                    return dataManager.buildShareIntent(uri, "导出 FlowBreak 数据");
                });
    }

    @PluginMethod public void clearLocalData(PluginCall call) {
        executor.execute(() -> {
            SharedPreferences preferences = prefs();
            try {
                dataManager.clearLocalData(preferences, serviceController);
                getActivity().runOnUiThread(call::resolve);
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject("清除本地数据失败", error));
            }
        });
    }

    @PluginMethod public void getBuildInfo(PluginCall call) {
        call.resolve(dataManager.buildInfo());
    }

    @PluginMethod public void consumePendingNavigation(PluginCall call) {
        call.resolve(dataManager.consumePendingNavigation(prefs()));
    }

    @PluginMethod public void getCrashLogs(PluginCall call) {
        execute(call, "读取崩溃日志失败", dataManager::crashLogs);
    }

    @PluginMethod public void clearCrashLogs(PluginCall call) {
        execute(call, "清除崩溃日志失败", () -> {
            dataManager.clearCrashLogs();
            return new JSObject();
        });
    }

    // ==================== Helpers ====================

    private void openSettings(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }

    private interface NativeTask {
        JSObject run() throws Exception;
    }

    private interface IntentTask {
        Intent run() throws Exception;
    }

    /** 在单线程 Executor 上执行任务，成功时在 UI 线程 resolve。 */
    private void execute(PluginCall call, String errorMessage, NativeTask task) {
        executor.execute(() -> {
            try {
                JSObject result = task.run();
                getActivity().runOnUiThread(() -> call.resolve(result));
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject(errorMessage, error));
            }
        });
    }

    /**
     * 在 Executor 上准备分享 Intent，然后在 UI 线程启动 chooser。
     * 准备失败用 prepareErrorMessage；chooser 启动失败用 launchErrorMessage。
     */
    private void executeShare(PluginCall call, String prepareErrorMessage,
                              String launchErrorMessage, String chooserTitle, IntentTask task) {
        executor.execute(() -> {
            try {
                Intent chooser = task.run();
                getActivity().runOnUiThread(() -> {
                    try {
                        getActivity().startActivity(chooser);
                        call.resolve();
                    } catch (Exception error) {
                        call.reject(launchErrorMessage, error);
                    }
                });
            } catch (Exception error) {
                getActivity().runOnUiThread(() -> call.reject(prepareErrorMessage, error));
            }
        });
    }
}
