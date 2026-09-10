package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class TargetSessionGapReconcilerTest {
    private static final String A = "com.example.a";
    private static final String B = "com.example.b";
    private static final String OTHER = "com.example.other";
    private static final long START = 1_000_000L;
    private static final long LIMIT = 300_000L;

    @Test public void A_continuousTargetOverLongGapIsRecovered() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 500_000L, A, true, true, setOf(A), Collections.emptyList()
        );

        assertEquals(TargetSessionGapReconciler.Status.SUCCESS, result.status);
        assertEquals(Long.valueOf(500_000L), result.usageMsByTargetPackage.get(A));
        assertEquals(500_000L, result.historicalTargetMsRecovered);
        assertEquals(500_000L, result.coverageMs);
    }

    @Test public void B_targetNonTargetForTwentySecondsKeepsSharedSession() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 80_000L,
                A,
                true,
                true,
                setOf(A),
                Arrays.asList(
                        TargetSessionGapReconciler.Event.foreground(OTHER, "Other", START + 20_000L),
                        TargetSessionGapReconciler.Event.foreground(A, "Main", START + 40_000L)
                )
        );
        BlockStateMachine machine = replay(result, true, START);

        assertEquals(Long.valueOf(60_000L), result.usageMsByTargetPackage.get(A));
        assertEquals(60_000L, machine.getSessionMs());
    }

    @Test public void C_targetNonTargetForThirtyOneSecondsResetsSession() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 61_000L,
                A,
                true,
                true,
                setOf(A),
                Arrays.asList(
                        TargetSessionGapReconciler.Event.foreground(OTHER, "Other", START + 20_000L),
                        TargetSessionGapReconciler.Event.foreground(A, "Main", START + 51_000L)
                )
        );
        BlockStateMachine machine = replay(result, true, START);

        assertEquals(10_000L, machine.getSessionMs());
    }

    @Test public void D_screenOffIntervalIsNotCredited() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 300_000L,
                A,
                true,
                true,
                setOf(A),
                Arrays.asList(
                        TargetSessionGapReconciler.Event.screenNonInteractive(START + 100_000L),
                        TargetSessionGapReconciler.Event.screenInteractive(START + 200_000L)
                )
        );

        assertEquals(Long.valueOf(200_000L), result.usageMsByTargetPackage.get(A));
    }

    @Test public void E_targetSwitchKeepsSessionButSplitsUsageByPackage() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 300_000L,
                A,
                true,
                true,
                setOf(A, B),
                Collections.singletonList(
                        TargetSessionGapReconciler.Event.foreground(B, "Main", START + 100_000L)
                )
        );
        BlockStateMachine machine = replay(result, true, START);

        assertEquals(Long.valueOf(100_000L), result.usageMsByTargetPackage.get(A));
        assertEquals(Long.valueOf(200_000L), result.usageMsByTargetPackage.get(B));
        assertEquals(300_000L, machine.getSessionMs());
    }

    @Test public void F_blockedStateStaysStickyAfterLongLeave() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 50_000L,
                A,
                true,
                true,
                setOf(A),
                Arrays.asList(
                        TargetSessionGapReconciler.Event.foreground(OTHER, "Other", START + 1_000L),
                        TargetSessionGapReconciler.Event.foreground(A, "Main", START + 41_000L)
                )
        );
        long blockedSession = LIMIT * 6L / 5L;
        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.BLOCKED, blockedSession, 0L, 0L, A
        );
        machine.seedCheckpoint(START, true);
        replayInto(machine, result);

        assertEquals(BlockStateMachine.State.BLOCKED, machine.getState());
        assertTrue(machine.getSessionMs() >= blockedSession);
    }

    @Test public void G_liveTenSecondTailIsNotDoubleCounted() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 490_000L,
                A,
                true,
                true,
                setOf(A),
                Collections.emptyList()
        );
        BlockStateMachine machine = replay(result, true, START);

        machine.update(true, A, START + 500_000L, LIMIT);
        assertEquals(500_000L, machine.getSessionMs());
    }

    @Test public void H_unorderedTimelineIsIncompleteWithoutBlindCredit() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 100_000L,
                A,
                true,
                true,
                setOf(A),
                Arrays.asList(
                        TargetSessionGapReconciler.Event.foreground(OTHER, "Other", START + 20_000L),
                        TargetSessionGapReconciler.Event.foreground(A, "Main", START + 10_000L)
                )
        );

        assertEquals(TargetSessionGapReconciler.Status.INCOMPLETE, result.status);
        assertTrue(result.usageMsByTargetPackage.isEmpty());
        assertEquals(0L, result.historicalTargetMsRecovered);
    }

    @Test public void I_threeHundredSixtyOneSecondsReachesBlocked() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 361_000L,
                A,
                true,
                true,
                setOf(A),
                Collections.emptyList()
        );
        BlockStateMachine machine = replay(result, true, START);

        assertEquals(BlockStateMachine.State.BLOCKED, machine.getState());
        assertEquals(361_000L, machine.getSessionMs());
    }

    @Test public void J_longGapUsesVerifiedHistoryRatherThanTenSecondClamp() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 490_000L,
                A,
                true,
                true,
                setOf(A),
                Collections.emptyList()
        );
        BlockStateMachine machine = replay(result, true, START);

        assertEquals(BlockStateMachine.State.BLOCKED, machine.getState());
        assertTrue(machine.getSessionMs() > 10_000L);
        assertEquals(490_000L, machine.getSessionMs());
    }

    @Test public void wechatTargetPortionIsMarkedIncomplete() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 100_000L,
                A,
                true,
                true,
                setOf(A, "com.tencent.mm"),
                Collections.singletonList(
                        TargetSessionGapReconciler.Event.foreground(
                                "com.tencent.mm", "VideoActivity", START + 10_000L
                        )
                )
        );

        assertEquals(TargetSessionGapReconciler.Status.INCOMPLETE, result.status);
        assertTrue(result.usageMsByTargetPackage.isEmpty());
    }

    @Test public void inconsistentStartingTargetCheckpointIsIncomplete() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 100_000L,
                OTHER,
                true,
                true,
                setOf(A),
                Collections.emptyList()
        );

        assertEquals(TargetSessionGapReconciler.Status.INCOMPLETE, result.status);
        assertTrue(result.usageMsByTargetPackage.isEmpty());
    }

    @Test public void classQualifiedBackgroundAfterPackageOnlyCheckpointIsConservative() {
        TargetSessionGapReconciler.Result result = reconcile(
                START + 200_000L,
                A,
                true,
                true,
                setOf(A),
                Collections.singletonList(
                        TargetSessionGapReconciler.Event.background(A, "MainActivity", START + 100_000L)
                )
        );

        assertEquals(TargetSessionGapReconciler.Status.SUCCESS, result.status);
        assertEquals(Long.valueOf(100_000L), result.usageMsByTargetPackage.get(A));
    }

    private static TargetSessionGapReconciler.Result reconcile(
            long end,
            String startingPackage,
            boolean startingTarget,
            boolean interaction,
            Set<String> targets,
            List<TargetSessionGapReconciler.Event> events
    ) {
        return TargetSessionGapReconciler.reconcile(new TargetSessionGapReconciler.Input(
                START,
                end,
                startingPackage,
                startingTarget,
                interaction,
                targets,
                events
        ));
    }

    private static BlockStateMachine replay(
            TargetSessionGapReconciler.Result result,
            boolean startingTarget,
            long checkpoint
    ) {
        BlockStateMachine machine = new BlockStateMachine(
                BlockStateMachine.State.IDLE, 0L, 0L, 0L, ""
        );
        machine.seedCheckpoint(checkpoint, startingTarget);
        replayInto(machine, result);
        return machine;
    }

    private static void replayInto(
            BlockStateMachine machine,
            TargetSessionGapReconciler.Result result
    ) {
        for (TargetSessionGapReconciler.TimelineSegment segment : result.segments) {
            boolean segmentTarget = segment.targetActive && segment.interactionAvailable;
            machine.updateVerifiedHistory(
                    segmentTarget,
                    segment.foregroundPackage,
                    segment.startWallMs,
                    LIMIT
            );
            machine.updateVerifiedHistory(
                    segmentTarget,
                    segment.foregroundPackage,
                    segment.endWallMs,
                    LIMIT
            );
        }
        if (!result.segments.isEmpty()) {
            TargetSessionGapReconciler.TimelineSegment last =
                    result.segments.get(result.segments.size() - 1);
            machine.updateVerifiedHistory(
                    result.finalTargetActive && result.finalInteractionAvailable,
                    result.finalForegroundPackage,
                    last.endWallMs,
                    LIMIT
            );
        }
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
