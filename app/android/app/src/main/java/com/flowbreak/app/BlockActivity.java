package com.flowbreak.app;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

public class BlockActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable emergencyRunnable;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(245, 248, 245));
        getWindow().setNavigationBarColor(Color.rgb(245, 248, 245));
        String packageName = getIntent().getStringExtra("blockedPackage");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(48), dp(28), dp(48));
        root.setBackgroundColor(Color.rgb(245, 248, 245));

        TextView title = label("先休息，再继续", 28, Color.rgb(24, 45, 33));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView detail = label(
                appLabel(packageName) + " 已暂停\n完成休息后开放 10 分钟访问窗口",
                16,
                Color.rgb(83, 101, 91)
        );
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(18), 0, dp(28));
        root.addView(detail);

        Button rest = new Button(this);
        rest.setText("开始休息");
        rest.setTextColor(Color.WHITE);
        rest.setTextSize(17);
        rest.setBackgroundColor(Color.rgb(50, 145, 87));
        rest.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("navigateTo", "rest");
            startActivity(intent);
            finish();
        });
        root.addView(rest, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ));

        SharedPreferences prefs = getSharedPreferences("FlowBreakPrefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("allowEmergencyUnlock", true)) {
            TextView emergency = label("紧急使用（长按 10 秒，每日一次）", 13, Color.rgb(150, 73, 62));
            emergency.setGravity(Gravity.CENTER);
            emergency.setPadding(0, dp(28), 0, dp(20));
            emergency.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    emergencyRunnable = () -> {
                        if (EmergencyUnlockManager.tryUnlock(this)) {
                            Intent service = new Intent(this, FlowForegroundService.class);
                            service.setAction(FlowForegroundService.ACTION_EMERGENCY);
                            ContextCompat.startForegroundService(this, service);
                            FlowRepository.get(this).log("emergency_unlock", packageName, "", 300, "");
                            finish();
                        } else {
                            emergency.setText("今日紧急使用已用完");
                        }
                    };
                    handler.postDelayed(emergencyRunnable, 10_000L);
                } else if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (emergencyRunnable != null) handler.removeCallbacks(emergencyRunnable);
                }
                return true;
            });
            root.addView(emergency, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(72)
            ));
        }
        setContentView(root);
    }

    @Override public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override protected void onDestroy() {
        if (emergencyRunnable != null) handler.removeCallbacks(emergencyRunnable);
        super.onDestroy();
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String appLabel(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)
            ).toString();
        } catch (Exception ignored) {
            return "目标应用";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
