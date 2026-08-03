import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import TargetApps from "../TargetApps";
import { useStore } from "../../hooks/useStore";
import { NativeFlow } from "../../backend/nativeFlow";

const mockNavigate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useLocation: () => ({ state: null }),
  };
});

vi.mock("../../backend/appNames", () => ({
  DEFAULT_TARGET_APPS: ["com.test.a", "com.test.b", "com.test.c"],
  APP_NAMES: {},
  getAppName: (pkg: string) => {
    const m: Record<string, string> = {
      "com.test.a": "应用A", "com.test.b": "应用B", "com.test.c": "应用C",
    };
    return m[pkg] || "未知";
  },
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    getLaunchableApps: vi.fn().mockResolvedValue({ apps: [] }),
    loadSettings: vi.fn().mockResolvedValue({
      limitMinutes: 15, restDuration: 120, targetApps: ["com.test.a"],
      allowEmergencyUnlock: false, strongBlockingEnabled: false, channel: "play",
    }),
    saveTargetApps: vi.fn().mockResolvedValue(undefined),
  },
  LaunchableApp: {},
}));

vi.mock("@capacitor/core", () => ({
  Capacitor: { isNativePlatform: () => false },
}));

function renderTargetApps(targets: string[] = ["com.test.a"]) {
  useStore.setState({
    profile: { ...useStore.getState().profile, targetApps: targets },
  });
  return render(
    <BrowserRouter><TargetApps /></BrowserRouter>
  );
}

describe("TargetApps", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    (NativeFlow.saveTargetApps as any).mockClear();
    (NativeFlow.saveTargetApps as any).mockResolvedValue(undefined);
  });

  it("加载完成后不显示未保存弹窗", async () => {
    renderTargetApps();
    await waitFor(() => {
      expect(screen.queryByText("有未保存的修改")).toBeNull();
    });
  });

  it("新增应用后返回弹确认", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByText("应用B")).toBeInTheDocument(); });
    fireEvent.click(screen.getByText("应用B"));
    fireEvent.click(screen.getByLabelText("返回"));
    expect(screen.getByText("有未保存的修改")).toBeInTheDocument();
  });

  it("取消已选应用后返回弹确认", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByText("应用A")).toBeInTheDocument(); });
    fireEvent.click(screen.getByText("应用A"));
    fireEvent.click(screen.getByLabelText("返回"));
    expect(screen.getByText("有未保存的修改")).toBeInTheDocument();
  });

  it("保存后返回", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByText("保存更改")).toBeInTheDocument(); });
    fireEvent.click(screen.getByText("保存更改"));
    await waitFor(() => { expect(mockNavigate).toHaveBeenCalled(); });
  });

  it("放弃修改不保存", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByText("应用B")).toBeInTheDocument(); });
    fireEvent.click(screen.getByText("应用B"));
    fireEvent.click(screen.getByLabelText("返回"));
    fireEvent.click(screen.getByText("放弃修改"));
    expect(NativeFlow.saveTargetApps).not.toHaveBeenCalled();
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });

  it("继续编辑关闭面板", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByText("应用B")).toBeInTheDocument(); });
    fireEvent.click(screen.getByText("应用B"));
    fireEvent.click(screen.getByLabelText("返回"));
    fireEvent.click(screen.getByText("继续编辑"));
    await waitFor(() => { expect(screen.queryByText("有未保存的修改")).toBeNull(); });
  });

  it("已选置顶排序", async () => {
    renderTargetApps();
    await waitFor(() => {
      const items = screen.getAllByText(/应用[A-C]/);
      expect(items.length).toBeGreaterThanOrEqual(3);
      expect(items[0].textContent).toBe("应用A");
    });
  });

  it("只看已选过滤", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByText("应用A")).toBeInTheDocument(); });
    fireEvent.click(screen.getByText("只看已选"));
    await waitFor(() => {
      expect(screen.queryByText("应用B")).toBeNull();
      expect(screen.getByText("应用A")).toBeInTheDocument();
    });
  });

  it("搜索过滤", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByPlaceholderText("搜索应用")).toBeInTheDocument(); });
    fireEvent.change(screen.getByPlaceholderText("搜索应用"), { target: { value: "应用B" } });
    await waitFor(() => {
      expect(screen.getByText("应用B")).toBeInTheDocument();
      expect(screen.queryByText("应用A")).toBeNull();
    });
  });

  it("清空搜索恢复全部", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByPlaceholderText("搜索应用")).toBeInTheDocument(); });
    fireEvent.change(screen.getByPlaceholderText("搜索应用"), { target: { value: "应用B" } });
    await waitFor(() => { expect(screen.getByText("清空搜索")).toBeInTheDocument(); });
    fireEvent.click(screen.getByText("清空搜索"));
    await waitFor(() => {
      expect(screen.getByText("应用A")).toBeInTheDocument();
      expect(screen.getByText("应用B")).toBeInTheDocument();
    });
  });

  it("搜索结果计数", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByPlaceholderText("搜索应用")).toBeInTheDocument(); });
    fireEvent.change(screen.getByPlaceholderText("搜索应用"), { target: { value: "应用B" } });
    await waitFor(() => { expect(screen.getByText("找到 1 个应用")).toBeInTheDocument(); });
  });

  it("30个上限", async () => {
    const many = Array.from({ length: 29 }, (_, i) => "com.test." + i);
    renderTargetApps(many);
    await waitFor(() => { expect(screen.getByText("应用A")).toBeInTheDocument(); });
    fireEvent.click(screen.getByText("应用A"));
    await waitFor(() => { expect(screen.getByText(/已选 30\/30/)).toBeInTheDocument(); });
  });
});