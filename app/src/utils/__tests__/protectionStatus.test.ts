import { describe, it, expect } from "vitest";
import { getNextThreshold, formatRemainingTime, formatCountdown, translateLimit, getProtectionViewModel, STATE_LABELS } from "../protectionStatus";
import type { BlockState } from "../../backend/nativeFlow";

describe("getNextThreshold", () => {
  it("session低于80%返回轻提醒", () => {
    const r = getNextThreshold(0, 15);
    expect(r.label).toBe("进入轻提醒");
    expect(r.blocked).toBe(false);
    expect(r.remainingSeconds).toBeGreaterThan(0);
  });

  it("80%到100%返回强提醒", () => {
    const r = getNextThreshold(12 * 60, 15);
    expect(r.label).toBe("进入强提醒");
    expect(r.blocked).toBe(false);
  });

  it("100%到120%返回休息引导", () => {
    const r = getNextThreshold(15 * 60, 15);
    expect(r.label).toBe("进入休息引导");
    expect(r.blocked).toBe(false);
  });

  it("120%以上blocked为true", () => {
    const r = getNextThreshold(18 * 60, 15);
    expect(r.label).toBe("已进入休息引导");
    expect(r.blocked).toBe(true);
    expect(r.remainingSeconds).toBe(0);
  });

  it("limit为0时不产生NaN", () => {
    const r = getNextThreshold(100, 0);
    expect(r.label).toBe("未设置限额");
    expect(r.remainingSeconds).toBe(0);
    expect(isNaN(r.remainingSeconds)).toBe(false);
  });

  it("limit为负数不产生异常", () => {
    const r = getNextThreshold(100, -5);
    expect(r.label).toBe("未设置限额");
    expect(isNaN(r.remainingSeconds)).toBe(false);
  });
});

describe("formatRemainingTime", () => {
  it("<=0显示即将触发", () => {
    expect(formatRemainingTime(0)).toBe("即将触发");
    expect(formatRemainingTime(-1)).toBe("即将触发");
  });

  it("秒级显示", () => {
    expect(formatRemainingTime(30)).toBe("30 秒");
  });

  it("分钟级显示", () => {
    expect(formatRemainingTime(120)).toBe("2 分钟");
  });

  it("分秒结合显示", () => {
    expect(formatRemainingTime(125)).toBe("2 分 5 秒");
  });

  it("小时级显示", () => {
    expect(formatRemainingTime(3660)).toBe("1 小时 1 分钟");
  });
});

describe("formatCountdown", () => {
  it("负数返回00:00", () => {
    expect(formatCountdown(-1)).toBe("00:00");
  });

  it("正常格式化", () => {
    expect(formatCountdown(65)).toBe("01:05");
  });

  it("0返回00:00", () => {
    expect(formatCountdown(0)).toBe("00:00");
  });
});

describe("translateLimit", () => {
  it("15分钟=12/15/18", () => {
    const t = translateLimit(15);
    expect(t.perceptionMinutes).toBe(12);
    expect(t.cognitionMinutes).toBe(15);
    expect(t.blockedMinutes).toBe(18);
  });

  it("25分钟=20/25/30", () => {
    const t = translateLimit(25);
    expect(t.perceptionMinutes).toBe(20);
    expect(t.cognitionMinutes).toBe(25);
    expect(t.blockedMinutes).toBe(30);
  });

  it("包含label字段", () => {
    const t = translateLimit(15);
    expect(t.perceptionLabel).toContain("12");
    expect(t.cognitionLabel).toContain("15");
    expect(t.blockedLabel).toContain("18");
  });

  it("包含秒数字段", () => {
    const t = translateLimit(15);
    expect(t.perceptionSeconds).toBeGreaterThan(0);
    expect(t.cognitionSeconds).toBe(15 * 60);
    expect(t.blockedSeconds).toBeGreaterThan(t.cognitionSeconds);
  });
});

describe("getProtectionViewModel", () => {
  const base = {
    isMonitoring: true,
    serviceError: "",
    blockState: "IDLE" as BlockState,
    sessionSeconds: 0,
    limitMinutes: 15,
    graceUntil: 0,
    now: Date.now(),
    missingPermissions: false,
    missingPermissionLabel: "",
    noTargetApps: false,
    currentAppName: "",
  };

  it("IDLE有nextThreshold", () => {
    const vm = getProtectionViewModel({ ...base, blockState: "IDLE" });
    expect(vm.nextThreshold.label).toBeTruthy();
  });

  it("PERCEPTION有nextThreshold", () => {
    const vm = getProtectionViewModel({ ...base, blockState: "PERCEPTION", sessionSeconds: 12 * 60 });
    expect(vm.nextThreshold.label).toBeTruthy();
  });

  it("COGNITION有nextThreshold", () => {
    const vm = getProtectionViewModel({ ...base, blockState: "COGNITION", sessionSeconds: 15 * 60 });
    expect(vm.nextThreshold.label).toBeTruthy();
  });

  it("BLOCKED无nextThreshold", () => {
    const vm = getProtectionViewModel({ ...base, blockState: "BLOCKED" });
    expect(vm.nextThreshold.label).toBe("");
  });

  it("RESTING无nextThreshold", () => {
    const vm = getProtectionViewModel({ ...base, blockState: "RESTING" });
    expect(vm.nextThreshold.label).toBe("");
  });

  it("GRACE计算访问窗口", () => {
    const now = Date.now();
    const graceUntil = now + 300000;
    const vm = getProtectionViewModel({ ...base, blockState: "GRACE", graceUntil, now });
    expect(vm.nextThreshold.label).toBe("");
    expect(vm.graceRemainingSeconds).toBeGreaterThan(0);
    expect(vm.graceRemainingSeconds).toBeLessThanOrEqual(300);
  });

  it("GRACE无nextThreshold", () => {
    const vm = getProtectionViewModel({ ...base, blockState: "GRACE", graceUntil: Date.now() + 60000, now: Date.now() });
    expect(vm.nextThreshold.label).toBe("");
  });

  it("missingPermissions保留", () => {
    const vm = getProtectionViewModel({ ...base, missingPermissions: true, missingPermissionLabel: "悬浮窗权限" });
    expect(vm.missingPermissions).toBe(true);
    expect(vm.missingPermissionLabel).toBe("悬浮窗权限");
  });

  it("noTargetApps保留", () => {
    const vm = getProtectionViewModel({ ...base, noTargetApps: true });
    expect(vm.noTargetApps).toBe(true);
  });

  it("currentAppName保留", () => {
    const vm = getProtectionViewModel({ ...base, currentAppName: "抖音" });
    expect(vm.currentAppName).toBe("抖音");
  });

  it("hasError在监控且服务出错时为true", () => {
    const vm = getProtectionViewModel({ ...base, serviceError: "服务错误" });
    expect(vm.hasError).toBe(true);
  });

  it("hasError在未监控时为false", () => {
    const vm = getProtectionViewModel({ ...base, isMonitoring: false, serviceError: "服务错误" });
    expect(vm.hasError).toBe(false);
  });
});

describe("STATE_LABELS", () => {
  it("包含所有状态", () => {
    expect(STATE_LABELS.IDLE).toBe("正常使用");
    expect(STATE_LABELS.PERCEPTION).toBe("轻提醒阶段");
    expect(STATE_LABELS.COGNITION).toBe("强提醒阶段");
    expect(STATE_LABELS.BLOCKED).toBe("已进入休息引导");
    expect(STATE_LABELS.RESTING).toBe("正在休息");
    expect(STATE_LABELS.GRACE).toBe("访问窗口");
  });
});
