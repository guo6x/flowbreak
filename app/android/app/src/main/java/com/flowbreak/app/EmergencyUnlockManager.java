package com.flowbreak.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class EmergencyUnlockManager {
    private static final String PREF_DAY = "emergencyUnlockDay";
    private static final String PREF_WALL_AT = "emergencyUnlockWallAt";
    private static final String PREF_MONOTONIC_AT = "emergencyUnlockMonotonicAt";
    /** 单调时钟与 wall clock 差异的容忍度，防止微小时钟同步导致误判 */
    private static final long CLOCK_SKEW_TOLERANCE_MS = 60_000L;

    private EmergencyUnlockManager() { }

    public static boolean tryUnlock(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("FlowBreakPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("allowEmergencyUnlock", true)) return false;

        long nowWall = System.currentTimeMillis();
        long nowMonotonic = SystemClock.elapsedRealtime();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(nowWall));

        // 检测系统时间篡改：如果今天已用过紧急解锁，验证时间是否被人为调整
        if (today.equals(prefs.getString(PREF_DAY, ""))) {
            return false; // 今天已用过，直接拒绝
        }

        // 时间篡改检测：上次的 wall clock 和 monotonic 都有记录
        long lastWall = prefs.getLong(PREF_WALL_AT, 0L);
        long lastMonotonic = prefs.getLong(PREF_MONOTONIC_AT, 0L);
        if (lastWall > 0L && lastMonotonic > 0L) {
            long wallDelta = nowWall - lastWall;
            long monotonicDelta = nowMonotonic - lastMonotonic;
            // 设备重启检测：SystemClock.elapsedRealtime() 重启后复位到接近 0，
            // 此时 monotonicDelta 为负或远小于 wallDelta，时间比较无意义，
            // 只能靠 calendar day 判断，不做篡改推断。
            if (monotonicDelta < 0L
                    || monotonicDelta < wallDelta - CLOCK_SKEW_TOLERANCE_MS * 10) {
                // 设备已重启，清除旧的 monotonic 记录，后续重新建立基线
                prefs.edit().putLong(PREF_MONOTONIC_AT, nowMonotonic).apply();
            } else if (wallDelta < 0L
                    || wallDelta > monotonicDelta + CLOCK_SKEW_TOLERANCE_MS) {
                // 时间被倒拨（wallDelta < 0）或前拨过多（wallDelta 远大于 monotonicDelta）
                prefs.edit().putString(PREF_DAY, today).apply();
                return false;
            }
        }

        prefs.edit()
                .putString(PREF_DAY, today)
                .putLong(PREF_WALL_AT, nowWall)
                .putLong(PREF_MONOTONIC_AT, nowMonotonic)
                .apply();
        return true;
    }

    public static int remainingToday(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("FlowBreakPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("allowEmergencyUnlock", true)) return 0;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return today.equals(prefs.getString(PREF_DAY, "")) ? 0 : 1;
    }
}
