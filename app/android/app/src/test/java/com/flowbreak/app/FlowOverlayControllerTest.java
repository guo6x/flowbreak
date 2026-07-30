package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

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
}
