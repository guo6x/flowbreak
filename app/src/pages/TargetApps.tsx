import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Capacitor } from "@capacitor/core";
import { ArrowLeft, Check, RefreshCw, Search, Shield } from "lucide-react";
import { useLocation, useNavigate } from "react-router";
import { DEFAULT_TARGET_APPS, getAppName } from "../backend/appNames";
import { LaunchableApp, NativeFlow } from "../backend/nativeFlow";
import { useStore } from "../hooks/useStore";

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
  const [saving, setSaving] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [onlySelected, setOnlySelected] = useState(false);
  const initialSelected = useRef<string[]>([...selected]);
  const isDirty = useMemo(() => {
    return !sameSelection(initialSelected.current, selected);
  }, [selected]);
  const [channel, setChannel] = useState<"play" | "domestic" | "base">("base");
  const returnTo =
    (location.state as { returnTo?: string } | null)?.returnTo === "/personalize"
      ? "/personalize"
      : "/profile";

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      if (!Capacitor.isNativePlatform()) {
        setApps(webApps);
        setSelected((current) =>
          current.length ? current : DEFAULT_TARGET_APPS
        );
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
      setSelected(saved.length ? saved : defaults);
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
      setError("无法读取应用列表，请点击重试。");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!loading && apps.length > 0) {
      initialSelected.current = [...selected];
    }
  }, [loading, apps.length]);

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
      ? base.filter((a) => selected.includes(a.packageName))
      : base;
    // Put selected apps first - sort on a copy
    result = [...result].sort((a, b) => {
      const aSel = selected.includes(a.packageName) ? 0 : 1;
      const bSel = selected.includes(b.packageName) ? 0 : 1;
      return aSel - bSel;
    });
    return result;
  }, [apps, query, onlySelected, selected]);

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
      navigate(returnTo, { replace: true });
    } catch (e) {
      setSaving(false);
      setError(e instanceof Error ? e.message : "保存失败");
    }
  };

  const handleDiscardAndReturn = () => {
    navigate(returnTo);
  };

  const handleBack = () => {
    if (isDirty) {
      setShowConfirm(true);
      return;
    }
    navigate(returnTo);
  };

  const save = async () => {
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
      navigate(returnTo, { replace: true });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "保存失败，请重试。");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div
      className="min-h-dvh px-5 pt-6"
      style={{
        paddingBottom: "calc(4rem + 1rem + env(safe-area-inset-bottom))",
      }}
    >
      <div className="flex items-center gap-3 mb-5">
        <button
          onClick={handleBack}
          aria-label="返回"
          className="w-10 h-10 rounded-full bg-white card flex items-center justify-center"
        >
          <ArrowLeft size={20} />
        </button>
        <div className="flex-1">
          <h1 className="text-[22px] font-bold">受限应用</h1>
          <p className="text-[12px] text-gray-500">
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
          <div className="flex gap-2">
            <button
              onClick={handleSaveAndReturn}
              disabled={saving}
              className="flex-1 h-10 bg-primary text-white rounded-xl text-[13px] font-medium"
            >
              {saving ? "保存中..." : "保存并返回"}
            </button>
            <button
              onClick={handleDiscardAndReturn}
              className="flex-1 h-10 bg-gray-200 text-gray-700 rounded-xl text-[13px] font-medium"
            >
              放弃修改
            </button>
            <button
              onClick={() => setShowConfirm(false)}
              className="flex-1 h-10 border border-gray-200 rounded-xl text-[13px] font-medium text-gray-600"
            >
              继续编辑
            </button>
          </div>
        </div>
      )}

      <div className="relative mb-4">
        <Search
          size={18}
          className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"
        />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="搜索应用"
          className="w-full rounded-2xl bg-white border border-gray-300/60 pl-11 pr-4 py-3.5 outline-none"
        />
        <div className="flex gap-2 mt-2">
          {selected.length > 0 && (
            <button
              onClick={() => setOnlySelected(!onlySelected)}
              className={`text-[12px] px-3 py-1 rounded-full border ${
                onlySelected
                  ? "bg-primary/10 border-primary text-primary"
                  : "border-gray-200 text-gray-500"
              }`}
            >
              只看已选
            </button>
          )}
          {query && (
            <button
              onClick={() => setQuery("")}
              className="text-[12px] px-3 py-1 rounded-full border border-gray-200 text-gray-500"
            >
              清空搜索
            </button>
          )}
        </div>
      </div>

      <div className="mb-4 p-4 bg-primary/5 border border-primary/10 rounded-2xl flex gap-3">
        <Shield size={20} className="text-primary shrink-0 mt-0.5" />
        <p className="text-[12px] text-gray-600 leading-relaxed">
          FlowBreak、自带桌面、系统设置、电话、短信和相机已自动排除，无法加入阻断列表。
        </p>
      </div>
      {channel === "play" && (
        <div className="mb-4 p-4 bg-amber-50 border border-amber-200 rounded-2xl text-[12px] text-amber-900 leading-relaxed">
          Google Play
          版不会把整个微信当作"视频号"阻断：系统无法可靠区分聊天和视频号。请在这里选择抖音、小红书、快手等可明确识别的应用。
        </div>
      )}
      {channel === "domestic" && (
        <div className="mb-4 p-4 bg-secondary/5 border border-secondary/10 rounded-2xl text-[12px] text-gray-600 leading-relaxed">
          国内版在开启"无障碍强阻断"后可尝试识别微信视频号页面；该能力受系统和微信版本影响，以实际拦截效果为准。
        </div>
      )}

      {loading && (
        <p className="text-center text-gray-500 py-12">正在读取应用...</p>
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
            const active = selected.includes(app.packageName);
            return (
              <button
                key={app.packageName}
                onClick={() => toggle(app.packageName)}
                className="w-full flex items-center gap-3 px-4 py-3 border-b border-gray-300/30 last:border-b-0 text-left"
              >
                {app.iconDataUri ? (
                  <img
                    src={app.iconDataUri}
                    alt=""
                    className="w-10 h-10 rounded-xl"
                  />
                ) : (
                  <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center text-primary font-bold">
                    {app.label.slice(0, 1)}
                  </div>
                )}
                <div className="flex-1 min-w-0">
                  <p className="text-[14px] font-medium truncate">
                    {app.label}
                  </p>
                  <p className="text-[10px] text-gray-400 truncate">
                    {app.packageName}
                  </p>
                </div>
                <div
                  className={`w-6 h-6 rounded-full border-2 flex items-center justify-center ${
                    active ? "bg-primary border-primary" : "border-gray-300"
                  }`}
                >
                  {active && <Check size={14} className="text-white" />}
                </div>
              </button>
            );
          })}
          {filtered.length === 0 && (
            <p className="text-center text-gray-500 py-12">没有匹配的应用</p>
          )}
        </div>
      )}
      {filtered.length > 0 && (
        <p className="text-[12px] text-gray-400 mt-2">
          找到 {filtered.length} 个应用
        </p>
      )}

      {error && <p className="text-error text-[12px] mt-3">{error}</p>}
      <div
        className="fixed left-0 right-0 bottom-0 px-5 py-4 bg-white/95 border-t border-gray-300/40"
        style={{ paddingBottom: "max(1rem, env(safe-area-inset-bottom))" }}
      >
        <button
          onClick={save}
          disabled={
            selected.length === 0 || loading || saving || apps.length === 0
          }
          className="btn-primary w-full disabled:opacity-50"
        >
          {saving ? "正在保存..." : "保存更改"}
        </button>
      </div>
    </div>
  );
}