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
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class FlowForegroundService extends Service {
    private static final String TAG = "FlowForegroundService";
    private static final String CHANNEL_ID = "FlowBreakServiceChannel";
    public static final String ACTION_START = "START";
    public static final String ACTION_STOP = "STOP";

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable monitorRunnable;
    private int limitMinutes = 30;
    private ArrayList<String> targetApps = new ArrayList<>();
    
    private WindowManager windowManager;
    private View overlayView;
    private boolean isOverlayShowing = false;
    private boolean isScreenOn = true;

    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                isScreenOn = false;
                stopMonitoring();
                Log.d(TAG, "Screen OFF, monitoring paused");
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                isScreenOn = true;
                startMonitoring();
                Log.d(TAG, "Screen ON, monitoring resumed");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                limitMinutes = intent.getIntExtra("limitMinutes", 30);
                ArrayList<String> apps = intent.getStringArrayListExtra("targetApps");
                if (apps != null) {
                    targetApps = apps;
                }
                startForegroundService();
                startMonitoring();
            } else if (ACTION_STOP.equals(action)) {
                stopMonitoring();
                removeOverlay();
                stopForeground(true);
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void startForegroundService() {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FlowBreak 专注中")
                .setContentText("已启用全时段防沉迷保护")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        startForeground(1, notification);
    }

    private void startMonitoring() {
        if (monitorRunnable != null || !isScreenOn) return;

        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                checkUsage();
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

        boolean isTarget = false;
        for (String pkg : targetApps) {
            if (topPackage.contains(pkg)) {
                isTarget = true;
                break;
            }
        }

        if (isTarget) {
            long totalMinutes = getTodayUsage(topPackage) / 1000 / 60;
            if (totalMinutes >= limitMinutes) {
                showOverlay(topPackage, totalMinutes);
            } else {
                removeOverlay();
            }
        } else if (!topPackage.equals(getPackageName())) {
            removeOverlay();
        }
    }

    private void showOverlay(String pkg, long minutes) {
        if (isOverlayShowing) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return;

        handler.post(() -> {
            try {
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);

                params.gravity = Gravity.CENTER;

                // 使用 FrameLayout 包裹以便拦截按键
                FrameLayout container = new FrameLayout(this) {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent event) {
                        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                            return true; // 拦截返回键，不让消失
                        }
                        return super.dispatchKeyEvent(event);
                    }
                };

                TextView textView = new TextView(this);
                textView.setText("FlowBreak 强制提醒：\n今日已使用 " + minutes + " 分钟\n请立即停止刷视频！");
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(22);
                textView.setGravity(Gravity.CENTER);
                textView.setBackgroundColor(Color.parseColor("#EE000000")); // 更深的颜色

                textView.setOnClickListener(v -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                });

                container.addView(textView);
                overlayView = container;
                windowManager.addView(overlayView, params);
                isOverlayShowing = true;
            } catch (Exception e) {
                Log.e(TAG, "Overlay error", e);
            }
        });
    }

    private void removeOverlay() {
        if (!isOverlayShowing || overlayView == null) return;
        handler.post(() -> {
            try {
                windowManager.removeView(overlayView);
                overlayView = null;
                isOverlayShowing = false;
            } catch (Exception e) {
                Log.e(TAG, "Remove overlay error", e);
            }
        });
    }

    private String getTopAppName() {
        UsageStatsManager mUsageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time);
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(screenReceiver);
        stopMonitoring();
        removeOverlay();
        super.onDestroy();
    }
}
