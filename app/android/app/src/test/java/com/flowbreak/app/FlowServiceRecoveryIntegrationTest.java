package com.flowbreak.app;

import static org.junit.Assert.*;

import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * 前台服务进程死亡恢复逻辑集成测试。
 *
 * 每个测试模拟 logical restart：
 * 1) 将状态写入真实 SharedPreferences
 * 2) 丢弃原 stateStore / machine / coordinator 对象
 * 3) 创建全新的 FlowServiceStateStore
 * 4) 调用 FlowServiceRecoveryCoordinator.restore()
 * 5) 验证恢复结果
 *
 * 验证的是持久化状态的重建逻辑，不替代真机进程终止测试。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class FlowServiceRecoveryIntegrationTest {
    private static final long NOW = 2_000_000_000L;
    private static final long GRACE_MS = FlowServiceStateStore.GRACE_MS; // 600_000L
    private static final int LIMIT_MINUTES = 25;
    private static final long LIMIT_MS = LIMIT_MINUTES * 60_000L; // 1_500_000L

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences(FlowServiceStateStore.PREFS, 0);
        prefs.edit().clear().commit();
    }

    @After
    public void tearDown() {
        prefs.edit().clear().commit();
    }

    // ==================== 1. BLOCKED 完整恢复 ====================

    @Test
    public void blockedRestoresWithAllFields() {
        Set<String> targetApps = setOf("com.example.video");
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.BLOCKED.name())
                .putLong("sessionMs", 1_900_000L)
                .putLong("graceUntil", 0L)
                .putLong("leftTargetsAt", 123_456L)
                .putString("blockedPackage", "com.example.video")
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, targetApps)
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        // 丢弃旧对象，创建新 StateStore
        FlowServiceStateStore newStore = new FlowServiceStateStore(
                ApplicationProvider.getApplicationContext());
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertEquals(BlockStateMachine.State.BLOCKED, result.machine.getState());
        assertEquals(1_900_000L, result.machine.getSessionMs());
        assertEquals(123_456L, result.machine.getLeftTargetsAt());
        assertEquals("com.example.video", result.machine.getBlockedPackage());
        assertEquals(25, result.config.limitMinutes);
        assertEquals(targetApps, result.config.targetApps);
        assertFalse(result.autoCompletedRest);
    }

    // ==================== 2. 所有非 RESTING 状态字段保持 ====================

    @Test
    public void idleRestoresFieldsIntact() {
        assertNonRestingFieldsIntact(BlockStateMachine.State.IDLE, "idle", 0L);
    }

    @Test
    public void perceptionRestoresFieldsIntact() {
        assertNonRestingFieldsIntact(BlockStateMachine.State.PERCEPTION, "perception", 1_300_000L);
    }

    @Test
    public void cognitionRestoresFieldsIntact() {
        assertNonRestingFieldsIntact(BlockStateMachine.State.COGNITION, "cognition", 1_600_000L);
    }

    @Test
    public void blockedRestoresFieldsIntact() {
        assertNonRestingFieldsIntact(BlockStateMachine.State.BLOCKED, "blocked", 1_900_000L);
    }

    @Test
    public void graceRestoresFieldsIntact() {
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.GRACE.name())
                .putLong("sessionMs", 0L)
                .putLong("graceUntil", NOW + 300_000L)
                .putLong("leftTargetsAt", 42L)
                .putString("blockedPackage", "")
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertEquals(BlockStateMachine.State.GRACE, result.machine.getState());
        assertEquals(0L, result.machine.getSessionMs());
        assertEquals(NOW + 300_000L, result.machine.getGraceUntil());
        assertEquals(42L, result.machine.getLeftTargetsAt());
        assertEquals("", result.machine.getBlockedPackage());
        assertFalse(result.autoCompletedRest);
    }

    private void assertNonRestingFieldsIntact(
            BlockStateMachine.State state, String blockedPackage, long sessionMs) {
        prefs.edit()
                .putString("blockState", state.name())
                .putLong("sessionMs", sessionMs)
                .putLong("graceUntil", 0L)
                .putLong("leftTargetsAt", 42L)
                .putString("blockedPackage", blockedPackage)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertEquals(state, result.machine.getState());
        assertEquals(sessionMs, result.machine.getSessionMs());
        assertEquals(42L, result.machine.getLeftTargetsAt());
        assertEquals(blockedPackage, result.machine.getBlockedPackage());
        assertFalse(result.autoCompletedRest);
    }

    // ==================== 3. 非法 blockState → IDLE ====================

    @Test
    public void invalidBlockStateFallsBackToIdle() {
        prefs.edit()
                .putString("blockState", "NOT_A_REAL_STATE")
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertEquals(BlockStateMachine.State.IDLE, result.machine.getState());
        assertEquals(0L, result.machine.getSessionMs());
        assertFalse(result.autoCompletedRest);
    }

    // ==================== 4. RESTING 未完成 ====================

    @Test
    public void restingInProgressStaysResting() {
        long startedAt = NOW - 60_000L;
        long requiredMs = 180_000L;
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong("sessionMs", 500_000L)
                .putLong("restStartedAt", startedAt)
                .putLong("restRequiredMs", requiredMs)
                .putLong("restSessionId", 1001L)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertEquals(BlockStateMachine.State.RESTING, result.machine.getState());
        assertFalse(result.autoCompletedRest);

        // 三个 active rest 字段仍存在且值不变
        assertEquals(startedAt, newStore.preferences().getLong(
                FlowForegroundService.PREF_REST_STARTED_AT, 0L));
        assertEquals(requiredMs, newStore.preferences().getLong(
                FlowForegroundService.PREF_REST_REQUIRED_MS, 0L));
        assertEquals(1001L, newStore.preferences().getLong(
                FlowForegroundService.PREF_REST_SESSION_ID, 0L));
    }

    // ==================== 5. RESTING 恰好完成 → GRACE ====================

    @Test
    public void restingExactlyCompleteAutoEntersGrace() {
        long startedAt = NOW - 180_000L;
        long requiredMs = 180_000L;
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong("sessionMs", 1L)
                .putString("blockedPackage", "com.example.video")
                .putLong("restStartedAt", startedAt)
                .putLong("restRequiredMs", requiredMs)
                .putLong("restSessionId", 2002L)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertTrue(result.autoCompletedRest);
        assertEquals(BlockStateMachine.State.GRACE, result.machine.getState());
        assertEquals(0L, result.machine.getSessionMs());
        assertEquals("", result.machine.getBlockedPackage());
        assertEquals(NOW + GRACE_MS, result.machine.getGraceUntil());

        // SharedPreferences 断言
        SharedPreferences sp = newStore.preferences();
        assertEquals(BlockStateMachine.State.GRACE.name(),
                sp.getString("blockState", ""));
        assertEquals(0L, sp.getLong("sessionMs", -1L));
        assertEquals("", sp.getString("blockedPackage", "X"));
        assertEquals(NOW + GRACE_MS, sp.getLong("graceUntil", 0L));
        assertFalse(sp.contains(FlowForegroundService.PREF_REST_STARTED_AT));
        assertFalse(sp.contains(FlowForegroundService.PREF_REST_REQUIRED_MS));
        assertEquals(2002L, sp.getLong(FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID, 0L));
        assertEquals(NOW + GRACE_MS, sp.getLong(
                FlowForegroundService.PREF_COMPLETED_REST_GRACE_UNTIL, 0L));
    }

    // ==================== 6. 第二次重启不能重复完成休息 ====================

    @Test
    public void secondRestartDoesNotRecompleteRest() {
        long startedAt = NOW - 180_000L;
        long requiredMs = 180_000L;
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong("sessionMs", 1L)
                .putString("blockedPackage", "com.example.video")
                .putLong("restStartedAt", startedAt)
                .putLong("restRequiredMs", requiredMs)
                .putLong("restSessionId", 3003L)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        // 第一次恢复
        FlowServiceStateStore store1 = newStore();
        FlowServiceRecoveryCoordinator.Result result1 =
                FlowServiceRecoveryCoordinator.restore(store1, NOW);
        assertTrue(result1.autoCompletedRest);
        assertEquals(BlockStateMachine.State.GRACE, result1.machine.getState());
        long firstGraceUntil = result1.machine.getGraceUntil();
        long firstCompletedId = store1.preferences().getLong(
                FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID, 0L);
        long firstCompletedGrace = store1.preferences().getLong(
                FlowForegroundService.PREF_COMPLETED_REST_GRACE_UNTIL, 0L);

        // 丢弃所有对象
        store1 = null;
        result1 = null;

        // 第二次恢复（时间推进 30 秒）
        long later = NOW + 30_000L;
        FlowServiceStateStore store2 = newStore();
        FlowServiceRecoveryCoordinator.Result result2 =
                FlowServiceRecoveryCoordinator.restore(store2, later);

        // 不应再次自动完成
        assertFalse("第二次启动不应再次自动完成休息", result2.autoCompletedRest);
        assertEquals(BlockStateMachine.State.GRACE, result2.machine.getState());
        // graceUntil 保持第一次生成的值，不因时间推进而更新
        assertEquals(firstGraceUntil, result2.machine.getGraceUntil());
        // completed 字段不变
        assertEquals(firstCompletedId, store2.preferences().getLong(
                FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID, 0L));
        assertEquals(firstCompletedGrace, store2.preferences().getLong(
                FlowForegroundService.PREF_COMPLETED_REST_GRACE_UNTIL, 0L));
    }

    // ==================== 7. RESTING 缺少 startedAt → 回退 ====================

    @Test
    public void restingWithoutStartedAtFallsBackBelow80Percent() {
        // sessionMs=0 → ratio=0 → IDLE
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong("sessionMs", 0L)
                .putLong("restStartedAt", 0L)
                .putLong("restRequiredMs", 180_000L)
                .putLong("restSessionId", 0L)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertEquals(BlockStateMachine.State.IDLE, result.machine.getState());
        assertFalse(result.autoCompletedRest);
    }

    @Test
    public void restingWithoutStartedAtFallsBackToCognitionAt100Percent() {
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong("sessionMs", LIMIT_MS) // 刚好 100%
                .putLong("restStartedAt", 0L)
                .putLong("restRequiredMs", 180_000L)
                .putLong("restSessionId", 0L)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertEquals(BlockStateMachine.State.COGNITION, result.machine.getState());
        assertFalse(result.autoCompletedRest);
    }

    @Test
    public void restingWithoutStartedAtFallsBackToBlockedAt120Percent() {
        long sessionMs = (long) (LIMIT_MS * 1.20d);
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong("sessionMs", sessionMs)
                .putLong("restStartedAt", 0L)
                .putLong("restRequiredMs", 180_000L)
                .putLong("restSessionId", 0L)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertEquals(BlockStateMachine.State.BLOCKED, result.machine.getState());
        assertFalse(result.autoCompletedRest);
    }

    // ==================== 8. 配置重启恢复 ====================

    @Test
    public void configLoadsCorrectlyAfterRestart() {
        Set<String> apps = setOf("com.example.a", "com.example.b");
        prefs.edit()
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, apps)
                .putInt("limitMinutes", 0) // < 1 → clamp to 1
                .putBoolean("monitoringEnabled", false)
                .putInt("restDuration", 240)
                .putBoolean("allowEmergencyUnlock", false)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertEquals(apps, result.config.targetApps);
        assertEquals(1, result.config.limitMinutes); // clamped
        assertFalse(result.config.monitoringEnabled);
        assertEquals(240, result.config.restDurationSeconds);
        assertFalse(result.config.allowEmergencyUnlock);
    }

    // ==================== 9. pullback 完整恢复 ====================

    @Test
    public void pullbackSnapshotRestoresAllFields() {
        prefs.edit()
                .putLong(FlowForegroundService.PREF_PULLBACK_SESSION_ID, 3003L)
                .putLong(FlowForegroundService.PREF_PULLBACK_STARTED_AT, NOW - 10_000L)
                .putLong(FlowForegroundService.PREF_PULLBACK_TARGET_MS, 5_000L)
                .putLong(FlowForegroundService.PREF_PULLBACK_LEFT_AT, NOW + 20_000L)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_SAW_TARGET, true)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_RETURN_REPORTED, false)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_RESOLVED, false)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_SUCCESS, false)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertTrue(result.pullbackSnapshot.present);
        assertEquals(3003L, result.pullbackSnapshot.sessionId);
        assertEquals(NOW - 10_000L, result.pullbackSnapshot.startedAt);
        assertEquals(5_000L, result.pullbackSnapshot.targetMs);
        assertEquals(NOW + 20_000L, result.pullbackSnapshot.leftTargetsAt);
        assertTrue(result.pullbackSnapshot.sawTarget);
        assertFalse(result.pullbackSnapshot.returnReported);
        assertFalse(result.pullbackSnapshot.resolved);
        assertFalse(result.pullbackSnapshot.success);

        // 新 Coordinator 通过 restore(snapshot) 重建
        PullbackSessionCoordinator coordinator = new PullbackSessionCoordinator();
        coordinator.restore(result.pullbackSnapshot);
        assertTrue(coordinator.isActive());

        // snapshot() 输出完全相同
        PullbackSessionCoordinator.Snapshot reconstructed = coordinator.snapshot();
        assertEquals(result.pullbackSnapshot.sessionId, reconstructed.sessionId);
        assertEquals(result.pullbackSnapshot.startedAt, reconstructed.startedAt);
        assertEquals(result.pullbackSnapshot.targetMs, reconstructed.targetMs);
        assertEquals(result.pullbackSnapshot.leftTargetsAt, reconstructed.leftTargetsAt);
        assertEquals(result.pullbackSnapshot.sawTarget, reconstructed.sawTarget);
        assertEquals(result.pullbackSnapshot.returnReported, reconstructed.returnReported);
        assertEquals(result.pullbackSnapshot.resolved, reconstructed.resolved);
        assertEquals(result.pullbackSnapshot.success, reconstructed.success);
    }

    // ==================== 10. 空 pullback 不能生成 tracker ====================

    @Test
    public void emptyPullbackDoesNotCreateTracker() {
        prefs.edit()
                .putLong(FlowForegroundService.PREF_PULLBACK_SESSION_ID, 0L)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertFalse(result.pullbackSnapshot.present);
        assertEquals(0L, result.pullbackSnapshot.sessionId);

        PullbackSessionCoordinator coordinator = new PullbackSessionCoordinator();
        coordinator.restore(result.pullbackSnapshot);
        assertFalse(coordinator.isActive());
    }

    @Test
    public void missingPullbackFieldsDoesNotCreateTracker() {
        // 没有写入任何 pullback 字段
        prefs.edit()
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        assertFalse(result.pullbackSnapshot.present);
    }

    // ==================== 11. 已上报 return 不能重复上报 ====================

    @Test
    public void returnReportedDoesNotFireAgainAfterRestart() {
        prefs.edit()
                .putLong(FlowForegroundService.PREF_PULLBACK_SESSION_ID, 4004L)
                .putLong(FlowForegroundService.PREF_PULLBACK_STARTED_AT, NOW - 10_000L)
                .putLong(FlowForegroundService.PREF_PULLBACK_TARGET_MS, 3_000L)
                .putLong(FlowForegroundService.PREF_PULLBACK_LEFT_AT, 0L)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_SAW_TARGET, true)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_RETURN_REPORTED, true)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_RESOLVED, false)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_SUCCESS, false)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        PullbackSessionCoordinator coordinator = new PullbackSessionCoordinator();
        coordinator.restore(result.pullbackSnapshot);
        assertTrue(coordinator.isActive());

        FakePullbackSink sink = new FakePullbackSink();
        // 再次 update（仍在目标应用）— return 应该已上报过
        PullbackSessionCoordinator.UpdateResult ur = coordinator.update(
                true, 2_000L, NOW + 35_000L, NOW + GRACE_MS, sink);

        assertFalse(ur.returnObservedNow);
        assertEquals(0, sink.returnCallCount);
    }

    // ==================== 12. 已 resolved 不能重复上报 outcome ====================

    @Test
    public void resolvedDoesNotFireAgainAfterRestart() {
        prefs.edit()
                .putLong(FlowForegroundService.PREF_PULLBACK_SESSION_ID, 5005L)
                .putLong(FlowForegroundService.PREF_PULLBACK_STARTED_AT, NOW - 10_000L)
                .putLong(FlowForegroundService.PREF_PULLBACK_TARGET_MS, 8_000L)
                .putLong(FlowForegroundService.PREF_PULLBACK_LEFT_AT, NOW + 40_000L)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_SAW_TARGET, true)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_RETURN_REPORTED, true)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_RESOLVED, true)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_SUCCESS, true)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore newStore = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore, NOW);

        PullbackSessionCoordinator coordinator = new PullbackSessionCoordinator();
        coordinator.restore(result.pullbackSnapshot);

        FakePullbackSink sink = new FakePullbackSink();
        // 多次 update
        for (int i = 0; i < 5; i++) {
            coordinator.update(true, 1_000L, NOW + (i + 1) * 5_000L, NOW + GRACE_MS, sink);
        }

        assertEquals(0, sink.returnCallCount);
        assertEquals(0, sink.outcomeCallCount);
    }

    // ==================== 13. clear active rest 后重启不复活 ====================

    @Test
    public void clearActiveRestDoesNotResurrectFields() {
        // 写入完整 active rest 字段
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong("restStartedAt", NOW - 60_000L)
                .putLong("restRequiredMs", 180_000L)
                .putLong("restSessionId", 6006L)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        // 调用 clearActiveRestSession
        FlowServiceStateStore store1 = newStore();
        store1.clearActiveRestSession();

        // 创建新 StateStore
        FlowServiceStateStore store2 = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(store2, NOW);

        // startedAt/requiredMs/sessionId 不存在
        assertFalse(store2.preferences().contains(FlowForegroundService.PREF_REST_STARTED_AT));
        assertFalse(store2.preferences().contains(FlowForegroundService.PREF_REST_REQUIRED_MS));
        assertFalse(store2.preferences().contains(FlowForegroundService.PREF_REST_SESSION_ID));

        // blockState=RESTING 但 restStartedAt=0 → 回退 stateFor
        assertEquals(BlockStateMachine.State.IDLE, result.machine.getState());
        assertFalse(result.autoCompletedRest);
    }

    // ==================== 14. clear pullback 后重启不复活 ====================

    @Test
    public void clearPullbackDoesNotResurrectFields() {
        prefs.edit()
                .putLong(FlowForegroundService.PREF_PULLBACK_SESSION_ID, 7007L)
                .putLong(FlowForegroundService.PREF_PULLBACK_STARTED_AT, NOW - 10_000L)
                .putLong(FlowForegroundService.PREF_PULLBACK_TARGET_MS, 5_000L)
                .putBoolean(FlowForegroundService.PREF_PULLBACK_SAW_TARGET, true)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        // 调用 clearPullbackSession
        FlowServiceStateStore store1 = newStore();
        store1.clearPullbackSession();

        // 创建新 StateStore
        FlowServiceStateStore store2 = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(store2, NOW);

        assertFalse(result.pullbackSnapshot.present);
        assertEquals(0L, result.pullbackSnapshot.sessionId);

        PullbackSessionCoordinator coordinator = new PullbackSessionCoordinator();
        coordinator.restore(result.pullbackSnapshot);
        assertFalse(coordinator.isActive());
    }

    // ==================== 15. begin rest remount 幂等 ====================

    @Test
    public void beginRestIsIdempotentAfterRestart() {
        int restDuration = 180;
        long startedAt = NOW;
        long requiredMs = 180_000L;
        long sessionId = NOW;

        // persistBeginRest 写入
        prefs.edit()
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", restDuration)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();
        FlowServiceStateStore store1 = newStore();
        store1.persistBeginRest(startedAt, requiredMs, sessionId);

        // 丢弃对象
        store1 = null;

        // 新 StateStore 读取
        FlowServiceStateStore store2 = newStore();
        long restoredStartedAt = store2.preferences().getLong(
                FlowForegroundService.PREF_REST_STARTED_AT, 0L);

        // 再次调用 prepareBeginRest（模拟 React remount）
        RestSessionManager.BeginRestDecision d2 = RestSessionManager.prepareBeginRest(
                true, restoredStartedAt, restDuration, NOW + 5_000L);

        // 已有活动 session 时应返回 null
        assertNull("React remount 不应生成第二个 session", d2);
    }

    // ==================== 16. machine 与 pullback 联合持久化 ====================

    @Test
    public void persistMachineAndPullbackRoundTripsAllFields() {
        // 构造非默认 machine 字段
        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.COGNITION, 1_700_000L, 0L, 99L, "com.test.app");

        // 构造完整 pullback snapshot
        PullbackSessionCoordinator.Snapshot pullback = new PullbackSessionCoordinator.Snapshot(
                true, 8008L, NOW - 5_000L, 3_500L, NOW + 100_000L,
                true, true, false, false);

        // persist
        prefs.edit().putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();
        FlowServiceStateStore store1 = newStore();
        store1.persist(machine, pullback);

        // 新 StateStore 读取
        FlowServiceStateStore store2 = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(store2, NOW);

        // machine 所有字段完整
        assertEquals(BlockStateMachine.State.COGNITION, result.machine.getState());
        assertEquals(1_700_000L, result.machine.getSessionMs());
        assertEquals(0L, result.machine.getGraceUntil());
        assertEquals(99L, result.machine.getLeftTargetsAt());
        assertEquals("com.test.app", result.machine.getBlockedPackage());

        // pullback 所有字段完整
        assertTrue(result.pullbackSnapshot.present);
        assertEquals(8008L, result.pullbackSnapshot.sessionId);
        assertEquals(NOW - 5_000L, result.pullbackSnapshot.startedAt);
        assertEquals(3_500L, result.pullbackSnapshot.targetMs);
        assertEquals(NOW + 100_000L, result.pullbackSnapshot.leftTargetsAt);
        assertTrue(result.pullbackSnapshot.sawTarget);
        assertTrue(result.pullbackSnapshot.returnReported);
        assertFalse(result.pullbackSnapshot.resolved);
        assertFalse(result.pullbackSnapshot.success);
    }

    // ==================== 辅助 ====================

    private FlowServiceStateStore newStore() {
        return new FlowServiceStateStore(ApplicationProvider.getApplicationContext());
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static final class FakePullbackSink
            implements PullbackSessionCoordinator.OutcomeSink {
        int returnCallCount;
        int outcomeCallCount;

        @Override
        public void recordPostRestReturn(long sessionId) {
            returnCallCount++;
        }

        @Override
        public void recordPullbackOutcome(boolean success, long targetSeconds, long sessionId) {
            outcomeCallCount++;
        }
    }
}
