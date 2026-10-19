import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor, within } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import BlockingSettings from "../BlockingSettings";
import { useStore } from "../../hooks/useStore";
import { NativeFlow } from "../../backend/nativeFlow";

const mockNavigate = vi.fn();
let isNative = false;
let permissionOverrides: Record<string, boolean> = {};

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../../hooks/useNativePermissions", () => ({
  useNativePermissions: () => ({
    isNative,
    permissions: {
      hasUsageStats: true, hasOverlay: true, isIgnoringBattery: false,
      hasNotification: false, hasAccessibility: false, isDomestic: false,
      channel: "base" as const, manufacturer: "",
      unsupportedDevice: false, protectionRuntimeAvailable: true,
      ...permissionOverrides,
    },
    checking: false, error: "", refresh: vi.fn(),
  }),
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    loadSettings: vi.fn().mockResolvedValue({ limitMinutes: 15, restDuration: 120, allowEmergencyUnlock: false, strongBlockingEnabled: false }),
    saveSettings: vi.fn().mockResolvedValue(undefined),
    requestAccessibilityPermission: vi.fn().mockResolvedValue(undefined),
  },
}));

function mockPermissions(native: boolean, domestic: boolean, unsupportedDevice = false) {
  isNative = native;
  permissionOverrides = { isDomestic: domestic, unsupportedDevice };
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
    isNative = false;
    permissionOverrides = {};
    vi.mocked(NativeFlow.saveSettings).mockClear().mockResolvedValue(undefined);
    vi.mocked(NativeFlow.loadSettings).mockClear().mockResolvedValue({ limitMinutes: 15, restDuration: 120, allowEmergencyUnlock: false, strongBlockingEnabled: false, targetApps: [], monitoringEnabled: true, channel: "base" });
    vi.mocked(NativeFlow.requestAccessibilityPermission).mockReset().mockResolvedValue(undefined);
  });

  it("15分钟=12/15/18换算", () => {
    renderBlockingSettings();
    expect(screen.getByRole("group", { name: "轻提醒时间" })).toHaveTextContent("12分钟轻提醒");
    expect(screen.getByRole("group", { name: "强提醒时间" })).toHaveTextContent("15分钟强提醒");
    expect(screen.getByRole("group", { name: "休息引导时间" })).toHaveTextContent("18分钟休息引导");
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
    expect(screen.getByText(/判断值及最近检测时间/)).toBeInTheDocument();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "暂不开启" }));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();
  });

  it("关闭、Escape 或返回告知时都不打开系统权限", () => {
    mockPermissions(true, true);
    renderBlockingSettings();

    fireEvent.click(screen.getByRole("button", { name: "前往授权无障碍服务" }));
    fireEvent.click(screen.getByRole("button", { name: "关闭告知" }));
    expect(screen.queryByRole("dialog")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "前往授权无障碍服务" }));
    fireEvent.keyDown(window, { key: "Escape" });
    expect(screen.queryByRole("dialog")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "前往授权无障碍服务" }));
    fireEvent.popState(window);
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

  it("权限请求失败时保留说明并可重试，确认中的重复点击不会重复请求", async () => {
    mockPermissions(true, true);
    renderBlockingSettings();
    fireEvent.click(screen.getByRole("button", { name: "前往授权无障碍服务" }));
    let rejectRequest!: (error: Error) => void;
    vi.mocked(NativeFlow.requestAccessibilityPermission).mockImplementationOnce(
      () => new Promise<void>((_resolve, reject) => { rejectRequest = reject; }),
    );
    const confirm = screen.getByRole("button", { name: "我理解，前往系统设置" });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(NativeFlow.requestAccessibilityPermission).toHaveBeenCalledTimes(1);

    rejectRequest(new Error("无法打开系统设置"));
    await waitFor(() => {
      const dialog = screen.getByRole("dialog");
      expect(within(dialog).getByRole("alert")).toHaveTextContent("无法打开无障碍设置");
    });
    fireEvent.click(screen.getByRole("button", { name: "我理解，前往系统设置" }));
    await waitFor(() => expect(NativeFlow.requestAccessibilityPermission).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("Play 版阻断设置不提供无障碍强阻断入口", () => {
    mockPermissions(true, false);
    renderBlockingSettings();
    expect(screen.queryByRole("button", { name: "前往授权无障碍服务" })).toBeNull();
    expect(screen.queryByRole("switch", { name: "无障碍强阻断" })).toBeNull();
  });

  it("不支持的 Domestic 设备禁用无障碍强阻断且不提供授权动作", () => {
    mockPermissions(true, true, true);
    renderBlockingSettings();
    expect(screen.queryByRole("switch", { name: "无障碍强阻断" })).toBeNull();
    expect(screen.getByText("当前版本暂不支持在此设备上开启保护")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "前往授权无障碍服务" })).toBeNull();
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();
  });

  it("不支持的设备仍能修改普通设置且不会索要无障碍权限", async () => {
    mockPermissions(true, true, true);
    vi.mocked(NativeFlow.loadSettings).mockResolvedValue({
      limitMinutes: 15,
      restDuration: 120,
      allowEmergencyUnlock: false,
      strongBlockingEnabled: true,
    } as Awaited<ReturnType<typeof NativeFlow.loadSettings>>);
    renderBlockingSettings();

    await waitFor(() => expect(screen.getByText("当前版本暂不支持在此设备上开启保护")).toBeInTheDocument());
    expect(screen.queryByRole("switch", { name: "无障碍强阻断" })).toBeNull();
    fireEvent.click(screen.getByRole("switch", { name: "允许每日一次紧急使用" }));
    fireEvent.click(screen.getByRole("button", { name: "保存设置" }));
    await waitFor(() => {
      expect(NativeFlow.saveSettings).toHaveBeenCalledWith(expect.objectContaining({
        allowEmergencyUnlock: true,
        strongBlockingEnabled: true,
      }));
    });
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();
    expect(screen.queryByText("开启强阻断前，请先授权无障碍服务。")).toBeNull();
  });

  it("保存按钮存在", () => {
    renderBlockingSettings();
    expect(screen.getByText("保存设置")).toBeInTheDocument();
  });

  it("限额和休息选择都有关联标签且滑动立即更新阈值", () => {
    renderBlockingSettings();
    const slider = screen.getByRole("slider", { name: "共享连续限额" });
    expect(slider).toHaveAttribute("aria-valuetext", "15 分钟");
    fireEvent.change(slider, { target: { value: "30" } });
    expect(slider).toHaveAttribute("aria-valuetext", "30 分钟");
    expect(screen.getByRole("group", { name: "休息引导时间" })).toHaveTextContent("36分钟休息引导");
    expect(screen.getByRole("combobox", { name: "解锁所需休息" })).toHaveValue("120");
  });

  it("原生保存后服务重载失败会回读真实设置，不承诺原设置未更改", async () => {
    isNative = true;
    vi.mocked(NativeFlow.saveSettings).mockRejectedValueOnce(new Error("设置已保存，但保护服务重载失败，请稍后重试。"));
    vi.mocked(NativeFlow.loadSettings)
      .mockResolvedValueOnce({ limitMinutes: 15, restDuration: 120, allowEmergencyUnlock: false, strongBlockingEnabled: false, targetApps: [], monitoringEnabled: true, channel: "base" })
      .mockResolvedValueOnce({ limitMinutes: 30, restDuration: 120, allowEmergencyUnlock: false, strongBlockingEnabled: false, targetApps: [], monitoringEnabled: true, channel: "base" });
    renderBlockingSettings();
    await waitFor(() => expect(screen.getByRole("slider")).not.toBeDisabled());
    fireEvent.change(screen.getByRole("slider"), { target: { value: "30" } });
    fireEvent.click(screen.getByRole("button", { name: "保存设置" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("设置已保存，但保护服务重载失败"));
    expect(screen.getByRole("alert")).toHaveTextContent("已同步设备中的最新设置");
    expect(screen.getByRole("alert")).not.toHaveTextContent("原设置未更改");
    expect(screen.getByRole("slider")).toHaveValue("30");
    expect(useStore.getState().profile.sessionLimit).toBe(30);
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it("国内版强阻断仍必须先取得无障碍授权", async () => {
    isNative = true;
    permissionOverrides = { isDomestic: true };
    renderBlockingSettings();
    await waitFor(() => expect(screen.getByRole("slider")).not.toBeDisabled());
    fireEvent.click(screen.getByRole("switch", { name: "无障碍强阻断" }));
    fireEvent.click(screen.getByRole("button", { name: "保存设置" }));
    expect(screen.getByRole("alert")).toHaveTextContent("开启强阻断前，请先授权无障碍服务");
    expect(NativeFlow.saveSettings).not.toHaveBeenCalled();
  });

  it("不支持设备保留规则但不显示无障碍权限引导", async () => {
    isNative = true;
    permissionOverrides = { isDomestic: true, unsupportedDevice: true, protectionRuntimeAvailable: false };
    renderBlockingSettings();
    await waitFor(() => expect(screen.getByRole("slider")).not.toBeDisabled());
    expect(screen.getByText("当前版本暂不支持在此设备上开启保护")).toBeInTheDocument();
    expect(screen.queryByRole("switch", { name: "无障碍强阻断" })).toBeNull();
    expect(screen.queryByRole("button", { name: "前往授权无障碍服务" })).toBeNull();
    expect(NativeFlow.requestAccessibilityPermission).not.toHaveBeenCalled();
  });

  it("读取失败不允许用界面缓存覆盖原生配置，重试成功后才能保存", async () => {
    isNative = true;
    vi.mocked(NativeFlow.loadSettings).mockRejectedValueOnce(new Error("暂时不可用"));
    renderBlockingSettings();
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("避免覆盖未读取的设置"));
    expect(screen.getByRole("slider")).toBeDisabled();
    expect(screen.getByRole("button", { name: "保存设置" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "保存设置" }));
    expect(NativeFlow.saveSettings).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "重新读取设置" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "保存设置" })).not.toBeDisabled());
    expect(NativeFlow.loadSettings).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole("alert")).toBeNull();
  });
});
