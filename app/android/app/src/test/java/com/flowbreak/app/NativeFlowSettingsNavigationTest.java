package com.flowbreak.app;

import static org.junit.Assert.*;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;

/** Real plugin entry points, with settings launches isolated from the host/device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NativeFlowSettingsNavigationTest {
    private RecordingContext context;
    private TestPlugin plugin;

    @Before public void setUp() {
        context = new RecordingContext(RuntimeEnvironment.getApplication());
        plugin = new TestPlugin(context);
        plugin.load();
    }

    @After public void tearDown() { plugin.handleOnDestroy(); }

    @Test public void successfulUsageSettingsLaunchSettlesOnceWithoutFallback() {
        RecordingCall call = new RecordingCall();

        plugin.requestUsageStatsPermission(call);

        assertActions(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        assertResolved(call);
    }

    @Test public void missingUsageSettingsFallsBackToThisAppsDetails() {
        context.failure = intent -> Settings.ACTION_USAGE_ACCESS_SETTINGS.equals(intent.getAction())
                ? new ActivityNotFoundException("OEM has no Usage Access activity") : null;
        RecordingCall call = new RecordingCall();

        plugin.requestUsageStatsPermission(call);

        assertActions(Settings.ACTION_USAGE_ACCESS_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        assertEquals("package:" + context.getPackageName(), context.attempts.get(1).getDataString());
        assertResolved(call);
    }

    @Test public void overlaySettingsDeniedByOemFallsBackToAppDetails() {
        context.failure = intent -> Settings.ACTION_MANAGE_OVERLAY_PERMISSION.equals(intent.getAction())
                ? new SecurityException("OEM rejects overlay settings") : null;
        RecordingCall call = new RecordingCall();

        plugin.requestOverlayPermission(call);

        assertActions(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        assertResolved(call);
    }

    @Test public void batteryFailureDoesNotRetryTheSameIntentAndCanUseGeneralSettings() {
        context.failure = intent -> Settings.ACTION_SETTINGS.equals(intent.getAction()) ? null
                : new ActivityNotFoundException("settings activity absent");
        RecordingCall call = new RecordingCall();

        plugin.requestIgnoreBatteryOptimizations(call);

        assertActions(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Settings.ACTION_SETTINGS);
        assertResolved(call);
        for (Intent attempt : context.attempts) {
            assertNotEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, attempt.getAction());
        }
    }

    @Test public void accessibilityFallbackIsDomesticOnlyAndPlayRemainsANoOp() {
        context.failure = intent -> Settings.ACTION_ACCESSIBILITY_SETTINGS.equals(intent.getAction())
                ? new SecurityException("accessibility page disabled") : null;
        RecordingCall call = new RecordingCall();

        plugin.requestAccessibilityPermission(call);

        if ("domestic".equals(BuildConfig.CHANNEL)) {
            assertActions(Settings.ACTION_ACCESSIBILITY_SETTINGS,
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        } else {
            assertTrue(context.attempts.isEmpty());
        }
        assertResolved(call);
    }

    @Test public void everyApplicablePermissionRequestRejectsOnceWhenAllSettingsAreUnavailable() {
        List<Consumer<PluginCall>> requests = new ArrayList<>();
        requests.add(plugin::requestUsageStatsPermission);
        requests.add(plugin::requestOverlayPermission);
        requests.add(plugin::requestIgnoreBatteryOptimizations);
        if ("domestic".equals(BuildConfig.CHANNEL)) requests.add(plugin::requestAccessibilityPermission);
        SecurityException finalError = new SecurityException("general settings forbidden");
        context.failure = intent -> Settings.ACTION_SETTINGS.equals(intent.getAction()) ? finalError
                : new ActivityNotFoundException("specific settings absent");

        for (Consumer<PluginCall> request : requests) {
            context.attempts.clear();
            RecordingCall call = new RecordingCall();
            request.accept(call);

            assertEquals(3, context.attempts.size());
            assertEquals(Settings.ACTION_SETTINGS, context.attempts.get(2).getAction());
            assertEquals(0, call.resolutions);
            assertEquals(1, call.rejections);
            assertNotNull(call.message);
            assertTrue(call.message.contains("系统设置"));
            assertSame(finalError, call.error);
        }
    }

    @Test public void autoStartPreservesEveryExistingOemCandidateBeforeBothFallbacks() {
        context.failure = intent -> Settings.ACTION_SETTINGS.equals(intent.getAction()) ? null
                : new ActivityNotFoundException("OEM entry absent");
        for (String manufacturer : new String[]{"xiaomi", "huawei", "honor", "oppo", "vivo",
                "meizu", "samsung", "letv", "asus", "zte", "lenovo", "unknown"}) {
            ShadowBuild.setManufacturer(manufacturer);
            context.attempts.clear();
            RecordingCall call = new RecordingCall();

            plugin.openAutoStartSettings(call);

            AutoStartTarget[] expected = AutoStartTarget.forManufacturer(manufacturer);
            assertEquals(manufacturer, expected.length + 2, context.attempts.size());
            for (int i = 0; i < expected.length; i++) {
                assertTrue(manufacturer + " candidate " + i,
                        expected[i].buildIntent().filterEquals(context.attempts.get(i)));
            }
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    context.attempts.get(expected.length).getAction());
            assertEquals(Settings.ACTION_SETTINGS,
                    context.attempts.get(expected.length + 1).getAction());
            assertResolved(call);
        }
    }

    @Test public void autoStartAllFailuresRejectOnceWithTheLastCause() {
        ShadowBuild.setManufacturer("oppo");
        SecurityException finalError = new SecurityException("settings blocked");
        context.failure = intent -> Settings.ACTION_SETTINGS.equals(intent.getAction()) ? finalError
                : new ActivityNotFoundException("entry absent");
        RecordingCall call = new RecordingCall();

        plugin.openAutoStartSettings(call);

        assertEquals(6, context.attempts.size());
        assertEquals(0, call.resolutions);
        assertEquals(1, call.rejections);
        assertSame(finalError, call.error);
    }

    private void assertActions(String... expected) {
        assertEquals(expected.length, context.attempts.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], context.attempts.get(i).getAction());
            assertNotEquals(0, context.attempts.get(i).getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK);
        }
    }

    private static void assertResolved(RecordingCall call) {
        assertEquals(1, call.resolutions);
        assertEquals(0, call.rejections);
    }

    private static final class RecordingContext extends ContextWrapper {
        final List<Intent> attempts = new ArrayList<>();
        Function<Intent, RuntimeException> failure = intent -> null;

        RecordingContext(Context base) { super(base); }
        @Override public Context getApplicationContext() { return this; }
        @Override public void startActivity(Intent intent) {
            attempts.add(new Intent(intent));
            RuntimeException error = failure.apply(intent);
            if (error != null) throw error;
        }
    }

    private static final class TestPlugin extends NativeFlowPlugin {
        private final Context context;
        TestPlugin(Context context) { this.context = context; }
        @Override public Context getContext() { return context; }
    }

    private static final class RecordingCall extends PluginCall {
        int resolutions;
        int rejections;
        String message;
        Exception error;

        RecordingCall() { super(null, "NativeFlow", "settings-test", "settings", new JSObject()); }
        @Override public void resolve() { resolutions++; }
        @Override public void reject(String message, Exception error) {
            rejections++;
            this.message = message;
            this.error = error;
        }
    }
}
