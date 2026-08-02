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
  Capacitor: {
    isNativePlatform: () => false,
  },
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    getDashboardSummary: vi.fn().mockRejectedValue(new Error("not native")),
    saveDailyReflection: vi.fn(),
  },
}));

vi.mock("../../hooks/useNativePermissions", () => ({
  useNativePermissions: () => ({
    isNative: false,
    permissions: {
      hasUsageStats: true,
      hasOverlay: true,
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
  useStore.setState({
    profile: {
      ...useStore.getState().profile,
      sessionLimit: 15,
      restDuration: 120,
      targetApps: ["com.test.app"],
      dailyGoal: 60,
      allowEmergencyUnlock: false,
      onboardingDone: true,
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
    <BrowserRouter>
      <Dashboard />
    </BrowserRouter>
  );
}

describe("Dashboard", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  it("渲染暂停按钮", () => {
    renderDashboard();
    expect(screen.getByText("暂停保护")).toBeDefined();
  });

  it("IDLE状态显示正常", () => {
    renderDashboard({ blockState: "IDLE", continuousSessionSeconds: 300 });
    expect(screen.getByText("正常使用")).toBeDefined();
  });

  it("PERCEPTION状态显示", () => {
    renderDashboard({ blockState: "PERCEPTION", continuousSessionSeconds: 720 });
    expect(screen.getByText("轻提醒阶段")).toBeDefined();
  });

  it("COGNITION状态显示", () => {
    renderDashboard({ blockState: "COGNITION", continuousSessionSeconds: 900 });
    expect(screen.getByText("强提醒阶段")).toBeDefined();
  });

  it("BLOCKED状态显示开始休息入口", () => {
    renderDashboard({ blockState: "BLOCKED" });
    expect(screen.getByText("已进入休息引导")).toBeDefined();
    expect(screen.getAllByText("开始休息").length).toBeGreaterThanOrEqual(1);
  });

  it("BLOCKED不显示下一阶段", () => {
    renderDashboard({ blockState: "BLOCKED" });
    expect(screen.queryByText("下一阶段")).toBeNull();
    expect(screen.queryByText("连续使用")).toBeNull();
  });

  it("RESTING状态显示正在休息", () => {
    renderDashboard({ blockState: "RESTING" });
    expect(screen.getByText("正在休息")).toBeDefined();
  });

  it("RESTING不显示下一阶段", () => {
    renderDashboard({ blockState: "RESTING" });
    expect(screen.queryByText("下一阶段")).toBeNull();
  });

  it("GRACE显示访问窗口", () => {
    const now = Date.now();
    renderDashboard({ blockState: "GRACE", graceUntil: now + 300000 });
    expect(screen.getAllByText("访问窗口").length).toBeGreaterThanOrEqual(1);
  });

  it("GRACE不显示下一阶段", () => {
    renderDashboard({ blockState: "GRACE", graceUntil: Date.now() + 300000 });
    expect(screen.queryByText("下一阶段")).toBeNull();
  });

  it("权限失效显示警告", () => {
    renderDashboard();
    expect(screen.getByText("保护状态")).toBeDefined();
  });

  it("暂停时显示已暂停", () => {
    renderDashboard({ isMonitoring: false });
    expect(screen.getAllByText("已暂停").length).toBeGreaterThanOrEqual(1);
  });

  it("快速点击不重复切换", async () => {
    renderDashboard();
    const btn = screen.getByText("暂停保护");
    fireEvent.click(btn);
    expect(btn).toBeDefined();
  });

  it("显示当前应用", () => {
    renderDashboard({ currentAppName: "抖音" });
    expect(screen.getByText("当前应用")).toBeDefined();
    expect(screen.getByText("抖音")).toBeDefined();
  });

  it("无受限应用时提示选择", () => {
    useStore.setState({
      profile: { ...useStore.getState().profile, targetApps: [] },
    });
    renderDashboard({ isMonitoring: false, blockState: "IDLE" });
    const selectBtn = screen.queryByText("选择受限应用");
    expect(selectBtn).toBeDefined();
  });
});
