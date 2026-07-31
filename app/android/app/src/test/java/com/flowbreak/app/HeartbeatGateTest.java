package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * HeartbeatGate 心跳节流纯逻辑测试。
 *
 * 覆盖任务要求的所有边界：
 * - 初次允许
 * - 29,999ms 不允许
 * - 30,000ms 允许
 * - 时间倒退不允许
 * - 成功写入后重新计时
 * - now < 0 拒绝
 */
public class HeartbeatGateTest {
    private static final long INTERVAL = HeartbeatGate.HEARTBEAT_INTERVAL_MS; // 30000

    // ==================== 初次允许 ====================

    @Test public void firstWriteWithZeroLastWrittenAllowed() {
        assertTrue(HeartbeatGate.shouldWrite(0L, 1_000L));
    }

    @Test public void firstWriteWithNegativeLastWrittenAllowed() {
        assertTrue(HeartbeatGate.shouldWrite(-1L, 1_000L));
    }

    @Test public void firstWriteAtZeroNowAllowed() {
        // now=0 也是合法时间，首次允许
        assertTrue(HeartbeatGate.shouldWrite(0L, 0L));
    }

    // ==================== 30 秒节流边界 ====================

    @Test public void twentyNineSecondsAfterLastWriteNotAllowed() {
        long last = 100_000L;
        assertFalse(HeartbeatGate.shouldWrite(last, last + 29_999L));
    }

    @Test public void exactlyThirtySecondsAfterLastWriteAllowed() {
        long last = 100_000L;
        assertTrue(HeartbeatGate.shouldWrite(last, last + 30_000L));
    }

    @Test public void aboveThirtySecondsAfterLastWriteAllowed() {
        long last = 100_000L;
        assertTrue(HeartbeatGate.shouldWrite(last, last + 60_000L));
    }

    @Test public void justBeforeThirtySecondsNotAllowed() {
        long last = 1_000_000L;
        assertFalse(HeartbeatGate.shouldWrite(last, last + INTERVAL - 1));
    }

    // ==================== 时间倒退 ====================

    @Test public void timeGoingBackwardsNotAllowed() {
        long last = 100_000L;
        assertFalse(HeartbeatGate.shouldWrite(last, 50_000L));
    }

    @Test public void equalTimestampsAfterFirstWriteNotAllowed() {
        // now == lastWrittenAt 且 lastWrittenAt > 0：间隔 0 < 30000，拒绝
        long last = 100_000L;
        assertFalse(HeartbeatGate.shouldWrite(last, last));
    }

    @Test public void oneMsAfterLastWriteNotAllowed() {
        long last = 100_000L;
        assertFalse(HeartbeatGate.shouldWrite(last, last + 1));
    }

    // ==================== now < 0 ====================

    @Test public void negativeNowRejectedEvenOnFirstWrite() {
        assertFalse(HeartbeatGate.shouldWrite(0L, -1L));
    }

    @Test public void negativeNowRejectedAfterLastWrite() {
        assertFalse(HeartbeatGate.shouldWrite(100_000L, -1L));
    }

    // ==================== 成功写入后重新计时 ====================

    @Test public void gateResetsAfterThirtySecondInterval() {
        // 模拟连续两次写入：第一次在 t=1000，第二次应在 t>=31000
        long first = 1_000L;
        assertTrue(HeartbeatGate.shouldWrite(0L, first));

        // 距 first 不足 30 秒：拒绝
        assertFalse(HeartbeatGate.shouldWrite(first, first + 20_000L));

        // 距 first 达到 30 秒：允许
        long second = first + INTERVAL;
        assertTrue(HeartbeatGate.shouldWrite(first, second));

        // 距 second 不足 30 秒：拒绝
        assertFalse(HeartbeatGate.shouldWrite(second, second + 25_000L));

        // 距 second 达到 30 秒：允许
        assertTrue(HeartbeatGate.shouldWrite(second, second + INTERVAL));
    }

    @Test public void intervalConstantIsThirtySeconds() {
        assertEquals(30_000L, HeartbeatGate.HEARTBEAT_INTERVAL_MS);
    }
}
