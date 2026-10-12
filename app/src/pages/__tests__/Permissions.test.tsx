import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import Permissions from "../Permissions";
import { NativeFlow } from "../../backend/nativeFlow";
import { useNativePermissions } from "../../hooks/useNativePermissions";

const mockNavigate = vi.fn();
const mockRefresh = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../../hooks/useNativePermissions", () => ({
  useNativePermissions: vi.fn(),
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    requestUsageStatsPermission: vi.fn(),
    requestOverlayPermission: vi.fn(),
    requestNotificationPermission: vi.fn(),
    requestIgnoreBatteryOptimizations: vi.fn(),
    requestAccessibilityPermission: vi.fn(),
    openAutoStartSettings: vi.fn(),
  },
  PermissionState: {},
}));

const basePermissions = {
  hasUsageStats: false,
  hasOverlay: false,
  isIgnoringBattery: false,
  hasNotification: false,
  hasAccessibility: false,
  isDomestic: false,
  channel: "base" as const,
  manufacturer: "",
  unsupportedDevice: false,
  protectionRuntimeAvailable: true,
};

function renderPermissions(overrides: Record<string, any> = {}) {
  (useNativePermissions as any).mockReturnValue({
    isNative: overrides.isNative ?? false,
    permissions: { ...basePermissions, ...overrides },
    checking: false,
    error: "",
    refresh: mockRefresh,
  });
  return render(
    <BrowserRouter>
      <Permissions />
    </BrowserRouter>
  );
}

describe("Permissions", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    mockRefresh.mockClear();
    vi.mocked(NativeFlow.requestAccessibilityPermission).mockReset();
    vi.mocked(NativeFlow.requestAccessibilityPermission).mockResolvedValue();
  });

  it("两个必需权限都开启时可继续", () => {
    renderPermissions({ hasUsageStats: true, hasOverlay: true, isNative: true });
    const btn = screen.getByText("继续设置保护");
    expect(btn).toBeInTheDocument();
    expect((btn as HTMLButtonElement).disabled).toBe(false);
  });

  it("缺usage时按钮提示usage", () => {
    renderPermissions({ hasUsageStats: false, hasOverlay: true, isNative: true });
    expect(screen.getByText("还需开启：使用情况访问")).toBeInTheDocument();
  });

  it("缺overlay时按钮提示overlay", () => {
    renderPermissions({ hasUsageStats: true, hasOverlay: false, isNative: true });
    expect(screen.getByText("还需开启：悬浮窗权限")).toBeInTheDocument();
  });

  it("vivo未开启电池优化豁免时不能继续", () => {
    renderPermissions({
      hasUsageStats: true,
      hasOverlay: true,
      isNative: true,
      manufacturer: "vivo",
      isIgnoringBattery: false,
    });
    const btn = screen.getByText("还需开启：电池优化豁免") as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
    expect(screen.getByText("电池优化豁免")).toBeInTheDocument();
  });

  it("vivo已开启电池优化豁免时可继续", () => {
    renderPermissions({
      hasUsageStats: true,
      hasOverlay: true,
      isNative: true,
      manufacturer: "vivo",
      isIgnoringBattery: true,
    });
    const btn = screen.getByText("继续设置保护") as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });

  it("unsupported vivo显示兼容性阻断且不引导继续开权限", () => {
    renderPermissions({
      hasUsageStats: true,
      hasOverlay: true,
      isNative: true,
      manufacturer: "vivo",
      isIgnoringBattery: true,
      unsupportedDevice: true,
      protectionRuntimeAvailable: false,
    });
    expect(screen.getAllByText("当前版本暂不支持在此设备上开启保护").length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText("提升后台稳定性（可稍后设置）")).toBeNull();
    const btn = screen.getByRole("button", { name: "当前版本暂不支持在此设备上开启保护" });
    expect(btn).toBeDisabled();
  });

  it("可选区域默认收起", () => {
    renderPermissions();
    expect(screen.queryByText("通知权限")).toBeNull();
  });

  it("展开后显示可选权限", () => {
    renderPermissions({ isNative: true, isDomestic: true });
    fireEvent.click(screen.getByText("提升后台稳定性（可稍后设置）"));
    expect(screen.getByText("通知权限")).toBeInTheDocument();
  });

  it("国内版无障碍权限在独立说明确认前不得打开系统设置", async () => {
    renderPermissions({ isNative: true, isDomestic: true });
    fireEvent.click(screen.getByText("提升后台稳定性（可稍后设置）"));
    fireEvent.click(screen.getByRole("button", { name: "设置" }));
    expect(screen.getByRole("dialog", { name: "开启无障碍强阻断前，请了解用途" })).toBeInTheDocument();
    expect(screen.getByText(/不会读取、保存或上传页面文字/)).toBeInTheDocument();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "暂不开启" }));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();
  });

  it("确认国内版独立无障碍说明后才打开 Android 系统设置", async () => {
    renderPermissions({ isNative: true, isDomestic: true });
    fireEvent.click(screen.getByText("提升后台稳定性（可稍后设置）"));
    fireEvent.click(screen.getByRole("button", { name: "设置" }));
    fireEvent.click(screen.getByRole("button", { name: "我理解，前往系统设置" }));

    await waitFor(() => {
      expect(NativeFlow.requestAccessibilityPermission).toHaveBeenCalledTimes(1);
    });
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("Play 版没有国内无障碍授权入口", () => {
    renderPermissions({ isNative: true, isDomestic: false });
    fireEvent.click(screen.getByText("提升后台稳定性（可稍后设置）"));
    expect(screen.queryByText("无障碍强阻断")).toBeNull();
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();
  });

  it("手动重新检测调用refresh", () => {
    renderPermissions();
    fireEvent.click(screen.getByText("重新检测权限"));
    expect(mockRefresh).toHaveBeenCalled();
  });

  it("optional权限不阻塞继续", () => {
    renderPermissions({ hasUsageStats: true, hasOverlay: true, isNative: true });
    const btn = screen.getByText("继续设置保护") as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
  });
});
