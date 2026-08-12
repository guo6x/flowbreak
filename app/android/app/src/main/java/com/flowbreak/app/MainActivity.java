package com.flowbreak.app;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // CrashLogger 已在 FlowBreakApplication.onCreate 中安装，覆盖所有进程（含服务进程）
        // 必须在 super.onCreate() 之前注册, BridgeActivity 在 onCreate 中通过 bridgeBuilder.create() 创建 Bridge,
        // super.onCreate() 之后再往 builder 加 plugin 不会被包含进去
        registerPlugin(NativeFlowPlugin.class);
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        // 系统返回键不经过 Web 端 history API（Capacitor 默认直接结束 Activity）。
        // 先询问 Web 层当前页面是否有未保存修改：返回 true 表示页面已消费
        // （例如弹出"有未保存的修改"确认框），否则保持默认退出行为。
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().evaluateJavascript(
                    "window.__flowbreakHandleBack && window.__flowbreakHandleBack();",
                    value -> {
                        String result = value == null ? "" : value.trim();
                        if (!"true".equals(result)) {
                            MainActivity.super.onBackPressed();
                        }
                    }
            );
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().postDelayed(() ->
                    getBridge().getWebView().evaluateJavascript(
                            "window.dispatchEvent(new Event('flow-permissions-changed'))",
                            null
                    ),
                    250
            );
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String navigateTo = intent.getStringExtra("navigateTo");
        if (navigateTo != null && !navigateTo.isEmpty()) {
            String path = navigateTo.startsWith("/") ? navigateTo : "/" + navigateTo;
            if (!"/rest".equals(path) && !"/dashboard".equals(path)) return;
            // Persist before touching the WebView. A fixed delay can lose the
            // event on a cold start while React is still loading.
            getSharedPreferences("FlowBreakPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("pendingNavigation", path)
                    .apply();
            dispatchPendingNavigation();
        }
    }

    private void dispatchPendingNavigation() {
        if (getBridge() == null || getBridge().getWebView() == null) return;
        getBridge().getWebView().postDelayed(() ->
                getBridge().getWebView().evaluateJavascript(
                        "window.dispatchEvent(new Event('flow-navigate'))", null
                ), 100
        );
    }
}
