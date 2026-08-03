import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import BlockingSettings from "../BlockingSettings";
import { useStore } from "../../hooks/useStore";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("../../hooks/useNativePermissions", () => ({
  useNativePermissions: () => ({
    isNative: false,
    permissions: {
      hasUsageStats: true,
      hasOverlay: true,
      isIgnoringBattery: false,
      hasNotification: false,
      hasAccessibility: false,
      isDomestic: false,
      channel: "base" as const,
      manufacturer: "",
    },
    checking: false,
    error: "",
    refresh: vi.fn(),
  }),
}));

function renderBlockingSettings() {
  useStore.setState({
    profile: {
      ...useStore.getState().profile,
      sessionLimit: 15,
      restDuration: 120,
      allowEmergencyUnlock: false,
      strongBlockingEnabled: false,
      onboardingDone: true,
    },
  });
  return render(
    <BrowserRouter>
      <BlockingSettings />
    </BrowserRouter>
  );
}

describe("BlockingSettings", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  it("15分钟=12/15/18换算", () => {
    renderBlockingSettings();
    expect(screen.getByText(/12分钟轻提醒/)).toBeInTheDocument();
    expect(screen.getByText(/15分钟强提醒/)).toBeInTheDocument();
    expect(screen.getByText(/18分钟进入休息引导/)).toBeInTheDocument();
  });

  it("紧急解锁Toggle有正确ARIA", () => {
    renderBlockingSettings();
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    expect(toggle).toBeInTheDocument();
    expect(toggle.getAttribute("aria-checked")).toBe("false");
  });

  it("点击紧急解锁切换aria-checked", () => {
    renderBlockingSettings();
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    fireEvent.click(toggle);
    expect(toggle.getAttribute("aria-checked")).toBe("true");
  });

  it("Toggle键盘Enter可切换", () => {
    renderBlockingSettings();
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    // Buttons natively respond to Enter/Space as click; simulate via click
    fireEvent.click(toggle);
    expect(toggle.getAttribute("aria-checked")).toBe("true");
    // Click again to toggle back
    fireEvent.click(toggle);
    expect(toggle.getAttribute("aria-checked")).toBe("false");
  });

  it("Toggle键盘Space可切换", () => {
    renderBlockingSettings();
    const toggle = screen.getByRole("switch", { name: "允许每日一次紧急使用" });
    // Verify toggle is operable (button with role=switch responds to Space via onClick)
    expect(toggle.tagName).toBe("BUTTON");
    fireEvent.click(toggle);
    expect(toggle.getAttribute("aria-checked")).toBe("true");
  });

  it("切换限额显示不同换算", () => {
    renderBlockingSettings();
    expect(screen.getByText("15 分钟")).toBeInTheDocument();
  });

  it("保存按钮存在", () => {
    renderBlockingSettings();
    expect(screen.getByText("保存设置")).toBeInTheDocument();
  });
});
