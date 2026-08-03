import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import Dashboard from "../Dashboard";
import { useStore } from "../../hooks/useStore";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("@capacitor/core", () => ({
  Capacitor: { isNativePlatform: () => false },
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    getDashboardSummary: vi.fn().mockRejectedValue(new Error("not native")),
    saveDailyReflection: vi.fn(),
  },
}));

let mockIsNative = false;
let mockHasUsage = true;
let mockHasOverlay = true;

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
    mockNavigate.mockClear();
    mockIsNative = false;
    mockHasUsage = true;
    mockHasOverlay = true;
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

  it("点击去授权导航到permissions", () => {
    mockIsNative = true; mockHasUsage = false; mockHasOverlay = true;
    renderDashboard({ isMonitoring: true, blockState: "IDLE" });
    fireEvent.click(screen.getByText("去授权"));
    expect(mockNavigate).toHaveBeenCalledWith("/permissions");
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
});
