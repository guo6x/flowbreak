package com.flowbreak.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/**
 * 悬浮层控制器，封装 blocker 全屏层、warning 顶部浮条、grace 底部倒计时条。
 *
 * 从 FlowForegroundService 抽取，保持原有：
 * - 所有颜色、字号、padding、文案
 * - TYPE_APPLICATION_OVERLAY 和旧版本 TYPE_PHONE
 * - LayoutParams flags
 * - warning 顶部 y=40dp，grace 底部 y=40dp
 * - warning 相同 level 不重建，level 变化时先 dismiss
 * - grace 已存在时只更新文字
 * - 无 overlay 权限时打开 BlockActivity
 * - addView 失败时 fallback
 * - 紧急长按 10 秒 Runnable
 * - 紧急长按顺序：tryUnlock → log → machine.emergencyUnlock → persistState → dismiss
 *
 * 控制器不持有 Service 或 Activity，通过 Callbacks 回调业务。
 */
public final class FlowOverlayController {
    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler;
    private final Callbacks callbacks;

    private View blockerView;
    private View warningBar;
    private int warningBarLevel; // 0=无, 1=80%, 2=100%
    private View graceCountdownBar;
    private TextView graceCountdownText;
    private Runnable emergencyRunnable;

    public interface Callbacks {
        /** 用户点击"开始休息"。 */
        void onStartRest();

        /** 紧急长按 10 秒触发。由 Service 执行 tryUnlock、log、machine.emergencyUnlock、persistState，返回是否成功。 */
        boolean onEmergencyLongPress();

        /** 紧急长按失败时发送通知。 */
        void onEmergencyExhausted();

        /** 获取当前会话已用毫秒（用于 blocker 文案）。 */
        long currentSessionMs();

        /** 获取当前限额分钟（用于 warning 文案）。 */
        int currentLimitMinutes();

        /** 是否允许紧急解锁（prefs.allowEmergencyUnlock）。 */
        boolean allowEmergencyUnlock();
    }

    public FlowOverlayController(Context context, WindowManager windowManager, Handler handler, Callbacks callbacks) {
        this.context = context.getApplicationContext();
        this.windowManager = windowManager;
        this.handler = handler;
        this.callbacks = callbacks;
    }

    /** 显示阻断全屏层。 */
    public void showBlocker(String packageName, long sessionMs, boolean allowEmergency) {
        if (blockerView != null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(context)) {
            openBlockActivity(packageName);
            return;
        }
        handler.post(() -> {
            if (blockerView != null) return;
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setPadding(dp(28), dp(48), dp(28), dp(48));
            root.setBackgroundColor(Color.rgb(245, 248, 245));

            TextView title = text("先休息，再继续", 28, Color.rgb(24, 45, 33));
            title.setGravity(Gravity.CENTER);
            root.addView(title);

            TextView detail = text(
                    appLabel(packageName) + " 已暂停\n连续使用 "
                            + Math.max(1, sessionMs / 60_000L)
                            + " 分钟 · 完成休息可解锁 10 分钟",
                    16,
                    Color.rgb(83, 101, 91)
            );
            detail.setGravity(Gravity.CENTER);
            detail.setPadding(0, dp(18), 0, dp(28));
            root.addView(detail);

            Button rest = new Button(context);
            rest.setText("开始休息");
            rest.setTextSize(17);
            rest.setTextColor(Color.WHITE);
            rest.setBackgroundColor(Color.rgb(50, 145, 87));
            rest.setOnClickListener(v -> callbacks.onStartRest());
            root.addView(rest, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
            ));

            if (allowEmergency) {
                TextView emergency = text("紧急使用（长按 10 秒，每日一次）", 13, Color.rgb(150, 73, 62));
                emergency.setGravity(Gravity.CENTER);
                emergency.setPadding(0, dp(28), 0, dp(20));
                emergency.setOnTouchListener((v, event) -> handleEmergencyTouch(event));
                root.addView(emergency, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(72)
                ));
            }

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE
            );
            blockerView = root;
            try {
                windowManager.addView(root, params);
            } catch (Exception ignored) {
                blockerView = null;
                openBlockActivity(packageName);
            }
        });
    }

    /** 紧急长按触摸处理，保持原有 10 秒 Runnable 顺序。 */
    private boolean handleEmergencyTouch(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            emergencyRunnable = () -> {
                if (callbacks.onEmergencyLongPress()) {
                    dismissBlocker();
                } else {
                    callbacks.onEmergencyExhausted();
                }
            };
            handler.postDelayed(emergencyRunnable, 10_000L);
        } else if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (emergencyRunnable != null) handler.removeCallbacks(emergencyRunnable);
        }
        return true;
    }

    public void dismissBlocker() {
        if (emergencyRunnable != null) handler.removeCallbacks(emergencyRunnable);
        if (blockerView == null) return;
        View view = blockerView;
        blockerView = null;
        handler.post(() -> {
            try { windowManager.removeView(view); } catch (Exception ignored) { }
        });
    }

    /**
     * 渐进式提醒浮条。
     * level 1 (PERCEPTION 80%): 橙色
     * level 2 (COGNITION 100%): 红色
     */
    public void showWarningBar(int level, long sessionMs, int limitMinutes) {
        if (warningBar != null && warningBarLevel == level) return;
        dismissWarningBar();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(context)) {
            return;
        }
        warningBarLevel = level;
        handler.post(() -> {
            if (warningBar != null) return;
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(20), dp(12), dp(20), dp(12));

            GradientDrawable bg = new GradientDrawable();
            int color = level == 1 ? Color.rgb(255, 152, 0) : Color.rgb(244, 67, 54);
            bg.setColor(color);
            bg.setCornerRadius(dp(12));
            root.setBackground(bg);

            TextView msg = new TextView(context);
            msg.setTextColor(Color.WHITE);
            msg.setTextSize(14);
            int usedMin = (int) (sessionMs / 60_000L);
            int limitMin = limitMinutes;
            int pct = limitMin > 0 ? (int) (usedMin * 100 / limitMin) : 0;
            if (level == 1) {
                msg.setText("已用 " + pct + "% · 准备休息了");
            } else {
                msg.setText("已用满 " + limitMin + " 分钟 · 即将暂停，请尽快完成一次休息");
            }
            msg.setShadowLayer(2, 1, 1, Color.argb(80, 0, 0, 0));
            root.addView(msg, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ));

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP;
            params.y = dp(40);
            warningBar = root;
            try {
                windowManager.addView(root, params);
            } catch (Exception ignored) {
                warningBar = null;
            }
        });
    }

    public void dismissWarningBar() {
        if (warningBar == null) {
            warningBarLevel = 0;
            return;
        }
        View view = warningBar;
        warningBar = null;
        warningBarLevel = 0;
        handler.post(() -> {
            try { windowManager.removeView(view); } catch (Exception ignored) { }
        });
    }

    /** GRACE 窗口最后 2 分钟倒计时浮条。 */
    public void showGraceCountdown(long graceUntil, long now) {
        String countdown = formatGraceCountdown(graceUntil, now);
        if (countdown == null) {
            dismissGraceCountdown();
            return;
        }
        if (graceCountdownBar != null) {
            if (graceCountdownText != null) graceCountdownText.setText(countdown);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(context)) {
            return;
        }
        handler.post(() -> {
            if (graceCountdownBar != null) return;
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(20), dp(10), dp(20), dp(10));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.argb(220, 244, 67, 54));
            bg.setCornerRadius(dp(10));
            root.setBackground(bg);

            TextView msg = new TextView(context);
            msg.setTextColor(Color.WHITE);
            msg.setTextSize(13);
            msg.setText(countdown);
            root.addView(msg, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.BOTTOM;
            params.y = dp(40);
            graceCountdownBar = root;
            graceCountdownText = msg;
            try {
                windowManager.addView(root, params);
            } catch (Exception ignored) {
                graceCountdownBar = null;
                graceCountdownText = null;
            }
        });
    }

    public void dismissGraceCountdown() {
        if (graceCountdownBar == null) return;
        View view = graceCountdownBar;
        graceCountdownBar = null;
        graceCountdownText = null;
        handler.post(() -> {
            try { windowManager.removeView(view); } catch (Exception ignored) { }
        });
    }

    /**
     * 格式化 GRACE 倒计时文本。纯函数，便于 JVM 测试。
     * 返回 null 表示不应显示（剩余大于 2 分钟或已到期）。
     * 秒数使用 Math.ceil 语义：例如 65_001ms → 66 秒 → 1:06。
     */
    public static String formatGraceCountdown(long graceUntil, long now) {
        long remaining = graceUntil - now;
        if (remaining <= 0 || remaining > 2 * 60_000L) return null;
        int secs = (int) Math.ceil(remaining / 1000d);
        int mins = secs / 60;
        int remSecs = secs % 60;
        return "访问窗口还剩 " + mins + ":"
                + String.format(Locale.ROOT, "%02d", remSecs)
                + " · 准备保存进度";
    }

    public void dismissAll() {
        dismissBlocker();
        dismissWarningBar();
        dismissGraceCountdown();
    }

    /** 移除所有 Handler 回调（onDestroy 时调用）。 */
    public void clearCallbacks() {
        if (emergencyRunnable != null) handler.removeCallbacks(emergencyRunnable);
    }

    private void openBlockActivity(String packageName) {
        Intent intent = new Intent(context, BlockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("blockedPackage", packageName);
        try { context.startActivity(intent); } catch (Exception ignored) { }
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private String appLabel(String packageName) {
        try {
            return context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(packageName, 0)
            ).toString();
        } catch (Exception ignored) {
            return packageName == null ? "目标应用" : packageName;
        }
    }
}
