package com.flowbreak.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import java.util.Set;
import java.util.UUID;
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

    /**
     * Keep service commands behind a small seam so persistence failures can be
     * exercised without starting a real Android service. Production behavior
     * remains the same; tests can inject a command failure after preferences
     * have been written.
     */
    protected void dispatchServiceAction(String action) {
        serviceController.sendAction(action);
    }

    /** Build the same persisted settings snapshot exposed by loadSettings. */
    private JSObject persistedSettingsSnapshot(SharedPreferences preferences) {
        com.getcapacitor.JSArray apps = new com.getcapacitor.JSArray();
        for (String value : PreferenceUtils.getMigratedTargetApps(preferences)) apps.put(value);
        JSObject result = new JSObject();
        result.put("limitMinutes", preferences.getInt("limitMinutes", 25));
        result.put("restDuration", preferences.getInt("restDuration", 180));
        result.put("allowEmergencyUnlock", preferences.getBoolean("allowEmergencyUnlock", true));
        result.put(
                "strongBlockingEnabled",
                PreferenceUtils.isStrongBlockingEnabled(
                        preferences, "domestic".equals(BuildConfig.CHANNEL)
                )
        );
        result.put("monitoringEnabled", preferences.getBoolean("monitoringEnabled", true));
        result.put("targetApps", apps);
        result.put("channel", BuildConfig.CHANNEL);
        return result;
    }

    /**
     * Preferences and service commands are not one atomic transaction. If the
     * command fails after apply(), report the persisted state instead of
     * promising that the old settings are still active.
     */
    private void rejectAfterPersist(
            PluginCall call,
            String message,
            String action,
            Exception error,
            SharedPreferences preferences
    ) {
        JSObject data = new JSObject();
        data.put("persisted", true);
        data.put("serviceReloaded", false);
        data.put("serviceAction", action);
        data.put("settings", persistedSettingsSnapshot(preferences));
        call.reject(message, "SERVICE_ACTION_FAILED_AFTER_PERSIST", error, data);
    }

    private void enqueueRestCancellationFallback(long expectedSessionId, String beginRequestToken) {
        try {
            serviceController.sendAction(
                    FlowForegroundService.ACTION_CANCEL_REST,
                    expectedSessionId,
                    beginRequestToken
            );
        } catch (RuntimeException error) {
            Log.w("NativeFlowPlugin", "Could not enqueue token-bound REST cancellation", error);
        }
    }

    private static void dispatchRestResult(Runnable completion) {
        // A service worker can finish after Activity detachment. The bridge
        // result still belongs on the main thread, but must not depend on an
        // Activity that may no longer exist.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            completion.run();
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(completion);
        }
    }

    FlowForegroundService.RestCompletionCallback restCompletionCallback(PluginCall call) {
        return new FlowForegroundService.RestCompletionCallback() {
            @Override public void onSuccess(NativeFlowRestCoordinator.Result result) {
                dispatchRestResult(() -> call.resolve(result.response));
            }

            @Override public void onDeferred() {
                dispatchRestResult(() -> call.reject(RestCompletionIntegrityGate.RETRYABLE_MESSAGE));
            }

            @Override public void onRejected(String message, Exception error) {
                dispatchRestResult(() -> {
                    if (error == null) call.reject(message);
                    else call.reject(message, error);
                });
            }
        };
    }

    /**
     * Reject any transition that would activate protection without the hard
     * core prerequisites. The persisted configuration remains untouched.
     */
    private boolean rejectMonitoringActivation(PluginCall call, boolean requested, int targetCount) {
        if (!requested) return false;
        ProtectionPrerequisiteGate.Result prerequisites = permissions.evaluateCorePrerequisites(
                true,
                targetCount
        );
        if (!prerequisites.isAllowed()) {
            call.reject(
                    "PROTECTION_PREREQUISITE_"
                            + prerequisites.code()
                            + ": "
                            + prerequisites.message()
            );
            return true;
        }
        return false;
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
        openSettings(call, permissions.usageStatsSettingsIntent(),
                "无法打开使用情况访问设置，请到系统设置中手动为 FlowBreak 开启使用情况访问权限");
    }

    @PluginMethod public void requestOverlayPermission(PluginCall call) {
        openSettings(call, permissions.overlaySettingsIntent(),
                "无法打开悬浮窗设置，请到系统设置中手动为 FlowBreak 开启悬浮窗权限");
    }

    @PluginMethod public void requestIgnoreBatteryOptimizations(PluginCall call) {
        openSettings(call, permissions.batteryOptimizationSettingsIntent(),
                "无法打开电池设置，请到系统设置中手动调整 FlowBreak 的后台电池限制");
    }

    @PluginMethod public void requestNotificationPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionForAlias("notifications", call, "checkPermissions");
        } else {
            call.resolve();
        }
    }

    @PluginMethod public void requestAccessibilityPermission(PluginCall call) {
        openSettings(call, permissions.accessibilitySettingsIntent(),
                "无法打开无障碍设置，请到系统设置中手动配置 FlowBreak 无障碍增强");
    }

    @PluginMethod public void openAutoStartSettings(PluginCall call) {
        openSettings(call, permissions.autoStartCandidateIntents(),
                "无法打开后台设置页，请到系统设置手动允许 FlowBreak 后台运行");
    }

    // ==================== Service Control ====================

    @PluginMethod public void startService(PluginCall call) {
        try {
            boolean monitoringEnabled = call.getBoolean("monitoringEnabled", true);
            Set<String> requestedTargets = null;
            com.getcapacitor.JSArray apps = call.getArray("apps");
            if (apps != null) {
                requestedTargets = appCatalog.filterTargetApps(
                        NativeFlowAppCatalog.toStringSet(apps)
                );
                if (requestedTargets.isEmpty()) {
                    call.reject("PROTECTION_PREREQUISITE_NO_TARGETS: 至少需要一个有效的受限应用");
                    return;
                }
            }
            int targetCount = requestedTargets == null
                    ? PreferenceUtils.getMigratedTargetApps(prefs()).size()
                    : requestedTargets.size();
            if (rejectMonitoringActivation(call, monitoringEnabled, targetCount)) return;
            SharedPreferences.Editor editor = prefs().edit()
                    .putBoolean("serviceConfigured", true)
                    .putBoolean("monitoringEnabled", monitoringEnabled);
            if (call.getData().has("limitMinutes")) {
                editor.putInt("limitMinutes", Math.max(1, call.getInt("limitMinutes", 25)));
            }
            if (requestedTargets != null) {
                editor.putStringSet(PreferenceUtils.PREF_TARGET_APPS, requestedTargets);
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
        String beginRequestToken = null;
        try {
            String persistedState = prefs().getString(
                    "blockState",
                    BlockStateMachine.State.IDLE.name()
            );
            if (!FlowForegroundService.isRestEntryAllowedForState(persistedState)) {
                call.reject("PROTECTION_RUNTIME_UNHEALTHY");
                return;
            }
            beginRequestToken = UUID.randomUUID().toString();
            boolean persisted = RestBeginRequestTokenStore.persistLatestRequest(
                    prefs(),
                    beginRequestToken
            );
            if (!persisted) {
                call.reject("休息模式请求未能安全保存，请重试。");
                return;
            }
            serviceController.sendAction(
                    FlowForegroundService.ACTION_BEGIN_REST,
                    null,
                    beginRequestToken
            );
            JSObject result = new JSObject();
            result.put("beginRequestToken", beginRequestToken);
            call.resolve(result);
        } catch (Exception error) {
            if (beginRequestToken != null) {
                try {
                    RestBeginRequestTokenStore.recordRejectedIfCurrent(
                            prefs(), beginRequestToken, "SERVICE_COMMAND_FAILED"
                    );
                } catch (Exception statusError) {
                    Log.w("NativeFlowPlugin", "Could not persist REST begin rejection", statusError);
                }
            }
            call.reject("休息模式启动失败", error);
        }
    }

    @PluginMethod public void cancelRest(PluginCall call) {
        try {
            String beginRequestToken = call.getString("beginRequestToken", "");
            if (beginRequestToken == null || beginRequestToken.isEmpty()) {
                call.reject("休息请求已失效，请返回后重试。");
                return;
            }
            long expectedRestSessionId = prefs().getLong(
                    FlowForegroundService.PREF_REST_SESSION_ID,
                    0L
            );
            boolean queued = FlowForegroundService.requestRestCancellation(
                    expectedRestSessionId,
                    beginRequestToken,
                    new FlowForegroundService.RestCancellationCallback() {
                        private void finishOnUiThread(Runnable completion) {
                            dispatchRestResult(completion);
                        }

                        @Override public void onCancelled() {
                            finishOnUiThread(call::resolve);
                        }

                        @Override public void onRejected(String message) {
                            finishOnUiThread(() -> call.reject(message));
                        }

                        @Override public void onDeferred() {
                            // The asynchronous API stays unconfirmed, but leave a token-bound
                            // service command queued so the session can still be reconciled.
                            enqueueRestCancellationFallback(
                                    expectedRestSessionId,
                                    beginRequestToken
                            );
                            finishOnUiThread(() -> call.reject(
                                    "尚未确认退出休息模式；后台可能仍在处理，请刷新状态后再操作。"
                            ));
                        }
                    }
            );
            if (!queued) {
                enqueueRestCancellationFallback(expectedRestSessionId, beginRequestToken);
                call.reject("尚未确认退出休息模式；请刷新状态后再操作。");
            }
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

    @PluginMethod public void getProtectionStatus(PluginCall call) {
        try {
            SharedPreferences preferences = prefs();
            Set<String> targets = PreferenceUtils.getMigratedTargetApps(preferences);
            boolean monitoringEnabled = preferences.getBoolean("monitoringEnabled", true);
            int limitMinutes = Math.max(1, preferences.getInt("limitMinutes", 25));
            FlowForegroundService.RuntimeConfigurationMatch runtimeConfigurationMatch =
                    FlowForegroundService.compareRuntimeConfiguration(
                            targets,
                            monitoringEnabled,
                            limitMinutes
                    );
            ProtectionRuntimeHealthEvaluator.Result runtimeHealth =
                    FlowForegroundService.getCurrentProtectionRuntimeHealth();
            long currentHeartbeatWall = FlowForegroundService.getLastCompletedMonitorTickWallMs();
            long currentHeartbeatElapsed = runtimeHealth.lastCompletedMonitorTickElapsedMs;
            long nowElapsed = runtimeHealth.nowElapsedMs;
            boolean supportedRuntime = permissions.isProtectionRuntimeAvailable();
            boolean hasUsageStats = permissions.hasUsageStats();
            boolean hasOverlay = permissions.hasOverlay();
            boolean serviceRuntimeActive = runtimeHealth.serviceRuntimeActive;
            boolean monitorThreadAlive = runtimeHealth.monitorThreadAlive;
            boolean strongRequested = PreferenceUtils.isStrongBlockingEnabled(
                    preferences, "domestic".equals(BuildConfig.CHANNEL)
            );
            boolean accessibilityEnabledInSettings = permissions.hasAccessibility();
            boolean accessibilityRuntimeConnected = AccessibilityRuntimeState.isConnected();
            ProtectionStatusEvaluator.Result status = ProtectionStatusEvaluator.evaluate(
                    new ProtectionStatusEvaluator.Input(
                            preferences.getBoolean("serviceConfigured", false),
                            monitoringEnabled,
                            supportedRuntime,
                            !targets.isEmpty(),
                            runtimeConfigurationMatch.targetSetMatchesPersisted,
                            runtimeConfigurationMatch.monitoringEnabledMatchesPersisted,
                            runtimeConfigurationMatch.runtimeLimitMinutesMatchesPersisted,
                            hasUsageStats,
                            hasOverlay,
                            serviceRuntimeActive,
                            monitorThreadAlive,
                            currentHeartbeatElapsed,
                            nowElapsed,
                            strongRequested,
                            accessibilityEnabledInSettings,
                            accessibilityRuntimeConnected,
                            runtimeHealth.integrityFailureReason
                    ),
                    runtimeHealth
            );
            JSObject result = new JSObject();
            result.put("status", status.status.name());
            result.put("reason", status.reason);
            result.put("coreProtectionOperational", status.coreProtectionOperational);
            result.put("strongBlockingRequested", status.strongBlockingRequested);
            result.put("strongBlockingOperational", status.strongBlockingOperational);
            result.put("strongBlockingDegradedReason", status.strongBlockingDegradedReason);
            result.put("monitoringConfigured", preferences.getBoolean("serviceConfigured", false));
            result.put("monitoringEnabled", monitoringEnabled);
            result.put("targetCount", targets.size());
            result.put("runtimeTargetsMatchPersisted",
                    runtimeConfigurationMatch.targetSetMatchesPersisted);
            result.put("runtimeMonitoringEnabledMatchesPersisted",
                    runtimeConfigurationMatch.monitoringEnabledMatchesPersisted);
            result.put("runtimeLimitMinutesMatchesPersisted",
                    runtimeConfigurationMatch.runtimeLimitMinutesMatchesPersisted);
            result.put("serviceRuntimeActive", serviceRuntimeActive);
            result.put("monitorThreadAlive", monitorThreadAlive);
            result.put("heartbeatFresh", status.runtimeHealth.heartbeatFresh);
            result.put("currentServiceHeartbeatAt", currentHeartbeatWall);
            result.put("currentServiceHeartbeatElapsedMs", currentHeartbeatElapsed);
            result.put("coreRuntimeHealthReason", status.runtimeHealth.reason);
            result.put("coreRuntimeHealthCheckedAtElapsedMs", status.runtimeHealth.nowElapsedMs);
            result.put("accessibilityEnabledInSettings", accessibilityEnabledInSettings);
            result.put("accessibilityRuntimeConnected", accessibilityRuntimeConnected);
            result.put("protectionRuntimeAvailable", supportedRuntime);
            result.put("permissions", permissions.permissionState());
            call.resolve(result);
        } catch (Exception error) {
            call.reject("读取保护状态失败", error);
        }
    }

    // ==================== Rest Coordination ====================

    @PluginMethod public void completeRestAndUnlock(PluginCall call) {
        String requestedActivity = call.getString("activity", "breathe");
        long expectedSessionId = call.getData().optLong("restSessionId", 0L);
        if (expectedSessionId <= 0L) {
            call.reject(RestCompletionIntegrityGate.RETRYABLE_MESSAGE);
            return;
        }
        boolean queued = FlowForegroundService.requestRestCompletion(
                expectedSessionId,
                requestedActivity,
                restCoordinator,
                restCompletionCallback(call)
        );
        if (!queued) call.reject(RestCompletionIntegrityGate.RETRYABLE_MESSAGE);
    }

    @PluginMethod public void requestEmergencyUnlock(PluginCall call) {
        JSObject result = restCoordinator.requestEmergencyUnlock(prefs());

        if (result.optBoolean("allowed")) {
            serviceController.sendAction(FlowForegroundService.ACTION_EMERGENCY);

            FlowRepository.get(getContext()).log(
                    "emergency_unlock",
                    FlowForegroundService.getBlockedPackage(),
                    "",
                    300,
                    ""
            );
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
        SharedPreferences current = prefs();
        boolean shouldReload = ProtectionSettingsSaveGate.mayReload(
                current.getBoolean("serviceConfigured", false),
                current.getBoolean("monitoringEnabled", true),
                permissions.isProtectionRuntimeAvailable(),
                permissions.isBackgroundStabilitySatisfied()
        );
        prefs().edit()
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, filtered)
                .apply();
        try {
            if (shouldReload) dispatchServiceAction(FlowForegroundService.ACTION_RELOAD);
            call.resolve();
        } catch (Exception error) {
            rejectAfterPersist(
                    call,
                    "受限应用已保存，但保护服务重载失败，请稍后重试。",
                    FlowForegroundService.ACTION_RELOAD,
                    error,
                    current
            );
        }
    }

    // ==================== Settings ====================

    @PluginMethod public void saveSettings(PluginCall call) {
        JSObject data = call.getData();
        SharedPreferences current = prefs();
        boolean monitoringEnabled = data.has("monitoringEnabled")
                ? call.getBoolean("monitoringEnabled", true)
                : current.getBoolean("monitoringEnabled", true);
        boolean wasConfigured = current.getBoolean("serviceConfigured", false);
        com.getcapacitor.JSArray apps = call.getArray("targetApps");
        Set<String> filteredTargets = null;
        if (apps != null) {
            filteredTargets = appCatalog.filterTargetApps(
                    NativeFlowAppCatalog.toStringSet(apps)
            );
        }
        int targetCount = filteredTargets == null
                ? PreferenceUtils.getMigratedTargetApps(current).size()
                : filteredTargets.size();
        // Ordinary edits are not activation requests. Explicit activation must
        // pass the same fail-closed device, permission, and stability gate used
        // by the monitoring control.
        if (ProtectionSettingsSaveGate.requiresActivationCheck(
                data.has("monitoringEnabled"), monitoringEnabled)
                && rejectMonitoringActivation(call, monitoringEnabled, targetCount)) return;
        SharedPreferences.Editor editor = current.edit();
        if (ProtectionSettingsSaveGate.shouldMarkConfigured(
                data.has("monitoringEnabled"), monitoringEnabled)) {
            editor.putBoolean("serviceConfigured", true);
        }
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
                    PreferenceUtils.PREF_STRONG_BLOCKING_ENABLED,
                    call.getBoolean(PreferenceUtils.PREF_STRONG_BLOCKING_ENABLED, false)
            );
        }
        if (data.has("monitoringEnabled")) editor.putBoolean(
                "monitoringEnabled", call.getBoolean("monitoringEnabled", true)
        );
        if (filteredTargets != null && !filteredTargets.isEmpty()) {
            editor.putStringSet(PreferenceUtils.PREF_TARGET_APPS, filteredTargets);
        }
        editor.apply();
        try {
            // Do not send a reload/start command from a settings-only save
            // when this device cannot run a supported protection service.
            if (data.has("monitoringEnabled") && !monitoringEnabled) {
                dispatchServiceAction(FlowForegroundService.ACTION_STOP);
            } else if (ProtectionSettingsSaveGate.mayReload(
                    wasConfigured,
                    monitoringEnabled,
                    permissions.isProtectionRuntimeAvailable(),
                    permissions.isBackgroundStabilitySatisfied())) {
                dispatchServiceAction(FlowForegroundService.ACTION_RELOAD);
            }
            call.resolve();
        } catch (Exception error) {
            String action = data.has("monitoringEnabled") && !monitoringEnabled
                    ? FlowForegroundService.ACTION_STOP
                    : FlowForegroundService.ACTION_RELOAD;
            String message = FlowForegroundService.ACTION_STOP.equals(action)
                    ? "设置已保存，但保护服务停止失败，请稍后重试。"
                    : "设置已保存，但保护服务重载失败，请稍后重试。";
            rejectAfterPersist(call, message, action, error, current);
        }
    }

    @PluginMethod public void loadSettings(PluginCall call) {
        SharedPreferences preferences = prefs();
        call.resolve(persistedSettingsSnapshot(preferences));
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
        if (NativeFlowStatisticsService.reflectionValue(value) == 0) {
            call.reject("无效的反馈值");
            return;
        }
        execute(
                call,
                "保存今日反馈失败",
                () -> statisticsService.saveDailyReflection(value)
        );
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

    private void openSettings(PluginCall call, Intent preferred, String failureMessage) {
        if (preferred == null) {
            // Preserve no-op semantics for capabilities unavailable on this API
            // or channel (especially Accessibility in the Play build).
            call.resolve();
            return;
        }
        openSettings(call, java.util.Collections.singletonList(preferred), failureMessage);
    }

    private void openSettings(PluginCall call, java.util.List<Intent> preferred,
            String failureMessage) {
        java.util.List<Intent> candidates = new java.util.ArrayList<>(preferred);
        candidates.add(permissions.appDetailFallbackIntent());
        candidates.add(permissions.generalSettingsFallbackIntent());
        new NativeFlowSettingsNavigator(getContext()::startActivity)
                .open(call, candidates, failureMessage);
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
