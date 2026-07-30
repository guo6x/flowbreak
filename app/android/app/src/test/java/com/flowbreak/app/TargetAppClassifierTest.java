package com.flowbreak.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TargetAppClassifierTest {
    private static final long NOW = 10_000_000L;
    private static final String WECHAT = "com.tencent.mm";
    private static final String VIDEO = "com.example.video";

    private static Set<String> set(String... apps) {
        return new HashSet<>(Arrays.asList(apps));
    }

    @Test public void ordinaryTargetPackageIsTarget() {
        TargetAppClassifier classifier = new TargetAppClassifier(false);
        assertTrue(classifier.isTarget(VIDEO, set(VIDEO), false, 0L, NOW));
    }

    @Test public void nonTargetPackageReturnsFalse() {
        TargetAppClassifier classifier = new TargetAppClassifier(false);
        assertFalse(classifier.isTarget("com.other.app", set(VIDEO), false, 0L, NOW));
    }

    @Test public void playChannelWechatIsNeverTargetSignal() {
        // Play 渠道无法检查视频号子页面，整个微信包不作为目标信号
        TargetAppClassifier play = new TargetAppClassifier(false);
        assertFalse(play.isTarget(WECHAT, set(WECHAT), true, NOW - 1_000L, NOW));
        assertFalse(play.isTarget(WECHAT, set(WECHAT), true, NOW - 100_000L, NOW));
        // 即使 pref=true 且新鲜，Play 渠道也不视为目标
        assertFalse(TargetAppClassifier.isFreshWechatVideoSignal(false, true, NOW - 1_000L, NOW));
    }

    @Test public void domesticWechatWithFreshSignalIsTarget() {
        TargetAppClassifier domestic = new TargetAppClassifier(true);
        assertTrue(domestic.isTarget(WECHAT, set(WECHAT), true, NOW - 1_000L, NOW));
    }

    @Test public void domesticWechatWithPrefFalseIsNotTarget() {
        TargetAppClassifier domestic = new TargetAppClassifier(true);
        assertFalse(domestic.isTarget(WECHAT, set(WECHAT), false, NOW - 1_000L, NOW));
    }

    @Test public void domesticWechatWithDetectedAtZeroIsNotTarget() {
        TargetAppClassifier domestic = new TargetAppClassifier(true);
        assertFalse(domestic.isTarget(WECHAT, set(WECHAT), true, 0L, NOW));
    }

    @Test public void domesticWechatSignalValidAt59999Ms() {
        // 59,999 毫秒仍有效（严格小于 60_000）
        assertTrue(TargetAppClassifier.isFreshWechatVideoSignal(true, true, NOW - 59_999L, NOW));
    }

    @Test public void domesticWechatSignalExpiredAt60000Ms() {
        // 等于 60,000 毫秒视为过期
        assertFalse(TargetAppClassifier.isFreshWechatVideoSignal(true, true, NOW - 60_000L, NOW));
    }

    @Test public void domesticWechatSignalExpiredBeyond60Seconds() {
        assertFalse(TargetAppClassifier.isFreshWechatVideoSignal(true, true, NOW - 61_000L, NOW));
        assertFalse(TargetAppClassifier.isFreshWechatVideoSignal(true, true, NOW - 120_000L, NOW));
    }

    @Test public void nonWechatPackageUnaffectedByVideoChannelPref() {
        // 非微信包不受视频号 pref 影响，只要在目标集合内即为目标
        TargetAppClassifier domestic = new TargetAppClassifier(true);
        assertTrue(domestic.isTarget(VIDEO, set(VIDEO), false, 0L, NOW));
        assertTrue(domestic.isTarget(VIDEO, set(VIDEO), true, NOW - 100_000L, NOW));

        TargetAppClassifier play = new TargetAppClassifier(false);
        assertTrue(play.isTarget(VIDEO, set(VIDEO), false, 0L, NOW));
    }

    @Test public void nullForegroundReturnsFalse() {
        TargetAppClassifier classifier = new TargetAppClassifier(true);
        assertFalse(classifier.isTarget(null, set(VIDEO), true, NOW, NOW));
    }

    @Test public void emptyTargetSetReturnsFalse() {
        TargetAppClassifier classifier = new TargetAppClassifier(true);
        assertFalse(classifier.isTarget(VIDEO, Collections.<String>emptySet(), true, NOW, NOW));
    }
}
