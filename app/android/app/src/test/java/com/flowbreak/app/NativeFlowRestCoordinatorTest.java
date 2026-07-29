package com.flowbreak.app;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * 验证休息协调器的纯逻辑方法。
 * 不依赖 Room、SharedPreferences 或 Android Context。
 *
 * 完整的 complete() 流程涉及数据库事务，由 RestSessionValidatorTest 和
 * FlowRepository 的集成测试覆盖。这里只验证 activity 规范化。
 */
public class NativeFlowRestCoordinatorTest {
    @Test public void eyeActivityIsPreserved() {
        assertEquals("eye", NativeFlowRestCoordinator.normalizeActivity("eye"));
    }

    @Test public void stretchActivityIsPreserved() {
        assertEquals("stretch", NativeFlowRestCoordinator.normalizeActivity("stretch"));
    }

    @Test public void breatheActivityIsPreserved() {
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity("breathe"));
    }

    @Test public void unknownActivityFallsBackToBreathe() {
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity("unknown"));
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity(""));
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity("walking"));
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity("EYE"));
        assertEquals("breathe", NativeFlowRestCoordinator.normalizeActivity(null));
    }
}
