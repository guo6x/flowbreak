// src/pages/Personalize.tsx
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { motion } from 'framer-motion';
import { Capacitor } from '@capacitor/core';
import { ArrowLeft, GraduationCap, Briefcase, Users, Sparkles, AppWindow, ChevronRight } from 'lucide-react';
import { useStore } from '../hooks/useStore';
import { NativeFlow } from '../backend/nativeFlow';

const userTypes = [
  { id: 'student' as const, icon: GraduationCap, label: '学生' },
  { id: 'worker' as const, icon: Briefcase, label: '上班族' },
  { id: 'other' as const, icon: Users, label: '其他' },
];

const goalOptions = [120, 180, 240, 300, 360]; // 分钟
const sessionOptions = [15, 20, 25, 30, 45]; // 分钟
const restOptions = [120, 180, 300]; // 秒，最低 2 分钟

const backgrounds = ['🌲 森林', '🌊 海洋', '🏔️ 山脉', '🌸 花园', '🌅 日落'];

export default function Personalize() {
  const navigate = useNavigate();
  const profile = useStore(s => s.profile);
  const updateProfile = useStore(s => s.updateProfile);
  const setMonitoring = useStore(s => s.setMonitoring);
  const [name, setName] = useState(profile.name);
  const [isEditingName, setIsEditingName] = useState(!profile.name);
  const [type, setType] = useState<'student' | 'worker' | 'other'>(profile.type);
  const [goal, setGoal] = useState(profile.dailyGoal);
  const [session, setSession] = useState(profile.sessionLimit);
  const [restDur, setRestDur] = useState(profile.restDuration);
  const [bg, setBg] = useState(profile.selectedBackground);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const targetApps = profile.targetApps || [];

  const finish = async () => {
    if (targetApps.length === 0) {
      setError('请先选择至少一个受限应用后再完成设置。');
      return;
    }
    setSaving(true);
    setError('');
    try {
      if (Capacitor.isNativePlatform()) {
        await NativeFlow.saveSettings({
          limitMinutes: session,
          restDuration: restDur,
          targetApps,
          allowEmergencyUnlock: profile.allowEmergencyUnlock,
        });
        await NativeFlow.startService({
          limitMinutes: session,
          apps: targetApps,
          monitoringEnabled: true,
        });
      }
      setMonitoring(true);
      updateProfile({
        name: name.trim(),
        type,
        dailyGoal: goal,
        sessionLimit: session,
        restDuration: restDur,
        selectedBackground: bg,
        onboardingDone: true,
      });
      navigate('/dashboard');
    } catch {
      setError('初始化保护服务失败，请检查权限后重试。');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col min-h-dvh px-8 pt-12 pb-12 no-scrollbar overflow-y-auto">
      <button onClick={() => navigate('/permissions')} aria-label="返回权限设置" className="w-10 h-10 rounded-full bg-white card flex items-center justify-center mb-4">
        <ArrowLeft size={20} />
      </button>
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col"
      >
        {/* Header */}
        <div className="w-14 h-14 rounded-2xl bg-accent/10 flex items-center justify-center mb-6">
          <Sparkles size={32} className="text-accent" />
        </div>
        <h1 className="text-[24px] font-bold text-gray-900 mb-1">
          {profile.name ? `你好，${profile.name}！让我们再了解一下` : '个性化设置'}
        </h1>
        <p className="text-[14px] text-gray-500 mb-8">帮助我们为你定制最佳体验</p>

        {isEditingName ? (
          <>
            <h3 className="text-[16px] font-bold text-gray-900 mb-3">你的昵称</h3>
            <input
              value={name}
              onChange={e => setName(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') setIsEditingName(false); }}
              maxLength={24}
              autoFocus
              placeholder="输入昵称（可选）"
              className="mb-2 bg-gray-100 rounded-2xl px-4 py-3.5 text-[14px] text-gray-900 placeholder:text-gray-400 outline-none"
            />
            <p className="text-[11px] text-gray-400 text-right mb-8">{name.length}/24</p>
          </>
        ) : (
          <div className="flex items-center justify-between mb-8 p-4 bg-gray-100 rounded-2xl">
            <div>
              <p className="text-[12px] text-gray-500 mb-0.5">当前昵称</p>
              <p className="text-[15px] font-bold text-gray-900">{name}</p>
            </div>
            <button
              onClick={() => setIsEditingName(true)}
              className="text-[13px] font-medium text-primary px-3 py-1.5 bg-primary/10 rounded-lg active:bg-primary/20 transition-colors"
            >
              修改
            </button>
          </div>
        )}

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
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">每日目标使用时间</h3>
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
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">单次连续使用提醒</h3>
        <p className="text-[12px] text-gray-500 mb-3">连续使用达到此时长后触发疲劳提醒</p>
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

        {/* Rest Duration */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">每次休息时长</h3>
        <p className="text-[12px] text-gray-500 mb-3">触发休息提醒后的建议休息时间</p>
        <div className="flex flex-wrap gap-2 mb-8">
          {restOptions.map(r => (
            <button
              key={r}
              onClick={() => setRestDur(r)}
              className={`px-4 py-2.5 rounded-xl text-[14px] font-medium transition-colors ${
                restDur === r ? 'bg-accent text-white' : 'bg-gray-100 text-gray-700'
              }`}
            >
              {r >= 60 ? `${r / 60}分钟` : `${r}秒`}
            </button>
          ))}
        </div>

        {/* Background */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">休息背景风格</h3>
        <div className="flex flex-wrap gap-2 mb-8">
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

        {/* Target Apps */}
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">监控应用</h3>
        <p className="text-[12px] text-gray-500 mb-3">选择容易沉迷的应用，它们共享同一连续使用限额。</p>
        <button
          onClick={() => navigate('/target-apps', { state: { returnTo: '/personalize' } })}
          className="card w-full p-4 mb-10 flex items-center gap-3 text-left"
        >
          <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center">
            <AppWindow size={20} className="text-primary" />
          </div>
          <div className="flex-1">
            <p className="text-[14px] font-medium">受限应用</p>
            <p className="text-[12px] text-gray-500">
              {targetApps.length > 0 ? `已选择 ${targetApps.length} 个应用` : '至少选择一个应用'}
            </p>
          </div>
          <ChevronRight size={18} className="text-gray-400" />
        </button>

        {/* Submit */}
        {error && <p className="text-[12px] text-error text-center mb-3">{error}</p>}
        <button onClick={finish} disabled={saving} className="btn-primary w-full disabled:opacity-50">
          {saving ? '正在启动保护...' : '完成设置，开始使用'}
        </button>
      </motion.div>
    </div>
  );
}
