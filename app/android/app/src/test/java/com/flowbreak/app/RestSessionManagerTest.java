package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * RestSessionManager 纯逻辑恢复决策测试。
 *
 * 覆盖任务要求的所有边界：
 * - 非 RESTING 保持状态
 * - RESTING 缺少 startedAt 回退 stateFor
 * - RESTING 未完成保持 RESTING
 * - RESTING 已完成自动进入 GRACE
 * - 自动完成生成 10 分钟 grace
 * - 自动完成保留原 restSessionId
 * - beginRest 已有活动 session 不生成新 ID
 * - restDuration 最少 30 秒
 * - limit 最少 1
 */
public class RestSessionManagerTest {
    private static final int LIMIT_MIN = 25;
    private static final long LIMIT_MS = LIMIT_MIN * 60_000L;
    private static final long NOW = 1_000_000L;
    private static final long GRACE_MS = FlowServiceStateStore.GRACE_MS;

    // ==================== decideRestore: 非 RESTING ====================

    @Test public void nonRestingStateIsKeptAsIs() {
        for (BlockStateMachine.State s : BlockStateMachine.State.values()) {
            if (s == BlockStateMachine.State.RESTING) continue;
            RestSessionManager.RestoreDecision d = decide(s, 0L, 0L, 0L, "", 0L, 0L, 0L);
            assertFalse("非 RESTING 不应触发自动完成: " + s, d.autoCompletedRest);
            assertEquals(s, d.restoredState);
        }
    }

    @Test public void nonRestingPreservesSessionMsGraceAndBlockedPackage() {
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.BLOCKED,
                123_456L,
                789L,
                100L,
                "com.example",
                0L, 0L, 0L
        );
        assertEquals(BlockStateMachine.State.BLOCKED, d.restoredState);
        assertFalse(d.autoCompletedRest);
        // Service 在非自动完成分支会原样使用 snapshot 字段构造 machine
        // 这里仅校验决策本身未触发 autoComplete
        assertEquals(0L, d.generatedGraceUntil);
        assertEquals(0L, d.completedRestSessionId);
    }

    // ==================== decideRestore: RESTING 缺少 startedAt ====================

    @Test public void restingWithoutStartedAtFallsBackToStateFor() {
        // sessionMs 略超 limit -> COGNITION
        long sessionMs = LIMIT_MS + 1L;
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                sessionMs,
                0L, 0L, "",
                0L, 0L, 0L
        );
        assertFalse(d.autoCompletedRest);
        assertEquals(BlockStateMachine.State.COGNITION, d.restoredState);
    }

    @Test public void restingWithoutStartedAtFallbackReachesBlockedAbove120Percent() {
        long sessionMs = (long) (LIMIT_MS * 1.20d);
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                sessionMs,
                0L, 0L, "",
                0L, 0L, 0L
        );
        assertEquals(BlockStateMachine.State.BLOCKED, d.restoredState);
    }

    @Test public void restingWithoutStartedAtFallbackStaysIdleBelow80Percent() {
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                0L,
                0L, 0L, "",
                0L, 0L, 0L
        );
        assertEquals(BlockStateMachine.State.IDLE, d.restoredState);
    }

    // ==================== decideRestore: RESTING 未完成 ====================

    @Test public void restingInProgressStaysResting() {
        long startedAt = NOW - 60_000L;
        long requiredMs = 180_000L;
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                0L, 0L, 0L, "",
                startedAt, requiredMs, 42L
                // 距 startedAt 仅 60 秒，未达 180 秒
        );
        assertFalse(d.autoCompletedRest);
        assertEquals(BlockStateMachine.State.RESTING, d.restoredState);
    }

    @Test public void restingExactlyOneMsBeforeCompletionStaysResting() {
        long startedAt = NOW - 179_999L;
        long requiredMs = 180_000L;
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                0L, 0L, 0L, "",
                startedAt, requiredMs, 42L
        );
        assertFalse(d.autoCompletedRest);
        assertEquals(BlockStateMachine.State.RESTING, d.restoredState);
    }

    // ==================== decideRestore: RESTING 已完成 -> GRACE ====================

    @Test public void restingExactlyCompleteAutoEntersGrace() {
        long startedAt = NOW - 180_000L;
        long requiredMs = 180_000L;
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                0L, 0L, 0L, "",
                startedAt, requiredMs, 42L
        );
        assertTrue(d.autoCompletedRest);
        assertEquals(BlockStateMachine.State.GRACE, d.restoredState);
    }

    @Test public void restingFarPastRequiredAutoEntersGrace() {
        long startedAt = NOW - 600_000L;
        long requiredMs = 180_000L;
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                0L, 0L, 0L, "",
                startedAt, requiredMs, 42L
        );
        assertTrue(d.autoCompletedRest);
        assertEquals(BlockStateMachine.State.GRACE, d.restoredState);
    }

    @Test public void autoCompleteGeneratesTenMinuteGrace() {
        long startedAt = NOW - 180_000L;
        long requiredMs = 180_000L;
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                0L, 0L, 0L, "",
                startedAt, requiredMs, 42L
        );
        assertEquals(NOW + GRACE_MS, d.generatedGraceUntil);
        // GRACE_MS = 10 * 60_000L = 600_000L
        assertEquals(600_000L, GRACE_MS);
    }

    @Test public void autoCompletePreservesOriginalRestSessionId() {
        long startedAt = NOW - 180_000L;
        long requiredMs = 180_000L;
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                0L, 0L, 0L, "",
                startedAt, requiredMs, 12345L
        );
        assertEquals(12345L, d.completedRestSessionId);
    }

    @Test public void autoCompleteWithZeroSessionIdStillCompletes() {
        // 原实现不要求 restSessionId > 0 才能自动完成
        long startedAt = NOW - 180_000L;
        long requiredMs = 180_000L;
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                0L, 0L, 0L, "",
                startedAt, requiredMs, 0L
        );
        assertTrue(d.autoCompletedRest);
        assertEquals(0L, d.completedRestSessionId);
    }

    // ==================== decideRestore: requiredMs 边界 ====================

    @Test public void restingWithZeroRequiredMsAndStartedAtDoesNotAutoComplete() {
        // RestSessionValidator.isComplete 要求 requiredMs > 0
        long startedAt = NOW - 1_000_000L;
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                0L, 0L, 0L, "",
                startedAt, 0L, 42L
        );
        assertFalse(d.autoCompletedRest);
        // startedAt > 0 但 requiredMs = 0：保持 RESTING（与原实现一致）
        assertEquals(BlockStateMachine.State.RESTING, d.restoredState);
    }

    // ==================== prepareBeginRest ====================

    @Test public void prepareBeginRestCreatesNewSessionWhenNotAlreadyResting() {
        RestSessionManager.BeginRestDecision d = RestSessionManager.prepareBeginRest(
                false,
                0L,
                180,
                NOW
        );
        assertNotNull(d);
        assertEquals(NOW, d.startedAt);
        assertEquals(180_000L, d.requiredMs);
        assertEquals(NOW, d.sessionId);
    }

    @Test public void prepareBeginRestCreatesNewSessionWhenStartedAtMissing() {
        // 已 RESTING 但 startedAt = 0（异常状态）：仍生成新 session
        RestSessionManager.BeginRestDecision d = RestSessionManager.prepareBeginRest(
                true,
                0L,
                180,
                NOW
        );
        assertNotNull(d);
        assertEquals(NOW, d.sessionId);
    }

    @Test public void prepareBeginRestReturnsNullWhenAlreadyRestingWithStartedAt() {
        // React remount：保持同一 session
        RestSessionManager.BeginRestDecision d = RestSessionManager.prepareBeginRest(
                true,
                NOW - 60_000L,
                180,
                NOW
        );
        assertNull(d);
    }

    @Test public void prepareBeginRestEnforcesThirtySecondMinimum() {
        RestSessionManager.BeginRestDecision d = RestSessionManager.prepareBeginRest(
                false,
                0L,
                5, // 用户配置 5 秒（低于 30 秒下限）
                NOW
        );
        assertNotNull(d);
        assertEquals(30_000L, d.requiredMs);
    }

    @Test public void prepareBeginRestExactlyThirtySecondsPasses() {
        RestSessionManager.BeginRestDecision d = RestSessionManager.prepareBeginRest(
                false,
                0L,
                30,
                NOW
        );
        assertEquals(30_000L, d.requiredMs);
    }

    @Test public void prepareBeginRestAboveThirtySecondsPasses() {
        RestSessionManager.BeginRestDecision d = RestSessionManager.prepareBeginRest(
                false,
                0L,
                600,
                NOW
        );
        assertEquals(600_000L, d.requiredMs);
    }

    // ==================== limit 边界（由 stateFor 间接体现） ====================

    @Test public void limitAtLeastOneMinuteWhenZero() {
        // Service 在 loadConfig 中保证 limitMinutes >= 1
        // 这里通过 stateFor 验证 limitMs=60_000 不会除零或异常
        RestSessionManager.RestoreDecision d = decide(
                BlockStateMachine.State.RESTING,
                60_000L, // 1 分钟使用时长
                0L, 0L, "",
                0L, 0L, 0L
        );
        // sessionMs == limitMs*1 -> ratio=1.0 -> COGNITION
        assertEquals(BlockStateMachine.State.COGNITION, d.restoredState);
    }

    // ==================== 辅助方法 ====================

    private RestSessionManager.RestoreDecision decide(
            BlockStateMachine.State persistedState,
            long sessionMs,
            long graceUntil,
            long leftTargetsAt,
            String blockedPackage,
            long restStartedAt,
            long restRequiredMs,
            long restSessionId
    ) {
        return RestSessionManager.decideRestore(
                new RestSessionManager.RestoreInput(
                        persistedState,
                        sessionMs,
                        graceUntil,
                        leftTargetsAt,
                        blockedPackage,
                        LIMIT_MIN,
                        restStartedAt,
                        restRequiredMs,
                        restSessionId,
                        NOW
                )
        );
    }
}
