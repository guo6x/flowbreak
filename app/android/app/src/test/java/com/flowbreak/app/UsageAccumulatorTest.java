package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageAccumulatorTest {
    private static final String PKG_A = "com.example.a";
    private static final String PKG_B = "com.example.b";

    /** 记录 sink 调用，便于断言写入次数与秒数。 */
    private static final class FakeSink implements UsageAccumulator.UsageSink {
        final List<String> calls = new ArrayList<>();

        @Override public void addUsage(String packageName, long seconds) {
            calls.add(packageName + ":" + seconds);
        }
    }

    // ==================== observe ====================

    @Test public void firstObservationOfTargetReturnsZero() {
        UsageAccumulator acc = new UsageAccumulator();
        // 首次观察目标应用：lastObservedTargetPackage 为空，continuedTarget=false
        assertEquals(0L, acc.observe(true, PKG_A, 1_000L));
    }

    @Test public void consecutiveTargetObservationsAccumulateDelta() {
        UsageAccumulator acc = new UsageAccumulator();
        acc.observe(true, PKG_A, 1_000L);
        // 第二次观察，delta = 2000 - 1000 = 1000ms
        assertEquals(1_000L, acc.observe(true, PKG_A, 3_000L));
        // 第三次，delta = 2000ms
        assertEquals(2_000L, acc.observe(true, PKG_A, 5_000L));
    }

    @Test public void switchingFromTargetAToTargetBStillAccumulatesDelta() {
        // 当前逻辑不要求前后是同一个目标包，只要前一次也是目标包，就会累计 delta
        UsageAccumulator acc = new UsageAccumulator();
        acc.observe(true, PKG_A, 1_000L);
        assertEquals(2_000L, acc.observe(true, PKG_B, 3_000L));
    }

    @Test public void switchingFromTargetToNonTargetReturnsZeroAndClearsContinuation() {
        UsageAccumulator acc = new UsageAccumulator();
        acc.observe(true, PKG_A, 1_000L);
        // 切到非目标：返回 0 并清空 lastObservedTargetPackage
        assertEquals(0L, acc.observe(false, "com.other", 3_000L));
        assertEquals("", acc.getLastObservedTargetPackage());
    }

    @Test public void switchingFromNonTargetBackToTargetReturnsZeroFirstTime() {
        // 非目标切回目标，首次返回 0（因为 lastObservedTargetPackage 已被清空）
        UsageAccumulator acc = new UsageAccumulator();
        acc.observe(true, PKG_A, 1_000L);
        acc.observe(false, "com.other", 3_000L);
        assertEquals(0L, acc.observe(true, PKG_A, 5_000L));
        // 再次观察才累计
        assertEquals(2_000L, acc.observe(true, PKG_A, 7_000L));
    }

    @Test public void deltaClampedToTenSeconds() {
        UsageAccumulator acc = new UsageAccumulator();
        acc.observe(true, PKG_A, 1_000L);
        // 间隔 15 秒，但 delta 被 clamp 到 10_000ms
        assertEquals(10_000L, acc.observe(true, PKG_A, 16_000L));
    }

    @Test public void timeGoingBackwardsReturnsZero() {
        UsageAccumulator acc = new UsageAccumulator();
        acc.observe(true, PKG_A, 5_000L);
        // 时间倒退：now - lastObservedAt < 0，被 clamp 到 0
        assertEquals(0L, acc.observe(true, PKG_A, 3_000L));
    }

    @Test public void resetObservationMakesNextTargetReturnZero() {
        UsageAccumulator acc = new UsageAccumulator();
        acc.observe(true, PKG_A, 1_000L);
        acc.observe(true, PKG_A, 3_000L);
        acc.resetObservation(10_000L);
        // reset 后 lastObservedTargetPackage 被清空，首次目标返回 0
        assertEquals(0L, acc.observe(true, PKG_A, 12_000L));
        assertEquals(2_000L, acc.observe(true, PKG_A, 14_000L));
    }

    @Test public void nullPackageOnTargetBehavesConsistently() {
        // isTarget=true 但 packageName=null：lastObservedTargetPackage 设为空字符串，
        // 与原实现一致（acc.isTarget && packageName != null ? packageName : ""）
        UsageAccumulator acc = new UsageAccumulator();
        acc.observe(true, PKG_A, 1_000L);
        acc.observe(true, null, 3_000L);
        // lastObservedTargetPackage 被清空，下一次目标观察应返回 0
        assertEquals("", acc.getLastObservedTargetPackage());
        assertEquals(0L, acc.observe(true, PKG_A, 5_000L));
    }

    // ==================== flush ====================

    @Test public void flushDoesNotWriteBeforeFifteenSecondsWithoutForce() {
        UsageAccumulator acc = new UsageAccumulator();
        FakeSink sink = new FakeSink();
        acc.queue(PKG_A, 3_000L);
        acc.flush(false, 1_000L, false, sink);
        assertTrue("未满 15 秒不应写入", sink.calls.isEmpty());
    }

    @Test public void forceFlushWritesImmediately() {
        UsageAccumulator acc = new UsageAccumulator();
        FakeSink sink = new FakeSink();
        acc.queue(PKG_A, 3_000L);
        acc.flush(true, 1_000L, false, sink);
        assertEquals(Arrays.asList(PKG_A + ":3"), sink.calls);
    }

    @Test public void flushWritesOnlyCompleteSecondsAndKeepsRemainder() {
        UsageAccumulator acc = new UsageAccumulator();
        FakeSink sink = new FakeSink();
        // 1500ms 只写 1 秒，保留 500ms
        acc.queue(PKG_A, 1_500L);
        acc.flush(true, 1_000L, false, sink);
        assertEquals(Arrays.asList(PKG_A + ":1"), sink.calls);
        Map<String, Long> snap = acc.pendingSnapshot();
        assertEquals(Long.valueOf(500L), snap.get(PKG_A));
    }

    @Test public void subsequentAccumulationWithRemainderWritesAnotherSecond() {
        UsageAccumulator acc = new UsageAccumulator();
        FakeSink sink = new FakeSink();
        acc.queue(PKG_A, 1_500L); // 余 500
        acc.flush(true, 1_000L, false, sink);
        sink.calls.clear();
        // 再加 500ms，凑成 1000ms，可再写 1 秒
        acc.queue(PKG_A, 500L);
        acc.flush(true, 2_000L, false, sink);
        assertEquals(Arrays.asList(PKG_A + ":1"), sink.calls);
        // 余数应为 0
        Map<String, Long> snap = acc.pendingSnapshot();
        assertEquals(Long.valueOf(0L), snap.get(PKG_A));
    }

    @Test public void flushWritesMultiplePackagesSeparately() {
        UsageAccumulator acc = new UsageAccumulator();
        FakeSink sink = new FakeSink();
        acc.queue(PKG_A, 2_500L);
        acc.queue(PKG_B, 1_200L);
        acc.flush(true, 1_000L, false, sink);
        // A 写 2 秒余 500，B 写 1 秒余 200
        assertTrue(sink.calls.contains(PKG_A + ":2"));
        assertTrue(sink.calls.contains(PKG_B + ":1"));
    }

    @Test public void flushIgnoresNonPositiveDuration() {
        UsageAccumulator acc = new UsageAccumulator();
        FakeSink sink = new FakeSink();
        // queue 时已经过滤非正时长
        acc.queue(PKG_A, 0L);
        acc.queue(PKG_A, -100L);
        acc.flush(true, 1_000L, false, sink);
        assertTrue(sink.calls.isEmpty());
    }

    @Test public void dataErasingClearsPendingAndDoesNotWrite() {
        UsageAccumulator acc = new UsageAccumulator();
        FakeSink sink = new FakeSink();
        acc.queue(PKG_A, 5_000L);
        acc.flush(true, 1_000L, true, sink);
        assertTrue(sink.calls.isEmpty());
        assertTrue(acc.pendingSnapshot().isEmpty());
    }

    @Test public void flushDoesNotWriteWhenPendingBelowOneSecond() {
        UsageAccumulator acc = new UsageAccumulator();
        FakeSink sink = new FakeSink();
        acc.queue(PKG_A, 500L);
        acc.flush(true, 1_000L, false, sink);
        assertTrue(sink.calls.isEmpty());
        // 余数保留
        assertEquals(Long.valueOf(500L), acc.pendingSnapshot().get(PKG_A));
    }

    @Test public void repeatedFlushDoesNotDuplicateCommittedSeconds() {
        UsageAccumulator acc = new UsageAccumulator();
        FakeSink sink = new FakeSink();
        acc.queue(PKG_A, 3_000L);
        acc.flush(true, 1_000L, false, sink);
        // 再次 force flush：余数为 0，不应再写
        sink.calls.clear();
        acc.flush(true, 2_000L, false, sink);
        assertTrue(sink.calls.isEmpty());
    }

    @Test public void queueIgnoresNullOrEmptyPackage() {
        UsageAccumulator acc = new UsageAccumulator();
        FakeSink sink = new FakeSink();
        acc.queue(null, 1_000L);
        acc.queue("", 1_000L);
        acc.flush(true, 1_000L, false, sink);
        assertTrue(sink.calls.isEmpty());
    }

    @Test public void flushAfterFifteenSecondIntervalWrites() {
        UsageAccumulator acc = new UsageAccumulator();
        FakeSink sink = new FakeSink();
        acc.queue(PKG_A, 3_000L);
        // 正好 15 秒后，非 force 也应写入
        acc.flush(false, 16_000L, false, sink);
        assertEquals(Arrays.asList(PKG_A + ":3"), sink.calls);
    }
}
