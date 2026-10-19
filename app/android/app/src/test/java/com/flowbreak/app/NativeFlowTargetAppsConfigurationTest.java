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

/** Exercises the real plugin persistence and boot receiver, not a copied policy. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NativeFlowTargetAppsConfigurationTest {
    private static final String TARGET = "com.example.video";
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

    private RecordingCall saveTargets(String... packages) {
        JSObject options = new JSObject();
        options.put("packageNames", new JSArray(Arrays.asList(packages)));
        RecordingCall call = new RecordingCall("saveTargetApps", options);
        plugin.saveTargetApps(call);
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

    private AppOpsManager appOps() {
        return (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
    }

    private static final class TestPlugin extends NativeFlowPlugin {
        private final Context context;

        TestPlugin(Context context) {
            this.context = context;
        }

        @Override public Context getContext() {
            return context;
        }
    }

    private static final class RecordingCall extends PluginCall {
        boolean resolved;
        String rejection;

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
    }
}
