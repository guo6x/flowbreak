package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FlowNotificationControllerTest {
    private static final long NOW = 10_000_000L;

    private static FlowNotificationController.State state(
            BlockStateMachine.State machineState,
            long sessionMs,
            long graceUntil,
            int limitMinutes,
            boolean targetAppsEmpty
    ) {
        return new FlowNotificationController.State(
                machineState, sessionMs, graceUntil, limitMinutes, targetAppsEmpty
        );
    }

    @Test public void nullMachineStateShowsMonitoringHint() {
        // machine 为 null 时显示通用检测说明
        FlowNotificationController.State s = state(null, 0L, 0L, 25, false);
        assertEquals("仅在本机检测所选应用的连续使用",
                FlowNotificationController.formatServiceContent(s, NOW));
    }

    @Test public void idleWithRemainingMinutesShowsProgress() {
        FlowNotificationController.State s = state(
                BlockStateMachine.State.IDLE, 10 * 60_000L, 0L, 25, false
        );
        assertEquals("连续使用 10/25 分钟 · 还剩 15 分钟",
                FlowNotificationController.formatServiceContent(s, NOW));
    }

    @Test public void idleAtLimitDropsRemainingSuffix() {
        // 已用满：remainMin=0，不显示"还剩"
        FlowNotificationController.State s = state(
                BlockStateMachine.State.IDLE, 25 * 60_000L, 0L, 25, false
        );
        assertEquals("连续使用 25/25 分钟",
                FlowNotificationController.formatServiceContent(s, NOW));
    }

    @Test public void blockedShowsPausedText() {
        FlowNotificationController.State s = state(
                BlockStateMachine.State.BLOCKED, 30 * 60_000L, 0L, 25, false
        );
        assertEquals("已暂停 · 完成休息可解锁 10 分钟",
                FlowNotificationController.formatServiceContent(s, NOW));
    }

    @Test public void restingShowsRestingText() {
        FlowNotificationController.State s = state(
                BlockStateMachine.State.RESTING, 0L, 0L, 25, false
        );
        assertEquals("休息中 · 完成后解锁 10 分钟",
                FlowNotificationController.formatServiceContent(s, NOW));
    }

    @Test public void graceUsesFloorMinutes() {
        // graceUntil - now = 125_999ms = 2 分 5.999 秒，向下取整为 2 分钟
        FlowNotificationController.State s = state(
                BlockStateMachine.State.GRACE, 0L, NOW + 125_999L, 25, false
        );
        assertEquals("访问窗口 · 还剩 2 分钟",
                FlowNotificationController.formatServiceContent(s, NOW));
    }

    @Test public void graceBelowOneMinuteShowsZero() {
        // graceRemain = 30_000ms = 0 分钟，向下取整为 0
        FlowNotificationController.State s = state(
                BlockStateMachine.State.GRACE, 0L, NOW + 30_000L, 25, false
        );
        assertEquals("访问窗口 · 还剩 0 分钟",
                FlowNotificationController.formatServiceContent(s, NOW));
    }

    @Test public void negativeRemainingClampedToZero() {
        // sessionMs 超过 limit 导致 remainMin 为负，应归零显示
        FlowNotificationController.State s = state(
                BlockStateMachine.State.IDLE, 30 * 60_000L, 0L, 25, false
        );
        // 30 - 25 = 5 分钟已超，但 remainMin 被 Math.max(0, ...) 归零，所以不显示"还剩"
        assertEquals("连续使用 30/25 分钟",
                FlowNotificationController.formatServiceContent(s, NOW));
    }

    @Test public void limitBoundaryAtOneMinute() {
        // limitMinutes=1, sessionMs=0：还剩 1 分钟
        FlowNotificationController.State s = state(
                BlockStateMachine.State.IDLE, 0L, 0L, 1, false
        );
        assertEquals("连续使用 0/1 分钟 · 还剩 1 分钟",
                FlowNotificationController.formatServiceContent(s, NOW));
    }

    @Test public void graceExpiredShowsZero() {
        // graceUntil 已过，graceRemain 被 Math.max(0, ...) 归零
        FlowNotificationController.State s = state(
                BlockStateMachine.State.GRACE, 0L, NOW - 1_000L, 25, false
        );
        assertEquals("访问窗口 · 还剩 0 分钟",
                FlowNotificationController.formatServiceContent(s, NOW));
    }

    @Test public void perceptionShowsRemainingMinutes() {
        // PERCEPTION 状态（80%）应显示剩余分钟
        FlowNotificationController.State s = state(
                BlockStateMachine.State.PERCEPTION, 20 * 60_000L, 0L, 25, false
        );
        assertEquals("连续使用 20/25 分钟 · 还剩 5 分钟",
                FlowNotificationController.formatServiceContent(s, NOW));
    }

    @Test public void cognitionShowsRemainingMinutes() {
        // COGNITION 状态（100%）应显示"已用满"格式
        FlowNotificationController.State s = state(
                BlockStateMachine.State.COGNITION, 25 * 60_000L, 0L, 25, false
        );
        assertEquals("连续使用 25/25 分钟",
                FlowNotificationController.formatServiceContent(s, NOW));
    }
}
