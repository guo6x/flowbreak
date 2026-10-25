package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowSettings;

@RunWith(RobolectricTestRunner.class)
public class FlowOverlayControllerTest {
    private static final long NOW = 10_000_000L;

    @Test public void remainingAboveTwoMinutesReturnsNull() {
        // 剩余大于 2 分钟不显示
        assertNull(FlowOverlayController.formatGraceCountdown(NOW + 120_001L, NOW));
        assertNull(FlowOverlayController.formatGraceCountdown(NOW + 180_000L, NOW));
    }

    @Test public void exactlyTwoMinutesShowsCountdown() {
        // 恰好 2 分钟（120_000ms）显示
        // ceil(120000/1000) = 120 秒 = 2:00
        String result = FlowOverlayController.formatGraceCountdown(NOW + 120_000L, NOW);
        assertEquals("访问窗口还剩 2:00 · 准备保存进度", result);
    }

    @Test public void oneMinuteFiveSecondsFormat() {
        // 65_000ms = 1 分 5 秒
        // ceil(65000/1000) = 65 秒 = 1:05
        String result = FlowOverlayController.formatGraceCountdown(NOW + 65_000L, NOW);
        assertEquals("访问窗口还剩 1:05 · 准备保存进度", result);
    }

    @Test public void sixtyFiveThousandOneMillisecondsUsesCeil() {
        // 65_001ms → ceil(65.001) = 66 秒 = 1:06
        // 验证 Math.ceil 语义
        String result = FlowOverlayController.formatGraceCountdown(NOW + 65_001L, NOW);
        assertEquals("访问窗口还剩 1:06 · 准备保存进度", result);
    }

    @Test public void belowOneMinuteFormat() {
        // 30_000ms = 30 秒 = 0:30
        String result = FlowOverlayController.formatGraceCountdown(NOW + 30_000L, NOW);
        assertEquals("访问窗口还剩 0:30 · 准备保存进度", result);
    }

    @Test public void oneSecondRemaining() {
        // 1_000ms = 1 秒 = 0:01
        String result = FlowOverlayController.formatGraceCountdown(NOW + 1_000L, NOW);
        assertEquals("访问窗口还剩 0:01 · 准备保存进度", result);
    }

    @Test public void fiveHundredMillisecondsCeilsToOneSecond() {
        // 500ms → ceil(0.5) = 1 秒 = 0:01
        String result = FlowOverlayController.formatGraceCountdown(NOW + 500L, NOW);
        assertEquals("访问窗口还剩 0:01 · 准备保存进度", result);
    }

    @Test public void expiredReturnsNull() {
        // 已到期（remaining=0 或负值）不显示
        assertNull(FlowOverlayController.formatGraceCountdown(NOW, NOW));
        assertNull(FlowOverlayController.formatGraceCountdown(NOW - 1_000L, NOW));
        assertNull(FlowOverlayController.formatGraceCountdown(NOW - 100_000L, NOW));
    }

    @Test public void oneMillisecondRemainingCeilsToOneSecond() {
        // 1ms → ceil(0.001) = 1 秒 = 0:01
        String result = FlowOverlayController.formatGraceCountdown(NOW + 1L, NOW);
        assertEquals("访问窗口还剩 0:01 · 准备保存进度", result);
    }

    @Test public void oneMinuteExactlyShowsOneMinuteZeroSeconds() {
        // 60_000ms = 60 秒 = 1:00
        String result = FlowOverlayController.formatGraceCountdown(NOW + 60_000L, NOW);
        assertEquals("访问窗口还剩 1:00 · 准备保存进度", result);
    }

    @Test public void fiftyNineThousandMillisecondsShowsFiftyNineSeconds() {
        // 59_000ms = 59 秒 = 0:59
        String result = FlowOverlayController.formatGraceCountdown(NOW + 59_000L, NOW);
        assertEquals("访问窗口还剩 0:59 · 准备保存进度", result);
    }

    @Test public void pendingOverlayShowIsInvalidatedByDismiss() {
        FlowOverlayController.GenerationGate gate = new FlowOverlayController.GenerationGate();

        long pendingShow = gate.issue();
        assertTrue(gate.isCurrent(pendingShow));

        // Models dismiss* running before the Handler executes the queued show.
        gate.invalidate();
        assertFalse(gate.isCurrent(pendingShow));
    }

    @Test public void newerOverlayShowSupersedesOlderPendingShow() {
        FlowOverlayController.GenerationGate gate = new FlowOverlayController.GenerationGate();

        long firstShow = gate.issue();
        long replacementShow = gate.issue();

        assertFalse(gate.isCurrent(firstShow));
        assertTrue(gate.isCurrent(replacementShow));
    }

    @Test public void clearCallbacksInvalidationLeavesNoPendingGeneration() {
        FlowOverlayController.GenerationGate blocker = new FlowOverlayController.GenerationGate();
        FlowOverlayController.GenerationGate warning = new FlowOverlayController.GenerationGate();
        FlowOverlayController.GenerationGate grace = new FlowOverlayController.GenerationGate();

        long blockerShow = blocker.issue();
        long warningShow = warning.issue();
        long graceShow = grace.issue();
        blocker.invalidate();
        warning.invalidate();
        grace.invalidate();

        assertFalse(blocker.isCurrent(blockerShow));
        assertFalse(warning.isCurrent(warningShow));
        assertFalse(grace.isCurrent(graceShow));
    }

    @Test public void existingBlockerRefreshesEmergencyActionWhenPreferenceChanges()
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ShadowSettings.setCanDrawOverlays(true);
        FlowOverlayController controller = new FlowOverlayController(
                context,
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE),
                new Handler(Looper.getMainLooper()),
                new FlowOverlayController.Callbacks() {
                    @Override public void onStartRest() { }
                    @Override public void onEmergencyLongPress(
                            FlowOverlayController.EmergencyUnlockCompletion completion
                    ) { completion.onComplete(false); }
                    @Override public void onEmergencyExhausted() { }
                    @Override public long currentSessionMs() { return 0L; }
                    @Override public int currentLimitMinutes() { return 25; }
                    @Override public boolean allowEmergencyUnlock() { return false; }
                }
        );

        controller.showBlocker("tv.example", 60_000L, false);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        TextView emergencyAction = emergencyAction(controller);
        assertNotNull(emergencyAction);
        assertEquals(View.GONE, emergencyAction.getVisibility());

        controller.showBlocker("tv.example", 60_000L, true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.VISIBLE, emergencyAction.getVisibility());

        controller.showBlocker("tv.example", 60_000L, false);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.GONE, emergencyAction.getVisibility());
        controller.dismissBlocker();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void emergencyOverlayWaitsForTheActualUnlockResult() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ShadowSettings.setCanDrawOverlays(true);
        AtomicReference<FlowOverlayController.EmergencyUnlockCompletion> pending =
                new AtomicReference<>();
        AtomicInteger exhausted = new AtomicInteger();
        FlowOverlayController controller = new FlowOverlayController(
                context,
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE),
                new Handler(Looper.getMainLooper()),
                new FlowOverlayController.Callbacks() {
                    @Override public void onStartRest() { }
                    @Override public void onEmergencyLongPress(
                            FlowOverlayController.EmergencyUnlockCompletion completion
                    ) { pending.set(completion); }
                    @Override public void onEmergencyExhausted() { exhausted.incrementAndGet(); }
                    @Override public long currentSessionMs() { return 0L; }
                    @Override public int currentLimitMinutes() { return 25; }
                    @Override public boolean allowEmergencyUnlock() { return true; }
                }
        );

        controller.showBlocker("tv.example", 60_000L, true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        TextView action = emergencyAction(controller);
        MotionEvent down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0);
        try {
            assertTrue(action.dispatchTouchEvent(down));
        } finally {
            down.recycle();
        }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(10L, TimeUnit.SECONDS);

        assertNotNull("long press should submit an asynchronous unlock request", pending.get());
        assertEquals("a pending request must not be reported as exhausted", 0, exhausted.get());
        assertNotNull("the blocker stays visible until the service confirms success",
                getField(controller, "blockerView"));

        pending.get().onComplete(false);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, exhausted.get());
        assertNotNull("failed confirmation must not dismiss the blocker",
                getField(controller, "blockerView"));
        controller.dismissBlocker();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void lateEmergencyResultCannotAffectAReplacementBlocker() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        ShadowSettings.setCanDrawOverlays(true);

        for (boolean staleResult : new boolean[]{true, false}) {
            AtomicReference<FlowOverlayController.EmergencyUnlockCompletion> firstRequest =
                    new AtomicReference<>();
            AtomicReference<FlowOverlayController.EmergencyUnlockCompletion> secondRequest =
                    new AtomicReference<>();
            AtomicInteger requestCount = new AtomicInteger();
            AtomicInteger exhausted = new AtomicInteger();
            FlowOverlayController controller = new FlowOverlayController(
                    context,
                    (WindowManager) context.getSystemService(Context.WINDOW_SERVICE),
                    new Handler(Looper.getMainLooper()),
                    new FlowOverlayController.Callbacks() {
                        @Override public void onStartRest() { }
                        @Override public void onEmergencyLongPress(
                                FlowOverlayController.EmergencyUnlockCompletion completion
                        ) {
                            if (requestCount.incrementAndGet() == 1) {
                                firstRequest.set(completion);
                            } else {
                                secondRequest.set(completion);
                            }
                        }
                        @Override public void onEmergencyExhausted() {
                            exhausted.incrementAndGet();
                        }
                        @Override public long currentSessionMs() { return 0L; }
                        @Override public int currentLimitMinutes() { return 25; }
                        @Override public boolean allowEmergencyUnlock() { return true; }
                    }
            );

            controller.showBlocker("tv.example", 60_000L, true);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            longPressEmergencyAction(controller);
            assertNotNull(firstRequest.get());

            controller.dismissBlocker();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            controller.showBlocker("tv.example", 60_000L, true);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Object replacementBlocker = getField(controller, "blockerView");
            longPressEmergencyAction(controller);
            assertNotNull(secondRequest.get());

            firstRequest.get().onComplete(staleResult);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("the replacement blocker must ignore the old result",
                    replacementBlocker, getField(controller, "blockerView"));
            assertEquals("the old failure must not clear the new pending request", 0,
                    exhausted.get());

            secondRequest.get().onComplete(true);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNull("only the replacement request may dismiss its blocker",
                    getField(controller, "blockerView"));
        }
    }

    private static void longPressEmergencyAction(FlowOverlayController controller) throws Exception {
        TextView action = emergencyAction(controller);
        MotionEvent down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0);
        try {
            assertTrue(action.dispatchTouchEvent(down));
        } finally {
            down.recycle();
        }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(10L, TimeUnit.SECONDS);
    }

    private static TextView emergencyAction(FlowOverlayController controller) throws Exception {
        java.lang.reflect.Field field = FlowOverlayController.class.getDeclaredField(
                "blockerEmergencyAction"
        );
        field.setAccessible(true);
        return (TextView) field.get(controller);
    }

    private static Object getField(Object target, String name) throws Exception {
        java.lang.reflect.Field field = FlowOverlayController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
