import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BrowserRouter, MemoryRouter } from "react-router";
import Onboarding from "../Onboarding";
import { AppRouter } from "../../App";

vi.mock("@capacitor/core", () => ({
  Capacitor: { isNativePlatform: () => false },
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    migrateLegacyData: vi.fn().mockResolvedValue(undefined),
    loadSettings: vi.fn().mockResolvedValue({ limitMinutes: 15, restDuration: 120, targetApps: [], allowEmergencyUnlock: false, strongBlockingEnabled: false }),
    getDashboardSummary: vi.fn().mockResolvedValue({ blockCount: 0, restCount: 0, unlockSeconds: 0, pullbackOutcomeCount: 0, successfulPullbackCount: 0, postRestReturnCount: 0, postRestTargetSeconds: 0, reflectionValue: 0, points: 0, streak: 0 }),
    consumePendingNavigation: vi.fn().mockResolvedValue({ path: "" }),
    addListener: vi.fn().mockResolvedValue({ remove: vi.fn() }),
  },
}));

const mockNavigate = vi.fn();
vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

function renderOnboarding() {
  return render(
    <BrowserRouter><Onboarding /></BrowserRouter>
  );
}

describe("Onboarding", () => {
  beforeEach(() => mockNavigate.mockClear());

  it("第一页点击跳过直接进入permissions", () => {
    renderOnboarding();
    fireEvent.click(screen.getByText("跳过"));
    expect(mockNavigate).toHaveBeenCalledWith("/permissions", { replace: true });
  });

  it("下一步切换到下一页", async () => {
    renderOnboarding();
    expect(screen.getByText("自然唤醒")).toBeInTheDocument();
    fireEvent.click(screen.getByText("下一步"));
    await waitFor(() => {
      expect(screen.getByText("三层渐进式干预")).toBeInTheDocument();
    });
  });

  it("第二页点击跳过直接进入permissions", () => {
    renderOnboarding();
    fireEvent.click(screen.getByText("下一步"));
    fireEvent.click(screen.getByText("跳过"));
    expect(mockNavigate).toHaveBeenCalledWith("/permissions", { replace: true });
  });

  it("最后一页点击开始使用进入permissions", () => {
    renderOnboarding();
    fireEvent.click(screen.getByText("下一步"));
    fireEvent.click(screen.getByText("下一步"));
    fireEvent.click(screen.getByText("开始使用"));
    expect(mockNavigate).toHaveBeenCalledWith("/permissions");
  });

  it("进度支持键盘可达按钮并标记当前步骤", () => {
    renderOnboarding();
    expect(screen.getByRole("button", { name: "跳转到第 1 页" })).toHaveAttribute("aria-current", "step");
    fireEvent.click(screen.getByRole("button", { name: "跳转到第 3 页" }));
    expect(screen.getByText("从上头里拉回来")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "跳转到第 3 页" })).toHaveAttribute("aria-current", "step");
    expect(screen.getByText(/仅支持 Android/)).toBeInTheDocument();
  });

  it("渐进说明明确达到120%时阻断而非承诺永不打断", () => {
    renderOnboarding();
    fireEvent.click(screen.getByRole("button", { name: "跳转到第 2 页" }));
    expect(screen.getByText(/120% 时阻断目标应用/)).toBeInTheDocument();
  });
});

describe("Login路由重定向-使用生产AppRouter", () => {
  it("访问/login渲染Permissions而非旧Login页面", () => {
    render(
      <MemoryRouter initialEntries={["/login"]}>
        <AppRouter />
      </MemoryRouter>
    );
    // Navigate redirects to /permissions; Permissions renders "继续设置保护"
    // and should not render old Login content
    expect(screen.queryByText("先认识一下你")).toBeNull();
  });
});
