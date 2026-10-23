package com.flowbreak.app;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.test.core.app.ApplicationProvider;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

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
    private static final int TEST_BOOT_COUNT = 7;

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
                .putLong(FlowForegroundService.PREF_REST_STARTED_ELAPSED_AT, startedAt)
                .putInt(FlowForegroundService.PREF_REST_STARTED_BOOT_COUNT, TEST_BOOT_COUNT)
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
    public void expiredRestDefersCompletionUntilHistoryCanBeChecked() {
        long startedAt = NOW - 180_000L;
        long requiredMs = 180_000L;
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong("sessionMs", 1L)
                .putLong(FlowForegroundService.PREF_REST_STARTED_AT, startedAt)
                .putLong(FlowForegroundService.PREF_REST_STARTED_ELAPSED_AT, startedAt)
                .putInt(FlowForegroundService.PREF_REST_STARTED_BOOT_COUNT, TEST_BOOT_COUNT)
                .putLong(FlowForegroundService.PREF_REST_REQUIRED_MS, requiredMs)
                .putLong(FlowForegroundService.PREF_REST_SESSION_ID, 2_001L)
                .putBoolean(FlowForegroundService.PREF_REST_START_TARGET_ACTIVE, true)
                .putBoolean(FlowForegroundService.PREF_REST_START_INTERACTION_AVAILABLE, true)
                .putString(FlowForegroundService.PREF_REST_START_FOREGROUND_PACKAGE, "a")
                .putStringSet(FlowForegroundService.PREF_REST_TARGET_APPS, setOf("a"))
                .putLong(FlowForegroundService.PREF_REST_TARGET_SESSION_ID, 2_001L)
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        FlowServiceStateStore store = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(store, NOW, true);

        assertFalse(result.autoCompletedRest);
        assertTrue(result.restCompletionPending);
        assertEquals(BlockStateMachine.State.RESTING, result.machine.getState());
        assertTrue(result.machineSnapshot.restStartTargetActive);
        assertTrue(result.machineSnapshot.restStartInteractionAvailable);
        assertEquals("a", result.machineSnapshot.restStartForegroundPackage);
        assertEquals(startedAt, store.preferences().getLong(
                FlowForegroundService.PREF_REST_STARTED_AT, 0L));
        assertEquals(2_001L, store.preferences().getLong(
                FlowForegroundService.PREF_REST_SESSION_ID, 0L));
        assertFalse(store.preferences().contains(
                FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID));
    }

    @Test
    public void restingExactlyCompleteAutoEntersGrace() {
        long startedAt = NOW - 180_000L;
        long requiredMs = 180_000L;
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong("sessionMs", 1L)
                .putString("blockedPackage", "com.example.video")
                .putLong("restStartedAt", startedAt)
                .putLong(FlowForegroundService.PREF_REST_STARTED_ELAPSED_AT, startedAt)
                .putInt(FlowForegroundService.PREF_REST_STARTED_BOOT_COUNT, TEST_BOOT_COUNT)
                .putLong("restRequiredMs", requiredMs)
                .putLong("restSessionId", 2002L)
                .putBoolean(FlowForegroundService.PREF_REST_START_TARGET_ACTIVE, false)
                .putBoolean(FlowForegroundService.PREF_REST_START_INTERACTION_AVAILABLE, true)
                .putString(FlowForegroundService.PREF_REST_START_FOREGROUND_PACKAGE, "com.example.video")
                .putStringSet(FlowForegroundService.PREF_REST_TARGET_APPS, setOf("a"))
                .putLong(FlowForegroundService.PREF_REST_TARGET_SESSION_ID, 2002L)
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
        assertFalse(sp.contains(FlowForegroundService.PREF_REST_START_TARGET_ACTIVE));
        assertFalse(sp.contains(FlowForegroundService.PREF_REST_START_INTERACTION_AVAILABLE));
        assertFalse(sp.contains(FlowForegroundService.PREF_REST_START_FOREGROUND_PACKAGE));
        assertFalse(sp.contains(FlowForegroundService.PREF_REST_TARGET_APPS));
        assertFalse(sp.contains(FlowForegroundService.PREF_REST_TARGET_SESSION_ID));
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
                .putLong(FlowForegroundService.PREF_REST_STARTED_ELAPSED_AT, startedAt)
                .putInt(FlowForegroundService.PREF_REST_STARTED_BOOT_COUNT, TEST_BOOT_COUNT)
                .putLong("restRequiredMs", requiredMs)
                .putLong("restSessionId", 3003L)
                .putStringSet(FlowForegroundService.PREF_REST_TARGET_APPS, setOf("a"))
                .putLong(FlowForegroundService.PREF_REST_TARGET_SESSION_ID, 3003L)
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

    @Test
    public void expiredLegacyRestWithoutTargetSnapshotDoesNotAutoComplete() {
        long startedAt = NOW - 180_000L;
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong("sessionMs", 1L)
                .putLong(FlowForegroundService.PREF_REST_STARTED_AT, startedAt)
                .putLong(FlowForegroundService.PREF_REST_STARTED_ELAPSED_AT, startedAt)
                .putInt(FlowForegroundService.PREF_REST_STARTED_BOOT_COUNT, TEST_BOOT_COUNT)
                .putLong(FlowForegroundService.PREF_REST_REQUIRED_MS, 180_000L)
                .putLong(FlowForegroundService.PREF_REST_SESSION_ID, 4_004L)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("com.example.new"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .commit();

        FlowServiceStateStore store = newStore();
        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(store, NOW);

        assertNull(result.machineSnapshot.restTargetApps);
        assertFalse(result.autoCompletedRest);
        assertTrue(result.restCompletionPending);
        assertEquals(BlockStateMachine.State.RESTING, result.machine.getState());
        assertFalse(store.preferences().contains(FlowForegroundService.PREF_COMPLETED_REST_SESSION_ID));
        assertEquals(startedAt, store.preferences().getLong(
                FlowForegroundService.PREF_REST_STARTED_AT, 0L));
    }

    @Test
    public void emptyRestTargetSnapshotIsDistinctFromMissingSnapshot() {
        long startedAt = NOW - 60_000L;
        FlowServiceStateStore store = newStore();
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(prefs, "empty-target-begin"));
        assertTrue(store.persistBeginRest(
                startedAt,
                60_000L,
                4_005L,
                false,
                true,
                "com.example.other",
                "empty-target-begin",
                setOf(),
                startedAt,
                TEST_BOOT_COUNT
        ));
        store.persist(
                new BlockStateMachine(BlockStateMachine.State.RESTING, 1L, 0L, 0L, ""),
                PullbackSessionCoordinator.Snapshot.empty()
        );

        FlowServiceRecoveryCoordinator.Result result =
                FlowServiceRecoveryCoordinator.restore(newStore(), NOW, true);

        assertNotNull(result.machineSnapshot.restTargetApps);
        assertTrue(result.machineSnapshot.restTargetApps.isEmpty());
        assertTrue(result.restCompletionPending);
        assertEquals(BlockStateMachine.State.RESTING, result.machine.getState());
    }

    @Test
    public void remountedBeginRebindsLatestTokenWithoutResettingActiveRestSession() {
        long startedAt = NOW - 45_000L;
        long requiredMs = 180_000L;
        long sessionId = 4_006L;
        FlowServiceStateStore store = newStore();

        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(prefs, "begin-original"));
        assertTrue(store.persistBeginRest(
                startedAt,
                requiredMs,
                sessionId,
                true,
                true,
                "com.example.video",
                "begin-original",
                setOf("com.example.video")
        ));

        // A WebView remount creates a new BEGIN request, but the same REST
        // session must remain authoritative and cancellable by its new page.
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(prefs, "begin-remounted"));
        assertTrue(store.bindActiveRestBeginRequestToken("begin-remounted"));

        assertEquals(startedAt, prefs.getLong(FlowForegroundService.PREF_REST_STARTED_AT, 0L));
        assertEquals(requiredMs, prefs.getLong(FlowForegroundService.PREF_REST_REQUIRED_MS, 0L));
        assertEquals(sessionId, prefs.getLong(FlowForegroundService.PREF_REST_SESSION_ID, 0L));
        assertEquals("begin-remounted", prefs.getString(
                FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN,
                ""
        ));
        assertTrue(RestCancellationGate.shouldCancel(
                BlockStateMachine.State.RESTING,
                sessionId,
                sessionId,
                "begin-remounted",
                prefs.getString(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, "")
        ));
    }

    @Test
    public void staleBeginCannotOverwriteNewestRequestOrPersistAnotherSession() {
        FlowServiceStateStore store = newStore();
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(prefs, "begin-current"));

        assertFalse(store.persistBeginRest(
                NOW,
                180_000L,
                4_007L,
                false,
                true,
                "",
                "begin-stale",
                setOf("com.example.video")
        ));

        assertEquals("begin-current", prefs.getString(
                FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN,
                ""
        ));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_SESSION_ID));
        assertFalse(RestCancellationGate.shouldAcceptBegin(
                "begin-stale",
                prefs.getString(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, "")
        ));
    }

    @Test
    public void restCancellationCommitAtomicallyPersistsStateAndClearsSession() {
        long sessionId = 4_008L;
        String requestToken = "begin-cancel-commit";
        FlowServiceStateStore store = newStore();
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(prefs, requestToken));
        assertTrue(store.persistBeginRest(
                NOW - 45_000L,
                180_000L,
                sessionId,
                true,
                true,
                "com.example.video",
                requestToken,
                setOf("com.example.video")
        ));
        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.RESTING,
                900_000L,
                0L,
                0L,
                "com.example.video"
        );
        FlowServiceStateStore.Checkpoint checkpoint = new FlowServiceStateStore.Checkpoint(
                NOW,
                90_000L,
                false,
                true,
                "com.example.other"
        );
        store.persist(
                machine,
                PullbackSessionCoordinator.Snapshot.empty(),
                checkpoint
        );

        assertFalse(store.commitRestCancellation(
                sessionId,
                "stale-cancel-token",
                BlockStateMachine.State.COGNITION,
                machine.getSessionMs(),
                machine.getGraceUntil(),
                machine.getLeftTargetsAt(),
                machine.getBlockedPackage(),
                PullbackSessionCoordinator.Snapshot.empty(),
                checkpoint
        ));
        assertEquals(BlockStateMachine.State.RESTING.name(), prefs.getString("blockState", ""));
        assertEquals(sessionId, prefs.getLong(FlowForegroundService.PREF_REST_SESSION_ID, 0L));

        assertTrue(store.commitRestCancellation(
                sessionId,
                requestToken,
                BlockStateMachine.State.COGNITION,
                machine.getSessionMs(),
                machine.getGraceUntil(),
                machine.getLeftTargetsAt(),
                machine.getBlockedPackage(),
                PullbackSessionCoordinator.Snapshot.empty(),
                checkpoint
        ));

        assertEquals(BlockStateMachine.State.COGNITION.name(), prefs.getString("blockState", ""));
        assertEquals(machine.getSessionMs(), prefs.getLong("sessionMs", -1L));
        assertEquals(NOW, prefs.getLong("checkpointWallMs", 0L));
        assertEquals(90_000L, prefs.getLong("checkpointElapsedMs", 0L));
        assertEquals("com.example.other", prefs.getString("checkpointForegroundPackage", ""));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_STARTED_AT));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_REQUIRED_MS));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_SESSION_ID));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_TARGET_APPS));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_TARGET_SESSION_ID));
    }

    @Test
    public void automaticCheatCancellationKeepsAQueuedNewBeginToken() {
        long sessionId = 4_009L;
        FlowServiceStateStore store = newStore();
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(prefs, "begin-original"));
        assertTrue(store.persistBeginRest(
                NOW - 45_000L,
                180_000L,
                sessionId,
                true,
                true,
                "com.example.video",
                "begin-original",
                setOf("com.example.video"),
                NOW - 45_000L,
                TEST_BOOT_COUNT
        ));
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(prefs, "begin-new"));
        assertFalse(store.markAutomaticRestCancellationPending(sessionId + 1L, "begin-new"));
        assertFalse(store.markAutomaticRestCancellationPending(sessionId, "stale-token"));
        assertTrue(store.markAutomaticRestCancellationPending(sessionId, "begin-new"));
        assertTrue(prefs.getBoolean(
                FlowForegroundService.PREF_REST_CHEAT_CANCELLATION_PENDING, false
        ));
        assertEquals(sessionId, prefs.getLong(
                FlowForegroundService.PREF_REST_CHEAT_CANCELLATION_SESSION_ID, 0L
        ));

        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.COGNITION, 900_000L, 0L, 0L, ""
        );
        assertTrue(store.commitAutomaticRestCancellation(
                sessionId,
                "begin-new",
                "begin-new",
                BlockStateMachine.State.COGNITION,
                machine.getSessionMs(),
                machine.getGraceUntil(),
                machine.getLeftTargetsAt(),
                machine.getBlockedPackage(),
                PullbackSessionCoordinator.Snapshot.empty(),
                new FlowServiceStateStore.Checkpoint(NOW, NOW, false, false, "")
        ));

        assertEquals(BlockStateMachine.State.COGNITION.name(),
                prefs.getString("blockState", ""));
        assertEquals("begin-new", prefs.getString(
                FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, ""
        ));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_SESSION_BEGIN_REQUEST_TOKEN));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_SESSION_ID));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_CHEAT_CANCELLATION_PENDING));
        assertFalse(prefs.contains(
                FlowForegroundService.PREF_REST_CHEAT_CANCELLATION_SESSION_ID
        ));
    }

    @Test
    public void automaticCompletionKeepsAQueuedNewBeginToken() {
        Context context = ApplicationProvider.getApplicationContext();
        int bootCount = 17;
        Settings.Global.putInt(
                context.getContentResolver(), Settings.Global.BOOT_COUNT, bootCount
        );
        ShadowSystemClock.advanceBy(2_000L, TimeUnit.MILLISECONDS);
        long nowWall = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();
        long sessionId = 4_010L;
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong(FlowForegroundService.PREF_REST_STARTED_AT, nowWall - 2_000L)
                .putLong(FlowForegroundService.PREF_REST_STARTED_ELAPSED_AT,
                        nowElapsed - 2_000L)
                .putInt(FlowForegroundService.PREF_REST_STARTED_BOOT_COUNT, bootCount)
                .putLong(FlowForegroundService.PREF_REST_REQUIRED_MS, 1L)
                .putLong(FlowForegroundService.PREF_REST_SESSION_ID, sessionId)
                .putString(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, "begin-new")
                .putString(FlowForegroundService.PREF_REST_SESSION_BEGIN_REQUEST_TOKEN,
                        "begin-original")
                .putStringSet(FlowForegroundService.PREF_REST_TARGET_APPS, setOf("target"))
                .putLong(FlowForegroundService.PREF_REST_TARGET_SESSION_ID, sessionId)
                .putBoolean(FlowForegroundService.PREF_REST_CHEAT_CANCELLATION_PENDING, true)
                .putLong(FlowForegroundService.PREF_REST_CHEAT_CANCELLATION_SESSION_ID, sessionId)
                .commit();

        assertTrue(newStore().persistAutoCompletedRest(
                nowWall + FlowServiceStateStore.GRACE_MS,
                sessionId,
                nowWall,
                nowElapsed,
                bootCount
        ));
        assertEquals(BlockStateMachine.State.GRACE.name(), prefs.getString("blockState", ""));
        assertEquals("begin-new", prefs.getString(
                FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, ""
        ));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_SESSION_BEGIN_REQUEST_TOKEN));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_CHEAT_CANCELLATION_PENDING));
        assertFalse(prefs.contains(
                FlowForegroundService.PREF_REST_CHEAT_CANCELLATION_SESSION_ID
        ));
    }

    @Test
    public void failClosedSessionCleanupKeepsAQueuedNewBeginToken() {
        prefs.edit()
                .putString("blockState", BlockStateMachine.State.RESTING.name())
                .putLong(FlowForegroundService.PREF_REST_SESSION_ID, 4_011L)
                .putString(FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, "begin-new")
                .putString(FlowForegroundService.PREF_REST_SESSION_BEGIN_REQUEST_TOKEN,
                        "begin-original")
                .commit();

        newStore().clearActiveRestSession();

        assertEquals("begin-new", prefs.getString(
                FlowForegroundService.PREF_REST_BEGIN_REQUEST_TOKEN, ""
        ));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_SESSION_BEGIN_REQUEST_TOKEN));
        assertFalse(prefs.contains(FlowForegroundService.PREF_REST_SESSION_ID));
    }

    @Test
    public void restTargetSnapshotSurvivesConfigurationChangeAndReplayCancels() {
        String oldTarget = "com.example.old-target";
        String newTarget = "com.example.new-target";
        String other = "com.example.other";
        long startedAt = NOW - 20_000L;
        long sessionId = 5_005L;
        FlowServiceStateStore store = newStore();
        assertTrue(RestBeginRequestTokenStore.persistLatestRequest(prefs, "begin-request"));
        assertTrue(store.persistBeginRest(
                startedAt,
                60_000L,
                sessionId,
                false,
                true,
                other,
                "begin-request",
                setOf(oldTarget)
        ));
        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.RESTING,
                2_000_000L,
                0L,
                0L,
                ""
        );
        store.persist(machine, PullbackSessionCoordinator.Snapshot.empty());

        // Simulate saveTargetApps/saveSettings changing the mutable next-session config.
        prefs.edit().putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf(newTarget)).commit();

        FlowServiceStateStore restartedStore = newStore();
        FlowServiceRecoveryCoordinator.Result restored =
                FlowServiceRecoveryCoordinator.restore(restartedStore, NOW, true);
        assertEquals(setOf(newTarget), restored.config.targetApps);
        assertEquals(setOf(oldTarget), restored.machineSnapshot.restTargetApps);

        TargetSessionGapReconciler.Result history = TargetSessionGapReconciler.reconcile(
                new TargetSessionGapReconciler.Input(
                        startedAt,
                        startedAt + 8_000L,
                        other,
                        false,
                        true,
                        restored.machineSnapshot.restTargetApps,
                        Arrays.asList(
                                TargetSessionGapReconciler.Event.foreground(
                                        oldTarget, "OldTarget", startedAt + 1_000L
                                ),
                                TargetSessionGapReconciler.Event.foreground(
                                        other, "Other", startedAt + 7_000L
                                )
                        )
                )
        );
        long verifiedTargetMs = RestCheatReplay.verifiedTargetMsDuring(
                history,
                startedAt,
                startedAt + 8_000L
        );
        RestCheatReplay.Decision decision = RestCheatReplay.applyVerifiedTargetMs(
                restored.machine,
                new RestCheatTracker(),
                verifiedTargetMs,
                LIMIT_MS
        );

        assertEquals(TargetSessionGapReconciler.Status.SUCCESS, history.status);
        assertEquals(6_000L, verifiedTargetMs);
        assertTrue(decision.cancelled);
        assertEquals(BlockStateMachine.State.BLOCKED, restored.machine.getState());
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
                .putStringSet(FlowForegroundService.PREF_REST_TARGET_APPS, setOf("a"))
                .putLong(FlowForegroundService.PREF_REST_TARGET_SESSION_ID, 6006L)
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
        assertFalse(store2.preferences().contains(FlowForegroundService.PREF_REST_TARGET_APPS));
        assertFalse(store2.preferences().contains(FlowForegroundService.PREF_REST_TARGET_SESSION_ID));

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

    @Test
    public void monitorCheckpointRoundTripsWithoutPersistingHistory() {
        prefs.edit()
                .putInt("limitMinutes", 25)
                .putStringSet(PreferenceUtils.PREF_TARGET_APPS, setOf("a"))
                .putBoolean("monitoringEnabled", true)
                .putInt("restDuration", 180)
                .putBoolean("allowEmergencyUnlock", true)
                .commit();

        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.COGNITION,
                100_000L,
                0L,
                0L,
                "a"
        );
        FlowServiceStateStore.Checkpoint checkpoint = new FlowServiceStateStore.Checkpoint(
                NOW - 2_000L,
                8_000L,
                true,
                true,
                "a"
        );

        newStore().persist(machine, PullbackSessionCoordinator.Snapshot.empty(), checkpoint);
        FlowServiceStateStore.MachineSnapshot snapshot = newStore().loadMachineSnapshot();

        assertEquals(NOW - 2_000L, snapshot.checkpointWallMs);
        assertEquals(8_000L, snapshot.checkpointElapsedMs);
        assertTrue(snapshot.checkpointTargetActive);
        assertTrue(snapshot.checkpointInteractionAvailable);
        assertEquals("a", snapshot.checkpointForegroundPackage);
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
