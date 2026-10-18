import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { motion, useReducedMotion } from "framer-motion";
import { Capacitor } from "@capacitor/core";
import { ArrowLeft, AppWindow, ChevronRight, Sparkles } from "lucide-react";
import { useStore } from "../hooks/useStore";
import { NativeFlow } from "../backend/nativeFlow";
import { translateLimit } from "../utils/protectionStatus";

const sessionOptions = [15, 20, 25, 30, 45]; // 分钟
const restOptions = [120, 180, 300]; // 秒 → 2, 3, 5 分钟

export interface SetupDraft {
  session: number;
  restDur: number;
  emergencyUnlock: boolean;
}

export default function Personalize() {
  const navigate = useNavigate();
  const location = useLocation();
  const draft = (location.state as { setupDraft?: SetupDraft } | null)?.setupDraft;
  const profile = useStore(s => s.profile);
  const updateProfile = useStore(s => s.updateProfile);
  const setMonitoring = useStore(s => s.setMonitoring);
  const isNative = Capacitor.isNativePlatform();
  const setupDraft = useRef({
    session: draft && sessionOptions.includes(draft.session) ? draft.session : undefined,
    restDur: draft && restOptions.includes(draft.restDur) ? draft.restDur : undefined,
    emergencyUnlock: typeof draft?.emergencyUnlock === "boolean" ? draft.emergencyUnlock : undefined,
  });
  const [session, setSession] = useState(setupDraft.current.session ?? profile.sessionLimit);
  const [restDur, setRestDur] = useState(setupDraft.current.restDur ?? profile.restDuration);
  const [emergencyUnlock, setEmergencyUnlock] = useState(setupDraft.current.emergencyUnlock ?? profile.allowEmergencyUnlock);
  const [nativeTargets, setNativeTargets] = useState<string[]>([]);
  const [loading, setLoading] = useState(isNative);
  const [loadFailed, setLoadFailed] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const settingsReady = useRef(!isNative);
  const saveInFlight = useRef(false);
  const [saving, setSaving] = useState(false);
  const [saveState, setSaveState] = useState<"idle" | "saving" | "starting">("idle");
  const [error, setError] = useState("");
  const targetApps = isNative ? nativeTargets : profile.targetApps || [];
  const reduceMotion = useReducedMotion();
  const controlsDisabled = loading || loadFailed || saving;

  useEffect(() => {
    if (!isNative) return;
    let active = true;
    settingsReady.current = false;
    setLoading(true);
    setLoadFailed(false);
    setError("");
    NativeFlow.loadSettings().then(settings => {
      if (!active) return;
      // A valid round-trip draft contains intentional edits. Otherwise the
      // device, not the browser cache captured during startup, is authoritative.
      setSession(setupDraft.current.session ?? settings.limitMinutes);
      setRestDur(setupDraft.current.restDur ?? settings.restDuration);
      setEmergencyUnlock(setupDraft.current.emergencyUnlock ?? settings.allowEmergencyUnlock);
      setNativeTargets([...settings.targetApps]);
      settingsReady.current = true;
    }).catch(() => {
      if (!active) return;
      setLoadFailed(true);
      setError("设备设置读取失败。为避免覆盖已有配置，请先重新读取。");
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
      settingsReady.current = false;
    };
  }, [isNative, loadAttempt]);

  const retryLoad = () => {
    settingsReady.current = false;
    setLoadAttempt(attempt => attempt + 1);
  };

  const chooseApps = () => {
    if (!settingsReady.current || saveInFlight.current) return;
    navigate("/target-apps", {
      state: {
        returnTo: "/personalize",
        setupDraft: { session, restDur, emergencyUnlock },
      },
    });
  };

  const finish = async () => {
    if (!settingsReady.current || loading || loadFailed || saveInFlight.current) return;
    if (targetApps.length === 0) {
      setError("请先选择至少一个受限应用后再完成设置。");
      return;
    }
    saveInFlight.current = true;
    setSaving(true);
    setSaveState("saving");
    setError("");
    try {
      if (isNative) {
        await NativeFlow.saveSettings({
          limitMinutes: session,
          restDuration: restDur,
          targetApps,
          allowEmergencyUnlock: emergencyUnlock,
        });
        setSaveState("starting");
        await NativeFlow.startService({
          limitMinutes: session,
          apps: targetApps,
          monitoringEnabled: true,
        });
      }
      setMonitoring(true);
      updateProfile({
        name: profile.name,
        type: profile.type,
        dailyGoal: profile.dailyGoal,
        sessionLimit: session,
        restDuration: restDur,
        selectedBackground: profile.selectedBackground,
        allowEmergencyUnlock: emergencyUnlock,
        targetApps,
        onboardingDone: true,
      });
      navigate("/dashboard");
    } catch (err) {
      const msg = err instanceof Error ? err.message : "初始化保护服务失败，请检查权限后重试。";
      setError(msg);
    } finally {
      saveInFlight.current = false;
      setSaving(false);
      setSaveState("idle");
    }
  };

  const limits = translateLimit(session);
  const canComplete = targetApps.length > 0;

  return (
    <div className="flex flex-col min-h-dvh px-5 pt-7 pb-8 sm:px-8">
      <div className="flex items-center justify-between mb-7">
        <button onClick={() => navigate("/permissions")} disabled={saving} aria-label="返回权限设置" className="w-[44px] h-[44px] rounded-full bg-white card flex items-center justify-center disabled:opacity-50">
          <ArrowLeft size={20} />
        </button>
        <span className="text-[13px] font-medium text-gray-500">保护设置 · 第 2 步</span>
      </div>
      <motion.div
        initial={reduceMotion ? false : { opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col flex-1"
      >
        {/* Header */}
        <div className="w-14 h-14 rounded-2xl bg-primary/10 flex items-center justify-center mb-5">
          <Sparkles size={28} className="text-primary" aria-hidden="true" />
        </div>
        <h1 className="text-[26px] font-bold tracking-tight text-gray-900 mb-3">设置你的保护规则</h1>
        <p className="text-[14px] text-gray-600 leading-6 mb-6">找到适合自己的节奏。规则可以随时在个人中心调整。</p>
        {!isNative && <p className="text-[13px] leading-relaxed text-secondary bg-secondary/5 rounded-2xl border border-secondary/10 p-4 mb-6">当前为网页预览：完成设置仅保存本地预览配置，不会开启 Android 保护服务。</p>}
        {loading && <p role="status" className="text-[13px] text-gray-600 mb-4">正在读取设备设置，请稍候...</p>}

        {/* Session Limit */}
        <section className="card p-5 mb-4" aria-labelledby="session-heading">
        <h2 id="session-heading" className="text-[16px] font-semibold text-gray-900 mb-1">连续使用限额</h2>
        <p className="text-[13px] text-gray-600 mb-4">所选应用共享计时，切换应用不会重新计时。</p>
        <div className="flex flex-wrap gap-2 mb-4" role="group" aria-label="连续使用限额">
          {sessionOptions.map(s => (
            <button
              key={s}
              onClick={() => setSession(s)}
              disabled={controlsDisabled}
              aria-pressed={session === s}
              className={`min-h-[44px] px-4 rounded-xl text-[14px] font-medium transition-colors disabled:opacity-50 ${
                session === s ? "bg-secondary text-white" : "bg-gray-100 text-gray-700"
              }`}
            >
              {s}分钟
            </button>
          ))}
        </div>
        <p className="text-[13px] text-gray-600 leading-6 rounded-xl bg-secondary/5 p-3" aria-live="polite">
          {limits.perceptionMinutes}分钟：轻提醒 · {limits.cognitionMinutes}分钟：强提醒 · {limits.blockedMinutes}分钟：进入休息引导
        </p>
        </section>

        {/* Rest Duration */}
        <section className="card p-5 mb-4" aria-labelledby="rest-heading">
        <h2 id="rest-heading" className="text-[16px] font-semibold text-gray-900 mb-4">休息时长</h2>
        <div className="grid grid-cols-3 gap-2 mb-3" role="group" aria-label="休息时长">
          {restOptions.map(r => (
            <button
              key={r}
              onClick={() => setRestDur(r)}
              disabled={controlsDisabled}
              aria-pressed={restDur === r}
              className={`min-h-[44px] rounded-xl text-[14px] font-medium transition-colors disabled:opacity-50 ${
                restDur === r ? "bg-primary text-white" : "bg-gray-100 text-gray-700"
              }`}
            >
              {r >= 60 ? `${r / 60}分钟` : `${r}秒`}
            </button>
          ))}
        </div>
        <p className="text-[13px] text-gray-600 leading-relaxed">完成休息后，将获得固定10分钟访问窗口</p>
        </section>

        {/* Emergency Unlock Toggle */}
        <div className="card p-5 mb-6">
          <button
            onClick={() => setEmergencyUnlock(!emergencyUnlock)}
            disabled={controlsDisabled}
            className="w-full min-h-[52px] flex items-center gap-4 text-left disabled:opacity-50"
            role="switch"
            aria-checked={emergencyUnlock}
            aria-label="允许每日一次紧急使用"
          >
            <div className="flex-1 min-w-0">
              <p className="text-[14px] font-semibold text-gray-900">紧急解锁</p>
              <p className="text-[13px] text-gray-600 mt-1">每日一次紧急使用</p>
              <p className="text-[13px] text-gray-600 mt-1 leading-relaxed">长按10秒后开放5分钟，并记录本地事件</p>
            </div>
            <span aria-hidden="true" className={`shrink-0 w-12 h-7 rounded-full p-1 transition-colors ${emergencyUnlock ? "bg-primary" : "bg-gray-300"}`}>
              <span className={`block w-5 h-5 rounded-full bg-white transition-transform ${emergencyUnlock ? "translate-x-5" : ""}`} />
            </span>
          </button>
        </div>

        {/* Target Apps */}
        <h2 className="text-[16px] font-semibold text-gray-900 mb-2">受限应用</h2>
        <p className="text-[13px] text-gray-600 mb-3">从想少刷一点的应用开始，至少选择一个。</p>

        {targetApps.length > 0 ? (
          <button
            onClick={chooseApps}
            disabled={controlsDisabled}
            className="card w-full p-4 mb-6 flex items-center gap-3 text-left disabled:opacity-50"
          >
            <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center">
              <AppWindow size={20} className="text-primary" />
            </div>
            <div className="flex-1">
              <p className="text-[14px] font-medium">受限应用</p>
              <p className="text-[13px] text-gray-500">已选择 {targetApps.length} 个应用</p>
            </div>
            <ChevronRight size={18} className="text-gray-400" />
          </button>
        ) : (
          <button
            onClick={chooseApps}
            disabled={controlsDisabled}
            className="card-lg w-full p-6 mb-6 flex flex-col items-center gap-3 border border-dashed border-primary/30 disabled:opacity-50"
          >
            <div className="w-14 h-14 rounded-2xl bg-primary/10 flex items-center justify-center">
              <AppWindow size={28} className="text-primary" />
            </div>
            <p className="text-[15px] font-medium text-gray-800">选择受限应用</p>
            <p className="text-[13px] text-gray-400">至少选择一个需要保护的应用</p>
          </button>
        )}

        {/* Error */}
        {error && (
          <div role="alert" className="mb-4 p-4 bg-error/5 border border-error/20 rounded-2xl">
            <p className="text-[13px] text-error mb-3">{error}</p>
            <div className="flex gap-2">
              {loadFailed ? (
                <button onClick={retryLoad} disabled={loading} className="flex-1 min-h-[44px] py-2 rounded-xl bg-error/10 text-error text-[13px] font-medium disabled:opacity-50">
                  重新读取设置
                </button>
              ) : error.includes("权限") ? (
                <button onClick={() => navigate("/permissions")} disabled={saving} className="flex-1 min-h-[44px] py-2 rounded-xl bg-error/10 text-error text-[13px] font-medium disabled:opacity-50">
                  前往权限设置
                </button>
              ) : error.includes("应用") ? (
                <button onClick={chooseApps} disabled={controlsDisabled} className="flex-1 min-h-[44px] py-2 rounded-xl bg-error/10 text-error text-[13px] font-medium disabled:opacity-50">
                  前往选择应用
                </button>
              ) : (
                <button onClick={finish} disabled={controlsDisabled} className="flex-1 min-h-[44px] py-2 rounded-xl bg-error/10 text-error text-[13px] font-medium disabled:opacity-50">
                  重试
                </button>
              )}
            </div>
          </div>
        )}

        {/* Submit */}
        <div className="mt-auto">
          <button onClick={finish} disabled={controlsDisabled || !canComplete} className="btn-primary w-full disabled:opacity-50">
            {loading ? "正在读取设置..." : loadFailed ? "请先重新读取设置" : saving
              ? (saveState === "saving" ? "正在保存设置..." : "正在启动保护...")
              : canComplete
                ? "开启保护"
                : "请先选择受限应用"}
          </button>
        </div>
      </motion.div>
    </div>
  );
}
