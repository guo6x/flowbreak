import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import BlockingSettings from "../BlockingSettings";
import { useStore } from "../../hooks/useStore";
import { useNativePermissions } from "../../hooks/useNativePermissions";
import { NativeFlow } from "../../backend/nativeFlow";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../../hooks/useNativePermissions", () => ({
  useNativePermissions: vi.fn(),
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    loadSettings: vi.fn(),
    saveSettings: vi.fn(),
    requestAccessibilityPermission: vi.fn(),
  },
}));

const permissionState = {
  hasUsageStats: true, hasOverlay: true, isIgnoringBattery: false,
  hasNotification: false, hasAccessibility: false, isDomestic: false,
  channel: "base", manufacturer: "", unsupportedDevice: false,
};

function mockPermissions(isNative: boolean, isDomestic: boolean) {
  vi.mocked(useNativePermissions).mockReturnValue({
    isNative,
    permissions: { ...permissionState, isDomestic },
    checking: false,
    error: "",
    refresh: vi.fn(),
  } as ReturnType<typeof useNativePermissions>);
}

function renderBlockingSettings() {
  useStore.setState({
    profile: { ...useStore.getState().profile, sessionLimit: 15, restDuration: 120, allowEmergencyUnlock: false, strongBlockingEnabled: false, onboardingDone: true },
  });
  return render(<BrowserRouter><BlockingSettings /></BrowserRouter>);
}

describe("BlockingSettings", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    mockPermissions(false, false);
    vi.mocked(NativeFlow.requestAccessibilityPermission).mockReset();
    vi.mocked(NativeFlow.requestAccessibilityPermission).mockResolvedValue();
    vi.mocked(NativeFlow.loadSettings).mockReset();
    vi.mocked(NativeFlow.loadSettings).mockResolvedValue({
      limitMinutes: 15,
      restDuration: 120,
      allowEmergencyUnlock: false,
      strongBlockingEnabled: false,
    } as Awaited<ReturnType<typeof NativeFlow.loadSettings>>);
  });

  it("15分钟=12/15/18换算", () => {
    renderBlockingSettings();
    expect(screen.getByText(/12分钟轻提醒/)).toBeInTheDocument();
    expect(screen.getByText(/15分钟强提醒/)).toBeInTheDocument();
    expect(screen.getByText(/18分钟进入休息引导/)).toBeInTheDocument();
  });

  it("紧急解锁Toggle元素为BUTTON且role=switch", () => {
    renderBlockingSettings();
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    expect(toggle.tagName).toBe("BUTTON");
    expect(toggle.getAttribute("role")).toBe("switch");
  });

  it("紧急解锁Toggle初始aria-checked为false", () => {
    renderBlockingSettings();
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    expect(toggle.getAttribute("aria-checked")).toBe("false");
  });

  it("点击紧急解锁切换aria-checked", () => {
    renderBlockingSettings();
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    fireEvent.click(toggle);
    expect(toggle.getAttribute("aria-checked")).toBe("true");
  });

  it("切换限额显示当前值", () => {
    renderBlockingSettings();
    expect(screen.getByText("15 分钟")).toBeInTheDocument();
  });

  it("国内版阻断设置入口必须先显示独立告知，拒绝不能打开系统权限", async () => {
    mockPermissions(true, true);
    renderBlockingSettings();
    fireEvent.click(screen.getByRole("button", { name: "前往授权无障碍服务" }));
    expect(screen.getByRole("dialog", { name: "开启无障碍强阻断前，请了解用途" })).toBeInTheDocument();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "暂不开启" }));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();
  });

  it("国内版阻断设置只能在主动确认之后打开系统权限", async () => {
    mockPermissions(true, true);
    renderBlockingSettings();
    fireEvent.click(screen.getByRole("button", { name: "前往授权无障碍服务" }));
    fireEvent.click(screen.getByRole("button", { name: "我理解，前往系统设置" }));
    await waitFor(() => expect(NativeFlow.requestAccessibilityPermission).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("Play 版阻断设置不提供无障碍强阻断入口", () => {
    mockPermissions(true, false);
    renderBlockingSettings();
    expect(screen.queryByRole("button", { name: "前往授权无障碍服务" })).toBeNull();
    expect(screen.queryByRole("switch", { name: "无障碍强阻断" })).toBeNull();
  });

  it("保存按钮存在", () => {
    renderBlockingSettings();
    expect(screen.getByText("保存设置")).toBeInTheDocument();
  });
});
