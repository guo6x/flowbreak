package com.flowbreak.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;

/**
 * 负责前台服务的启停动作、当前阻断状态读取和疲劳百分比兼容返回。
 * 不持有 Activity，不直接处理 PluginCall。
 *
 * 必须保持原有的 ContextCompat.startForegroundService 与 Context.startService 选择条件：
 * SDK >= O 且 action != ACTION_STOP 时使用 startForegroundService，否则使用 startService。
 */
public final class NativeFlowServiceController {
    private final Context context;

    public NativeFlowServiceController(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 发送一个 action 到 FlowForegroundService。 */
    public void sendAction(String action) {
        Intent intent = new Intent(context, FlowForegroundService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !FlowForegroundService.ACTION_STOP.equals(action)) {
            ContextCompat.startForegroundService(context, intent);
        } else {
            context.startService(intent);
        }
    }

    /** 停止前台服务。 */
    public void stopService() {
        context.stopService(new Intent(context, FlowForegroundService.class));
    }

    /**
     * 读取当前阻断状态。优先返回 SharedPreferences 中持久化的值，
     * 缺失时回退到 FlowForegroundService 的内存静态值。
     */
    public JSObject blockState(SharedPreferences prefs) {
        JSObject result = new JSObject();
        result.put("state", prefs.getString(
                "blockState", FlowForegroundService.getState().name()
        ));
        result.put("sessionSeconds", prefs.getLong(
                "sessionMs", FlowForegroundService.getSessionSeconds() * 1000L
        ) / 1000L);
        result.put("graceUntil", prefs.getLong(
                "graceUntil", FlowForegroundService.getGraceUntil()
        ));
        result.put("blockedPackage", prefs.getString(
                "blockedPackage", FlowForegroundService.getBlockedPackage()
        ));
        result.put("restStartedAt", prefs.getLong(
                FlowForegroundService.PREF_REST_STARTED_AT, 0L
        ));
        result.put("restRequiredSeconds", prefs.getLong(
                FlowForegroundService.PREF_REST_REQUIRED_MS, 0L
        ) / 1000L);
        return result;
    }

    /** 疲劳等级兼容返回。 */
    public JSObject fatigueLevel() {
        JSObject result = new JSObject();
        result.put("level", FlowForegroundService.getCurrentLevel());
        result.put("minutes", FlowForegroundService.getTotalMinutes());
        return result;
    }

    /** 当前前台包名。 */
    public JSObject currentApp() {
        JSObject result = new JSObject();
        result.put("packageName", FlowForegroundService.getForegroundPackage());
        return result;
    }
}
