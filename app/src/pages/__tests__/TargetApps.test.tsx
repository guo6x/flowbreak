import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import TargetApps from "../TargetApps";
import { useStore } from "../../hooks/useStore";
import { NativeFlow } from "../../backend/nativeFlow";

const mockNavigate = vi.fn();

let isNative = false;
let routeState: { returnTo: string; setupDraft: { session: number; restDur: number; emergencyUnlock: boolean } } | null = null;

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate, useLocation: () => ({ state: routeState }) };
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
    routeState = null;
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
    isNative = true;
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
    fireEvent.click(screen.getByRole("button", { name: "应用30" }));
    await waitFor(() => {
      expect(screen.getByText(/最多选择 30 个应用/)).toBeInTheDocument();
      expect(screen.getByText(/已选 30/)).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "应用30" })).toHaveAttribute("aria-pressed", "false");
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

  it("应用按钮暴露选中状态且搜索有可访问名称", async () => {
    renderTargetApps();
    await waitFor(() => expect(screen.getByRole("button", { name: "应用A" })).toHaveAttribute("aria-pressed", "true"));
    expect(screen.getByRole("searchbox", { name: "搜索应用名称或包名" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "应用B" }));
    expect(screen.getByRole("button", { name: "应用B" })).toHaveAttribute("aria-pressed", "true");
  });

  it("清空已选不会立即写入已保存配置且禁止空保存", async () => {
    renderTargetApps();
    await waitFor(() => expect(screen.getByText("应用A")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "清空已选" }));
    expect(screen.getByText(/已选 0\/30/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "保存更改" })).toBeDisabled();
    expect(useStore.getState().profile.targetApps).toEqual(["com.test.a"]);
    expect(NativeFlow.saveTargetApps).not.toHaveBeenCalled();
    fireEvent.click(screen.getByLabelText("返回"));
    expect(screen.getByRole("button", { name: "保存并返回" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "放弃修改" }));
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });

  it("只看已选时取消最后一个应用后仍能恢复完整列表", async () => {
    renderTargetApps();
    await waitFor(() => expect(screen.getByText("应用A")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "只看已选" }));
    fireEvent.click(screen.getByRole("button", { name: "应用A" }));
    expect(screen.getByText("没有匹配的应用")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "只看已选" })).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(screen.getByRole("button", { name: "只看已选" }));
    expect(screen.getByText("应用B")).toBeInTheDocument();
  });

  it("初次填充网页默认应用不误报未保存修改", async () => {
    renderTargetApps([]);
    await waitFor(() => expect(screen.getByText("应用A")).toBeInTheDocument());
    expect((window as any).__flowbreakHandleBack()).toBe(false);
    fireEvent.click(screen.getByLabelText("返回"));
    expect(screen.queryByText("有未保存的修改")).toBeNull();
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });

  it("原生设置与界面缓存不同时以读取结果为干净基线", async () => {
    isNative = true;
    renderTargetApps([]);
    await waitFor(() => expect(screen.getByRole("button", { name: "应用A" })).toHaveAttribute("aria-pressed", "true"));
    expect((window as any).__flowbreakHandleBack()).toBe(false);
    fireEvent.click(screen.getByLabelText("返回"));
    expect(screen.queryByText("有未保存的修改")).toBeNull();
  });

  it.each(["loadSettings", "getLaunchableApps"] as const)("原生 %s 读取失败时不保存缓存，重试成功后使用设备配置", async (readMethod) => {
    isNative = true;
    (NativeFlow.loadSettings as any).mockResolvedValue({
      limitMinutes: 15, restDuration: 120, targetApps: ["com.test.b"],
      allowEmergencyUnlock: false, strongBlockingEnabled: false, channel: "play",
    });
    (NativeFlow[readMethod] as any).mockRejectedValueOnce(new Error("读取失败"));
    renderTargetApps(["com.test.a"]);

    await waitFor(() => expect(screen.getByText("无法读取应用列表或已保存设置，请重新读取后再保存。")).toBeInTheDocument());
    expect(screen.getByText(/已选 1\/30/)).toBeInTheDocument();
    const saveButton = screen.getByRole("button", { name: "保存更改" });
    expect(saveButton).toBeDisabled();
    fireEvent.click(saveButton);
    expect(NativeFlow.saveTargetApps).not.toHaveBeenCalled();
    expect(useStore.getState().profile.targetApps).toEqual(["com.test.a"]);
    expect(mockNavigate).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "重新读取应用列表" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "应用B" })).toHaveAttribute("aria-pressed", "true"));
    expect(screen.getByRole("button", { name: "应用A" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "保存更改" })).toBeEnabled();
    expect((window as any).__flowbreakHandleBack()).toBe(false);
    fireEvent.click(screen.getByRole("button", { name: "保存更改" }));
    await waitFor(() => expect(NativeFlow.saveTargetApps).toHaveBeenCalledWith({ packageNames: ["com.test.b"] }));
    expect(useStore.getState().profile.targetApps).toEqual(["com.test.b"]);
  });

  it("读取失败后仍可放弃草稿返回，但不能从返回确认保存未知配置", async () => {
    isNative = true;
    (NativeFlow.loadSettings as any).mockRejectedValueOnce(new Error("读取失败"));
    renderTargetApps(["com.test.a"]);
    await waitFor(() => expect(screen.getByRole("button", { name: "重新读取应用列表" })).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "清空已选" }));
    expect((window as any).__flowbreakHandleBack()).toBe(true);
    await waitFor(() => expect(screen.getByText("有未保存的修改")).toBeInTheDocument());
    const saveAndReturnButton = screen.getByRole("button", { name: "保存并返回" });
    expect(saveAndReturnButton).toBeDisabled();
    fireEvent.click(saveAndReturnButton);
    expect(NativeFlow.saveTargetApps).not.toHaveBeenCalled();
    expect(useStore.getState().profile.targetApps).toEqual(["com.test.a"]);
    fireEvent.click(screen.getByRole("button", { name: "放弃修改" }));
    expect(mockNavigate).toHaveBeenCalledWith("/profile");
  });

  it("保存应用并返回首次设置时原样带回保护规则草稿", async () => {
    routeState = { returnTo: "/personalize", setupDraft: { session: 30, restDur: 300, emergencyUnlock: true } };
    renderTargetApps();
    await waitFor(() => expect(screen.getByText("应用B")).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "应用B" }));
    fireEvent.click(screen.getByRole("button", { name: "保存更改" }));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/personalize", {
      replace: true, state: { setupDraft: routeState?.setupDraft },
    }));
    expect(useStore.getState().profile.targetApps).toEqual(["com.test.a", "com.test.b"]);
  });
});
