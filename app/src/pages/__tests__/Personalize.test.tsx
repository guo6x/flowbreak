import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import Personalize, { type SetupDraft } from "../Personalize";
import { useStore } from "../../hooks/useStore";
import { NativeFlow, type NativeSettings } from "../../backend/nativeFlow";

const mockNavigate = vi.fn();
let isNative = true;

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("@capacitor/core", () => ({
  Capacitor: { isNativePlatform: () => isNative },
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    loadSettings: vi.fn(),
    saveSettings: vi.fn().mockResolvedValue(undefined),
    startService: vi.fn().mockResolvedValue(undefined),
  },
}));

const nativeSettings: NativeSettings = {
  limitMinutes: 15,
  restDuration: 120,
  targetApps: ["com.test.app"],
  allowEmergencyUnlock: false,
  strongBlockingEnabled: false,
  monitoringEnabled: true,
  channel: "play",
};

function renderPersonalize(targetApps: string[] = [], setupDraft?: SetupDraft) {
  useStore.setState({
    profile: {
      ...useStore.getState().profile,
      targetApps,
      sessionLimit: 15,
      restDuration: 120,
      allowEmergencyUnlock: false,
      strongBlockingEnabled: false,
      onboardingDone: false,
    },
  });
  return render(
    <MemoryRouter initialEntries={[{ pathname: "/personalize", state: { setupDraft } }]}><Personalize /></MemoryRouter>
  );
}

async function waitForSettings() {
  await waitFor(() => expect(screen.getByRole("button", { name: "15分钟" })).toBeEnabled());
}

describe("Personalize", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    isNative = true;
    vi.mocked(NativeFlow.loadSettings).mockReset().mockImplementation(async () => ({
      ...nativeSettings,
      targetApps: [...useStore.getState().profile.targetApps],
    }));
    vi.mocked(NativeFlow.saveSettings).mockReset().mockResolvedValue(undefined);
    vi.mocked(NativeFlow.startService).mockReset().mockResolvedValue(undefined);
    useStore.setState({ profile: { ...useStore.getState().profile, targetApps: [] } });
  });

  it("无应用时完成按钮disabled", async () => {
    renderPersonalize([]);
    await waitForSettings();
    const btn = screen.getByText("请先选择受限应用") as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("15/20/25/30/45分钟都在选项中", async () => {
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    [15, 20, 25, 30, 45].forEach(m => {
      expect(screen.getByText(m + "分钟")).toBeInTheDocument();
    });
  });

  it("展示时间换算", async () => {
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    expect(screen.getByText(/轻提醒/)).toBeInTheDocument();
    expect(screen.getByText(/强提醒/)).toBeInTheDocument();
    expect(screen.getByText(/进入休息引导/)).toBeInTheDocument();
  });

  it("选择应用入口导航到target-apps带returnTo", async () => {
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    fireEvent.click(screen.getAllByText("受限应用")[1]);
    expect(mockNavigate).toHaveBeenCalledWith("/target-apps", {
      state: { returnTo: "/personalize", setupDraft: { session: 15, restDur: 120, emergencyUnlock: false } },
    });
  });

  it("选择应用前透传尚未保存的全部规则", async () => {
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    fireEvent.click(screen.getByRole("button", { name: "30分钟" }));
    fireEvent.click(screen.getByRole("button", { name: "5分钟" }));
    fireEvent.click(screen.getByRole("switch", { name: "允许每日一次紧急使用" }));
    fireEvent.click(screen.getAllByText("受限应用")[1]);
    expect(mockNavigate).toHaveBeenCalledWith("/target-apps", {
      state: { returnTo: "/personalize", setupDraft: { session: 30, restDur: 300, emergencyUnlock: true } },
    });
    expect(useStore.getState().profile.sessionLimit).toBe(15);
    expect(NativeFlow.saveSettings).not.toHaveBeenCalled();
  });

  it("从应用选择返回时恢复草稿并在最终开启时保存", async () => {
    renderPersonalize(["com.test.app"], { session: 30, restDur: 300, emergencyUnlock: true });
    await waitForSettings();
    expect(screen.getByRole("button", { name: "30分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "5分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("switch")).toHaveAttribute("aria-checked", "true");
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => expect(NativeFlow.startService).toHaveBeenCalled());
    expect(NativeFlow.saveSettings).toHaveBeenCalledWith({
      limitMinutes: 30, restDuration: 300, targetApps: ["com.test.app"], allowEmergencyUnlock: true,
    });
  });

  it("忽略路由中不在合法选项里的限额和休息时长", async () => {
    renderPersonalize(["com.test.app"], { session: 999, restDur: 0, emergencyUnlock: false });
    await waitForSettings();
    expect(screen.getByRole("button", { name: "15分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "2分钟" })).toHaveAttribute("aria-pressed", "true");
  });

  it("网页说明仅预览且不会调用原生保护", async () => {
    isNative = false;
    renderPersonalize(["com.test.app"]);
    expect(screen.getByText(/不会开启 Android 保护服务/)).toBeInTheDocument();
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/dashboard"));
    expect(NativeFlow.saveSettings).not.toHaveBeenCalled();
    expect(NativeFlow.startService).not.toHaveBeenCalled();
    expect(NativeFlow.loadSettings).not.toHaveBeenCalled();
  });

  it("原生读取完成前禁用编辑与保存，读取后使用设备配置而非缓存", async () => {
    let resolveRead!: (settings: NativeSettings) => void;
    vi.mocked(NativeFlow.loadSettings).mockImplementationOnce(() => new Promise(resolve => { resolveRead = resolve; }));
    renderPersonalize(["com.cached.app"]);
    const pendingSave = screen.getByRole("button", { name: "正在读取设置..." });
    expect(pendingSave).toBeDisabled();
    expect(screen.getByRole("button", { name: "30分钟" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "5分钟" })).toBeDisabled();
    expect(screen.getByRole("switch")).toBeDisabled();
    expect(screen.getByRole("button", { name: /选择受限应用/ })).toBeDisabled();
    fireEvent.click(pendingSave);
    fireEvent.click(screen.getByRole("button", { name: /选择受限应用/ }));
    expect(NativeFlow.saveSettings).not.toHaveBeenCalled();
    expect(NativeFlow.startService).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();

    await act(async () => resolveRead({
      ...nativeSettings,
      limitMinutes: 45,
      restDuration: 300,
      allowEmergencyUnlock: true,
      targetApps: ["com.device.a", "com.device.b"],
    }));
    await waitForSettings();
    expect(screen.getByRole("button", { name: "45分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "5分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("switch")).toHaveAttribute("aria-checked", "true");
    expect(screen.getByText("已选择 2 个应用")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "开启保护" }));
    await waitFor(() => expect(NativeFlow.startService).toHaveBeenCalledWith({
      limitMinutes: 45, apps: ["com.device.a", "com.device.b"], monitoringEnabled: true,
    }));
    expect(NativeFlow.saveSettings).toHaveBeenCalledWith({
      limitMinutes: 45, restDuration: 300, targetApps: ["com.device.a", "com.device.b"], allowEmergencyUnlock: true,
    });
    expect(useStore.getState().profile.targetApps).toEqual(["com.device.a", "com.device.b"]);
  });

  it("原生读取失败时不覆盖缓存或启动服务，重试恢复后保留有效规则草稿", async () => {
    vi.mocked(NativeFlow.loadSettings).mockRejectedValueOnce(new Error("读取失败"));
    vi.mocked(NativeFlow.loadSettings).mockResolvedValueOnce({
      ...nativeSettings, limitMinutes: 45, restDuration: 180, targetApps: ["com.device.app"],
    });
    renderPersonalize(["com.cached.app"], { session: 30, restDur: 300, emergencyUnlock: true });
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("设备设置读取失败"));
    expect(screen.getByRole("button", { name: "请先重新读取设置" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "30分钟" })).toBeDisabled();
    expect(screen.getByRole("switch")).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "请先重新读取设置" }));
    expect(NativeFlow.saveSettings).not.toHaveBeenCalled();
    expect(NativeFlow.startService).not.toHaveBeenCalled();
    expect(useStore.getState().profile).toMatchObject({
      sessionLimit: 15, restDuration: 120, targetApps: ["com.cached.app"], onboardingDone: false,
    });

    fireEvent.click(screen.getByRole("button", { name: "重新读取设置" }));
    await waitForSettings();
    expect(NativeFlow.loadSettings).toHaveBeenCalledTimes(2);
    expect(screen.getByRole("button", { name: "30分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "5分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("switch")).toHaveAttribute("aria-checked", "true");
    fireEvent.click(screen.getByRole("button", { name: "开启保护" }));
    await waitFor(() => expect(NativeFlow.startService).toHaveBeenCalled());
    expect(NativeFlow.saveSettings).toHaveBeenCalledWith({
      limitMinutes: 30, restDuration: 300, targetApps: ["com.device.app"], allowEmergencyUnlock: true,
    });
  });

  it("无效草稿在原生读取后使用设备限额与休息时长", async () => {
    vi.mocked(NativeFlow.loadSettings).mockResolvedValueOnce({ ...nativeSettings, limitMinutes: 45, restDuration: 180 });
    renderPersonalize(["com.cached.app"], { session: 999, restDur: 0, emergencyUnlock: false });
    await waitForSettings();
    expect(screen.getByRole("button", { name: "45分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "3分钟" })).toHaveAttribute("aria-pressed", "true");
  });

  it("同一渲染周期重复点击只保存和启动一次", async () => {
    let resolveSave!: () => void;
    vi.mocked(NativeFlow.saveSettings).mockImplementationOnce(() => new Promise(resolve => { resolveSave = () => resolve(); }));
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    const saveButton = screen.getByRole("button", { name: "开启保护" });
    act(() => {
      fireEvent.click(saveButton);
      fireEvent.click(saveButton);
    });
    expect(NativeFlow.saveSettings).toHaveBeenCalledTimes(1);
    expect(NativeFlow.startService).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "30分钟" })).toBeDisabled();
    expect(screen.getByRole("button", { name: /受限应用/ })).toBeDisabled();
    await act(async () => resolveSave());
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/dashboard"));
    expect(NativeFlow.startService).toHaveBeenCalledTimes(1);
  });

  it("保存设置失败时不尝试启动原生服务", async () => {
    (NativeFlow.saveSettings as any).mockRejectedValueOnce(new Error("保存失败"));
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("保存失败"));
    expect(NativeFlow.startService).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalledWith("/dashboard");
    expect(useStore.getState().profile.onboardingDone).toBe(false);
  });

  it("紧急解锁Toggle有正确ARIA", async () => {
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    expect(toggle).toBeInTheDocument();
    expect(toggle.getAttribute("aria-checked")).toBe("false");
  });

  it("点击紧急解锁toggle切换aria-checked", async () => {
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    fireEvent.click(toggle);
    expect(toggle.getAttribute("aria-checked")).toBe("true");
  });

  it("每日解锁文案正确", async () => {
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    expect(screen.getByText("每日一次紧急使用")).toBeInTheDocument();
  });

  it("成功保存后onboardingDone=true且参数正确", async () => {
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => {
      expect(useStore.getState().profile.onboardingDone).toBe(true);
    });
    expect(NativeFlow.saveSettings).toHaveBeenCalledWith({
      limitMinutes: 15, restDuration: 120, targetApps: ["com.test.app"],
      allowEmergencyUnlock: false,
    });
    expect(NativeFlow.startService).toHaveBeenCalledWith({
      limitMinutes: 15, apps: ["com.test.app"], monitoringEnabled: true,
    });
  });

  it("startService失败时不导航且保留状态", async () => {
    (NativeFlow.startService as any).mockRejectedValueOnce(new Error("启动失败"));
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("启动失败"));
    expect(mockNavigate).not.toHaveBeenCalledWith("/dashboard");
    expect(useStore.getState().profile.onboardingDone).toBe(false);
  });

  it("失败后限额等状态保留", async () => {
    (NativeFlow.startService as any).mockRejectedValueOnce(new Error("错误"));
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    fireEvent.click(screen.getByText("30分钟"));
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("错误"));
    expect(screen.getByRole("button", { name: "30分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(useStore.getState().profile.onboardingDone).toBe(false);
  });

  it("权限错误显示权限入口", async () => {
    (NativeFlow.startService as any).mockRejectedValueOnce(new Error("权限不足，请检查权限"));
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => {
      expect(screen.getByText("前往权限设置")).toBeInTheDocument();
    });
  });

  it("应用错误显示选择应用入口", async () => {
    (NativeFlow.startService as any).mockRejectedValueOnce(new Error("请先选择应用"));
    renderPersonalize(["com.test.app"]);
    await waitForSettings();
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => {
      expect(screen.getByText("前往选择应用")).toBeInTheDocument();
    });
  });
});
