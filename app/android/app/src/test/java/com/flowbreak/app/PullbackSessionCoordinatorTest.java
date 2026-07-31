package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PullbackSessionCoordinator 副作用顺序和持久化快照测试。
 *
 * 使用 fake sink 验证：
 * - 无 tracker 时 update 无副作用
 * - restore 后字段完整
 * - returnObservedNow 只记录一次
 * - resolvedNow 只记录一次
 * - 同一次 update 同时产生两个事件时，return 先于 outcome
 * - resolved 后重复 update 不重复记录
 * - clear 后 snapshot 为空
 * - snapshot 字段完整
 */
public class PullbackSessionCoordinatorTest {
    private static final long START = 10_000L;
    private static final long END = START + 600_000L; // 10 分钟窗口
    private static final long SESSION_ID = 7L;

    /** 记录 sink 调用顺序，便于断言 return 早于 outcome。 */
    private static final class FakeSink implements PullbackSessionCoordinator.OutcomeSink {
        final List<String> calls = new ArrayList<>();

        @Override public void recordPostRestReturn(long sessionId) {
            calls.add("return:" + sessionId);
        }

        @Override public void recordPullbackOutcome(boolean success, long targetSeconds, long sessionId) {
            calls.add("outcome:" + success + ":" + targetSeconds + ":" + sessionId);
        }
    }

    // ==================== 无 tracker ====================

    @Test public void updateWithoutTrackerIsNoop() {
        PullbackSessionCoordinator c = new PullbackSessionCoordinator();
        FakeSink sink = new FakeSink();
        PullbackSessionCoordinator.UpdateResult r = c.update(true, 5_000L, START + 60_000L, END, sink);
        assertFalse(r.returnObservedNow);
        assertFalse(r.resolvedNow);
        assertTrue(sink.calls.isEmpty());
        assertFalse(c.isActive());
        assertEquals(0L, c.currentSessionId());
    }

    @Test public void updateWithoutTrackerAndNullSinkIsNoop() {
        PullbackSessionCoordinator c = new PullbackSessionCoordinator();
        PullbackSessionCoordinator.UpdateResult r = c.update(true, 5_000L, START, END, null);
        assertFalse(r.returnObservedNow);
        assertFalse(r.resolvedNow);
    }

    @Test public void emptySnapshotWhenNoTracker() {
        PullbackSessionCoordinator c = new PullbackSessionCoordinator();
        PullbackSessionCoordinator.Snapshot s = c.snapshot();
        assertFalse(s.present);
        assertEquals(0L, s.sessionId);
    }

    // ==================== restore ====================

    @Test public void restoreWithValidSnapshotCreatesTracker() {
        PullbackSessionCoordinator c = new PullbackSessionCoordinator();
        c.restore(snapshot(true, SESSION_ID, START, 0L, 0L, false, false, false, false));
        assertTrue(c.isActive());
        assertEquals(SESSION_ID, c.currentSessionId());
    }

    @Test public void restoreWithZeroSessionIdClearsTracker() {
        PullbackSessionCoordinator c = new PullbackSessionCoordinator();
        c.restore(snapshot(true, SESSION_ID, START, 0L, 0L, false, false, false, false));
        assertTrue(c.isActive());
        // restore session=0 应清除
        c.restore(snapshot(true, 0L, START, 0L, 0L, false, false, false, false));
        assertFalse(c.isActive());
    }

    @Test public void restoreWithNotPresentSnapshotClearsTracker() {
        PullbackSessionCoordinator c = new PullbackSessionCoordinator();
        c.restore(snapshot(true, SESSION_ID, START, 0L, 0L, false, false, false, false));
        c.restore(PullbackSessionCoordinator.Snapshot.empty());
        assertFalse(c.isActive());
    }

    @Test public void restorePreservesAllFieldsInSnapshot() {
        PullbackSessionCoordinator c = new PullbackSessionCoordinator();
        PullbackSessionCoordinator.Snapshot input = snapshot(
                true, SESSION_ID, START, 12_000L, 34_000L, true, true, false, false
        );
        c.restore(input);
        PullbackSessionCoordinator.Snapshot s = c.snapshot();
        assertTrue(s.present);
        assertEquals(SESSION_ID, s.sessionId);
        assertEquals(START, s.startedAt);
        assertEquals(12_000L, s.targetMs);
        assertEquals(34_000L, s.leftTargetsAt);
        assertTrue(s.sawTarget);
        assertTrue(s.returnReported);
        assertFalse(s.resolved);
        assertFalse(s.success);
    }

    // ==================== return 事件 ====================

    @Test public void returnObservedNowRecordedOnce() {
        PullbackSessionCoordinator c = fresh();
        FakeSink sink = new FakeSink();

        // 第一次目标观察：触发 return
        PullbackSessionCoordinator.UpdateResult r1 = c.update(true, 4_000L, START + 20_000L, END, sink);
        assertTrue(r1.returnObservedNow);
        assertFalse(r1.resolvedNow);
        assertEquals(Arrays.asList("return:7"), sink.calls);

        // 第二次目标观察：不再触发 return
        PullbackSessionCoordinator.UpdateResult r2 = c.update(true, 6_000L, START + 26_000L, END, sink);
        assertFalse(r2.returnObservedNow);
        assertEquals(1, sink.calls.size()); // 仍只有一次 return
    }

    @Test public void returnNotRecordedWhenSinkNull() {
        PullbackSessionCoordinator c = fresh();
        // sink=null 但 returnObservedNow 仍应反映在 UpdateResult 中
        PullbackSessionCoordinator.UpdateResult r = c.update(true, 4_000L, START + 20_000L, END, null);
        assertTrue(r.returnObservedNow);
    }

    // ==================== outcome 事件 ====================

    @Test public void resolvedNowRecordedOnceOnSuccess() {
        PullbackSessionCoordinator c = fresh();
        FakeSink sink = new FakeSink();

        // 先触发 return
        c.update(true, 4_000L, START + 20_000L, END, sink);
        // 离开目标
        c.update(false, 0L, START + 30_000L, END, sink);
        // 30 秒后离开成功
        PullbackSessionCoordinator.UpdateResult r = c.update(false, 0L, START + 60_000L, END, sink);
        assertTrue(r.resolvedNow);
        assertTrue(r.success);
        assertEquals(4L, r.targetSeconds);

        // 验证 sink 顺序：return 先于 outcome
        assertEquals(Arrays.asList("return:7", "outcome:true:4:7"), sink.calls);

        // 再次 update：不重复记录
        sink.calls.clear();
        PullbackSessionCoordinator.UpdateResult r2 = c.update(false, 0L, START + 61_000L, END, sink);
        assertFalse(r2.returnObservedNow);
        assertFalse(r2.resolvedNow);
        assertTrue(sink.calls.isEmpty());
    }

    @Test public void resolvedOnWindowExpiryWithoutReturnIsSuccess() {
        PullbackSessionCoordinator c = fresh();
        FakeSink sink = new FakeSink();

        // 整个窗口期间都在非目标应用：到期时 resolved+success
        PullbackSessionCoordinator.UpdateResult r = c.update(false, 0L, END, END, sink);
        assertTrue(r.resolvedNow);
        assertTrue(r.success);
        assertFalse(r.returnObservedNow);
        assertEquals(0L, r.targetSeconds);
        assertEquals(Arrays.asList("outcome:true:0:7"), sink.calls);
    }

    @Test public void resolvedOnWindowExpiryWithTargetStaysIsFailure() {
        PullbackSessionCoordinator c = fresh();
        FakeSink sink = new FakeSink();

        // 一直在目标应用：到期时 resolved+!success
        c.update(true, 300_000L, START + 300_000L, END, sink); // return
        PullbackSessionCoordinator.UpdateResult r = c.update(true, 300_000L, END, END, sink);
        assertTrue(r.resolvedNow);
        assertFalse(r.success);
        assertEquals(600L, r.targetSeconds);

        // 顺序：return 先于 outcome
        assertEquals(Arrays.asList("return:7", "outcome:false:600:7"), sink.calls);
    }

    // ==================== 同一次 update 同时产生两个事件 ====================

    @Test public void returnAndOutcomeOnSameUpdateReturnFirst() {
        // 构造场景：首次进入目标，且恰好窗口到期
        // 此时 returnObservedNow 和 resolvedNow 都为 true
        // 必须保证 return 先于 outcome 调用 sink
        PullbackSessionCoordinator c = fresh();
        FakeSink sink = new FakeSink();

        // 第一次 update 在窗口结束时间，且为目标：return+resolved(失败)
        PullbackSessionCoordinator.UpdateResult r = c.update(true, 600_000L, END, END, sink);
        assertTrue(r.returnObservedNow);
        assertTrue(r.resolvedNow);
        assertFalse(r.success);

        // 验证顺序：return 先于 outcome
        assertEquals(2, sink.calls.size());
        assertEquals("return:7", sink.calls.get(0));
        assertEquals("outcome:false:600:7", sink.calls.get(1));
    }

    // ==================== clear ====================

    @Test public void clearRemovesTracker() {
        PullbackSessionCoordinator c = fresh();
        assertTrue(c.isActive());
        c.clear();
        assertFalse(c.isActive());
        assertEquals(0L, c.currentSessionId());
        PullbackSessionCoordinator.Snapshot s = c.snapshot();
        assertFalse(s.present);
    }

    @Test public void clearOnEmptyCoordinatorIsNoop() {
        PullbackSessionCoordinator c = new PullbackSessionCoordinator();
        c.clear();
        assertFalse(c.isActive());
    }

    @Test public void snapshotAfterClearIsEmpty() {
        PullbackSessionCoordinator c = fresh();
        c.clear();
        PullbackSessionCoordinator.Snapshot s = c.snapshot();
        assertFalse(s.present);
        assertEquals(0L, s.sessionId);
        assertEquals(0L, s.startedAt);
        assertEquals(0L, s.targetMs);
        assertFalse(s.sawTarget);
        assertFalse(s.resolved);
    }

    // ==================== snapshot 字段完整 ====================

    @Test public void snapshotReflectsTrackerState() {
        PullbackSessionCoordinator c = fresh();
        FakeSink sink = new FakeSink();
        c.update(true, 5_000L, START + 10_000L, END, sink); // return + targetMs=5000
        c.update(false, 0L, START + 20_000L, END, sink);    // leftTargetsAt=30000

        PullbackSessionCoordinator.Snapshot s = c.snapshot();
        assertTrue(s.present);
        assertEquals(SESSION_ID, s.sessionId);
        assertEquals(START, s.startedAt);
        assertEquals(5_000L, s.targetMs);
        assertEquals(START + 20_000L, s.leftTargetsAt);
        assertTrue(s.sawTarget);
        assertTrue(s.returnReported);
        assertFalse(s.resolved);
        assertFalse(s.success);
    }

    @Test public void snapshotReflectsResolvedState() {
        PullbackSessionCoordinator c = fresh();
        FakeSink sink = new FakeSink();
        c.update(true, 5_000L, START + 10_000L, END, sink);
        c.update(false, 0L, START + 20_000L, END, sink);
        c.update(false, 0L, START + 60_000L, END, sink); // resolved+success

        PullbackSessionCoordinator.Snapshot s = c.snapshot();
        assertTrue(s.present);
        assertTrue(s.resolved);
        assertTrue(s.success);
        assertEquals(5_000L, s.targetMs);
    }

    // ==================== 辅助方法 ====================

    private PullbackSessionCoordinator fresh() {
        PullbackSessionCoordinator c = new PullbackSessionCoordinator();
        c.restore(snapshot(true, SESSION_ID, START, 0L, 0L, false, false, false, false));
        return c;
    }

    private static PullbackSessionCoordinator.Snapshot snapshot(
            boolean present,
            long sessionId,
            long startedAt,
            long targetMs,
            long leftTargetsAt,
            boolean sawTarget,
            boolean returnReported,
            boolean resolved,
            boolean success
    ) {
        return new PullbackSessionCoordinator.Snapshot(
                present, sessionId, startedAt, targetMs, leftTargetsAt,
                sawTarget, returnReported, resolved, success
        );
    }
}
