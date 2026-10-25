package com.flowbreak.app;

import android.content.Intent;
import com.getcapacitor.PluginCall;
import java.util.List;

/**
 * Opens the first available settings page without treating navigation as a grant.
 * The launcher seam keeps OEM ActivityNotFound/Security failures testable on JVM.
 */
public final class NativeFlowSettingsNavigator {
    public interface Launcher {
        void open(Intent intent);
    }

    private final Launcher launcher;

    public NativeFlowSettingsNavigator(Launcher launcher) {
        this.launcher = launcher;
    }

    public void open(PluginCall call, List<Intent> candidates, String failureMessage) {
        RuntimeException lastError = null;
        for (Intent candidate : candidates) {
            try {
                candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                launcher.open(candidate);
            } catch (RuntimeException error) {
                // Missing/private OEM activities must not strand the JS promise.
                lastError = error;
                continue;
            }
            // Settle outside the launch try/catch so callback failures never
            // launch another page or produce a second settlement.
            call.resolve();
            return;
        }
        call.reject(failureMessage, lastError);
    }
}
