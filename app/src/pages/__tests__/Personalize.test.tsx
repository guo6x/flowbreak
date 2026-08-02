import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import Personalize from "../Personalize";
import { useStore } from "../../hooks/useStore";

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

function renderPersonalize(targetApps: string[] = []) {
  useStore.setState({
    profile: {
      ...useStore.getState().profile,
      targetApps,
      sessionLimit: 15,
      restDuration: 120,
      allowEmergencyUnlock: false,
    },
  });
  return render(
    <BrowserRouter>
      <Personalize />
    </BrowserRouter>
  );
}

describe("Personalize", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    useStore.setState({ profile: { ...useStore.getState().profile, targetApps: [] } });
  });

  it("无应用时完成按钮disabled", () => {
    renderPersonalize([]);
    const btn = screen.getByText("请先选择受限应用");
    expect((btn as HTMLButtonElement).disabled).toBe(true);
  });

  it("15/20/25/30/45分钟都在选项中", () => {
    renderPersonalize(["com.test.app"]);
    [15, 20, 25, 30, 45].forEach(m => {
      expect(screen.getByText(`${m}分钟`)).toBeDefined();
    });
  });

  it("展示时间换算", () => {
    renderPersonalize(["com.test.app"]);
    expect(screen.getByText(/轻提醒/)).toBeDefined();
    expect(screen.getByText(/强提醒/)).toBeDefined();
    expect(screen.getByText(/进入休息引导/)).toBeDefined();
  });

  it("可以点击选择应用", () => {
    renderPersonalize(["com.test.app"]);
    const elements = screen.getAllByText("受限应用");
    expect(elements.length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("已选择 1 个应用")).toBeDefined();
  });

  it("紧急解锁Toggle有正确ARIA", () => {
    renderPersonalize(["com.test.app"]);
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    expect(toggle).toBeDefined();
    expect(toggle.getAttribute("aria-checked")).toBe("false");
  });

  it("紧急解锁每日次数文案正确", () => {
    renderPersonalize(["com.test.app"]);
    expect(screen.getByText("每日一次紧急使用")).toBeDefined();
    expect(screen.getByText("长按10秒后开放5分钟，并记录本地事件")).toBeDefined();
  });

  it("点击紧急解锁toggle切换aria-checked", () => {
    renderPersonalize(["com.test.app"]);
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    fireEvent.click(toggle);
    expect(toggle.getAttribute("aria-checked")).toBe("true");
  });
});
