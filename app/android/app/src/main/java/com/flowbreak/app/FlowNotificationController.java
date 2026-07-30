package com.flowbreak.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;

/**
 * 通知控制器，封装两个 channel、常驻通知和提醒通知。
 *
 * 从 FlowForegroundService 抽取，保持原有：
 * - CHANNEL_SERVICE = flowbreak_service
 * - CHANNEL_ALERT = flowbreak_alert
 * - SERVICE_NOTIFICATION_ID = 1001
 * - ALERT_NOTIFICATION_ID = 1002
 * - 所有中文文案完全不变
 * - GRACE 正文使用整数分钟向下取整
 * - channel 只在不存在时创建
 * - 通知权限判断
 *
 * 控制器只负责构建 Notification，不调用 startForeground。
 */
public final class FlowNotificationController {
    public static final String CHANNEL_SERVICE = "flowbreak_service";
    public static final String CHANNEL_ALERT = "flowbreak_alert";
    public static final int SERVICE_NOTIFICATION_ID = 1001;
    public static final int ALERT_NOTIFICATION_ID = 1002;

    private final Context context;

    public FlowNotificationController(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 创建两个 channel，只在不存在时创建。 */
    public void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (manager.getNotificationChannel(CHANNEL_SERVICE) == null) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_SERVICE, "FlowBreak 运行状态", NotificationManager.IMPORTANCE_LOW
            ));
        }
        if (manager.getNotificationChannel(CHANNEL_ALERT) == null) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ALERT, "休息与阻断提醒", NotificationManager.IMPORTANCE_HIGH
            ));
        }
    }

    /** 构建常驻前台通知。 */
    public Notification buildServiceNotification(State snapshot) {
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String content = formatServiceContent(snapshot);
        String title = snapshot.targetAppsEmpty
                ? "FlowBreak 未配置目标应用"
                : "FlowBreak 正在保护你";
        if (snapshot.targetAppsEmpty) {
            content = "尚未选择要限制的应用，点击进入设置。";
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SERVICE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(content)
                .setOngoing(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        int usedSec = snapshot.machineState == null ? 0 : (int) (snapshot.sessionMs / 1000L);
        int limitSec = snapshot.limitMinutes * 60;
        if (limitSec > 0) {
            int progress = Math.min(100, usedSec * 100 / limitSec);
            builder.setProgress(100, progress, false);
        }
        return builder.build();
    }

    /** 刷新常驻通知。 */
    public void updateServiceNotification(State snapshot) {
        if (!canPostNotifications()) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(snapshot));
        } catch (Exception ignored) { }
    }

    /** 发送高优先级提醒通知。 */
    public void alert(String title, String body, boolean blocked) {
        if (!canPostNotifications()) return;
        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("navigateTo", blocked ? "rest" : "dashboard");
        PendingIntent pending = PendingIntent.getActivity(
                context, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(ALERT_NOTIFICATION_ID, notification);
    }

    public boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 构建常驻通知正文：显示当前状态与剩余额度。
     * 纯函数，便于 JVM 测试。
     */
    public static String formatServiceContent(State snapshot) {
        return formatServiceContent(snapshot, System.currentTimeMillis());
    }

    /**
     * 构建常驻通知正文的重载，接受固定 now 用于测试。
     * GRACE 正文使用整数分钟向下取整（不向上取整）。
     */
    public static String formatServiceContent(State snapshot, long now) {
        if (snapshot.machineState == null) return "仅在本机检测所选应用的连续使用";
        BlockStateMachine.State state = snapshot.machineState;
        int usedMin = (int) (snapshot.sessionMs / 60_000L);
        int limitMin = snapshot.limitMinutes;
        int remainMin = Math.max(0, limitMin - usedMin);
        if (state == BlockStateMachine.State.BLOCKED) {
            return "已暂停 · 完成休息可解锁 10 分钟";
        }
        if (state == BlockStateMachine.State.GRACE) {
            long graceRemain = Math.max(0, snapshot.graceUntil - now);
            int graceMin = (int) (graceRemain / 60_000L);
            return "访问窗口 · 还剩 " + Math.max(0, graceMin) + " 分钟";
        }
        if (state == BlockStateMachine.State.RESTING) {
            return "休息中 · 完成后解锁 10 分钟";
        }
        if (remainMin > 0) {
            return "连续使用 " + usedMin + "/" + limitMin + " 分钟 · 还剩 " + remainMin + " 分钟";
        }
        return "连续使用 " + usedMin + "/" + limitMin + " 分钟";
    }

    /** 不可变通知状态快照，由 Service 每次传入。 */
    public static final class State {
        public final BlockStateMachine.State machineState;
        public final long sessionMs;
        public final long graceUntil;
        public final int limitMinutes;
        public final boolean targetAppsEmpty;

        public State(
                BlockStateMachine.State machineState,
                long sessionMs,
                long graceUntil,
                int limitMinutes,
                boolean targetAppsEmpty
        ) {
            this.machineState = machineState;
            this.sessionMs = sessionMs;
            this.graceUntil = graceUntil;
            this.limitMinutes = limitMinutes;
            this.targetAppsEmpty = targetAppsEmpty;
        }
    }
}
