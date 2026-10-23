// src/pages/ProfilePreferences.tsx
import { useState } from "react";
import { useNavigate } from "react-router";
import { ArrowLeft, User, Target, Palette, Save, Check } from "lucide-react";
import { useStore } from "../hooks/useStore";
import { themeGlows } from "./rest-mode/themeData";

const bgNames = ["森林", "海洋", "山脉", "花园", "日落"];
const bgPreviews = bgNames.map((_, index) => themeGlows[index].baseBg);

const typeOptions = [
  { value: "student" as const, label: "学生" },
  { value: "worker" as const, label: "上班族" },
  { value: "other" as const, label: "其他" },
];

const goalOptions = [30, 60, 90, 120, 180, 240];

function formatGoal(minutes: number): string {
  if (minutes < 60) return minutes + "分钟";
  const hours = minutes / 60;
  if (Number.isInteger(hours)) return hours + "小时";
  return hours.toFixed(1) + "小时";
}

export default function ProfilePreferences() {
  const navigate = useNavigate();
  const profile = useStore((s) => s.profile);
  const updateProfile = useStore((s) => s.updateProfile);

  const [name, setName] = useState(profile.name);
  const [type, setType] = useState<"student" | "worker" | "other">(profile.type);
  const [dailyGoal, setDailyGoal] = useState(profile.dailyGoal);
  const [background, setBackground] = useState(profile.selectedBackground);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const handleSave = () => {
    setSaving(true);
    setError("");
    try {
      updateProfile({
        name: name.trim(),
        type,
        dailyGoal,
        selectedBackground: background,
      });
      navigate("/profile");
    } catch {
      setError("保存失败，请重试。");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col pb-10 px-5 pt-7 sm:px-8 min-h-dvh">
      <div className="flex items-center gap-3 mb-7">
        <button
          onClick={() => navigate("/profile")}
          aria-label="返回个人中心"
          className="w-[44px] h-[44px] shrink-0 rounded-full bg-white card flex items-center justify-center"
        >
          <ArrowLeft size={20} />
        </button>
        <div>
          <h1 className="text-[24px] font-bold tracking-tight text-gray-900">偏好设置</h1>
          <p className="text-[13px] text-gray-500 mt-1">把这里，布置成你喜欢的样子</p>
        </div>
      </div>

      <section className="card p-5 mb-4" aria-labelledby="nickname-label">
      <label htmlFor="profile-nickname" id="nickname-label" className="text-[15px] font-semibold text-gray-900 mb-3 flex items-center gap-2">
        <User size={18} className="text-primary" aria-hidden="true" />
        昵称
      </label>
      <input
        id="profile-nickname"
        type="text"
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="FlowBreak 用户"
        className="w-full min-h-[50px] p-3.5 rounded-xl border border-gray-200 bg-gray-50 text-[15px] text-gray-900 outline-none focus:ring-2 focus:ring-primary/30"
        aria-describedby="nickname-help"
        maxLength={20}
      />
      <p id="nickname-help" className="text-[13px] text-gray-500 mt-3">可以留空，最多 20 个字符。只在本机显示。</p>
      </section>

      <section className="card p-5 mb-4" aria-labelledby="identity-heading">
      <h2 id="identity-heading" className="text-[15px] font-semibold text-gray-900 mb-3 flex items-center gap-2">
        <User size={18} className="text-primary" aria-hidden="true" />
        身份
      </h2>
      <div className="grid grid-cols-3 gap-2" role="group" aria-label="身份">
        {typeOptions.map((opt) => (
          <button
            key={opt.value}
            onClick={() => setType(opt.value)}
            aria-pressed={type === opt.value}
            className={
              "min-h-[44px] px-2 rounded-xl text-[14px] font-medium transition-colors " +
              (type === opt.value
                ? "bg-primary text-white"
                : "bg-gray-100 text-gray-700")
            }
          >
            {opt.label}
          </button>
        ))}
      </div>
      <p className="text-[13px] text-gray-500 mt-3">仅用于个性化展示，不影响保护规则。</p>
      </section>

      <section className="card p-5 mb-4" aria-labelledby="daily-goal-heading">
      <h2 id="daily-goal-heading" className="text-[15px] font-semibold text-gray-900 mb-3 flex items-center gap-2">
        <Target size={18} className="text-primary" aria-hidden="true" />
        每日目标
      </h2>
      <div className="flex flex-wrap gap-2 mb-3" role="group" aria-label="每日目标">
        {goalOptions.map((goal) => (
          <button
            key={goal}
            onClick={() => setDailyGoal(goal)}
            aria-pressed={dailyGoal === goal}
            className={
              "min-h-[44px] px-4 rounded-xl text-[14px] font-medium transition-colors " +
              (dailyGoal === goal
                ? "bg-primary text-white"
                : "bg-gray-100 text-gray-700")
            }
          >
            {formatGoal(goal)}
          </button>
        ))}
      </div>
      <p className="text-[13px] text-gray-500 leading-relaxed">已选应用的每日参考目标，不会触发额外阻断。连续使用限额请在「阻断设置」中调整。</p>
      </section>

      <section className="card p-5 mb-6" aria-labelledby="background-heading">
      <h2 id="background-heading" className="text-[15px] font-semibold text-gray-900 mb-3 flex items-center gap-2">
        <Palette size={18} className="text-secondary" aria-hidden="true" />
        休息背景
      </h2>
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3" role="group" aria-label="休息背景">
        {bgPreviews.map((gradient, idx) => (
          <button
            key={idx}
            onClick={() => setBackground(idx)}
            aria-pressed={background === idx}
            className={
              "relative h-24 rounded-2xl overflow-hidden transition-all " +
              (background === idx ? "ring-2 ring-primary ring-offset-2" : "")
            }
          >
            <div aria-hidden="true" className="w-full h-full" style={{ background: gradient }} />
            <span className="absolute inset-0 bg-black/10" aria-hidden="true" />
            <span className="absolute inset-0 flex items-center justify-center text-white text-[14px] font-medium drop-shadow">
              {bgNames[idx]}
            </span>
            {background === idx && <span className="absolute top-2 right-2 w-6 h-6 rounded-full bg-white flex items-center justify-center text-primary" aria-hidden="true"><Check size={14} /></span>}
          </button>
        ))}
      </div>
      <p className="text-[13px] text-gray-500 mt-4">休息时换个风景，也给视线一点空间。</p>
      </section>

      {error && (
        <div role="alert" className="mb-4 p-4 bg-error/5 border border-error/20 rounded-2xl">
          <p className="text-[13px] text-error">{error}</p>
        </div>
      )}

      <button
        onClick={handleSave}
        disabled={saving}
        className="btn-primary w-full flex items-center justify-center gap-2 disabled:opacity-50"
      >
        <Save size={18} />
        <span>{saving ? "保存中..." : "保存"}</span>
      </button>
      <p className="text-[13px] text-gray-500 text-center mt-4">偏好仅保存在这台设备，不会上传。</p>
    </div>
  );
}
