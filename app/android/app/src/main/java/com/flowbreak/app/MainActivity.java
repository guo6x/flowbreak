package com.flowbreak.app;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
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

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String navigateTo = intent.getStringExtra("navigateTo");
        if ("rest".equals(navigateTo)) {
            if (getBridge() != null && getBridge().getWebView() != null) {
                // Wait for bridge to be ready and React router to be initialized
                getBridge().getWebView().postDelayed(() -> {
                    getBridge().getWebView().evaluateJavascript("window.dispatchEvent(new CustomEvent('flow-navigate',{detail:'/rest'}))", null);
                }, 300);
            }
        }
    }
}
