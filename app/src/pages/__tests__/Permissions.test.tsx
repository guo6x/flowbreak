import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import Permissions from "../Permissions";
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

  it("电池优化豁免保持可选，不阻塞核心权限继续", () => {
    renderPermissions({
      hasUsageStats: true,
      hasOverlay: true,
      isNative: true,
      manufacturer: "vivo",
      isIgnoringBattery: false,
    });
    const btn = screen.getByText("继续设置保护") as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
    fireEvent.click(screen.getByText("提升后台稳定性（可稍后设置）"));
    expect(screen.getByText("电池优化豁免")).toBeInTheDocument();
  });

  it("电池优化豁免开启时同样可继续", () => {
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
