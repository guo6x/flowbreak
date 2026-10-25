import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
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
    vi.clearAllMocks();
    mockNavigate.mockClear();
    mockRefresh.mockClear();
    vi.mocked(NativeFlow.requestAccessibilityPermission).mockReset();
    vi.mocked(NativeFlow.requestAccessibilityPermission).mockResolvedValue();
    vi.mocked(NativeFlow.openAutoStartSettings).mockReset();
    vi.mocked(NativeFlow.openAutoStartSettings).mockResolvedValue();
  });

  it("两个必需权限都开启时可继续", () => {
    renderPermissions({ hasUsageStats: true, hasOverlay: true, isNative: true });
    const btn = screen.getByText("继续设置保护");
    expect(btn).toBeInTheDocument();
    expect((btn as HTMLButtonElement).disabled).toBe(false);
  });

  it("缺usage时按钮提示usage", () => {
    renderPermissions({ hasUsageStats: false, hasOverlay: true, isNative: true });
    expect(screen.getByRole("button", { name: "还需开启：使用情况访问" })).toBeDisabled();
  });

  it("缺overlay时按钮提示overlay", () => {
    renderPermissions({ hasUsageStats: true, hasOverlay: false, isNative: true });
    expect(screen.getByRole("button", { name: "还需开启：悬浮窗权限" })).toBeDisabled();
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

  it("国内版无障碍权限在独立说明确认前不得打开系统设置", async () => {
    renderPermissions({ isNative: true, isDomestic: true });
    fireEvent.click(screen.getByText("提升后台稳定性（可稍后设置）"));
    fireEvent.click(screen.getByRole("button", { name: "设置无障碍强阻断" }));
    expect(screen.getByRole("dialog", { name: "开启无障碍强阻断前，请了解用途" })).toBeInTheDocument();
    expect(screen.getByText(/判断值及最近检测时间/)).toBeInTheDocument();
    expect(screen.getByText(/不会读取、保存或上传页面文字/)).toBeInTheDocument();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "暂不开启" }));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();
  });

  it("关闭、Escape 或返回告知时都不打开系统设置", () => {
    renderPermissions({ isNative: true, isDomestic: true });
    fireEvent.click(screen.getByText("提升后台稳定性（可稍后设置）"));

    fireEvent.click(screen.getByRole("button", { name: "设置无障碍强阻断" }));
    fireEvent.click(screen.getByRole("button", { name: "关闭告知" }));
    expect(screen.queryByRole("dialog")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "设置无障碍强阻断" }));
    fireEvent.keyDown(window, { key: "Escape" });
    expect(screen.queryByRole("dialog")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "设置无障碍强阻断" }));
    fireEvent.popState(window);
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();
  });

  it("确认国内版独立无障碍说明后才打开 Android 系统设置", async () => {
    renderPermissions({ isNative: true, isDomestic: true });
    fireEvent.click(screen.getByText("提升后台稳定性（可稍后设置）"));
    fireEvent.click(screen.getByRole("button", { name: "设置无障碍强阻断" }));
    fireEvent.click(screen.getByRole("button", { name: "我理解，前往系统设置" }));

    await waitFor(() => {
      expect(NativeFlow.requestAccessibilityPermission).toHaveBeenCalledTimes(1);
    });
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("权限请求失败后保留说明并允许重试，确认中的重复点击只发出一次请求", async () => {
    renderPermissions({ isNative: true, isDomestic: true });
    fireEvent.click(screen.getByText("提升后台稳定性（可稍后设置）"));
    fireEvent.click(screen.getByRole("button", { name: "设置无障碍强阻断" }));
    let rejectRequest!: (error: Error) => void;
    vi.mocked(NativeFlow.requestAccessibilityPermission).mockImplementationOnce(
      () => new Promise<void>((_resolve, reject) => { rejectRequest = reject; }),
    );
    const confirm = screen.getByRole("button", { name: "我理解，前往系统设置" });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(NativeFlow.requestAccessibilityPermission).toHaveBeenCalledTimes(1);

    rejectRequest(new Error("系统设置暂不可用"));
    await waitFor(() => {
      const dialog = screen.getByRole("dialog");
      expect(within(dialog).getByRole("alert")).toHaveTextContent("系统设置暂不可用");
    });
    fireEvent.click(screen.getByRole("button", { name: "我理解，前往系统设置" }));
    await waitFor(() => expect(NativeFlow.requestAccessibilityPermission).toHaveBeenCalledTimes(2));
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

  it("折叠区域暴露展开状态并显示两项权限完成进度", () => {
    renderPermissions({ hasUsageStats: true, hasOverlay: false, isNative: true });
    const toggle = screen.getByRole("button", { name: "提升后台稳定性（可稍后设置）" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByRole("status")).toHaveTextContent("已开启 1 / 2");
    fireEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(document.getElementById(toggle.getAttribute("aria-controls")!)).toBeInTheDocument();
  });

  it("网页预览不能请求原生必需权限", () => {
    renderPermissions();
    const usage = screen.getByRole("button", { name: "开启使用情况访问" });
    expect(usage).toBeDisabled();
    fireEvent.click(usage);
    expect(NativeFlow.requestUsageStatsPermission).not.toHaveBeenCalled();
    expect(screen.getByText(/正式阻断能力只支持 Android/)).toBeInTheDocument();
  });

  it("原生页面可以直接打开对应权限而不混淆两项设置", () => {
    renderPermissions({ isNative: true });
    fireEvent.click(screen.getByRole("button", { name: "开启悬浮窗权限" }));
    expect(NativeFlow.requestOverlayPermission).toHaveBeenCalledOnce();
    expect(NativeFlow.requestUsageStatsPermission).not.toHaveBeenCalled();
  });

  it.each([true, false])('两渠道均提供小米后台手动路径，且不伪造授权状态 domestic=%s', async isDomestic => {
    renderPermissions({ isNative: true, isDomestic, manufacturer: 'Xiaomi' });
    fireEvent.click(screen.getByText('提升后台稳定性（可稍后设置）'));
    expect(screen.getByText('小米 / 红米 / 黑鲨 后台设置')).toBeInTheDocument();
    expect(screen.getByText(/设置中搜索.*后台自启动/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '设置小米 / 红米 / 黑鲨 后台设置' }));
    await waitFor(() => expect(NativeFlow.openAutoStartSettings).toHaveBeenCalledOnce());
    expect(await screen.findByText('请在系统中确认')).toBeInTheDocument();
    expect(screen.getByText(/打开设置不代表已授权/)).toBeInTheDocument();
  });

  it('三星给出睡眠名单指引，未知厂商仍可打开通用应用详情', () => {
    renderPermissions({ isNative: true, manufacturer: 'Samsung' });
    fireEvent.click(screen.getByText('提升后台稳定性（可稍后设置）'));
    expect(screen.getByText(/从不休眠/)).toBeInTheDocument();
    expect(screen.queryByText(/三星 自启动/)).toBeNull();
  });

  it('未知厂商也提供通用后台设置而不是隐藏入口', async () => {
    renderPermissions({ isNative: true, manufacturer: 'Google' });
    fireEvent.click(screen.getByText('提升后台稳定性（可稍后设置）'));
    fireEvent.click(screen.getByRole('button', { name: '设置系统 后台设置' }));
    await waitFor(() => expect(NativeFlow.openAutoStartSettings).toHaveBeenCalledOnce());
  });

  it('后台受限警告独立于电池豁免且不成为新增硬权限', () => {
    renderPermissions({ isNative: true, manufacturer: 'Samsung', hasUsageStats: true,
      hasOverlay: true, isIgnoringBattery: true, backgroundRestriction: 'restricted' });
    expect(screen.getByRole('alert')).toHaveTextContent('系统当前限制后台运行');
    expect(screen.getByRole('button', { name: '继续设置保护' })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: '查看后台设置建议' }));
    expect(screen.getByText(/从不休眠/)).toBeInTheDocument();
  });

  it.each(['unknown', 'not_restricted', undefined])('不把未知或未受限状态当故障 %s', backgroundRestriction => {
    renderPermissions({ isNative: true, backgroundRestriction });
    expect(screen.queryByText(/系统当前限制后台运行/)).toBeNull();
  });

  it('后台设置全部不可达时如实报错，不标记已打开', async () => {
    vi.mocked(NativeFlow.openAutoStartSettings).mockRejectedValueOnce(new Error('请在系统设置中手动查找 FlowBreak'));
    renderPermissions({ isNative: true, manufacturer: 'OPPO' });
    fireEvent.click(screen.getByText('提升后台稳定性（可稍后设置）'));
    fireEvent.click(screen.getByRole('button', { name: '设置OPPO / 一加 / realme 后台设置' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('请在系统设置中手动查找 FlowBreak');
    expect(screen.queryByText('请在系统中确认')).toBeNull();
  });

  it('先打开成功后重试失败时清除旧成功描述', async () => {
    renderPermissions({ isNative: true, manufacturer: 'OPPO' });
    fireEvent.click(screen.getByText('提升后台稳定性（可稍后设置）'));
    const open = screen.getByRole('button', { name: '设置OPPO / 一加 / realme 后台设置' });
    fireEvent.click(open);
    expect(await screen.findByText('请在系统中确认')).toBeInTheDocument();
    vi.mocked(NativeFlow.openAutoStartSettings).mockRejectedValueOnce(new Error('本次无法打开系统设置'));
    fireEvent.click(open);
    expect(await screen.findByRole('alert')).toHaveTextContent('本次无法打开系统设置');
    expect(screen.queryByText('请在系统中确认')).toBeNull();
    expect(screen.queryByText(/已打开系统设置/)).toBeNull();
  });
});
