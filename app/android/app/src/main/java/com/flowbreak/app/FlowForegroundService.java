package com.flowbreak.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class FlowForegroundService extends Service {
    private static final String TAG = "FlowForegroundService";
    private static final String CHANNEL_ID = "FlowBreakServiceChannel";
    private static final String ALERT_CHANNEL_ID = "FlowBreakAlertChannel";
    private static final String PREFS_NAME = "FlowBreakPrefs";
    private static final String PREF_LIMIT_MINUTES = "limitMinutes";
    private static final String PREF_TARGET_APPS = "targetApps";
    public static final String ACTION_START = "START";
    public static final String ACTION_STOP = "STOP";
    public static final String ACTION_SNOOZE = "SNOOZE";

    // Percentage thresholds for intervention levels
    private static final int LEVEL1_MIN_PERCENT = 80;   // PERCEPTION
    private static final int LEVEL2_MIN_PERCENT = 100;  // COGNITION
    private static final int LEVEL3_MIN_PERCENT = 120;  // ACTION

    // Cooldown durations (ms)
    private static final long COOLDOWN_LEVEL1_MS = 5 * 60 * 1000;
    private static final long COOLDOWN_LEVEL2_MS = 5 * 60 * 1000;
    private static final long COOLDOWN_LEVEL3_MS = 10 * 60 * 1000;

    // Pulse periods (ms)
    private static final int PULSE_PERIOD_MS = 3000;

    // Monitoring
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable monitorRunnable;
    private int limitMinutes = 30;
    private ArrayList<String> targetApps = new ArrayList<>();

    // Continuous session tracking (Truth Source)
    private long continuousSessionMs = 0;
    private long lastLeaveTargetAppTime = 0;
    private long lastLockTime = 0;

    // Global state for polling from JS
    public static volatile int staticCurrentLevel = 0;
    public static volatile long staticTotalMinutes = 0;

    public static int getCurrentLevel() { return staticCurrentLevel; }
    public static long getTotalMinutes() { return staticTotalMinutes; }

    // Overlay
    private WindowManager windowManager;
    private View overlayView;
    private int currentLevel = 0; // 0=none, 1=perception, 2=cognition, 3=action
    private String currentTopApp = "";

    // Screen state
    private boolean isScreenOn = true;

    // Cooldowns
    private long level1CooldownUntil = 0;
    private long level2CooldownUntil = 0;
    private long level3CooldownUntil = 0;

    // Timed reminder
    private long lastTimedReminderMs = 0;
    private int reminderIntervalMinutes = 30;
    private boolean reminderEnabled = true;
    private int reminderStartHour = 8;
    private int reminderEndHour = 22;

    // Pulse animation
    private Runnable pulseRunnable;
    private long pulseStartTime;
    private int currentPulseColor;   // raw argb int
    private float currentPulseMinAlpha;
    private float currentPulseMaxAlpha;
    private View borderTop, borderBottom, borderLeft, borderRight;

    // ────────────────────────────────────────────
    // Screen on/off receiver
    // ────────────────────────────────────────────
    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                isScreenOn = false;
                lastLockTime = System.currentTimeMillis();
                stopMonitoring();
                Log.d(TAG, "Screen OFF, monitoring paused");
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                isScreenOn = true;
                if (lastLockTime > 0 && System.currentTimeMillis() - lastLockTime > 30000) {
                    continuousSessionMs = 0;
                    staticTotalMinutes = 0;
                    dismissOverlay();
                }
                lastLockTime = 0;
                startMonitoring();
                Log.d(TAG, "Screen ON, monitoring resumed");
            }
        }
    };

    // ────────────────────────────────────────────
    // Service lifecycle
    // ────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);

        loadFromPrefs();

        // Restart foreground notification and monitoring on cold start (START_STICKY recreation)
        startForegroundService();
        startMonitoring();
        Log.d(TAG, "Service created, monitoring started from cold start");
    }

    private void loadFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        limitMinutes = prefs.getInt(PREF_LIMIT_MINUTES, 30);
        
        // Try reading as StringSet (new format)
        Set<String> savedApps = null;
        try {
            savedApps = prefs.getStringSet(PREF_TARGET_APPS, null);
        } catch (ClassCastException e) {
            Log.d(TAG, "targetApps is not a StringSet, will try legacy String format");
        }

        if (savedApps != null && !savedApps.isEmpty()) {
            targetApps = new ArrayList<>(savedApps);
        } else {
            // Migration logic: check if it's stored as a JSON string (old format)
            String appsStr = "";
            try {
                appsStr = prefs.getString(PREF_TARGET_APPS, "");
            } catch (ClassCastException e) {
                // Already tried StringSet, so this shouldn't happen, but safe to catch
            }

            if (!appsStr.isEmpty()) {
                try {
                    // Parse JSON array like ["com.xx","com.yy"]
                    String[] parts = appsStr.replace("[", "").replace("]", "").replace("\"", "").split(",");
                    HashSet<String> migratedApps = new HashSet<>();
                    for (String p : parts) {
                        String trimmed = p.trim();
                        if (!trimmed.isEmpty()) migratedApps.add(trimmed);
                    }
                    if (!migratedApps.isEmpty()) {
                        targetApps = new ArrayList<>(migratedApps);
                        // Migrate to new format immediately to avoid future ClassCastException
                        prefs.edit().putStringSet(PREF_TARGET_APPS, migratedApps).apply();
                        Log.i(TAG, "Migrated targetApps from legacy String to StringSet");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing legacy targetApps string", e);
                }
            }
        }

        reminderEnabled = prefs.getBoolean("reminderEnabled", true);
        reminderIntervalMinutes = prefs.getInt("reminderIntervalMinutes", 30);
        reminderStartHour = prefs.getInt("reminderStartHour", 8);
        reminderEndHour = prefs.getInt("reminderEndHour", 22);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                // Always reload from prefs first to get base values
                loadFromPrefs();

                // Check if intent has overrides
                if (intent.hasExtra("limitMinutes")) {
                    limitMinutes = intent.getIntExtra("limitMinutes", 30);
                }
                ArrayList<String> apps = intent.getStringArrayListExtra("targetApps");
                if (apps != null && !apps.isEmpty()) {
                    targetApps = apps;
                }

                // If intent had specific overrides, we save them back to sync
                if (intent.hasExtra("limitMinutes") || apps != null) {
                    saveToSharedPreferences();
                }

                startForegroundService();
                startMonitoring();
            } else if (ACTION_STOP.equals(action)) {
                stopMonitoring();
                dismissOverlay();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else {
                    stopForeground(true);
                }
                stopSelf();
            } else if (ACTION_SNOOZE.equals(action)) {
                continuousSessionMs = Math.max(0, continuousSessionMs - 10 * 60 * 1000);
                staticTotalMinutes = continuousSessionMs / 1000 / 60;
                dismissOverlay();
            }
        }
        return START_STICKY;
    }

    private void saveToSharedPreferences() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_LIMIT_MINUTES, limitMinutes)
                .putStringSet(PREF_TARGET_APPS, new HashSet<>(targetApps))
                .apply();
    }

    // --------------------------------------------
    // Foreground notification
    // --------------------------------------------
    private void startForegroundService() {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FlowBreak \u4fdd\u62a4\u4e2d")
                .setContentText("\u5df2\u542f\u7528\u5168\u65f6\u6bb5\u5065\u5eb7\u4f7f\u7528\u4fdd\u62a4")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
    }

    // ────────────────────────────────────────────
    // Monitoring
    // ────────────────────────────────────────────
    private void startMonitoring() {
        if (monitorRunnable != null || !isScreenOn) return;
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                checkUsage();
                checkTimedReminder();
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(monitorRunnable);
    }

    private void stopMonitoring() {
        if (monitorRunnable != null) {
            handler.removeCallbacks(monitorRunnable);
            monitorRunnable = null;
        }
    }

    private void checkUsage() {
        String topPackage = getTopAppName();
        if (topPackage == null) return;
        currentTopApp = topPackage;

        boolean isTarget = false;
        for (String pkg : targetApps) {
            if (topPackage.equals(pkg)) {
                isTarget = true;
                break;
            }
        }

        if (isTarget) {
            // Increment continuous session (monitor runs every 5s)
            continuousSessionMs += 5000;
            lastLeaveTargetAppTime = 0;

            long totalMinutes = continuousSessionMs / 1000 / 60;
            staticTotalMinutes = totalMinutes;

            if (limitMinutes <= 0) limitMinutes = 1;
            int usagePercent = (int) ((totalMinutes * 100) / limitMinutes);

            Log.d(TAG, "Target app in foreground: " + topPackage
                    + ", continuous usage: " + totalMinutes
                    + "min / " + limitMinutes + "min (" + usagePercent + "%)");

            determineAndShowOverlay(topPackage, totalMinutes, usagePercent);
        } else {
            // Leave target app: Start timer or check if 30s passed
            if (lastLeaveTargetAppTime == 0) {
                lastLeaveTargetAppTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastLeaveTargetAppTime > 30000) {
                // Clear session after 30s
                continuousSessionMs = 0;
                staticTotalMinutes = 0;
                dismissOverlay();
            }
        }
    }

    private void checkTimedReminder() {
        if (!reminderEnabled) return;
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        if (hour < reminderStartHour || hour >= reminderEndHour) return;

        long nowMs = System.currentTimeMillis();
        long intervalMs = reminderIntervalMinutes * 60L * 1000L;
        if (lastTimedReminderMs > 0 && (nowMs - lastTimedReminderMs) < intervalMs) return;

        lastTimedReminderMs = nowMs;
        Log.d(TAG, "Timed reminder triggered after " + reminderIntervalMinutes + " minutes");
        showFallbackNotification("FlowBreak \u4f11\u606f\u63d0\u9192",
                "\u5df2\u7ecf\u4f7f\u7528\u4e86\u4e00\u6bb5\u65f6\u95f4\uff0c\u8be5\u4f11\u606f\u4e00\u4e0b\u4e86");
    }

    // ────────────────────────────────────────────
    // Progressive level detection + overlay
    // ────────────────────────────────────────────
    private void determineAndShowOverlay(String pkg, long minutes, int percent) {
        long now = System.currentTimeMillis();

        // Check level 3 first (most urgent, overrides cooldowns)
        if (percent >= LEVEL3_MIN_PERCENT) {
            if (now < level3CooldownUntil) {
                // Still on cooldown; show lower level if applicable
                if (percent >= LEVEL2_MIN_PERCENT && now >= level2CooldownUntil && currentLevel != 2) {
                    showLevel2(pkg, minutes);
                } else if (currentLevel < 2 && now >= level1CooldownUntil) {
                    showLevel1(pkg, minutes);
                }
                return;
            }
            if (currentLevel != 3) showLevel3(pkg, minutes);
            return;
        }

        // Check level 2
        if (percent >= LEVEL2_MIN_PERCENT) {
            if (now < level2CooldownUntil) {
                if (currentLevel < 2 && now >= level1CooldownUntil) {
                    showLevel1(pkg, minutes);
                }
                return;
            }
            if (currentLevel != 2) showLevel2(pkg, minutes);
            return;
        }

        // Check level 1
        if (percent >= LEVEL1_MIN_PERCENT) {
            if (now < level1CooldownUntil) {
                dismissOverlay();
                return;
            }
            if (currentLevel != 1) showLevel1(pkg, minutes);
            return;
        }

        // Below threshold — remove overlay
        dismissOverlay();
    }

    // ────────────────────────────────────────────
    // Level 1: PERCEPTION — green border + time indicator
    // ────────────────────────────────────────────
    private boolean canShowOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return false;
        }
        return true;
    }

    private void showFallbackNotification(String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        createAlertChannel();

        Intent notifyIntent = new Intent(this, MainActivity.class);
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notifyIntent.putExtra("navigateTo", "rest");
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 2, notifyIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();

        nm.notify(100, notification);
    }

    private void showLevel1(String pkg, long minutes) {
        dismissOverlay();

        if (!canShowOverlay()) {
            showFallbackNotification("FlowBreak \u63d0\u9192",
                    "\u5df2\u89c2\u770b " + minutes + " \u5206\u949f\uff0c\u8bf7\u524d\u5f80\u5e94\u7528\u5f00\u542f\u60ac\u6d6e\u7a97\u6743\u9650\u4ee5\u83b7\u5f97\u5b8c\u6574\u4f11\u606f\u63d0\u9192");
            return;
        }

        handler.post(() -> {
            try {
                int screenW, screenH;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    screenW = windowManager.getCurrentWindowMetrics().getBounds().width();
                    screenH = windowManager.getCurrentWindowMetrics().getBounds().height();
                } else {
                    DisplayMetrics metrics = new DisplayMetrics();
                    windowManager.getDefaultDisplay().getMetrics(metrics);
                    screenW = metrics.widthPixels;
                    screenH = metrics.heightPixels;
                }

                FrameLayout container = new FrameLayout(this);
                container.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                container.setBackgroundColor(Color.TRANSPARENT);

                // Four edge border views
                int borderThickness = dpToPx(2);

                // Top border
                borderTop = new View(this);
                borderTop.setBackgroundColor(Color.argb(30, 0xA5, 0xD6, 0xA7)); // #A5D6A7 ~12% alpha
                FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(screenW, borderThickness);
                tp.gravity = Gravity.TOP;
                container.addView(borderTop, tp);

                // Bottom border
                borderBottom = new View(this);
                borderBottom.setBackgroundColor(Color.argb(30, 0xA5, 0xD6, 0xA7));
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(screenW, borderThickness);
                bp.gravity = Gravity.BOTTOM;
                container.addView(borderBottom, bp);

                // Left border
                borderLeft = new View(this);
                borderLeft.setBackgroundColor(Color.argb(30, 0xA5, 0xD6, 0xA7));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(borderThickness, screenH);
                lp.gravity = Gravity.LEFT;
                container.addView(borderLeft, lp);

                // Right border
                borderRight = new View(this);
                borderRight.setBackgroundColor(Color.argb(30, 0xA5, 0xD6, 0xA7));
                FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(borderThickness, screenH);
                rp.gravity = Gravity.RIGHT;
                container.addView(borderRight, rp);

                // Time indicator at bottom-right
                TextView timeIndicator = new TextView(this);
                timeIndicator.setText("\u5df2\u89c2\u770b " + minutes + " \u5206\u949f");
                timeIndicator.setTextColor(Color.parseColor("#BDBDBD"));
                timeIndicator.setTextSize(11);
                timeIndicator.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
                timeIndicator.setBackgroundColor(Color.argb(180, 33, 33, 33)); // dark semi-transparent
                timeIndicator.setGravity(Gravity.CENTER);

                FrameLayout.LayoutParams tp2 = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                tp2.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                tp2.setMargins(0, 0, dpToPx(16), dpToPx(16));
                container.addView(timeIndicator, tp2);

                // Tap time indicator to dismiss level 1
                timeIndicator.setOnClickListener(v -> dismissLevel(1));

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        screenW, screenH,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.START;

                overlayView = container;
                windowManager.addView(overlayView, params);
                staticCurrentLevel = currentLevel = 1;

                // Start pulsing animation on border
                startPulse(0xFFA5D6A7, 0.04f, 0.12f, PULSE_PERIOD_MS);
            } catch (Exception e) {
                Log.e(TAG, "Level 1 overlay error", e);
            }
        });
    }

    // ────────────────────────────────────────────
    // Level 2: COGNITION — orange border + bottom card
    // ────────────────────────────────────────────
    private void showLevel2(String pkg, long minutes) {
        dismissOverlay();

        if (!canShowOverlay()) {
            showFallbackNotification("FlowBreak \u4f11\u606f\u63d0\u9192",
                    "\u5df2\u7ecf\u8fde\u7eed\u89c2\u770b " + minutes + " \u5206\u949f\uff0c\u5efa\u8bae\u7acb\u523b\u4f11\u606f");
            return;
        }

        handler.post(() -> {
            try {
                int screenW, screenH;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    screenW = windowManager.getCurrentWindowMetrics().getBounds().width();
                    screenH = windowManager.getCurrentWindowMetrics().getBounds().height();
                } else {
                    DisplayMetrics metrics = new DisplayMetrics();
                    windowManager.getDefaultDisplay().getMetrics(metrics);
                    screenW = metrics.widthPixels;
                    screenH = metrics.heightPixels;
                }

                FrameLayout container = new FrameLayout(this);
                container.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                container.setBackgroundColor(Color.TRANSPARENT);

                // Orange border (4 edges)
                int borderThickness = dpToPx(2);

                borderTop = new View(this);
                borderTop.setBackgroundColor(Color.argb(50, 0xFF, 0x98, 0x00));
                FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(screenW, borderThickness);
                tp.gravity = Gravity.TOP;
                container.addView(borderTop, tp);

                borderBottom = new View(this);
                borderBottom.setBackgroundColor(Color.argb(50, 0xFF, 0x98, 0x00));
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(screenW, borderThickness);
                bp.gravity = Gravity.BOTTOM;
                container.addView(borderBottom, bp);

                borderLeft = new View(this);
                borderLeft.setBackgroundColor(Color.argb(50, 0xFF, 0x98, 0x00));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(borderThickness, screenH);
                lp.gravity = Gravity.LEFT;
                container.addView(borderLeft, lp);

                borderRight = new View(this);
                borderRight.setBackgroundColor(Color.argb(50, 0xFF, 0x98, 0x00));
                FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(borderThickness, screenH);
                rp.gravity = Gravity.RIGHT;
                container.addView(borderRight, rp);

                // Bottom card (25% screen height)
                int cardHeight = screenH / 4;
                LinearLayout bottomCard = new LinearLayout(this);
                bottomCard.setOrientation(LinearLayout.VERTICAL);
                bottomCard.setBackgroundColor(Color.argb(217, 255, 255, 255)); // 85% opacity white
                bottomCard.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(20));
                bottomCard.setElevation(dpToPx(8));

                // Message
                TextView msg = new TextView(this);
                msg.setText("\u4f60\u5df2\u7ecf\u8fde\u7eed\u89c2\u770b\u4e86 " + minutes
                        + " \u5206\u949f\uff0c\u773c\u775b\u9700\u8981\u4f11\u606f\u4e00\u4e0b");
                msg.setTextColor(Color.parseColor("#212121"));
                msg.setTextSize(15);
                bottomCard.addView(msg);

                // Button row
                LinearLayout btnRow = new LinearLayout(this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                btnRowParams.setMargins(0, dpToPx(12), 0, 0);
                btnRow.setLayoutParams(btnRowParams);

                // "知道了" button
                Button dismissBtn = new Button(this);
                dismissBtn.setText("\u77e5\u9053\u4e86");
                dismissBtn.setTextColor(Color.parseColor("#616161"));
                dismissBtn.setBackgroundColor(Color.parseColor("#F5F5F5"));
                dismissBtn.setOnClickListener(v -> dismissLevel(2));
                LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                dismissParams.setMargins(0, 0, dpToPx(8), 0);
                dismissBtn.setLayoutParams(dismissParams);
                btnRow.addView(dismissBtn);

                // "去休息" button
                Button restBtn = new Button(this);
                restBtn.setText("\u53bb\u4f11\u606f");
                restBtn.setTextColor(Color.WHITE);
                restBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
                restBtn.setOnClickListener(v -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra("navigateTo", "rest");
                    startActivity(intent);
                });
                LinearLayout.LayoutParams restParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                restBtn.setLayoutParams(restParams);
                btnRow.addView(restBtn);

                bottomCard.addView(btnRow);

                FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, cardHeight);
                cardParams.gravity = Gravity.BOTTOM;
                container.addView(bottomCard, cardParams);

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        screenW, screenH,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.START;

                overlayView = container;
                windowManager.addView(overlayView, params);
                staticCurrentLevel = currentLevel = 2;

                // Pulsing orange border
                startPulse(0xFFFF9800, 0.08f, 0.20f, PULSE_PERIOD_MS);
            } catch (Exception e) {
                Log.e(TAG, "Level 2 overlay error", e);
            }
        });
    }

    // ────────────────────────────────────────────
    // Level 3: ACTION — red tint + center card
    // ────────────────────────────────────────────
    private void showLevel3(String pkg, long minutes) {
        dismissOverlay();

        if (!canShowOverlay()) {
            showFallbackNotification("\u26a0\ufe0f \u9700\u8981\u7acb\u5373\u4f11\u606f",
                    "\u5df2\u8d85\u8fc7\u5efa\u8bae\u4f7f\u7528\u65f6\u957f " + minutes + " \u5206\u949f\uff0c\u8bf7\u7acb\u5373\u4f11\u606f");
            return;
        }

        handler.post(() -> {
            try {
                int screenW, screenH;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    screenW = windowManager.getCurrentWindowMetrics().getBounds().width();
                    screenH = windowManager.getCurrentWindowMetrics().getBounds().height();
                } else {
                    DisplayMetrics metrics = new DisplayMetrics();
                    windowManager.getDefaultDisplay().getMetrics(metrics);
                    screenW = metrics.widthPixels;
                    screenH = metrics.heightPixels;
                }

                // Full screen red-tinted background
                FrameLayout container = new FrameLayout(this);
                container.setBackgroundColor(Color.argb(77, 244, 67, 54)); // #F44336 30% opacity
                // Consume background touches so user can't bypass by tapping outside card
                container.setOnTouchListener((v, event) -> true);

                // Center card
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(Color.WHITE);
                card.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(20));
                card.setElevation(dpToPx(12));

                int cardWidth = (int) (screenW * 0.85);
                FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                        cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.gravity = Gravity.CENTER;

                // Title
                TextView title = new TextView(this);
                title.setText("\u662f\u65f6\u5019\u4f11\u606f\u4e00\u4e0b\u4e86");
                title.setTextColor(Color.parseColor("#212121"));
                title.setTextSize(20);
                title.setGravity(Gravity.CENTER);
                title.setPadding(0, 0, 0, dpToPx(4));
                card.addView(title);

                // Subtitle
                TextView subtitle = new TextView(this);
                subtitle.setText("\u63a8\u8350\u4ee5\u4e0b 3 \u5206\u949f\u6062\u590d\u6d3b\u52a8");
                subtitle.setTextColor(Color.parseColor("#9E9E9E"));
                subtitle.setTextSize(13);
                subtitle.setGravity(Gravity.CENTER);
                subtitle.setPadding(0, 0, 0, dpToPx(16));
                card.addView(subtitle);

                // 3 activity options
                String[][] activities = {
                        { "\ud83d\udc41\ufe0f", "\u773c\u90e8\u653e\u677e", "\u8f6c\u52a8\u773c\u7403 + \u770b\u8fdc\u5904", "#4CAF50" },
                        { "\ud83e\udde0", "\u8eab\u4f53\u62c9\u4f38", "\u9888\u90e8 + \u80a9\u90e8\u62c9\u4f38", "#2196F3" },
                        { "\u23f0", "\u6df1\u547c\u5438", "4-7-8 \u547c\u5438\u6cd5", "#FF9800" },
                };

                for (String[] act : activities) {
                    LinearLayout item = new LinearLayout(this);
                    item.setOrientation(LinearLayout.HORIZONTAL);
                    item.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

                    // Icon
                    TextView iconView = new TextView(this);
                    iconView.setText(act[0]);
                    iconView.setTextSize(18);
                    iconView.setGravity(Gravity.CENTER);
                    iconView.setBackgroundColor(Color.parseColor(act[3] + "20"));
                    iconView.setWidth(dpToPx(36));
                    iconView.setHeight(dpToPx(36));
                    item.addView(iconView);

                    // Label + desc
                    LinearLayout textCol = new LinearLayout(this);
                    textCol.setOrientation(LinearLayout.VERTICAL);
                    textCol.setPadding(dpToPx(10), 0, 0, 0);

                    TextView label = new TextView(this);
                    label.setText(act[1]);
                    label.setTextColor(Color.parseColor("#212121"));
                    label.setTextSize(14);
                    textCol.addView(label);

                    TextView desc = new TextView(this);
                    desc.setText(act[2]);
                    desc.setTextColor(Color.parseColor("#9E9E9E"));
                    desc.setTextSize(12);
                    textCol.addView(desc);

                    item.addView(textCol);

                    LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    itemParams.setMargins(0, 0, 0, dpToPx(6));
                    item.setLayoutParams(itemParams);
                    card.addView(item);
                }

                // Button: 开始休息
                Button restBtn = new Button(this);
                restBtn.setText("\u5f00\u59cb\u4f11\u606f");
                restBtn.setTextColor(Color.WHITE);
                restBtn.setBackgroundColor(Color.parseColor("#4CAF50"));
                LinearLayout.LayoutParams restBtnParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                restBtnParams.setMargins(0, dpToPx(12), 0, dpToPx(6));
                restBtn.setLayoutParams(restBtnParams);
                restBtn.setOnClickListener(v -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra("navigateTo", "rest");
                    startActivity(intent);
                });
                card.addView(restBtn);

                // Level 3 has no snooze — user must rest
                TextView mustRestHint = new TextView(this);
                mustRestHint.setText("\u5df2\u8d85\u8fc7\u5efa\u8bae\u4f7f\u7528\u65f6\u957f\uff0c\u8bf7\u7acb\u5373\u4f11\u606f");
                mustRestHint.setTextColor(Color.parseColor("#F44336"));
                mustRestHint.setTextSize(12);
                mustRestHint.setGravity(Gravity.CENTER);
                mustRestHint.setPadding(0, dpToPx(8), 0, 0);
                card.addView(mustRestHint);

                container.addView(card, cardParams);

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        screenW, screenH,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.START;

                overlayView = container;
                windowManager.addView(overlayView, params);
                staticCurrentLevel = currentLevel = 3;
            } catch (Exception e) {
                Log.e(TAG, "Level 3 overlay error", e);
            }
        });
    }

    // ────────────────────────────────────────────
    // Overlay management
    // ────────────────────────────────────────────
    private void dismissOverlay() {
        stopPulse();
        if (overlayView == null) return;
        handler.post(() -> {
            try {
                windowManager.removeView(overlayView);
                overlayView = null;
                borderTop = borderBottom = borderLeft = borderRight = null;
                staticCurrentLevel = currentLevel = 0;
            } catch (Exception e) {
                Log.e(TAG, "Remove overlay error", e);
            }
        });
    }

    private void dismissLevel(int level) {
        long now = System.currentTimeMillis();
        switch (level) {
            case 1:
                level1CooldownUntil = now + COOLDOWN_LEVEL1_MS;
                Log.d(TAG, "Level 1 dismissed, cooldown until " + level1CooldownUntil);
                break;
            case 2:
                level2CooldownUntil = now + COOLDOWN_LEVEL2_MS;
                Log.d(TAG, "Level 2 dismissed, cooldown until " + level2CooldownUntil);
                break;
            case 3:
                level3CooldownUntil = now + COOLDOWN_LEVEL3_MS;
                Log.d(TAG, "Level 3 dismissed, cooldown until " + level3CooldownUntil);
                break;
        }
        dismissOverlay();
    }

    // ────────────────────────────────────────────
    // Pulsing border animation
    // ────────────────────────────────────────────
    private void startPulse(int color, float minAlpha, float maxAlpha, int periodMs) {
        stopPulse();
        currentPulseColor = color;
        currentPulseMinAlpha = minAlpha;
        currentPulseMaxAlpha = maxAlpha;
        pulseStartTime = System.currentTimeMillis();

        pulseRunnable = new Runnable() {
            @Override
            public void run() {
                if (overlayView == null) return;
                long elapsed = System.currentTimeMillis() - pulseStartTime;
                double phase = (elapsed % periodMs) / (double) periodMs;
                float factor = (float) ((Math.sin(phase * 2 * Math.PI) + 1) / 2.0);
                float alpha = currentPulseMinAlpha + (currentPulseMaxAlpha - currentPulseMinAlpha) * factor;
                int argb = (Math.round(alpha * 255) << 24)
                        | (currentPulseColor & 0x00FFFFFF);

                View[] borders = { borderTop, borderBottom, borderLeft, borderRight };
                for (View b : borders) {
                    if (b != null) b.setBackgroundColor(argb);
                }
                handler.postDelayed(this, 50);
            }
        };
        handler.post(pulseRunnable);
    }

    private void stopPulse() {
        if (pulseRunnable != null) {
            handler.removeCallbacks(pulseRunnable);
            pulseRunnable = null;
        }
    }

    // ────────────────────────────────────────────
    // Utility: dp to px
    // ────────────────────────────────────────────
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    // ────────────────────────────────────────────
    // Usage stats helpers (unchanged logic, same as before)
    // ────────────────────────────────────────────
    private String getTopAppName() {
        UsageStatsManager mUsageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        List<UsageStats> stats = mUsageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time);
        if (stats != null) {
            SortedMap<Long, UsageStats> mySortedMap = new TreeMap<>();
            for (UsageStats usageStats : stats) {
                mySortedMap.put(usageStats.getLastTimeUsed(), usageStats);
            }
            if (!mySortedMap.isEmpty()) {
                return mySortedMap.get(mySortedMap.lastKey()).getPackageName();
            }
        }
        return null;
    }

    private long getTodayUsage(String packageName) {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
        if (stats != null) {
            for (UsageStats usageStats : stats) {
                if (usageStats.getPackageName().equals(packageName)) {
                    return usageStats.getTotalTimeInForeground();
                }
            }
        }
        return 0;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "FlowBreak Foreground Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void createAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel alertChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "FlowBreak \u4f11\u606f\u63d0\u9192",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("\u7528\u4e8e\u663e\u793a\u4f11\u606f\u63d0\u9192\u548c\u5e72\u9884\u901a\u77e5");
            alertChannel.enableVibration(true);
            alertChannel.setVibrationPattern(new long[]{0, 200, 100, 300});
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(alertChannel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screenReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver not registered", e);
        }
        stopMonitoring();
        dismissOverlay();
        super.onDestroy();
    }
}
