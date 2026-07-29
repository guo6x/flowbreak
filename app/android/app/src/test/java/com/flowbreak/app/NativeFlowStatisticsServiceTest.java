package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * 验证统计服务的纯逻辑方法。
 * 不依赖 Room 数据库或 Android Context。
 */
public class NativeFlowStatisticsServiceTest {
    @Test public void boundDaysClampsToLowerLimit() {
        assertEquals(1, NativeFlowStatisticsService.boundDays(0));
        assertEquals(1, NativeFlowStatisticsService.boundDays(-1));
        assertEquals(1, NativeFlowStatisticsService.boundDays(-100));
    }

    @Test public void boundDaysClampsToUpperLimit() {
        assertEquals(90, NativeFlowStatisticsService.boundDays(91));
        assertEquals(90, NativeFlowStatisticsService.boundDays(100));
        assertEquals(90, NativeFlowStatisticsService.boundDays(1000));
    }

    @Test public void boundDaysPreservesValidRange() {
        assertEquals(1, NativeFlowStatisticsService.boundDays(1));
        assertEquals(7, NativeFlowStatisticsService.boundDays(7));
        assertEquals(30, NativeFlowStatisticsService.boundDays(30));
        assertEquals(90, NativeFlowStatisticsService.boundDays(90));
    }

    @Test public void reflectionValueMapsHelpedToThree() {
        assertEquals(3, NativeFlowStatisticsService.reflectionValue("helped"));
    }

    @Test public void reflectionValueMapsNeutralToTwo() {
        assertEquals(2, NativeFlowStatisticsService.reflectionValue("neutral"));
    }

    @Test public void reflectionValueMapsNotHelpedToOne() {
        assertEquals(1, NativeFlowStatisticsService.reflectionValue("not_helped"));
    }

    @Test public void reflectionValueReturnsZeroForInvalidString() {
        assertEquals(0, NativeFlowStatisticsService.reflectionValue(""));
        assertEquals(0, NativeFlowStatisticsService.reflectionValue("unknown"));
        assertEquals(0, NativeFlowStatisticsService.reflectionValue(null));
    }

    @Test public void dayFormatsTimestampAsYearMonthDay() {
        // 2025-01-01 00:00:00 UTC = 1735689600000 ms
        String date = NativeFlowStatisticsService.day(1735689600000L);
        // Note: uses default timezone, so just verify format pattern
        assertTrue("date should match yyyy-MM-dd pattern: " + date,
                date.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test public void dayDaysAgoReturnsDescendingDates() {
        String today = NativeFlowStatisticsService.day(System.currentTimeMillis());
        String yesterday = NativeFlowStatisticsService.dayDaysAgo(1);
        String threeDaysAgo = NativeFlowStatisticsService.dayDaysAgo(3);

        assertTrue("today should not equal yesterday", !today.equals(yesterday));
        assertTrue("dates should be different for different offsets",
                !yesterday.equals(threeDaysAgo));
    }

    @Test public void dayDaysAgoZeroReturnsToday() {
        String today = NativeFlowStatisticsService.day(System.currentTimeMillis());
        String dayDaysAgoZero = NativeFlowStatisticsService.dayDaysAgo(0);
        assertEquals(today, dayDaysAgoZero);
    }

    @Test public void dayDaysAgoNegativeIsClampedToZero() {
        String dayDaysAgoNeg = NativeFlowStatisticsService.dayDaysAgo(-5);
        String dayDaysAgoZero = NativeFlowStatisticsService.dayDaysAgo(0);
        assertEquals(dayDaysAgoZero, dayDaysAgoNeg);
    }
}
