// src/pages/ProfilePreferences.tsx
import { useState } from "react";
import { useNavigate } from "react-router";
import { ArrowLeft, User, Target, Palette, Save } from "lucide-react";
import { useStore } from "../hooks/useStore";

const bgNames = ["森林", "海洋", "山脉", "花园", "日落"];
const bgPreviews = [
  "bg-gradient-to-br from-green-900 via-green-700 to-green-400",
  "bg-gradient-to-br from-blue-900 via-blue-700 to-blue-400",
  "bg-gradient-to-br from-gray-800 via-gray-600 to-gray-400",
  "bg-gradient-to-br from-pink-900 via-pink-600 to-pink-300",
  "bg-gradient-to-br from-orange-800 via-orange-600 to-orange-300",
];

const typeOptions = [
  { value: "student" as const, label: "学生" },
  { value: "worker" as const, label: "上班族" },
  { value: "other" as const, label: "其他" },
];

const goalOptions = [30, 60, 90, 120, 180];

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
    <div className="flex flex-col pb-24 px-5 pt-6 no-scrollbar overflow-y-auto min-h-dvh">
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate("/profile")}
          aria-label="返回个人中心"
          className="w-10 h-10 rounded-full bg-white card flex items-center justify-center"
        >
          <ArrowLeft size={20} />
        </button>
        <h1 className="text-[24px] font-bold text-gray-900">偏好设置</h1>
      </div>

      <h3 className="text-[16px] font-bold text-gray-900 mb-3 flex items-center gap-2">
        <User size={18} className="text-primary" />
        昵称
      </h3>
      <input
        type="text"
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder="FlowBreak 用户"
        className="card w-full p-4 text-[15px] text-gray-900 mb-8 outline-none focus:ring-2 focus:ring-primary/30"
        maxLength={20}
      />

      <h3 className="text-[16px] font-bold text-gray-900 mb-3 flex items-center gap-2">
        <User size={18} className="text-primary" />
        身份
      </h3>
      <div className="flex flex-wrap gap-2 mb-8">
        {typeOptions.map((opt) => (
          <button
            key={opt.value}
            onClick={() => setType(opt.value)}
            className={
              "px-5 py-2.5 rounded-xl text-[14px] font-medium transition-colors " +
              (type === opt.value
                ? "bg-primary text-white"
                : "bg-gray-100 text-gray-700")
            }
          >
            {opt.label}
          </button>
        ))}
      </div>

      <h3 className="text-[16px] font-bold text-gray-900 mb-3 flex items-center gap-2">
        <Target size={18} className="text-accent" />
        每日目标
      </h3>
      <div className="flex flex-wrap gap-2 mb-3">
        {goalOptions.map((goal) => (
          <button
            key={goal}
            onClick={() => setDailyGoal(goal)}
            className={
              "px-4 py-2.5 rounded-xl text-[14px] font-medium transition-colors " +
              (dailyGoal === goal
                ? "bg-accent text-white"
                : "bg-gray-100 text-gray-700")
            }
          >
            {formatGoal(goal)}
          </button>
        ))}
      </div>
      <p className="text-[12px] text-gray-400 mb-8">每日屏幕使用时间目标上限</p>

      <h3 className="text-[16px] font-bold text-gray-900 mb-3 flex items-center gap-2">
        <Palette size={18} className="text-secondary" />
        休息背景
      </h3>
      <div className="grid grid-cols-3 gap-3 mb-8">
        {bgPreviews.map((gradient, idx) => (
          <button
            key={idx}
            onClick={() => setBackground(idx)}
            className={
              "relative h-20 rounded-xl overflow-hidden transition-all " +
              (background === idx ? "ring-2 ring-primary ring-offset-2" : "")
            }
          >
            <div className={"w-full h-full " + gradient} />
            <span className="absolute inset-0 flex items-center justify-center text-white text-[12px] font-medium drop-shadow">
              {bgNames[idx]}
            </span>
          </button>
        ))}
      </div>

      {error && (
        <div className="mb-4 p-4 bg-error/5 border border-error/20 rounded-2xl">
          <p className="text-[12px] text-error">{error}</p>
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
    </div>
  );
}