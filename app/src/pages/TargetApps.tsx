import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Capacitor } from "@capacitor/core";
import { ArrowLeft, Check, RefreshCw, Search, Shield } from "lucide-react";
import { useLocation, useNavigate } from "react-router";
import { DEFAULT_TARGET_APPS, getAppName } from "../backend/appNames";
import { LaunchableApp, NativeFlow } from "../backend/nativeFlow";
import { useStore } from "../hooks/useStore";
import type { SetupDraft } from "./Personalize";
import { getNativeErrorMessage } from "../utils/nativeError";

function sameSelection(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false;
  const setA = new Set(a);
  return b.every((pkg) => setA.has(pkg));
}

const webApps: LaunchableApp[] = DEFAULT_TARGET_APPS.map((packageName) => ({
  packageName,
  label: getAppName(packageName),
  iconDataUri: "",
}));

export default function TargetApps() {
  const navigate = useNavigate();
  const location = useLocation();
  const profile = useStore((s) => s.profile);
  const updateProfile = useStore((s) => s.updateProfile);
  const [apps, setApps] = useState<LaunchableApp[]>([]);
  const [selected, setSelected] = useState<string[]>(profile.targetApps || []);
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [onlySelected, setOnlySelected] = useState(false);
  const selectedSet = useMemo(() => new Set(selected), [selected]);
  const initialSelected = useRef<string[]>([...selected]);
  const isDirty = useMemo(() => {
    return !sameSelection(initialSelected.current, selected);
  }, [selected]);
  const [channel, setChannel] = useState<"play" | "domestic" | "base">("base");
  const routeState = location.state as { returnTo?: string; setupDraft?: SetupDraft } | null;
  const returnTo =
    routeState?.returnTo === "/personalize"
      ? "/personalize"
      : "/profile";

  const returnToPrevious = (replace = false) => {
    if (returnTo === "/personalize" && routeState?.setupDraft) {
      navigate(returnTo, { replace, state: { setupDraft: routeState.setupDraft } });
    } else if (replace) {
      navigate(returnTo, { replace: true });
    } else {
      navigate(returnTo);
    }
  };

  const load = useCallback(async () => {
    setLoading(true);
    setLoadFailed(false);
    setError("");
    try {
      if (!Capacitor.isNativePlatform()) {
        setApps(webApps);
        const initial = initialSelected.current.length ? initialSelected.current : [...DEFAULT_TARGET_APPS];
        initialSelected.current = [...initial];
        setSelected(initial);
        return;
      }
      const [launchable, settings] = await Promise.all([
        NativeFlow.getLaunchableApps(),
        NativeFlow.loadSettings(),
      ]);
      setApps(launchable.apps);
      const installed = new Set(launchable.apps.map((app) => app.packageName));
      const saved = settings.targetApps.filter((pkg) => installed.has(pkg));
      const defaults = DEFAULT_TARGET_APPS.filter((pkg) => installed.has(pkg));
      const initial = saved.length ? saved : defaults;
      initialSelected.current = [...initial];
      setSelected(initial);
      setChannel(
        settings.channel === "play" || settings.channel === "domestic"
          ? settings.channel
          : "base"
      );
      if (launchable.apps.length === 0) {
        setError("没有读取到可启动应用，请重试或检查系统限制。");
      }
    } catch {
      setApps([]);
      setLoadFailed(true);
      setError("无法读取应用列表或已保存设置，请重新读取后再保存。");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const reconcileAfterSaveFailure = async (message: string) => {
    if (!Capacitor.isNativePlatform()) {
      setError(message);
      return;
    }
    try {
      const [launchable, settings] = await Promise.all([
        NativeFlow.getLaunchableApps(),
        NativeFlow.loadSettings(),
      ]);
      const installed = new Set(launchable.apps.map((app) => app.packageName));
      const saved = settings.targetApps.filter((pkg) => installed.has(pkg));
      const defaults = DEFAULT_TARGET_APPS.filter((pkg) => installed.has(pkg));
      const actual = saved.length ? saved : defaults;
      setApps(launchable.apps);
      initialSelected.current = [...actual];
      setSelected(actual);
      setChannel(
        settings.channel === "play" || settings.channel === "domestic"
          ? settings.channel
          : "base",
      );
      updateProfile({ targetApps: actual });
      setLoadFailed(false);
      setError(`${message} 已同步设备中的最新受限应用。`);
    } catch {
      setLoadFailed(true);
      setError(`${message} 暂时无法读取设备中的最新受限应用，请重新读取后再试。`);
    }
  };

  // 系统返回键（Capacitor WebView 不经 history API）：由 MainActivity 原生层
  // 通过 evaluateJavascript 询问本钩子。dirty 时消费按键并弹出与页面内返回
  // 按钮一致的确认框；未挂载/无修改时返回 false 保持默认退出行为。
  useEffect(() => {
    const handleSystemBack = () => {
      if (isDirty) {
        setShowConfirm(true);
        return true;
      }
      return false;
    };
    (window as any).__flowbreakHandleBack = handleSystemBack;
    return () => {
      delete (window as any).__flowbreakHandleBack;
    };
  }, [isDirty]);

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    const base = keyword
      ? apps.filter(
          (app) =>
            app.label.toLowerCase().includes(keyword) ||
            app.packageName.toLowerCase().includes(keyword)
        )
      : [...apps];
    let result = onlySelected
      ? base.filter((a) => selectedSet.has(a.packageName))
      : base;
    // Put selected apps first - sort on a copy
    result = [...result].sort((a, b) => {
      const aSel = selectedSet.has(a.packageName) ? 0 : 1;
      const bSel = selectedSet.has(b.packageName) ? 0 : 1;
      return aSel - bSel;
    });
    return result;
  }, [apps, query, onlySelected, selectedSet]);

  const toggle = (packageName: string) => {
    setError("");
    setSelected((current) => {
      if (current.includes(packageName))
        return current.filter((pkg) => pkg !== packageName);
      if (current.length >= 30) {
        setError("最多选择 30 个应用。");
        return current;
      }
      return [...current, packageName];
    });
  };

  const handleSaveAndReturn = async () => {
    if (loading || loadFailed || saving || apps.length === 0) return;
    if (selected.length === 0) {
      setError("至少选择一个应用。");
      return;
    }
    setSaving(true);
    setError("");
    try {
      if (Capacitor.isNativePlatform()) {
        await NativeFlow.saveTargetApps({ packageNames: selected });
      }
      updateProfile({ targetApps: selected });
      initialSelected.current = [...selected];
      returnToPrevious(true);
    } catch (cause) {
      setSaving(false);
      await reconcileAfterSaveFailure(
        getNativeErrorMessage(cause, "保存受限应用失败，请重试。"),
      );
    }
  };

  const handleDiscardAndReturn = () => {
    returnToPrevious();
  };

  const handleBack = () => {
    if (isDirty) {
      setShowConfirm(true);
      return;
    }
    returnToPrevious();
  };

  const save = async () => {
    if (loading || loadFailed || saving || apps.length === 0) return;
    if (selected.length === 0) {
      setError("至少选择一个应用。");
      return;
    }
    setSaving(true);
    setError("");
    try {
      if (Capacitor.isNativePlatform()) {
        await NativeFlow.saveTargetApps({ packageNames: selected });
      }
      updateProfile({ targetApps: selected });
      initialSelected.current = [...selected];
      returnToPrevious(true);
    } catch (cause) {
      await reconcileAfterSaveFailure(
        getNativeErrorMessage(cause, "保存受限应用失败，请重试。"),
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className="min-h-dvh px-5 pt-7 sm:px-8"
      style={{
        paddingBottom: "calc(4rem + 1rem + env(safe-area-inset-bottom))",
      }}
    >
      <div className="flex items-center gap-3 mb-5">
        <button
          onClick={handleBack}
          aria-label="返回"
          className="w-[44px] h-[44px] shrink-0 rounded-full bg-white card flex items-center justify-center"
        >
          <ArrowLeft size={20} />
        </button>
        <div className="flex-1">
          <h1 className="text-[24px] font-bold tracking-tight">受限应用</h1>
          <p className="text-[13px] text-gray-500">
            共享同一连续使用限额 · 已选 {selected.length}/30
          </p>
        </div>
      </div>

      {showConfirm && (
        <div
          className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-4"
          role="alert"
        >
          <p className="text-[14px] font-medium text-amber-800 mb-3">
            有未保存的修改
          </p>
          <div className="flex flex-wrap gap-2">
            <button
              onClick={handleSaveAndReturn}
              disabled={loading || loadFailed || saving || apps.length === 0 || selected.length === 0}
              className="flex-1 min-w-[100px] min-h-[44px] bg-primary text-white rounded-xl text-[13px] font-medium disabled:opacity-50"
            >
              {saving ? "保存中..." : "保存并返回"}
            </button>
            <button
              onClick={handleDiscardAndReturn}
              disabled={saving}
              className="flex-1 min-w-[100px] min-h-[44px] bg-gray-200 text-gray-700 rounded-xl text-[13px] font-medium disabled:opacity-50"
            >
              放弃修改
            </button>
            <button
              onClick={() => setShowConfirm(false)}
              disabled={saving}
              className="flex-1 min-w-[100px] min-h-[44px] border border-gray-200 rounded-xl text-[13px] font-medium text-gray-600 disabled:opacity-50"
            >
              继续编辑
            </button>
          </div>
        </div>
      )}

      {!Capacitor.isNativePlatform() && <p className="mb-4 px-4 py-3 rounded-2xl bg-secondary/5 border border-secondary/10 text-[13px] text-secondary leading-relaxed">网页仅展示示例应用，无法读取本机应用或执行阻断。正式功能请在 Android 上使用。</p>}

      <div className="mb-4">
        <div className="relative">
        <Search
          size={18}
          className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"
        />
        <input
          type="search"
          aria-label="搜索应用名称或包名"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="搜索应用"
          className="w-full min-h-[50px] rounded-2xl bg-white border border-gray-300/60 pl-11 pr-4 py-3.5 outline-none focus:ring-2 focus:ring-primary/30"
        />
        </div>
        <div className="flex flex-wrap gap-2 mt-3">
          {(selected.length > 0 || onlySelected) && (
            <button
              onClick={() => setOnlySelected(!onlySelected)}
              aria-pressed={onlySelected}
              className={`text-[13px] min-h-[44px] px-4 rounded-full border font-medium ${
                onlySelected
                  ? "bg-primary/10 border-primary text-primary"
                  : "border-gray-200 text-gray-500"
              }`}
            >
              只看已选
            </button>
          )}
          {selected.length > 0 && <button onClick={() => { setSelected([]); setError(""); }} className="text-[13px] min-h-[44px] px-4 rounded-full border border-gray-200 text-gray-600 font-medium">清空已选</button>}
          {query && (
            <button
              onClick={() => setQuery("")}
              className="text-[13px] min-h-[44px] px-4 rounded-full border border-gray-200 text-gray-600 font-medium"
            >
              清空搜索
            </button>
          )}
        </div>
      </div>

      <div className="mb-4 p-4 bg-primary/5 border border-primary/10 rounded-2xl flex gap-3">
        <Shield size={20} className="text-primary shrink-0 mt-0.5" />
        <p className="text-[13px] text-gray-600 leading-relaxed">
          FlowBreak、自带桌面、系统设置、电话、短信和相机已自动排除，无法加入阻断列表。
        </p>
      </div>
      {channel === "play" && (
        <div className="mb-4 p-4 bg-amber-50 border border-amber-200 rounded-2xl text-[13px] text-amber-900 leading-relaxed">
          Google Play
          版不会把整个微信当作"视频号"阻断：系统无法可靠区分聊天和视频号。请在这里选择抖音、小红书、快手等可明确识别的应用。
        </div>
      )}
      {channel === "domestic" && (
        <div className="mb-4 p-4 bg-secondary/5 border border-secondary/10 rounded-2xl text-[13px] text-gray-600 leading-relaxed">
          国内版在开启"无障碍强阻断"后可尝试识别微信视频号页面；这是未经真机专项验证的实验性能力，可能受系统和微信版本影响。
        </div>
      )}

      {loading && (
        <p role="status" className="text-center text-gray-500 py-12">正在读取应用...</p>
      )}
      {!loading && apps.length === 0 && (
        <button
          onClick={load}
          className="w-full py-4 rounded-2xl bg-gray-100 text-gray-700 flex items-center justify-center gap-2"
        >
          <RefreshCw size={17} />
          重新读取应用列表
        </button>
      )}
      {!loading && apps.length > 0 && (
        <div className="card overflow-hidden">
          {filtered.map((app) => {
            const active = selectedSet.has(app.packageName);
            return (
              <button
                key={app.packageName}
                onClick={() => toggle(app.packageName)}
                aria-pressed={active}
                aria-label={app.label}
                className={`w-full min-h-[76px] flex items-center gap-3 px-4 py-4 border-b border-gray-300/30 last:border-b-0 text-left transition-colors ${active ? "bg-primary/[0.04]" : "hover:bg-gray-50"}`}
              >
                {app.iconDataUri ? (
                  <img
                    src={app.iconDataUri}
                    alt=""
                    className="w-11 h-11 shrink-0 rounded-[14px]"
                  />
                ) : (
                  <div aria-hidden="true" className="w-11 h-11 shrink-0 rounded-[14px] bg-primary/10 flex items-center justify-center text-primary font-bold">
                    {app.label.slice(0, 1)}
                  </div>
                )}
                <div className="flex-1 min-w-0">
                  <p className="text-[14px] font-medium truncate">
                    {app.label}
                  </p>
                  <p className="text-[12px] text-gray-500 truncate mt-1">
                    {app.packageName}
                  </p>
                </div>
                <div
                  aria-hidden="true"
                  className={`w-6 h-6 shrink-0 rounded-full border-2 flex items-center justify-center ${
                    active ? "bg-primary border-primary" : "border-gray-300"
                  }`}
                >
                  {active && <Check size={14} className="text-white" />}
                </div>
              </button>
            );
          })}
          {filtered.length === 0 && (
            <div className="text-center px-5 py-12">
              <Search size={28} className="text-gray-400 mx-auto mb-3" aria-hidden="true" />
              <p className="text-gray-700 font-medium">没有匹配的应用</p>
              <p className="text-[13px] text-gray-500 mt-2">试试其他名称，或关闭「只看已选」。</p>
            </div>
          )}
        </div>
      )}
      {filtered.length > 0 && (
        <p role="status" className="text-[13px] text-gray-500 mt-3">
          找到 {filtered.length} 个应用
        </p>
      )}

      {error && <p role="alert" className="text-error text-[13px] mt-3">{error}</p>}
      <div
        className="app-fixed-bottom fixed left-0 right-0 bottom-0 px-5 py-4 bg-white/95 border-t border-gray-300/40 z-20"
        style={{ paddingBottom: "max(1rem, env(safe-area-inset-bottom))" }}
      >
        <button
          onClick={save}
          disabled={
            selected.length === 0 || loading || loadFailed || saving || apps.length === 0
          }
          className="btn-primary w-full disabled:opacity-50"
        >
          {saving ? "正在保存..." : "保存更改"}
        </button>
      </div>
    </div>
  );
}
