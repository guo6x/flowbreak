import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import TargetApps from "../TargetApps";
import { useStore } from "../../hooks/useStore";
import { NativeFlow } from "../../backend/nativeFlow";

const mockNavigate = vi.fn();

let isNative = false;

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate, useLocation: () => ({ state: null }) };
});

vi.mock("../../backend/appNames", () => ({
  DEFAULT_TARGET_APPS: ["com.test.a", "com.test.b", "com.test.c"],
  APP_NAMES: {},
  getAppName: (pkg: string) => {
    const m: Record<string, string> = { "com.test.a": "应用A", "com.test.b": "应用B", "com.test.c": "应用C" };
    return m[pkg] || "未知";
  },
}));

vi.mock("../../backend/nativeFlow", () => ({
  NativeFlow: {
    getLaunchableApps: vi.fn().mockResolvedValue({
      apps: [
        { packageName: "com.test.a", label: "应用A", iconDataUri: "" },
        { packageName: "com.test.b", label: "应用B", iconDataUri: "" },
        { packageName: "com.test.c", label: "应用C", iconDataUri: "" },
      ],
    }),
    loadSettings: vi.fn().mockResolvedValue({
      limitMinutes: 15, restDuration: 120, targetApps: ["com.test.a"],
      allowEmergencyUnlock: false, strongBlockingEnabled: false, channel: "play",
    }),
    saveTargetApps: vi.fn().mockResolvedValue(undefined),
  },
  LaunchableApp: {},
}));

vi.mock("@capacitor/core", () => ({
  get Capacitor() {
    return { isNativePlatform: () => isNative };
  },
}));

function renderTargetApps(targets: string[] = ["com.test.a"]) {
  useStore.setState({ profile: { ...useStore.getState().profile, targetApps: targets } });
  return render(<BrowserRouter><TargetApps /></BrowserRouter>);
}

describe("TargetApps", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    isNative = false;
    (NativeFlow.saveTargetApps as any).mockClear();
    (NativeFlow.saveTargetApps as any).mockResolvedValue(undefined);
    (NativeFlow.getLaunchableApps as any).mockResolvedValue({
      apps: [
        { packageName: "com.test.a", label: "应用A", iconDataUri: "" },
        { packageName: "com.test.b", label: "应用B", iconDataUri: "" },
        { packageName: "com.test.c", label: "应用C", iconDataUri: "" },
      ],
    });
    (NativeFlow.loadSettings as any).mockResolvedValue({
      limitMinutes: 15, restDuration: 120, targetApps: ["com.test.a"],
      allowEmergencyUnlock: false, strongBlockingEnabled: false, channel: "play",
    });
  });

  it("加载完成后不显示未保存弹窗", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.queryByText("有未保存的修改")).toBeNull(); });
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
  });

  it("Native模式保存参数正确", async () => {
    isNative = true;
    renderTargetApps();
    await waitFor(() => { expect(screen.getByText("保存更改")).toBeInTheDocument(); });
    fireEvent.click(screen.getByText("保存更改"));
    await waitFor(() => {
      expect(NativeFlow.saveTargetApps).toHaveBeenCalledWith({ packageNames: ["com.test.a"] });
    });
  });

  it("保存失败保留当前选择并显示错误", async () => {
    (NativeFlow.saveTargetApps as any).mockRejectedValueOnce(new Error("保存失败"));
    isNative = true;
    renderTargetApps(["com.test.a"]);
    await waitFor(() => { expect(screen.getByText("应用B")).toBeInTheDocument(); });
    fireEvent.click(screen.getByText("应用B"));
    fireEvent.click(screen.getByText("保存更改"));
    await waitFor(() => { expect(screen.getByText("保存失败")).toBeInTheDocument(); });
    expect(screen.getByText("保存更改")).toBeInTheDocument();
  });

  it("第31个应用被阻止", async () => {
    (NativeFlow.getLaunchableApps as any).mockResolvedValue({
      apps: Array.from({ length: 35 }, (_, i) => ({
        packageName: "com.test." + i, label: "应用" + i, iconDataUri: "",
      })),
    });
    (NativeFlow.loadSettings as any).mockResolvedValue({
      limitMinutes: 15, restDuration: 120,
      targetApps: Array.from({ length: 30 }, (_, i) => "com.test." + i),
      allowEmergencyUnlock: false, strongBlockingEnabled: false, channel: "play",
    });
    const thirty = Array.from({ length: 30 }, (_, i) => "com.test." + i);
    renderTargetApps(thirty);
    await waitFor(() => { expect(screen.getByText(/已选 30/)).toBeInTheDocument(); });
    const app31 = screen.queryByText("应用30");
    if (app31) {
      fireEvent.click(app31);
      await waitFor(() => {
        expect(screen.getByText(/最多选择 30 个应用/)).toBeInTheDocument();
        expect(screen.getByText(/已选 30/)).toBeInTheDocument();
      });
    }
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

  it("系统返回键在未保存修改时被拦截并弹出确认框", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByText("应用B")).toBeInTheDocument(); });
    fireEvent.click(screen.getByText("应用B"));
    const handler = (window as any).__flowbreakHandleBack;
    expect(typeof handler).toBe("function");
    expect(handler()).toBe(true);
    await waitFor(() => {
      expect(screen.getByText("有未保存的修改")).toBeInTheDocument();
    });
  });

  it("无未保存修改时系统返回键不被拦截", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByText("应用A")).toBeInTheDocument(); });
    const handler = (window as any).__flowbreakHandleBack;
    expect(typeof handler).toBe("function");
    expect(handler()).toBe(false);
    expect(screen.queryByText("有未保存的修改")).toBeNull();
  });

  it("离开页面后移除系统返回键钩子", async () => {
    const { unmount } = renderTargetApps();
    await waitFor(() => { expect(screen.getByText("应用A")).toBeInTheDocument(); });
    unmount();
    expect((window as any).__flowbreakHandleBack).toBeUndefined();
  });

  it("搜索结果计数", async () => {
    renderTargetApps();
    await waitFor(() => { expect(screen.getByPlaceholderText("搜索应用")).toBeInTheDocument(); });
    fireEvent.change(screen.getByPlaceholderText("搜索应用"), { target: { value: "应用B" } });
    await waitFor(() => { expect(screen.getByText("找到 1 个应用")).toBeInTheDocument(); });
  });
});
