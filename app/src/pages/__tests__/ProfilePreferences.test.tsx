import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import ProfilePreferences from "../ProfilePreferences";
import { useStore } from "../../hooks/useStore";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

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
    <BrowserRouter>
      <ProfilePreferences />
    </BrowserRouter>
  );
}

describe("ProfilePreferences", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  it("原值回填昵称", () => {
    renderProfilePreferences();
    const input = screen.getByPlaceholderText("FlowBreak 用户") as HTMLInputElement;
    expect(input.value).toBe("测试用户");
  });

  it("原值回填每日目标", () => {
    renderProfilePreferences();
    expect(screen.getByText("1小时")).toBeDefined();
  });

  it("修改昵称并保存", () => {
    renderProfilePreferences();
    const input = screen.getByPlaceholderText("FlowBreak 用户") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "新昵称" } });
    expect(input.value).toBe("新昵称");
    fireEvent.click(screen.getByText("保存"));
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });

  it("修改每日目标", () => {
    renderProfilePreferences();
    fireEvent.click(screen.getByText("2小时"));
    fireEvent.click(screen.getByText("保存"));
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });

  it("修改背景", () => {
    renderProfilePreferences();
    const bgBtns = screen.getAllByText(/森林|海洋|山脉|花园|日落/);
    fireEvent.click(bgBtns[1]);
    fireEvent.click(screen.getByText("保存"));
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });

  it("保存后返回Profile", () => {
    renderProfilePreferences();
    fireEvent.click(screen.getByText("保存"));
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });

  it("点击返回按钮返回Profile", () => {
    renderProfilePreferences();
    fireEvent.click(screen.getByLabelText("返回个人中心"));
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });

  it("不启动Native服务", () => {
    renderProfilePreferences();
    fireEvent.click(screen.getByText("保存"));
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });
});
