// src/pages/Personalize.tsx
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { GraduationCap, Briefcase, Users, Sparkles } from 'lucide-react';
import { getProfile, saveProfile } from '../backend/storage';

const userTypes = [
  { id: 'student' as const, icon: GraduationCap, label: '学生' },
  { id: 'worker' as const, icon: Briefcase, label: '上班族' },
  { id: 'other' as const, icon: Users, label: '其他' },
];

const goalOptions = [120, 180, 240, 300, 360]; // 分钟
const sessionOptions = [15, 20, 25, 30, 45]; // 分钟

const backgrounds = ['🌲 森林', '🌊 海洋', '🏔️ 山脉', '🌸 花园', '🌅 日落'];

export default function Personalize() {
  const navigate = useNavigate();
  const profile = getProfile();
  const [name, setName] = useState(profile.name);
  const [type, setType] = useState<'student' | 'worker' | 'other'>(profile.type);
  const [goal, setGoal] = useState(profile.dailyGoal);
  const [session, setSession] = useState(profile.sessionLimit);
  const [bg, setBg] = useState(profile.selectedBackground);

  const finish = () => {
    saveProfile({
      name: name.trim(),
      type,
      dailyGoal: goal,
      sessionLimit: session,
      selectedBackground: bg,
      onboardingDone: true,
    });
    navigate('/dashboard');
  };

  return (
    <div className="flex flex-col min-h-dvh bg-white px-8 pt-12 pb-12 no-scrollbar overflow-y-auto">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col"
      >
        {/* Header */}
        <div className="w-14 h-14 rounded-2xl bg-accent/10 flex items-center justify-center mb-6">
          <Sparkles size={32} className="text-accent" />
        </div>
        <h1 className="text-[24px] font-bold text-gray-900 mb-1">个性化设置</h1>
        <p className="text-[14px] text-gray-500 mb-8">帮助我们为你定制最佳体验</p>

        <h3 className="text-[16px] font-bold text-gray-900 mb-3">你的昵称</h3>
        <input
          value={name}
          onChange={e => setName(e.target.value)}
          placeholder="输入昵称（可选）"
          className="mb-8 bg-gray-100 rounded-2xl px-4 py-3.5 text-[14px] text-gray-900 placeholder:text-gray-400 outline-none"
        />

        {/* User Type */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">你是</h3>
        <div className="flex gap-3 mb-8">
          {userTypes.map(t => {
            const Icon = t.icon;
            const active = type === t.id;
            return (
              <button
                key={t.id}
                onClick={() => setType(t.id)}
                className={`flex-1 flex flex-col items-center gap-2 py-4 rounded-2xl border-2 transition-colors ${
                  active ? 'border-primary bg-primary/5' : 'border-gray-300/60'
                }`}
              >
                <Icon size={24} className={active ? 'text-primary' : 'text-gray-400'} />
                <span className={`text-[12px] font-medium ${active ? 'text-primary' : 'text-gray-500'}`}>{t.label}</span>
              </button>
            );
          })}
        </div>

        {/* Daily Goal */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">每日目标屏幕时间</h3>
        <div className="flex flex-wrap gap-2 mb-8">
          {goalOptions.map(g => (
            <button
              key={g}
              onClick={() => setGoal(g)}
              className={`px-4 py-2.5 rounded-xl text-[14px] font-medium transition-colors ${
                goal === g ? 'bg-primary text-white' : 'bg-gray-100 text-gray-700'
              }`}
            >
              {g / 60}小时
            </button>
          ))}
        </div>

        {/* Session Limit */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">单次视频提醒时间</h3>
        <div className="flex flex-wrap gap-2 mb-8">
          {sessionOptions.map(s => (
            <button
              key={s}
              onClick={() => setSession(s)}
              className={`px-4 py-2.5 rounded-xl text-[14px] font-medium transition-colors ${
                session === s ? 'bg-secondary text-white' : 'bg-gray-100 text-gray-700'
              }`}
            >
              {s}分钟
            </button>
          ))}
        </div>

        {/* Background */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">休息背景风格</h3>
        <div className="flex flex-wrap gap-2 mb-10">
          {backgrounds.map((b, i) => (
            <button
              key={i}
              onClick={() => setBg(i)}
              className={`px-4 py-2.5 rounded-xl text-[14px] transition-colors ${
                bg === i ? 'bg-accent text-white font-medium' : 'bg-gray-100 text-gray-700'
              }`}
            >
              {b}
            </button>
          ))}
        </div>

        {/* Submit */}
        <button onClick={finish} className="btn-primary w-full">
          完成设置，开始使用 🎉
        </button>
      </motion.div>
    </div>
  );
}
