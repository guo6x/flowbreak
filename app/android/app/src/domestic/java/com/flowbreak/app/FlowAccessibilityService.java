package com.flowbreak.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import java.util.Locale;
import java.util.Set;

public class FlowAccessibilityService extends AccessibilityService {

    private static final String WECHAT = "com.tencent.mm";

    /**
     * 微信视频号相关的 Activity class name 关键词。
     * 使用 "finder"（视频号内部模块名）和 "videochannel" 精确匹配，
     * 避免 "channel" 误匹配公众号文章分享、聊天频道等无关页面。
     */
    private static final String[] VIDEO_CHANNEL_KEYWORDS = {
            "finder", "videochannel"
    };

    /** wechatInVideoChannel pref 的有效期（毫秒），超时后视为过期数据不信任 */
    private static final long VIDEO_CHANNEL_PREF_TTL_MS = 60_000L;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence packageNameValue = event.getPackageName();
        if (packageNameValue == null) return;
        String packageName = packageNameValue.toString();
        if (packageName.equals(getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences("FlowBreakPrefs", MODE_PRIVATE);
        boolean strongDefault = "domestic".equals(BuildConfig.CHANNEL);
        if (!prefs.getBoolean("strongBlockingEnabled", strongDefault)) return;
        Set<String> targets = PreferenceUtils.getMigratedTargetApps(prefs);

        // 微信场景细化：检测当前是否在视频号页面，并记录时间戳供 FlowForegroundService 判断时效性
        if (WECHAT.equals(packageName)) {
            boolean inVideoChannel = isInVideoChannel(event.getClassName());
            prefs.edit()
                    .putBoolean("wechatInVideoChannel", inVideoChannel)
                    .putLong("wechatInVideoChannelAt", System.currentTimeMillis())
                    .apply();

            // 只有在视频号页面且处于阻断状态才执行强阻断
            if (inVideoChannel && isBlocked(prefs) && targets.contains(packageName)) {
                performGlobalAction(GLOBAL_ACTION_HOME);
                startBlockActivity(packageName);
            }
            return;
        }

        // 其他目标应用：维持原有强阻断逻辑
        if (!targets.contains(packageName)) return;
        if (!isBlocked(prefs)) return;

        performGlobalAction(GLOBAL_ACTION_HOME);
        startBlockActivity(packageName);
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        // 不主动清除 wechatInVideoChannel pref：服务被系统回收重启时，
        // 清除会导致下一次窗口事件到来前的检测盲区。60s TTL 已足够让过期数据自然失效。
    }

    private boolean isBlocked(SharedPreferences prefs) {
        return BlockStateMachine.State.BLOCKED.name().equals(
                prefs.getString("blockState", BlockStateMachine.State.IDLE.name())
        );
    }

    /**
     * 判断当前 className 是否属于微信视频号页面。
     */
    private boolean isInVideoChannel(CharSequence className) {
        if (className == null) return false;
        String name = className.toString().toLowerCase(Locale.ROOT);
        for (String keyword : VIDEO_CHANNEL_KEYWORDS) {
            if (name.contains(keyword)) return true;
        }
        return false;
    }

    private void startBlockActivity(String packageName) {
        Intent block = new Intent(this, BlockActivity.class);
        block.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        block.putExtra("blockedPackage", packageName);
        try { startActivity(block); } catch (Exception ignored) { }
    }

    @Override public void onInterrupt() { }
}
