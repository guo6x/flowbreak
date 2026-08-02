import { useState } from "react";
import { useNavigate } from "react-router";
import { motion } from "framer-motion";
import { Capacitor } from "@capacitor/core";
import { ArrowLeft, AppWindow, ChevronRight, Sparkles } from "lucide-react";
import { useStore } from "../hooks/useStore";
import { NativeFlow } from "../backend/nativeFlow";
import { translateLimit } from "../utils/protectionStatus";

const sessionOptions = [15, 20, 25, 30, 45]; // 分钟
const restOptions = [120, 180, 300]; // 秒 → 2, 3, 5 分钟

export default function Personalize() {
  const navigate = useNavigate();
  const profile = useStore(s => s.profile);
  const updateProfile = useStore(s => s.updateProfile);
  const setMonitoring = useStore(s => s.setMonitoring);
  const [session, setSession] = useState(profile.sessionLimit);
  const [restDur, setRestDur] = useState(profile.restDuration);
  const [emergencyUnlock, setEmergencyUnlock] = useState(profile.allowEmergencyUnlock);
  const [saving, setSaving] = useState(false);
  const [saveState, setSaveState] = useState<"idle" | "saving" | "starting">("idle");
  const [error, setError] = useState("");
  const targetApps = profile.targetApps || [];

  const finish = async () => {
    if (targetApps.length === 0) {
      setError("请先选择至少一个受限应用后再完成设置。");
      return;
    }
    setSaving(true);
    setSaveState("saving");
    setError("");
    try {
      if (Capacitor.isNativePlatform()) {
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
        onboardingDone: true,
      });
      navigate("/dashboard");
    } catch (err) {
      const msg = err instanceof Error ? err.message : "初始化保护服务失败，请检查权限后重试。";
      setError(msg);
    } finally {
      setSaving(false);
      setSaveState("idle");
    }
  };

  const limits = translateLimit(session);
  const canComplete = targetApps.length > 0;

  return (
    <div className="flex flex-col min-h-dvh px-8 pt-12 pb-12 no-scrollbar overflow-y-auto">
      <button onClick={() => navigate("/permissions")} aria-label="返回权限设置" className="w-10 h-10 rounded-full bg-white card flex items-center justify-center mb-4">
        <ArrowLeft size={20} />
      </button>
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col flex-1"
      >
        {/* Header */}
        <div className="w-14 h-14 rounded-2xl bg-accent/10 flex items-center justify-center mb-6">
          <Sparkles size={32} className="text-accent" />
        </div>
        <h1 className="text-[24px] font-bold text-gray-900 mb-1">设置你的保护规则</h1>
        <p className="text-[14px] text-gray-500 mb-8">配置后将立即启动保护</p>

        {/* Session Limit */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">连续使用限额</h3>
        <div className="flex flex-wrap gap-2 mb-3">
          {sessionOptions.map(s => (
            <button
              key={s}
              onClick={() => setSession(s)}
              className={`px-4 py-2.5 rounded-xl text-[14px] font-medium transition-colors ${
                session === s ? "bg-secondary text-white" : "bg-gray-100 text-gray-700"
              }`}
            >
              {s}分钟
            </button>
          ))}
        </div>
        <p className="text-[12px] text-gray-400 mb-8">
          {limits.perceptionMinutes}分钟：轻提醒 · {limits.cognitionMinutes}分钟：强提醒 · {limits.blockedMinutes}分钟：进入休息引导
        </p>

        {/* Rest Duration */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">休息时长</h3>
        <div className="flex flex-wrap gap-2 mb-3">
          {restOptions.map(r => (
            <button
              key={r}
              onClick={() => setRestDur(r)}
              className={`px-4 py-2.5 rounded-xl text-[14px] font-medium transition-colors ${
                restDur === r ? "bg-accent text-white" : "bg-gray-100 text-gray-700"
              }`}
            >
              {r >= 60 ? `${r / 60}分钟` : `${r}秒`}
            </button>
          ))}
        </div>
        <p className="text-[12px] text-gray-400 mb-5">完成休息后，将获得固定10分钟访问窗口</p>

        {/* Emergency Unlock Toggle */}
        <div className="flex items-center justify-between p-3 bg-gray-50 rounded-xl mb-8">
          <div>
            <p className="text-[13px] font-medium text-gray-700">紧急解锁</p>
            <p className="text-[12px] text-gray-400">每日一次紧急使用</p>
            <p className="text-[11px] text-gray-400">长按10秒后开放5分钟，并记录本地事件</p>
          </div>
          <button
            onClick={() => setEmergencyUnlock(!emergencyUnlock)}
            className={`relative w-12 h-7 rounded-full p-1 transition-colors ${emergencyUnlock ? "bg-primary" : "bg-gray-300"}`}
            role="switch"
            aria-checked={emergencyUnlock}
            aria-label="允许每日一次紧急使用"
          >
            <div className={`w-5 h-5 rounded-full bg-white transition-transform ${emergencyUnlock ? "translate-x-5" : ""}`} />
          </button>
        </div>

        {/* Target Apps */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">受限应用</h3>
        <p className="text-[12px] text-gray-500 mb-3">选择容易沉迷的应用，它们共享同一连续使用限额。</p>

        {targetApps.length > 0 ? (
          <button
            onClick={() => navigate("/target-apps", { state: { returnTo: "/personalize" } })}
            className="card w-full p-4 mb-10 flex items-center gap-3 text-left"
          >
            <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center">
              <AppWindow size={20} className="text-primary" />
            </div>
            <div className="flex-1">
              <p className="text-[14px] font-medium">受限应用</p>
              <p className="text-[12px] text-gray-500">已选择 {targetApps.length} 个应用</p>
            </div>
            <ChevronRight size={18} className="text-gray-400" />
          </button>
        ) : (
          <button
            onClick={() => navigate("/target-apps", { state: { returnTo: "/personalize" } })}
            className="card-lg w-full p-6 mb-10 flex flex-col items-center gap-3"
          >
            <div className="w-14 h-14 rounded-2xl bg-primary/10 flex items-center justify-center">
              <AppWindow size={28} className="text-primary" />
            </div>
            <p className="text-[15px] font-medium text-gray-800">选择受限应用</p>
            <p className="text-[12px] text-gray-400">至少选择一个需要保护的应用</p>
          </button>
        )}

        {/* Error */}
        {error && (
          <div className="mb-4 p-4 bg-error/5 border border-error/20 rounded-2xl">
            <p className="text-[12px] text-error mb-3">{error}</p>
            <div className="flex gap-2">
              {error.includes("权限") ? (
                <button onClick={() => navigate("/permissions")} className="flex-1 py-2 rounded-xl bg-error/10 text-error text-[13px] font-medium">
                  前往权限设置
                </button>
              ) : error.includes("应用") ? (
                <button onClick={() => navigate("/target-apps", { state: { returnTo: "/personalize" } })} className="flex-1 py-2 rounded-xl bg-error/10 text-error text-[13px] font-medium">
                  前往选择应用
                </button>
              ) : (
                <button onClick={finish} className="flex-1 py-2 rounded-xl bg-error/10 text-error text-[13px] font-medium">
                  重试
                </button>
              )}
            </div>
          </div>
        )}

        {/* Submit */}
        <div className="mt-auto">
          <button onClick={finish} disabled={saving || !canComplete} className="btn-primary w-full disabled:opacity-50">
            {saving
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