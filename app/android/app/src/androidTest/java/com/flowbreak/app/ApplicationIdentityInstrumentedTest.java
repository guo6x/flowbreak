package com.flowbreak.app;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Installed-binary smoke only; this does not certify OEM enforcement behavior. */
@RunWith(AndroidJUnit4.class)
public class ApplicationIdentityInstrumentedTest {

    @Test
    public void installedIdentityMatchesTheSelectedChannel() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String expected = "domestic".equals(BuildConfig.CHANNEL)
                ? "com.flowbreak.app.cn" : "com.flowbreak.app";
        assertEquals(expected, appContext.getPackageName());
    }

    @Test
    public void accessibilityServiceIsPackagedOnlyForDomestic() throws Exception {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageInfo info = appContext.getPackageManager().getPackageInfo(
                appContext.getPackageName(), PackageManager.GET_SERVICES
        );
        boolean accessibilityPresent = false;
        boolean foregroundServicePresent = false;
        if (info.services != null) {
            for (ServiceInfo service : info.services) {
                if ("com.flowbreak.app.FlowAccessibilityService".equals(service.name)) {
                    accessibilityPresent = true;
                    assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", service.permission);
                }
                if ("com.flowbreak.app.FlowForegroundService".equals(service.name)) {
                    foregroundServicePresent = true;
                    assertFalse(service.exported);
                }
            }
        }
        assertTrue(foregroundServicePresent);
        assertEquals("domestic".equals(BuildConfig.CHANNEL), accessibilityPresent);
    }
}
