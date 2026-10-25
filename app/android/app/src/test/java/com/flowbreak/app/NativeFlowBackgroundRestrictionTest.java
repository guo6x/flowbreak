package com.flowbreak.app;

import static org.junit.Assert.*;

import android.app.ActivityManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/** Standard Android restriction evidence only, not an OEM keep-alive guarantee. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NativeFlowBackgroundRestrictionTest {
    private Context context;
    private ActivityManager activityManager;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }

    @Test public void restrictedStandardApiStateIsExposedAsAWarningField() {
        Shadows.shadowOf(activityManager).setBackgroundRestricted(true);

        assertEquals("restricted", new NativeFlowPermissionManager(context)
                .permissionState().optString("backgroundRestriction"));
    }

    @Test public void unrestrictedStandardApiStateDoesNotChangeCorePrerequisites() {
        Shadows.shadowOf(activityManager).setBackgroundRestricted(false);
        NativeFlowPermissionManager permissions = new NativeFlowPermissionManager(context);

        assertEquals("not_restricted", permissions.permissionState().optString("backgroundRestriction"));
        assertTrue(permissions.isProtectionRuntimeAvailable());
        assertTrue(permissions.isBackgroundStabilitySatisfied());
    }

    @Test public void missingActivityManagerReportsUnknownRatherThanUnrestricted() {
        assertEquals("unknown", new NativeFlowPermissionManager(withActivityManagerFailure(false))
                .permissionState().optString("backgroundRestriction"));
    }

    @Test public void deniedStandardApiReadReportsUnknownRatherThanThrowing() {
        assertEquals("unknown", new NativeFlowPermissionManager(withActivityManagerFailure(true))
                .permissionState().optString("backgroundRestriction"));
    }

    @Test public void preApi28ReportsUnknownBecauseTheStandardReadIsUnavailable() {
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 27);

        assertEquals("unknown", new NativeFlowPermissionManager(context)
                .permissionState().optString("backgroundRestriction"));
    }

    private Context withActivityManagerFailure(boolean denied) {
        return new ContextWrapper(context) {
            @Override public Context getApplicationContext() { return this; }
            @Override public Object getSystemService(String name) {
                if (Context.ACTIVITY_SERVICE.equals(name)) {
                    if (denied) throw new SecurityException("system policy denies status read");
                    return null;
                }
                return super.getSystemService(name);
            }
        };
    }
}
