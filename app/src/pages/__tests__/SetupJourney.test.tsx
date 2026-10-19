import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import Personalize from "../Personalize";
import TargetApps from "../TargetApps";
import { DEFAULT_TARGET_APPS, getAppName } from "../../backend/appNames";
import { useStore } from "../../hooks/useStore";
import { NativeFlow, type NativeSettings } from "../../backend/nativeFlow";

let isNative = false;
let deviceSettings: NativeSettings;

vi.mock("@capacitor/core", () => ({
  Capacitor: { isNativePlatform: () => isNative },
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: { loadSettings: vi.fn(), getLaunchableApps: vi.fn(), saveTargetApps: vi.fn(), saveSettings: vi.fn(), startService: vi.fn() },
}));

describe("首次设置的真实前端路由往返", () => {
  beforeEach(() => {
    localStorage.clear();
    isNative = false;
    deviceSettings = {
      limitMinutes: 15, restDuration: 120, allowEmergencyUnlock: false,
      strongBlockingEnabled: false, monitoringEnabled: true, targetApps: [DEFAULT_TARGET_APPS[0]], channel: "play",
    };
    vi.mocked(NativeFlow.loadSettings).mockReset().mockImplementation(async () => ({ ...deviceSettings, targetApps: [...deviceSettings.targetApps] }));
    vi.mocked(NativeFlow.getLaunchableApps).mockReset().mockResolvedValue({
      apps: DEFAULT_TARGET_APPS.map(packageName => ({ packageName, label: getAppName(packageName), iconDataUri: "" })),
    });
    vi.mocked(NativeFlow.saveTargetApps).mockReset().mockImplementation(async ({ packageNames }) => {
      deviceSettings = { ...deviceSettings, targetApps: [...packageNames] };
    });
    vi.mocked(NativeFlow.saveSettings).mockReset().mockResolvedValue(undefined);
    vi.mocked(NativeFlow.startService).mockReset().mockResolvedValue(undefined);
    useStore.getState().updateProfile({
      sessionLimit: 25,
      restDuration: 180,
      allowEmergencyUnlock: false,
      targetApps: [DEFAULT_TARGET_APPS[0]],
      onboardingDone: false,
    });
  });

  it("调整规则 → 选择更多应用 → 保存返回后仍保留全部规则草稿", async () => {
    render(
      <MemoryRouter initialEntries={["/personalize"]}>
        <Routes>
          <Route path="/personalize" element={<Personalize />} />
          <Route path="/target-apps" element={<TargetApps />} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByRole("button", { name: "30分钟" }));
    fireEvent.click(screen.getByRole("button", { name: "5分钟" }));
    fireEvent.click(screen.getByRole("switch", { name: "允许每日一次紧急使用" }));
    fireEvent.click(screen.getByRole("button", { name: /受限应用/ }));
    await waitFor(() => expect(screen.getByRole("button", { name: getAppName(DEFAULT_TARGET_APPS[1]) })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: getAppName(DEFAULT_TARGET_APPS[1]) }));
    fireEvent.click(screen.getByRole("button", { name: "保存更改" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "设置你的保护规则" })).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "30分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "5分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("switch", { name: "允许每日一次紧急使用" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByText("已选择 2 个应用")).toBeInTheDocument();
    expect(useStore.getState().profile.sessionLimit).toBe(25);
    expect(useStore.getState().profile.onboardingDone).toBe(false);
  });

  it("原生应用选择往返后读取失败仍保留草稿，重试成功再写入设备并启动", async () => {
    isNative = true;
    vi.mocked(NativeFlow.loadSettings)
      .mockResolvedValueOnce({ ...deviceSettings })
      .mockResolvedValueOnce({ ...deviceSettings })
      .mockRejectedValueOnce(new Error("返回时读取失败"));
    render(
      <MemoryRouter initialEntries={["/personalize"]}>
        <Routes>
          <Route path="/personalize" element={<Personalize />} />
          <Route path="/target-apps" element={<TargetApps />} />
          <Route path="/dashboard" element={<h1>保护设置完成</h1>} />
        </Routes>
      </MemoryRouter>,
    );
    await waitFor(() => expect(screen.getByRole("button", { name: "15分钟" })).toBeEnabled());
    expect(screen.getByRole("button", { name: "15分钟" })).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(screen.getByRole("button", { name: "30分钟" }));
    fireEvent.click(screen.getByRole("button", { name: "5分钟" }));
    fireEvent.click(screen.getByRole("switch", { name: "允许每日一次紧急使用" }));
    fireEvent.click(screen.getByRole("button", { name: /受限应用/ }));
    await waitFor(() => expect(screen.getByRole("button", { name: getAppName(DEFAULT_TARGET_APPS[1]) })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: getAppName(DEFAULT_TARGET_APPS[1]) }));
    fireEvent.click(screen.getByRole("button", { name: "保存更改" }));
    await waitFor(() => expect(NativeFlow.saveTargetApps).toHaveBeenCalledWith({
      packageNames: [DEFAULT_TARGET_APPS[0], DEFAULT_TARGET_APPS[1]],
    }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "设置你的保护规则" })).toBeInTheDocument());
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("设备设置读取失败"));
    expect(screen.getByRole("button", { name: "请先重新读取设置" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "30分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "5分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(NativeFlow.saveSettings).not.toHaveBeenCalled();
    expect(NativeFlow.startService).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "重新读取设置" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "开启保护" })).toBeEnabled());
    expect(screen.getByRole("button", { name: "30分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "5分钟" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("switch", { name: "允许每日一次紧急使用" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByText("已选择 2 个应用")).toBeInTheDocument();
    expect(useStore.getState().profile.sessionLimit).toBe(25);
    fireEvent.click(screen.getByRole("button", { name: "开启保护" }));
    await waitFor(() => expect(screen.getByRole("heading", { name: "保护设置完成" })).toBeInTheDocument());
    expect(NativeFlow.saveSettings).toHaveBeenCalledWith({
      limitMinutes: 30, restDuration: 300, targetApps: [DEFAULT_TARGET_APPS[0], DEFAULT_TARGET_APPS[1]], allowEmergencyUnlock: true,
    });
    expect(NativeFlow.startService).toHaveBeenCalledTimes(1);
    expect(useStore.getState().profile).toMatchObject({ sessionLimit: 30, restDuration: 300, onboardingDone: true });
  });
});
