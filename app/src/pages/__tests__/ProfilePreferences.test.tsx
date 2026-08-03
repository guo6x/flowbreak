import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import ProfilePreferences from "../ProfilePreferences";
import { useStore } from "../../hooks/useStore";
import { NativeFlow } from "../../backend/nativeFlow";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    saveSettings: vi.fn().mockResolvedValue(undefined),
    startService: vi.fn().mockResolvedValue(undefined),
  },
}));

function renderProfilePreferences(overrides: Record<string, any> = {}) {
  useStore.setState({
    profile: {
      ...useStore.getState().profile,
      name: "测试用户",
      type: "student" as const,
      dailyGoal: 60,
      selectedBackground: 0,
      sessionLimit: 15,
      restDuration: 120,
      targetApps: ["com.test.app"],
      allowEmergencyUnlock: false,
      onboardingDone: true,
      ...overrides,
    },
  });
  return render(
    <BrowserRouter><ProfilePreferences /></BrowserRouter>
  );
}

describe("ProfilePreferences", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    (NativeFlow.saveSettings as any).mockClear();
    (NativeFlow.startService as any).mockClear();
  });

  it("原值回填昵称", () => {
    renderProfilePreferences();
    const input = screen.getByPlaceholderText("FlowBreak 用户") as HTMLInputElement;
    expect(input.value).toBe("测试用户");
  });

  it("原值回填每日目标", () => {
    renderProfilePreferences();
    expect(screen.getByText("1小时")).toBeInTheDocument();
  });

  it("修改昵称并保存Store更新", () => {
    renderProfilePreferences();
    const input = screen.getByPlaceholderText("FlowBreak 用户") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "新昵称" } });
    fireEvent.click(screen.getByText("保存"));
    expect(useStore.getState().profile.name).toBe("新昵称");
  });

  it("修改每日目标Store更新", () => {
    renderProfilePreferences();
    fireEvent.click(screen.getByText("2小时"));
    fireEvent.click(screen.getByText("保存"));
    expect(useStore.getState().profile.dailyGoal).toBe(120);
  });

  it("修改背景Store更新", () => {
    renderProfilePreferences();
    const bgBtns = screen.getAllByText(/森林|海洋|山脉|花园|日落/);
    fireEvent.click(bgBtns[1]);
    fireEvent.click(screen.getByText("保存"));
    expect(useStore.getState().profile.selectedBackground).toBe(1);
  });

  it("修改用户类型Store更新", () => {
    renderProfilePreferences();
    fireEvent.click(screen.getByText("上班族"));
    fireEvent.click(screen.getByText("保存"));
    expect(useStore.getState().profile.type).toBe("worker");
  });

  it("保存后返回Profile", () => {
    renderProfilePreferences();
    fireEvent.click(screen.getByText("保存"));
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });

  it("返回按钮回Profile", () => {
    renderProfilePreferences();
    fireEvent.click(screen.getByLabelText("返回个人中心"));
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });

  it("不调用NativeFlow.startService", () => {
    renderProfilePreferences();
    fireEvent.click(screen.getByText("保存"));
    expect(NativeFlow.startService).not.toHaveBeenCalled();
  });

  it("不调用NativeFlow.saveSettings", () => {
    renderProfilePreferences();
    fireEvent.click(screen.getByText("保存"));
    expect(NativeFlow.saveSettings).not.toHaveBeenCalled();
  });

  it("保存带空格的昵称去除首尾空格", () => {
    renderProfilePreferences();
    const input = screen.getByPlaceholderText("FlowBreak 用户") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "  小明  " } });
    fireEvent.click(screen.getByText("保存"));
    expect(useStore.getState().profile.name).toBe("小明");
  });

  it("保存纯空格后Store为空字符串", () => {
    renderProfilePreferences();
    const input = screen.getByPlaceholderText("FlowBreak 用户") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "   " } });
    fireEvent.click(screen.getByText("保存"));
    expect(useStore.getState().profile.name).toBe("");
  });
});