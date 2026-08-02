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
    expect(btn).toBeDefined();
    expect((btn as HTMLButtonElement).disabled).toBe(false);
  });

  it("缺usage时按钮提示usage", () => {
    renderPermissions({ hasUsageStats: false, hasOverlay: true, isNative: true });
    expect(screen.getByText("还需开启：使用情况访问")).toBeDefined();
  });

  it("缺overlay时按钮提示overlay", () => {
    renderPermissions({ hasUsageStats: true, hasOverlay: false, isNative: true });
    expect(screen.getByText("还需开启：悬浮窗权限")).toBeDefined();
  });

  it("可选区域默认收起", () => {
    renderPermissions();
    expect(screen.queryByText("通知权限")).toBeNull();
  });

  it("展开后显示可选权限", () => {
    renderPermissions({ isNative: true, isDomestic: true });
    fireEvent.click(screen.getByText("提升后台稳定性（可稍后设置）"));
    expect(screen.getByText("通知权限")).toBeDefined();
  });

  it("手动重新检测调用refresh", () => {
    renderPermissions();
    fireEvent.click(screen.getByText("重新检测权限"));
    expect(mockRefresh).toHaveBeenCalled();
  });

  it("optional权限不阻塞继续", () => {
    renderPermissions({ hasUsageStats: true, hasOverlay: true, isNative: true });
    const btn = screen.getByText("继续设置保护");
    expect((btn as HTMLButtonElement).disabled).toBe(false);
  });
});
