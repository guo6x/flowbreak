package com.flowbreak.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 强阻断 fallback 入口横幅（TYPE_ACCESSIBILITY_OVERLAY）。
 *
 * HyperOS 会拒绝应用在后台启动 BlockActivity（MIUILOG- Permission Denied
 * Activity / Abort background activity starts），因此无障碍服务把用户送回
 * HOME 后，用不依赖悬浮窗权限、不依赖后台 Activity 的常驻横幅提供
 * "目标应用已暂停 / 开始休息"入口。
 *
 * 保证：
 * - 单实例：重复 show 不叠加
 * - 不锁整机：非焦点、触摸可穿透（仅按钮区域消费触摸）
 * - BLOCKED 解除或 service 断开时由调用方 dismiss
 */
final class BlockedTargetBanner {
    interface Listener {
        void onStartRest();
    }

    private static final String TAG = "FlowAccessibility";

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler;
    private final Listener listener;

    private View bannerView;

    BlockedTargetBanner(Context context, WindowManager windowManager, Handler handler, Listener listener) {
        this.context = context.getApplicationContext();
        this.windowManager = windowManager;
        this.handler = handler;
        this.listener = listener;
    }

    boolean isShowing() {
        return bannerView != null;
    }

    /** 显示横幅。重复调用不叠加。 */
    void show(String packageName) {
        if (bannerView != null || windowManager == null) return;
        final View view = build(packageName);
        try {
            windowManager.addView(view, params());
            bannerView = view;
        } catch (Exception e) {
            Log.w(TAG, "accessibility block banner addView failed for " + packageName, e);
        }
    }

    /** 移除横幅。未显示时为空操作。 */
    void dismiss() {
        if (bannerView == null) return;
        final View view = bannerView;
        bannerView = null;
        try {
            windowManager.removeView(view);
        } catch (Exception e) {
            Log.w(TAG, "accessibility block banner removeView failed", e);
        }
    }

    private View build(String packageName) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.setBackgroundColor(Color.argb(235, 244, 67, 54));

        TextView message = new TextView(context);
        message.setText(appLabel(packageName) + " 已暂停 · 请先休息");
        message.setTextColor(Color.WHITE);
        message.setTextSize(14);
        message.setPadding(0, 0, dp(10), 0);
        root.addView(message, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        Button rest = new Button(context);
        rest.setText("开始休息");
        rest.setTextColor(Color.WHITE);
        rest.setTextSize(13);
        rest.setBackgroundColor(Color.rgb(50, 145, 87));
        rest.setOnClickListener(v -> {
            dismiss();
            listener.onStartRest();
        });
        root.addView(rest, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)
        ));
        return root;
    }

    private WindowManager.LayoutParams params() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP;
        params.y = dp(48);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private String appLabel(String packageName) {
        try {
            return context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(packageName, 0)
            ).toString();
        } catch (Exception e) {
            Log.w(TAG, "app label lookup failed for " + packageName, e);
            return packageName == null ? "目标应用" : packageName;
        }
    }
}
