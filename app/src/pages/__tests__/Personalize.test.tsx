import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import Personalize from "../Personalize";
import { useStore } from "../../hooks/useStore";
import { NativeFlow } from "../../backend/nativeFlow";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("@capacitor/core", () => ({
  Capacitor: { isNativePlatform: () => true },
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    saveSettings: vi.fn().mockResolvedValue(undefined),
    startService: vi.fn().mockResolvedValue(undefined),
  },
}));

function renderPersonalize(targetApps: string[] = []) {
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
    <BrowserRouter><Personalize /></BrowserRouter>
  );
}

describe("Personalize", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    (NativeFlow.saveSettings as any).mockClear();
    (NativeFlow.startService as any).mockClear();
    (NativeFlow.saveSettings as any).mockResolvedValue(undefined);
    (NativeFlow.startService as any).mockResolvedValue(undefined);
    useStore.setState({ profile: { ...useStore.getState().profile, targetApps: [] } });
  });

  it("无应用时完成按钮disabled", () => {
    renderPersonalize([]);
    const btn = screen.getByText("请先选择受限应用") as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });

  it("15/20/25/30/45分钟都在选项中", () => {
    renderPersonalize(["com.test.app"]);
    [15, 20, 25, 30, 45].forEach(m => {
      expect(screen.getByText(m + "分钟")).toBeInTheDocument();
    });
  });

  it("展示时间换算", () => {
    renderPersonalize(["com.test.app"]);
    expect(screen.getByText(/轻提醒/)).toBeInTheDocument();
    expect(screen.getByText(/强提醒/)).toBeInTheDocument();
    expect(screen.getByText(/进入休息引导/)).toBeInTheDocument();
  });

  it("选择应用入口导航到target-apps带returnTo", () => {
    renderPersonalize(["com.test.app"]);
    const all = screen.getAllByText("受限应用");
    fireEvent.click(all[1]);
    expect(mockNavigate).toHaveBeenCalledWith("/target-apps", { state: { returnTo: "/personalize" } });
  });

  it("紧急解锁Toggle有正确ARIA", () => {
    renderPersonalize(["com.test.app"]);
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    expect(toggle).toBeInTheDocument();
    expect(toggle.getAttribute("aria-checked")).toBe("false");
  });

  it("点击紧急解锁toggle切换aria-checked", () => {
    renderPersonalize(["com.test.app"]);
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    fireEvent.click(toggle);
    expect(toggle.getAttribute("aria-checked")).toBe("true");
  });

  it("每日解锁文案正确", () => {
    renderPersonalize(["com.test.app"]);
    expect(screen.getByText("每日一次紧急使用")).toBeInTheDocument();
  });

  it("startService失败时不导航Dashboard", async () => {
    (NativeFlow.startService as any).mockRejectedValueOnce(new Error("服务启动失败"));
    renderPersonalize(["com.test.app"]);
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => {
      expect(mockNavigate).not.toHaveBeenCalledWith("/dashboard");
    });
  });

  it("权限错误显示权限入口", async () => {
    (NativeFlow.startService as any).mockRejectedValueOnce(new Error("权限不足，请检查权限"));
    renderPersonalize(["com.test.app"]);
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => {
      expect(screen.getByText("前往权限设置")).toBeInTheDocument();
    });
  });

  it("应用错误显示选择应用入口", async () => {
    (NativeFlow.startService as any).mockRejectedValueOnce(new Error("请先选择应用"));
    renderPersonalize(["com.test.app"]);
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => {
      expect(screen.getByText("前往选择应用")).toBeInTheDocument();
    });
  });

  it("成功保存后设置onboardingDone", async () => {
    renderPersonalize(["com.test.app"]);
    fireEvent.click(screen.getByText("开启保护"));
    await waitFor(() => {
      expect(useStore.getState().profile.onboardingDone).toBe(true);
    });
  });
});