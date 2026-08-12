package com.flowbreak.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.content.ContextCompat;
import java.util.Locale;
import java.util.Set;

public class FlowAccessibilityService extends AccessibilityService {

    private static final String TAG = "FlowAccessibility";
    private static final String WECHAT = "com.tencent.mm";
    /** 横幅轮询间隔：BLOCKED 解除（休息完成/紧急解锁/关闭监控）后自动清理入口。 */
    private static final long BLOCKED_POLL_MS = 2_000L;

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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BlockedTargetBanner blockBanner;

    private final Runnable blockPoll = new Runnable() {
        @Override public void run() {
            if (blockBanner == null || !blockBanner.isShowing()) return;
            if (!isBlocked(prefs())) {
                blockBanner.dismiss();
                return;
            }
            handler.postDelayed(this, BLOCKED_POLL_MS);
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        blockBanner = new BlockedTargetBanner(
                this,
                (WindowManager) getSystemService(WINDOW_SERVICE),
                handler,
                this::onStartRestClicked
        );
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence packageNameValue = event.getPackageName();
        if (packageNameValue == null) return;
        String packageName = packageNameValue.toString();
        if (packageName.equals(getPackageName())) return;

        SharedPreferences prefs = prefs();
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
                kickBlockedTarget(packageName);
            }
            return;
        }

        // 其他目标应用：维持原有强阻断逻辑
        if (!targets.contains(packageName)) return;
        if (!isBlocked(prefs)) return;

        kickBlockedTarget(packageName);
    }

    /**
     * 强阻断核心动作：回 HOME + 无障碍横幅入口 + 尽力启动 BlockActivity。
     * BlockActivity 在 HyperOS 可能被后台弹出限制拒绝，横幅不依赖它。
     */
    private void kickBlockedTarget(String packageName) {
        performGlobalAction(GLOBAL_ACTION_HOME);
        blockBanner.show(packageName);
        tryStartBlockActivity(packageName);
        handler.removeCallbacks(blockPoll);
        handler.postDelayed(blockPoll, BLOCKED_POLL_MS);
    }

    /** 尽力启动 BlockActivity；失败必须记录日志（由横幅兜底入口），不静默吞异常。 */
    boolean tryStartBlockActivity(String packageName) {
        Intent block = new Intent(this, BlockActivity.class);
        block.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        block.putExtra("blockedPackage", packageName);
        try {
            startActivity(block);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "startBlockActivity rejected for " + packageName, e);
            return false;
        }
    }

    /** 横幅"开始休息"：进入休息会话并尝试打开休息页。 */
    private void onStartRestClicked() {
        Intent service = new Intent(this, FlowForegroundService.class);
        service.setAction(FlowForegroundService.ACTION_BEGIN_REST);
        try {
            ContextCompat.startForegroundService(this, service);
        } catch (Exception e) {
            Log.w(TAG, "startForegroundService(BEGIN_REST) failed", e);
        }
        Intent rest = new Intent(this, MainActivity.class);
        rest.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        rest.putExtra("navigateTo", "rest");
        try {
            startActivity(rest);
        } catch (Exception e) {
            Log.w(TAG, "open rest page from block banner failed", e);
        }
    }

    @Override public boolean onUnbind(Intent intent) {
        cleanupBanner();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        cleanupBanner();
        super.onDestroy();
    }

    private void cleanupBanner() {
        handler.removeCallbacks(blockPoll);
        if (blockBanner != null) blockBanner.dismiss();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("FlowBreakPrefs", MODE_PRIVATE);
    }

    boolean isBlockBannerShowing() {
        return blockBanner != null && blockBanner.isShowing();
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

    @Override public void onInterrupt() { }
}
