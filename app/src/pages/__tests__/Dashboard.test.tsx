import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { Capacitor } from "@capacitor/core";
import { BrowserRouter } from "react-router";
import Dashboard from "../Dashboard";
import { useStore } from "../../hooks/useStore";
import { NativeFlow, NativeProtectionStatus } from "../../backend/nativeFlow";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("@capacitor/core", () => ({
  Capacitor: { isNativePlatform: vi.fn(() => false) },
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    getDashboardSummary: vi.fn().mockRejectedValue(new Error("not native")),
    getUsageStats: vi.fn().mockResolvedValue({ screenTimeSeconds: 0 }),
    saveDailyReflection: vi.fn(),
    getProtectionStatus: vi.fn(),
    saveSettings: vi.fn().mockResolvedValue(undefined),
    startService: vi.fn().mockResolvedValue(undefined),
    stopService: vi.fn().mockResolvedValue(undefined),
  },
}));

let mockIsNative = false;
let mockHasUsage = true;
let mockHasOverlay = true;
let mockUnsupported = false;

function protectionStatus(
  overrides: Partial<NativeProtectionStatus> = {}
): NativeProtectionStatus {
  return {
    status: "ACTIVE",
    reason: "",
    coreProtectionOperational: true,
    strongBlockingRequested: false,
    strongBlockingOperational: true,
    strongBlockingDegradedReason: "",
    monitoringConfigured: true,
    monitoringEnabled: true,
    targetCount: 1,
    serviceRuntimeActive: true,
    monitorThreadAlive: true,
    heartbeatFresh: true,
    currentServiceHeartbeatAt: Date.now(),
    accessibilityEnabledInSettings: false,
    accessibilityRuntimeConnected: false,
    protectionRuntimeAvailable: true,
    permissions: {
      hasUsageStats: true,
      hasOverlay: true,
      isIgnoringBattery: false,
      hasNotification: true,
      hasAccessibility: false,
      isDomestic: false,
      channel: "play",
      manufacturer: "redmi",
      unsupportedDevice: false,
      protectionRuntimeAvailable: true,
    },
    ...overrides,
  };
}

vi.mock("../../hooks/useNativePermissions", () => ({
  useNativePermissions: () => ({
    isNative: mockIsNative,
    permissions: {
      hasUsageStats: mockHasUsage,
      hasOverlay: mockHasOverlay,
      isIgnoringBattery: false,
      hasNotification: false,
      hasAccessibility: false,
      isDomestic: false,
      channel: "base" as const,
      manufacturer: "",
      unsupportedDevice: mockUnsupported,
      protectionRuntimeAvailable: !mockUnsupported,
    },
    checking: false,
    error: "",
    refresh: vi.fn(),
  }),
}));

function renderDashboard(overrides: Record<string, any> = {}) {
  const profileOverrides = overrides.profile || {};
  delete overrides.profile;
  useStore.setState({
    profile: {
      ...useStore.getState().profile,
      sessionLimit: 15,
      restDuration: 120,
      targetApps: ["com.test.app"],
      dailyGoal: 60,
      allowEmergencyUnlock: false,
      onboardingDone: true,
      ...profileOverrides,
    },
    isMonitoring: true,
    blockState: "IDLE" as any,
    continuousSessionSeconds: 0,
    graceUntil: 0,
    serviceError: "",
    todayStats: {
      ...useStore.getState().todayStats,
      totalScreenTime: 0,
      interventionCount: 0,
      restCount: 0,
    },
    fatigueScore: 0,
    fatigueLevel: "NONE" as any,
    currentAppName: "",
    ...overrides,
  });
  return render(
    <BrowserRouter><Dashboard /></BrowserRouter>
  );
}

describe("Dashboard", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(false);
    vi.mocked(NativeFlow.getUsageStats).mockReset().mockResolvedValue({ screenTimeSeconds: 0 });
    mockNavigate.mockClear();
    mockIsNative = false;
    mockHasUsage = true;
    mockHasOverlay = true;
    mockUnsupported = false;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(null as never);
  });

  it("渲染暂停按钮", () => {
    renderDashboard();
    expect(screen.getByText("暂停保护")).toBeInTheDocument();
  });

  it("IDLE状态显示正常", () => {
    renderDashboard({ blockState: "IDLE", continuousSessionSeconds: 300 });
    expect(screen.getByText("正常使用")).toBeInTheDocument();
  });

  it("PERCEPTION状态显示", () => {
    renderDashboard({ blockState: "PERCEPTION", continuousSessionSeconds: 720 });
    expect(screen.getByText("轻提醒阶段")).toBeInTheDocument();
  });

  it("COGNITION状态显示", () => {
    renderDashboard({ blockState: "COGNITION", continuousSessionSeconds: 900 });
    expect(screen.getByText("强提醒阶段")).toBeInTheDocument();
  });

  it("BLOCKED状态显示开始休息入口", () => {
    renderDashboard({ blockState: "BLOCKED" });
    expect(screen.getByText("已进入休息引导")).toBeInTheDocument();
    const restBtns = screen.getAllByText("开始休息");
    expect(restBtns.length).toBeGreaterThanOrEqual(1);
  });

  it("BLOCKED不显示下一阶段", () => {
    renderDashboard({ blockState: "BLOCKED" });
    expect(screen.queryByText("下一阶段")).toBeNull();
    expect(screen.queryByText("连续使用")).toBeNull();
  });

  it("RESTING状态显示正在休息", () => {
    renderDashboard({ blockState: "RESTING" });
    expect(screen.getByText("正在休息")).toBeInTheDocument();
  });

  it("RESTING不显示下一阶段", () => {
    renderDashboard({ blockState: "RESTING" });
    expect(screen.queryByText("下一阶段")).toBeNull();
  });

  it("GRACE显示访问窗口", () => {
    const now = Date.now();
    renderDashboard({ blockState: "GRACE", graceUntil: now + 300000 });
    const graceNodes = screen.getAllByText("访问窗口");
    expect(graceNodes.length).toBeGreaterThanOrEqual(1);
  });

  it("GRACE不显示下一阶段", () => {
    renderDashboard({ blockState: "GRACE", graceUntil: Date.now() + 300000 });
    expect(screen.queryByText("下一阶段")).toBeNull();
  });

  it("暂停时显示已暂停", () => {
    renderDashboard({ isMonitoring: false });
    const pausedNodes = screen.getAllByText("已暂停");
    expect(pausedNodes.length).toBeGreaterThanOrEqual(1);
  });

  it("显示当前应用", () => {
    renderDashboard({ currentAppName: "抖音" });
    expect(screen.getByText("当前应用")).toBeInTheDocument();
    expect(screen.getByText("抖音")).toBeInTheDocument();
  });

  it("无应用且isMonitoring=true显示未配置状态", () => {
    renderDashboard({
      isMonitoring: true, blockState: "IDLE",
      profile: { targetApps: [] },
    });
    const badges = screen.getAllByText("未配置"); expect(badges.length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText("已开启")).toBeNull();
    expect(screen.getByText("选择受限应用")).toBeInTheDocument();
    expect(screen.getByText("请先选择受限应用")).toBeInTheDocument();
    const btn = screen.getByText("请先选择受限应用") as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("无应用时点击主按钮不触发切换", () => {
    renderDashboard({
      isMonitoring: false, blockState: "IDLE",
      profile: { targetApps: [] },
    });
    const initialMonitoring = useStore.getState().isMonitoring;
    fireEvent.click(screen.getByText("请先选择受限应用"));
    expect(useStore.getState().isMonitoring).toBe(initialMonitoring);
  });

  it("残留状态-无应用时isMonitoring=true清理所有活跃指示", () => {
    const futureGrace = Date.now() + 300000;
    renderDashboard({
      isMonitoring: true, blockState: "IDLE",
      currentAppName: "抖音", graceUntil: futureGrace,
      profile: { targetApps: [] },
    });
    // Status card badge
    const badges = screen.getAllByText("未配置"); expect(badges.length).toBeGreaterThanOrEqual(1);
    // Header must NOT show "监控中"
    expect(screen.queryByText("监控中")).toBeNull();
    // Header must NOT show "当前: 抖音"
    expect(screen.queryByText("当前: 抖音")).toBeNull();
    // Status card must NOT show 当前应用
    expect(screen.queryByText("当前应用")).toBeNull();
    // Grace window stat must show "--"
    const cards = screen.getAllByText("--");
    expect(cards.length).toBeGreaterThanOrEqual(1);
    // Main button text
    expect(screen.getByText("请先选择受限应用")).toBeInTheDocument();
    // aria-label
    expect(screen.getByLabelText("请先选择受限应用")).toBeInTheDocument();
    // disabled
    const btn = screen.getByLabelText("请先选择受限应用") as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
    // No 下一阶段
    expect(screen.queryByText("下一阶段")).toBeNull();
  });

  it("权限失效-缺usage显示警告", () => {
    mockIsNative = true; mockHasUsage = false; mockHasOverlay = true;
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });
    expect(screen.getByText("使用情况访问权限已失效")).toBeInTheDocument();
  });

  it("权限失效-缺overlay显示警告", () => {
    mockIsNative = true; mockHasUsage = true; mockHasOverlay = false;
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });
    expect(screen.getByText("悬浮窗权限已失效")).toBeInTheDocument();
  });

  it("权限齐全时不显示警告", () => {
    mockIsNative = true; mockHasUsage = true; mockHasOverlay = true;
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });
    expect(screen.queryByText("使用情况访问权限已失效")).toBeNull();
    expect(screen.queryByText("悬浮窗权限已失效")).toBeNull();
  });

  it("unsupported OEM显示阻断状态且不能开启保护", () => {
    mockIsNative = true;
    mockUnsupported = true;
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });
    expect(screen.getAllByText("设备不支持").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("当前版本暂不支持在此设备上开启保护").length).toBeGreaterThanOrEqual(1);
    const button = screen.getByRole("button", { name: "当前版本暂不支持在此设备上开启保护" });
    expect(button).toBeDisabled();
  });

  it("点击去授权导航到permissions", () => {
    mockIsNative = true; mockHasUsage = false; mockHasOverlay = true;
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });
    fireEvent.click(screen.getByText("去授权"));
    expect(mockNavigate).toHaveBeenCalledWith("/permissions");
  });

  it("native service absent never renders protection as active", async () => {
    mockIsNative = true;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(protectionStatus({
      status: "STARTING_OR_UNCONFIRMED",
      reason: "SERVICE_NOT_RUNNING",
      coreProtectionOperational: false,
      serviceRuntimeActive: false,
      monitorThreadAlive: false,
      heartbeatFresh: false,
      currentServiceHeartbeatAt: 0,
    }));
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });

    await waitFor(() => expect(screen.getAllByText("正在确认").length).toBeGreaterThanOrEqual(1));
    expect(screen.queryByText("已开启")).toBeNull();
    expect(screen.getByText("保护服务当前未运行，保护尚未生效")).toBeInTheDocument();
  });

  it("native stale heartbeat never renders protection as active", async () => {
    mockIsNative = true;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(protectionStatus({
      status: "STARTING_OR_UNCONFIRMED",
      reason: "HEARTBEAT_STALE",
      coreProtectionOperational: false,
      heartbeatFresh: false,
      currentServiceHeartbeatAt: Date.now() - 60_000,
    }));
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });

    await waitFor(() => expect(screen.getAllByText("正在确认").length).toBeGreaterThanOrEqual(1));
    expect(screen.queryByText("已开启")).toBeNull();
    expect(screen.getByText("保护服务心跳已过期，保护尚未生效")).toBeInTheDocument();
  });

  it("native dead monitor thread never renders protection as active", async () => {
    mockIsNative = true;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(protectionStatus({
      status: "STARTING_OR_UNCONFIRMED",
      reason: "MONITOR_THREAD_NOT_ALIVE",
      coreProtectionOperational: false,
      monitorThreadAlive: false,
    }));
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });

    await waitFor(() => expect(screen.getAllByText("正在确认").length).toBeGreaterThanOrEqual(1));
    expect(screen.queryByText("已开启")).toBeNull();
    expect(screen.getByText("保护监控线程当前不可用，保护尚未生效")).toBeInTheDocument();
  });

  it.each([
    ["TARGET_SET_MISMATCH", "受限应用配置与保护服务不一致，当前保护未生效，请重新开启保护以同步设置"],
    ["MONITORING_STATE_MISMATCH", "保护开关状态与系统服务不一致，当前保护未生效，请重新开启保护"],
    ["LIMIT_MINUTES_MISMATCH", "单次使用时长与保护服务不一致，当前保护未生效，请重新开启保护以同步设置"],
  ])("native %s integrity mismatch displays actionable Chinese guidance", async (reason, message) => {
    mockIsNative = true;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(protectionStatus({
      status: "DEGRADED",
      reason,
      coreProtectionOperational: false,
    }));
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });

    await waitFor(() => expect(screen.getAllByText("保护降级").length).toBeGreaterThanOrEqual(1));
    expect(screen.queryByText("已开启")).toBeNull();
    expect(screen.getByText(message)).toBeInTheDocument();
  });

  it("native missing usage or overlay is degraded rather than green", async () => {
    mockIsNative = true;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(protectionStatus({
      status: "DEGRADED",
      reason: "USAGE_ACCESS_MISSING",
      coreProtectionOperational: false,
      permissions: {
        ...protectionStatus().permissions,
        hasUsageStats: false,
      },
    }));
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });

    await waitFor(() => expect(screen.getAllByText("保护降级").length).toBeGreaterThanOrEqual(1));
    expect(screen.queryByText("已开启")).toBeNull();
    expect(screen.getByText("使用情况访问权限已失效")).toBeInTheDocument();
  });

  it("native strong blocking degradation keeps core status separate", async () => {
    mockIsNative = true;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(protectionStatus({
      status: "DEGRADED",
      reason: "ACCESSIBILITY_MISSING",
      coreProtectionOperational: true,
      strongBlockingRequested: true,
      strongBlockingOperational: false,
      strongBlockingDegradedReason: "ACCESSIBILITY_MISSING",
    }));
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });

    await waitFor(() => expect(screen.getAllByText("保护降级").length).toBeGreaterThanOrEqual(1));
    expect(screen.getByText("无障碍强阻断不可用，仅保留核心悬浮窗保护")).toBeInTheDocument();
    expect(screen.getByText("连续使用")).toBeInTheDocument();
  });

  it("native accessibility enabled but disconnected stays degraded without disabling core protection", async () => {
    mockIsNative = true;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(protectionStatus({
      status: "DEGRADED",
      reason: "ACCESSIBILITY_SERVICE_NOT_CONNECTED",
      coreProtectionOperational: true,
      strongBlockingRequested: true,
      strongBlockingOperational: false,
      strongBlockingDegradedReason: "ACCESSIBILITY_SERVICE_NOT_CONNECTED",
      accessibilityEnabledInSettings: true,
      accessibilityRuntimeConnected: false,
    }));
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });

    await waitFor(() => expect(screen.getAllByText("保护降级").length).toBeGreaterThanOrEqual(1));
    expect(screen.getByText("无障碍强阻断尚未运行，核心悬浮窗保护仍有效")).toBeInTheDocument();
    expect(screen.getByText("连续使用")).toBeInTheDocument();
  });

  it("native ACTIVE without core truth never renders as active", async () => {
    mockIsNative = true;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(protectionStatus({
      status: "ACTIVE",
      coreProtectionOperational: false,
      reason: "HEARTBEAT_STALE",
      heartbeatFresh: false,
    }));
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });

    await waitFor(() => expect(screen.getAllByText("正在确认").length).toBeGreaterThanOrEqual(1));
    expect(screen.queryByText("已开启")).toBeNull();
  });

  it("native status diverged from local intent invokes the native stop directly", async () => {
    mockIsNative = true;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(protectionStatus({
      monitoringEnabled: true,
    }));
    renderDashboard({ isMonitoring: false, blockState: "IDLE" });

    await waitFor(() => expect(screen.getByText("暂停保护")).toBeInTheDocument());
    fireEvent.click(screen.getByText("暂停保护"));

    await waitFor(() => expect(NativeFlow.stopService).toHaveBeenCalledTimes(1));
  });

  it("native status diverged from local intent invokes the native start directly", async () => {
    mockIsNative = true;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(protectionStatus({
      monitoringEnabled: false,
      status: "PAUSED",
      coreProtectionOperational: false,
      serviceRuntimeActive: false,
      monitorThreadAlive: false,
      heartbeatFresh: false,
    }));
    vi.mocked(NativeFlow.saveSettings).mockClear();
    vi.mocked(NativeFlow.startService).mockClear();
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });

    await waitFor(() => expect(screen.getByText("开启保护")).toBeInTheDocument());
    fireEvent.click(screen.getByText("开启保护"));

    await waitFor(() => {
      expect(NativeFlow.saveSettings).toHaveBeenCalledWith({
        limitMinutes: 15,
        targetApps: ["com.test.app"],
        monitoringEnabled: true,
      });
      expect(NativeFlow.startService).toHaveBeenCalledWith({
        limitMinutes: 15,
        apps: ["com.test.app"],
        monitoringEnabled: true,
      });
    });
  });

  it("native PAUSED remains paused in the UI until the owner starts it", async () => {
    mockIsNative = true;
    vi.mocked(NativeFlow.getProtectionStatus).mockResolvedValue(protectionStatus({
      status: "PAUSED",
      reason: "MONITORING_DISABLED",
      monitoringEnabled: false,
      coreProtectionOperational: false,
      serviceRuntimeActive: false,
      monitorThreadAlive: false,
      heartbeatFresh: false,
    }));
    vi.mocked(NativeFlow.startService).mockClear();
    renderDashboard({ isMonitoring: false, blockState: "IDLE" });

    await waitFor(() => expect(screen.getByText("开启保护")).toBeInTheDocument());
    expect(screen.queryByText("暂停保护")).toBeNull();
    expect(screen.queryByText("已开启")).toBeNull();
    expect(NativeFlow.startService).not.toHaveBeenCalled();
  });

  it("快速点击300ms内不重复切换", async () => {
    renderDashboard();
    const btn = screen.getByText("暂停保护");
    fireEvent.click(btn);
    fireEvent.click(btn);
    expect(useStore.getState().isMonitoring).toBe(false);
    await new Promise(r => setTimeout(r, 400));
    fireEvent.click(btn);
    expect(useStore.getState().isMonitoring).toBe(true);
  });

  it("serviceError在页面中只出现一次且保留操作入口", () => {
    renderDashboard({ serviceError: "测试错误" });
    // Error text should appear exactly once (in status card, not duplicated below)
    const errors = screen.getAllByText("测试错误");
    expect(errors.length).toBe(1);
    // Check that operation buttons are still present
    expect(screen.getByText("检查权限")).toBeInTheDocument();
    expect(screen.getByText("重试开启")).toBeInTheDocument();
  });

  it.each(["NONE", "PERCEPTION", "COGNITION", "ACTION"])("%s stage does not claim physiological fatigue detection", level => {
    renderDashboard({ fatigueLevel: level, fatigueScore: 0.9 });
    expect(screen.queryByText(/未检测到明显疲劳|认知疲劳|轻度疲劳|疲劳指数/)).not.toBeInTheDocument();
    expect(screen.getByText("今日目标应用时间")).toBeInTheDocument();
  });

  it("paused protection never presents a stale normal-use state as operational", () => {
    renderDashboard({ isMonitoring: false, blockState: "IDLE" });
    expect(screen.queryByText("正常使用")).not.toBeInTheDocument();
    expect(screen.getByText("当前未开启保护")).toBeInTheDocument();
  });

  it("exposes an accessible goal progress bar and clamps progress at 100%", () => {
    renderDashboard({ todayStats: { totalScreenTime: 7200, restCount: 0, interventionCount: 0 }, profile: { dailyGoal: 60 } });
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "100");
    expect(screen.getByRole("progressbar")).toHaveAccessibleName("今日目标应用使用时间占每日目标");
  });

  it("does not produce NaN progress for a missing daily goal", () => {
    renderDashboard({ profile: { dailyGoal: 0 } });
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "0");
  });

  it("reflection buttons expose the selected state to assistive technology", () => {
    renderDashboard();
    const helped = screen.getByRole("button", { name: "有帮助" });
    expect(helped).toHaveAttribute("aria-pressed", "false");
    fireEvent.click(helped);
    expect(helped).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(screen.getByRole("button", { name: "一般" }));
    expect(helped).toHaveAttribute("aria-pressed", "false");
  });

  it("places the core rest action before optional daily reflection", () => {
    renderDashboard();
    const rest = screen.getByRole("button", { name: /^开始休息/ });
    const reflection = screen.getByTestId("daily-reflection");
    expect(rest.compareDocumentPosition(reflection) & Node.DOCUMENT_POSITION_FOLLOWING).not.toBe(0);
  });

  it("does not show local counters while native dashboard counts are still loading", async () => {
    mockIsNative = true;
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(true);
    let resolve!: (value: Awaited<ReturnType<typeof NativeFlow.getDashboardSummary>>) => void;
    vi.mocked(NativeFlow.getDashboardSummary).mockReturnValueOnce(new Promise(resolver => { resolve = resolver; }));
    renderDashboard({ todayStats: { totalScreenTime: 0, interventionCount: 97, restCount: 98 } });
    expect(screen.queryByText("97")).not.toBeInTheDocument();
    expect(screen.queryByText("98")).not.toBeInTheDocument();
    await act(async () => resolve({
      blockCount: 3, restCount: 2, rescuedSeconds: 0, unlockSeconds: 1200, points: 0, streak: 0,
      pullbackOutcomeCount: 0, successfulPullbackCount: 0, postRestReturnCount: 0,
      postRestTargetSeconds: 0, reflectionValue: 0,
    }));
    expect(screen.getByText("完成休息").nextElementSibling).toHaveTextContent("2次");
    expect(screen.getByText("今日阻断").nextElementSibling).toHaveTextContent("3次");
  });

  it("does not substitute local counters after native summary errors", async () => {
    mockIsNative = true;
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(true);
    vi.mocked(NativeFlow.getDashboardSummary).mockRejectedValueOnce(new Error("native read failed"));
    renderDashboard({ todayStats: { totalScreenTime: 0, interventionCount: 97, restCount: 98 } });
    expect(await screen.findByText("今日计数暂未读取成功，稍后会自动重试。")).toBeInTheDocument();
    expect(screen.getByText("完成休息").nextElementSibling).toHaveTextContent("--次");
    expect(screen.queryByText("97")).not.toBeInTheDocument();
    expect(screen.queryByText("98")).not.toBeInTheDocument();
  });

  it("shows unknown native time instead of the legacy local total until the read succeeds", async () => {
    mockIsNative = true;
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(true);
    let resolve!: (value: { screenTimeSeconds: number }) => void;
    vi.mocked(NativeFlow.getUsageStats).mockReturnValueOnce(new Promise(resolver => { resolve = resolver; }));
    renderDashboard({ todayStats: { totalScreenTime: 72000, restCount: 0, interventionCount: 0 } });
    expect(screen.getByTestId("dashboard-total-time")).toHaveTextContent("--");
    expect(screen.getByRole("progressbar")).not.toHaveAttribute("aria-valuenow");
    expect(screen.queryByText("20小时")).not.toBeInTheDocument();
    await act(async () => resolve({ screenTimeSeconds: 600 }));
    expect(screen.getByTestId("dashboard-total-time")).toHaveTextContent("10分钟");
  });

  it("does not fall back to the legacy local total when native usage cannot be read", async () => {
    mockIsNative = true;
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(true);
    vi.mocked(NativeFlow.getUsageStats).mockRejectedValueOnce(new Error("native usage unavailable"));
    renderDashboard({ todayStats: { totalScreenTime: 72000, restCount: 0, interventionCount: 0 } });
    expect(await screen.findByText("暂时无法读取设备记录")).toBeInTheDocument();
    expect(screen.getByTestId("dashboard-total-time")).toHaveTextContent("--");
    expect(screen.queryByText("20小时")).not.toBeInTheDocument();
  });
});
