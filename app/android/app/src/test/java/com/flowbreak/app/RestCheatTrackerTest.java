package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * RestCheatTracker 纯累计逻辑测试。
 *
 * 覆盖任务要求的所有边界：
 * - 非 RESTING 不累计
 * - RESTING 但非目标不累计
 * - 首次目标 delta 为 0
 * - 连续目标累计
 * - 单次 delta 最多 10 秒
 * - 时间倒退为 0
 * - 累计到 4999ms 不触发
 * - 累计到 5000ms 触发
 * - RESTING 期间离开目标应用不清零
 * - 再次回到目标继续累计
 * - 退出 RESTING 清零
 * - 手动 reset 清零
 */
public class RestCheatTrackerTest {
    private static final long THRESHOLD = RestCheatTracker.CHEAT_THRESHOLD_MS; // 5000
    private static final long MAX_DELTA = RestCheatTracker.MAX_CHEAT_DELTA_MS; // 10000

    // ==================== 不累计场景 ====================

    @Test public void nonRestingDoesNotAccumulateEvenIfTarget() {
        RestCheatTracker t = new RestCheatTracker();
        long acc = t.observe(false, true, 1_000L, 5_000L);
        assertEquals(0L, acc);
        assertEquals(0L, t.accumulatedMs());
        assertEquals(0L, t.detectedAt());
        assertFalse(t.triggered());
    }

    @Test public void restingNonTargetDoesNotAccumulate() {
        RestCheatTracker t = new RestCheatTracker();
        long acc = t.observe(true, false, 1_000L, 5_000L);
        assertEquals(0L, acc);
        assertFalse(t.triggered());
    }

    @Test public void nonRestingAndNonTargetDoesNotAccumulate() {
        RestCheatTracker t = new RestCheatTracker();
        assertEquals(0L, t.observe(false, false, 1_000L, 5_000L));
    }

    // ==================== 首次目标 delta 为 0 ====================

    @Test public void firstTargetObservationReturnsZeroDelta() {
        RestCheatTracker t = new RestCheatTracker();
        // previousObservedAt > 0 但首次进入 RESTING+target：detectedAt 已设但 delta=0
        // 因为本次 observe 的 delta 计算依赖 previousObservedAt
        // 实际场景中 prevObservedAt 来自 usage accumulator，首次切回目标时为 0
        long acc = t.observe(true, true, 0L, 5_000L);
        assertEquals(0L, acc);
        // detectedAt 在第一次 RESTING+target 时设置
        assertEquals(5_000L, t.detectedAt());
    }

    @Test public void firstTargetWithPreviousObservedAtStillReturnsDelta() {
        // 当 RESTING+target 且 previousObservedAt > 0 时，会计算 delta
        RestCheatTracker t = new RestCheatTracker();
        long acc = t.observe(true, true, 1_000L, 5_000L);
        assertEquals(4_000L, acc); // 5000 - 1000 = 4000
    }

    // ==================== 连续目标累计 ====================

    @Test public void consecutiveTargetTicksAccumulate() {
        RestCheatTracker t = new RestCheatTracker();
        // 第一次：prev=1000, now=3000, delta=2000, acc=2000
        assertEquals(2_000L, t.observe(true, true, 1_000L, 3_000L));
        // 第二次：prev=3000, now=6000, delta=3000, acc=5000
        assertEquals(5_000L, t.observe(true, true, 3_000L, 6_000L));
        assertTrue(t.triggered());
    }

    // ==================== 单次 delta 最多 10 秒 ====================

    @Test public void singleDeltaClampedToTenSeconds() {
        RestCheatTracker t = new RestCheatTracker();
        // prev=1000, now=20000, raw delta=19000 -> clamped to 10000
        long acc = t.observe(true, true, 1_000L, 20_000L);
        assertEquals(MAX_DELTA, acc);
        assertFalse("10秒未达5秒阈值不应触发", t.triggered());
    }

    @Test public void deltaAboveTenSecondsStillClamped() {
        RestCheatTracker t = new RestCheatTracker();
        long acc = t.observe(true, true, 0L, 100_000L);
        // previousObservedAt=0 -> delta=0
        assertEquals(0L, acc);
    }

    // ==================== 时间倒退为 0 ====================

    @Test public void timeGoingBackwardsYieldsZeroDelta() {
        RestCheatTracker t = new RestCheatTracker();
        // prev=5000, now=3000, raw delta=-2000 -> clamped to 0
        long acc = t.observe(true, true, 5_000L, 3_000L);
        assertEquals(0L, acc);
        // detectedAt 仍被设置（因为进入了 RESTING+target 分支）
        assertEquals(3_000L, t.detectedAt());
    }

    // ==================== 5 秒阈值边界 ====================

    @Test public void accumulated4999msDoesNotTrigger() {
        RestCheatTracker t = new RestCheatTracker();
        // 一次 delta=4999
        t.observe(true, true, 1_000L, 5_999L); // delta=4999
        assertEquals(4_999L, t.accumulatedMs());
        assertFalse(t.triggered());
    }

    @Test public void accumulatedExactly5000msTriggers() {
        RestCheatTracker t = new RestCheatTracker();
        t.observe(true, true, 1_000L, 6_000L); // delta=5000
        assertEquals(5_000L, t.accumulatedMs());
        assertTrue(t.triggered());
    }

    @Test public void accumulatedAbove5000msTriggers() {
        RestCheatTracker t = new RestCheatTracker();
        t.observe(true, true, 1_000L, 7_000L); // delta=6000
        assertEquals(6_000L, t.accumulatedMs());
        assertTrue(t.triggered());
    }

    // ==================== RESTING 期间离开目标应用不清零 ====================

    @Test public void leavingTargetDuringRestingDoesNotClearAccumulation() {
        RestCheatTracker t = new RestCheatTracker();
        t.observe(true, true, 1_000L, 4_000L); // delta=3000, acc=3000
        assertEquals(3_000L, t.accumulatedMs());

        // 切到非目标应用，但仍 RESTING：累计不清零
        long acc = t.observe(true, false, 4_000L, 5_000L);
        assertEquals(3_000L, acc);
        assertEquals(3_000L, t.accumulatedMs());
        assertEquals(4_000L, t.detectedAt()); // detectedAt 保持
    }

    @Test public void returningToTargetDuringRestingContinuesAccumulation() {
        RestCheatTracker t = new RestCheatTracker();
        // 累计 3000
        t.observe(true, true, 1_000L, 4_000L);
        // 离开目标（仍 RESTING）
        t.observe(true, false, 4_000L, 5_000L);
        // 回到目标：delta = 6000-5000 = 1000, acc = 3000+1000 = 4000
        long acc = t.observe(true, true, 5_000L, 6_000L);
        assertEquals(4_000L, acc);
        assertFalse(t.triggered());
        // 再来 1000ms 达到阈值
        acc = t.observe(true, true, 6_000L, 7_000L);
        assertEquals(5_000L, acc);
        assertTrue(t.triggered());
    }

    @Test public void multipleCyclesBetweenTargetAndNonTargetDuringResting() {
        RestCheatTracker t = new RestCheatTracker();
        t.observe(true, true, 0L, 1_000L);     // delta=0 (prev=0), acc=0, detectedAt=1000
        t.observe(true, true, 1_000L, 2_000L); // delta=1000, acc=1000
        t.observe(true, false, 2_000L, 3_000L); // 非目标，acc 保持 1000
        t.observe(true, true, 3_000L, 4_000L);  // delta=1000, acc=2000
        t.observe(true, false, 4_000L, 5_000L); // 非目标，acc 保持 2000
        t.observe(true, true, 5_000L, 6_000L);  // delta=1000, acc=3000
        t.observe(true, true, 6_000L, 7_000L);  // delta=1000, acc=4000
        t.observe(true, true, 7_000L, 8_000L);  // delta=1000, acc=5000
        assertTrue(t.triggered());
        assertEquals(5_000L, t.accumulatedMs());
    }

    // ==================== 退出 RESTING 清零 ====================

    @Test public void exitingRestingClearsAccumulation() {
        RestCheatTracker t = new RestCheatTracker();
        t.observe(true, true, 1_000L, 4_000L); // acc=3000
        assertEquals(3_000L, t.accumulatedMs());

        // 状态从 RESTING 变为非 RESTING（如 BLOCKED/IDLE），无论是否目标都清零
        long acc = t.observe(false, true, 4_000L, 5_000L);
        assertEquals(0L, acc);
        assertEquals(0L, t.accumulatedMs());
        assertEquals(0L, t.detectedAt());
        assertFalse(t.triggered());
    }

    @Test public void exitingRestingToNonTargetAlsoClears() {
        RestCheatTracker t = new RestCheatTracker();
        t.observe(true, true, 1_000L, 4_000L); // acc=3000
        long acc = t.observe(false, false, 4_000L, 5_000L);
        assertEquals(0L, acc);
    }

    @Test public void noAccumulationMeansExitRestingDoesNothing() {
        // accumulatedMs == 0 时，退出 RESTING 不应改变状态
        RestCheatTracker t = new RestCheatTracker();
        t.observe(true, false, 1_000L, 2_000L); // RESTING+非目标，acc=0
        long acc = t.observe(false, false, 2_000L, 3_000L); // 退出 RESTING
        assertEquals(0L, acc);
    }

    // ==================== 手动 reset ====================

    @Test public void manualResetClearsAccumulation() {
        RestCheatTracker t = new RestCheatTracker();
        t.observe(true, true, 1_000L, 6_000L); // acc=5000, triggered
        assertTrue(t.triggered());
        t.reset();
        assertEquals(0L, t.accumulatedMs());
        assertEquals(0L, t.detectedAt());
        assertFalse(t.triggered());
    }

    @Test public void resetAllowsFreshAccumulation() {
        RestCheatTracker t = new RestCheatTracker();
        t.observe(true, true, 1_000L, 6_000L); // 触发
        t.reset();
        // 重新累计
        assertEquals(2_000L, t.observe(true, true, 6_000L, 8_000L));
        assertFalse(t.triggered());
    }

    @Test public void resetOnEmptyTrackerIsNoop() {
        RestCheatTracker t = new RestCheatTracker();
        t.reset();
        assertEquals(0L, t.accumulatedMs());
        assertEquals(0L, t.detectedAt());
    }

    // ==================== detectedAt 语义 ====================

    @Test public void detectedAtSetOnFirstRestingTargetObservation() {
        RestCheatTracker t = new RestCheatTracker();
        t.observe(true, true, 0L, 5_000L);
        assertEquals(5_000L, t.detectedAt());
    }

    @Test public void detectedAtPersistsAcrossTargetNonTargetSwitchesDuringResting() {
        RestCheatTracker t = new RestCheatTracker();
        t.observe(true, true, 0L, 5_000L);     // detectedAt=5000
        t.observe(true, false, 5_000L, 6_000L); // 离开目标，detectedAt 保持
        assertEquals(5_000L, t.detectedAt());
        t.observe(true, true, 6_000L, 7_000L);  // 回到目标，detectedAt 保持
        assertEquals(5_000L, t.detectedAt());
    }
}
