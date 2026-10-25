package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Process;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.shadows.ShadowBuild;

/** Exercises the real plugin persistence and boot receiver, not a copied policy. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NativeFlowTargetAppsConfigurationTest {
    private static final String TARGET = "com.example.video";
    private static final String UPDATED_TARGET = "com.example.updated-video";
    private SharedPreferences preferences;
    private TestPlugin plugin;
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("FlowBreakPrefs", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        ShadowSettings.setCanDrawOverlays(false);
        Shadows.shadowOf(appOps()).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName(), AppOpsManager.MODE_ERRORED);
        plugin = new TestPlugin(context);
        plugin.load();
    }

    @After public void tearDown() {
        plugin.handleOnDestroy();
        preferences.edit().clear().commit();
    }

    @Test public void freshTargetSelectionDoesNotGrantBootActivationEligibility() {
        RecordingCall call = saveTargets(TARGET);

        assertResolved(call);
        assertEquals(Collections.singleton(TARGET),
                PreferenceUtils.getMigratedTargetApps(preferences));
        assertFalse(preferences.contains("serviceConfigured"));
        assertFalse(preferences.contains("monitoringEnabled"));
        assertNoServiceStartAfterBoot();
    }

    @Test public void explicitlyUnconfiguredDeviceRemainsUnconfiguredAfterTargetSelection() {
        preferences.edit()
                .putBoolean("serviceConfigured", false)
                .putBoolean("monitoringEnabled", true)
                .commit();

        RecordingCall call = saveTargets(TARGET);

        assertResolved(call);
        assertFalse(preferences.getBoolean("serviceConfigured", true));
        assertTrue(preferences.getBoolean("monitoringEnabled", false));
        assertNoServiceStartAfterBoot();
    }

    @Test public void configuredActiveDeviceCanUpdateTargetsAndReloadExistingProtection() {
        preferences.edit()
                .putBoolean("serviceConfigured", true)
                .putBoolean("monitoringEnabled", true)
                .putInt("limitMinutes", 30)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS,
                        Collections.singleton("com.example.old"))
                .commit();

        RecordingCall call = saveTargets(TARGET);

        assertResolved(call);
        assertEquals(Collections.singleton(TARGET),
                PreferenceUtils.getMigratedTargetApps(preferences));
        assertTrue(preferences.getBoolean("serviceConfigured", false));
        assertTrue(preferences.getBoolean("monitoringEnabled", false));
        assertEquals(30, preferences.getInt("limitMinutes", 0));
        Intent reload = Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .getNextStartedService();
        assertNotNull(reload);
        assertEquals(FlowForegroundService.ACTION_RELOAD, reload.getAction());
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    @Test public void configuredPausedDeviceCanUpdateTargetsWithoutReenablingProtection() {
        preferences.edit()
                .putBoolean("serviceConfigured", true)
                .putBoolean("monitoringEnabled", false)
                .commit();

        RecordingCall call = saveTargets(TARGET);

        assertResolved(call);
        assertEquals(Collections.singleton(TARGET),
                PreferenceUtils.getMigratedTargetApps(preferences));
        assertTrue(preferences.getBoolean("serviceConfigured", false));
        assertFalse(preferences.getBoolean("monitoringEnabled", true));
        assertNoServiceStartAfterBoot();
    }

    @Test public void unsupportedConfiguredDeviceCanSaveTargetsWithoutReloadingProtection() {
        ShadowBuild.setManufacturer("vivo");
        preferences.edit()
                .putBoolean("serviceConfigured", true)
                .putBoolean("monitoringEnabled", true)
                .commit();

        RecordingCall call = saveTargets(UPDATED_TARGET);

        assertResolved(call);
        assertEquals(Collections.singleton(UPDATED_TARGET),
                PreferenceUtils.getMigratedTargetApps(preferences));
        assertTrue(preferences.getBoolean("monitoringEnabled", false));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    @Test public void unsupportedConfiguredDeviceCanSaveRulesWithoutReloadingProtection() {
        ShadowBuild.setManufacturer("iQOO");
        preferences.edit()
                .putBoolean("serviceConfigured", true)
                .putBoolean("monitoringEnabled", true)
                .commit();
        JSObject options = new JSObject();
        options.put("limitMinutes", 40);

        RecordingCall call = saveSettings(options);

        assertResolved(call);
        assertEquals(40, preferences.getInt("limitMinutes", 0));
        assertTrue(preferences.getBoolean("monitoringEnabled", false));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    @Test public void targetSaveReportsPersistedStateWhenReloadFails() {
        preferences.edit()
                .putBoolean("serviceConfigured", true)
                .putBoolean("monitoringEnabled", true)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS,
                        Collections.singleton(TARGET))
                .commit();
        plugin.failServiceAction = true;

        RecordingCall call = saveTargets(UPDATED_TARGET);

        assertPartialPersistenceFailure(call, FlowForegroundService.ACTION_RELOAD);
        assertEquals(Collections.singleton(UPDATED_TARGET),
                PreferenceUtils.getMigratedTargetApps(preferences));
        assertEquals("[\"com.example.updated-video\"]",
                call.rejectionData.getJSObject("settings")
                        .optJSONArray("targetApps")
                        .toString());
    }

    @Test public void invalidTargetSelectionDoesNotChangeConfigurationOrExistingTargets() {
        preferences.edit()
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, Collections.singleton(TARGET))
                .commit();

        RecordingCall call = saveTargets("com.android.settings");

        assertFalse(call.resolved);
        assertNotNull(call.rejection);
        assertEquals(Collections.singleton(TARGET),
                PreferenceUtils.getMigratedTargetApps(preferences));
        assertFalse(preferences.contains("serviceConfigured"));
        assertNoServiceStartAfterBoot();
    }

    @Test public void explicitStartAfterTargetSelectionStillRequiresActivationPrerequisites() {
        assertResolved(saveTargets(TARGET));
        RecordingCall start = new RecordingCall("startService", new JSObject());

        plugin.startService(start);

        assertFalse(start.resolved);
        assertNotNull(start.rejection);
        assertTrue(start.rejection.startsWith("PROTECTION_PREREQUISITE_"));
        assertFalse(preferences.getBoolean("serviceConfigured", false));
        assertNoServiceStartAfterBoot();
    }

    @Test public void explicitStartWithPrerequisitesStillConfiguresAndActivatesProtection() {
        assertResolved(saveTargets(TARGET));
        Shadows.shadowOf(appOps()).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName(), AppOpsManager.MODE_ALLOWED);
        ShadowSettings.setCanDrawOverlays(true);
        JSObject options = new JSObject();
        options.put("monitoringEnabled", true);
        RecordingCall start = new RecordingCall("startService", options);

        plugin.startService(start);

        assertResolved(start);
        assertTrue(preferences.getBoolean("serviceConfigured", false));
        assertTrue(preferences.getBoolean("monitoringEnabled", false));
        Intent started = Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .getNextStartedService();
        assertNotNull(started);
        assertEquals(FlowForegroundService.ACTION_START, started.getAction());
        assertTrue(BootReceiver.shouldStartMonitoring(
                preferences.getBoolean("serviceConfigured", false),
                preferences.getBoolean("monitoringEnabled", true), "xiaomi", true));
    }

    @Test public void ordinarySettingsSaveDoesNotGrantBootActivationEligibility() {
        JSObject options = new JSObject();
        options.put("limitMinutes", 30);

        RecordingCall call = saveSettings(options);

        assertResolved(call);
        assertEquals(30, preferences.getInt("limitMinutes", 0));
        assertFalse(preferences.contains("serviceConfigured"));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    @Test public void explicitEnableStillFailsClosedBeforeSavingOrStartingService() {
        preferences.edit()
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, Collections.singleton(TARGET))
                .putInt("limitMinutes", 25)
                .commit();
        JSObject options = new JSObject();
        options.put("monitoringEnabled", true);
        options.put("limitMinutes", 40);

        RecordingCall call = saveSettings(options);

        assertFalse(call.resolved);
        assertNotNull(call.rejection);
        assertEquals(25, preferences.getInt("limitMinutes", 0));
        assertFalse(preferences.getBoolean("serviceConfigured", false));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    @Test public void explicitDisablePersistsAndStopsExistingProtection() {
        preferences.edit()
                .putBoolean("serviceConfigured", true)
                .putBoolean("monitoringEnabled", true)
                .commit();
        JSObject options = new JSObject();
        options.put("monitoringEnabled", false);

        RecordingCall call = saveSettings(options);

        assertResolved(call);
        assertTrue(preferences.getBoolean("serviceConfigured", false));
        assertFalse(preferences.getBoolean("monitoringEnabled", true));
        Intent stop = Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .getNextStartedService();
        assertNotNull(stop);
        assertEquals(FlowForegroundService.ACTION_STOP, stop.getAction());
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    @Test public void configuredSupportedDeviceReloadsOnlyExistingProtection() {
        preferences.edit()
                .putBoolean("serviceConfigured", true)
                .putBoolean("monitoringEnabled", true)
                .commit();
        Shadows.shadowOf(appOps()).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName(), AppOpsManager.MODE_ALLOWED);
        ShadowSettings.setCanDrawOverlays(true);
        JSObject options = new JSObject();
        options.put("limitMinutes", 30);

        RecordingCall call = saveSettings(options);

        assertResolved(call);
        Intent reload = Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .getNextStartedService();
        assertNotNull(reload);
        assertEquals(FlowForegroundService.ACTION_RELOAD, reload.getAction());
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }

    @Test public void settingsSaveReportsPersistedStateWhenReloadFails() {
        preferences.edit()
                .putBoolean("serviceConfigured", true)
                .putBoolean("monitoringEnabled", true)
                .putInt("limitMinutes", 25)
                .commit();
        Shadows.shadowOf(appOps()).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName(), AppOpsManager.MODE_ALLOWED);
        ShadowSettings.setCanDrawOverlays(true);
        plugin.failServiceAction = true;
        JSObject options = new JSObject();
        options.put("limitMinutes", 40);

        RecordingCall call = saveSettings(options);

        assertPartialPersistenceFailure(call, FlowForegroundService.ACTION_RELOAD);
        assertEquals(40, preferences.getInt("limitMinutes", 0));
        assertEquals(40, call.rejectionData.getJSObject("settings").optInt("limitMinutes"));
    }

    private RecordingCall saveTargets(String... packages) {
        JSObject options = new JSObject();
        options.put("packageNames", new JSArray(Arrays.asList(packages)));
        RecordingCall call = new RecordingCall("saveTargetApps", options);
        plugin.saveTargetApps(call);
        return call;
    }

    private RecordingCall saveSettings(JSObject options) {
        RecordingCall call = new RecordingCall("saveSettings", options);
        plugin.saveSettings(call);
        return call;
    }

    private void assertNoServiceStartAfterBoot() {
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        new BootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        assertFalse(BootReceiver.shouldStartMonitoring(
                preferences.getBoolean("serviceConfigured", false),
                preferences.getBoolean("monitoringEnabled", true), "xiaomi", true));
    }

    private void assertResolved(RecordingCall call) {
        assertTrue(call.resolved);
        assertNull(call.rejection);
    }

    private void assertPartialPersistenceFailure(RecordingCall call, String action) {
        assertFalse(call.resolved);
        assertEquals("SERVICE_ACTION_FAILED_AFTER_PERSIST", call.rejectionCode);
        assertNotNull(call.rejection);
        assertTrue(call.rejection.contains("已保存"));
        assertNotNull(call.rejectionData);
        assertTrue(call.rejectionData.optBoolean("persisted"));
        assertFalse(call.rejectionData.optBoolean("serviceReloaded"));
        assertEquals(action, call.rejectionData.getString("serviceAction"));
    }

    private AppOpsManager appOps() {
        return (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
    }

    private static final class TestPlugin extends NativeFlowPlugin {
        private final Context context;
        boolean failServiceAction;

        TestPlugin(Context context) {
            this.context = context;
        }

        @Override public Context getContext() {
            return context;
        }

        @Override protected void dispatchServiceAction(String action) {
            if (failServiceAction) throw new IllegalStateException("injected service action failure");
            super.dispatchServiceAction(action);
        }
    }

    private static final class RecordingCall extends PluginCall {
        boolean resolved;
        String rejection;
        String rejectionCode;
        JSObject rejectionData;

        RecordingCall(String method, JSObject options) {
            super(null, "NativeFlow", "test-call", method, options);
        }

        @Override public void resolve() {
            resolved = true;
        }

        @Override public void reject(String message) {
            rejection = message;
        }

        @Override public void reject(String message, Exception cause) {
            rejection = message;
        }

        @Override public void reject(
                String message,
                String code,
                Exception cause,
                JSObject data
        ) {
            rejection = message;
            rejectionCode = code;
            rejectionData = data;
        }
    }
}
