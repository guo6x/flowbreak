// src/pages/RestMode.tsx
// PRD 2.3.2 + 6.2.3 休息引导全屏页面
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Eye, StretchHorizontal, Wind, ChevronLeft, ChevronRight, Check } from 'lucide-react';
import { addPoints, completeRestActivity, getProfile } from '../backend/storage';

const activities = [
  {
    id: 'eye',
    icon: Eye,
    title: '眼部放松',
    desc: '转动眼球 + 看远处绿色植物',
    steps: ['闭上眼睛深呼吸3次', '慢慢睁开，向上看5秒', '缓缓向右转动眼球', '向下看5秒', '向左转动眼球完成一圈', '看向窗外最远的绿色物体'],
    color: '#4CAF50',
  },
  {
    id: 'stretch',
    icon: StretchHorizontal,
    title: '身体拉伸',
    desc: '颈部 + 肩部拉伸动作',
    steps: ['站起来，双脚与肩同宽', '将头缓缓向右倾斜', '保持5秒，感受左侧拉伸', '换向左侧倾斜，保持5秒', '双手向上举起伸展', '缓缓放下，转动肩膀'],
    color: '#2196F3',
  },
  {
    id: 'breathe',
    icon: Wind,
    title: '深呼吸',
    desc: '4-7-8 呼吸法',
    steps: ['找一个舒适的坐姿', '用鼻子吸气 4 秒', '屏住呼吸 7 秒', '用嘴慢慢呼气 8 秒', '重复 3 次', '感受身体的放松'],
    color: '#FF9800',
  },
];

const bgGradients = [
  'linear-gradient(135deg, #1B5E20 0%, #2E7D32 30%, #43A047 60%, #66BB6A 100%)', // 森林
  'linear-gradient(135deg, #0D47A1 0%, #1565C0 30%, #1E88E5 60%, #42A5F5 100%)', // 海洋
  'linear-gradient(135deg, #37474F 0%, #546E7A 30%, #78909C 60%, #B0BEC5 100%)', // 山脉
  'linear-gradient(135deg, #880E4F 0%, #AD1457 30%, #E91E63 60%, #F48FB1 100%)', // 花园
  'linear-gradient(135deg, #E65100 0%, #EF6C00 30%, #FB8C00 60%, #FFB74D 100%)', // 日落
];

export default function RestMode() {
  const navigate = useNavigate();
  const profile = getProfile();
  const [activityIdx, setActivityIdx] = useState(0);
  const [timeLeft, setTimeLeft] = useState(profile.restDuration || 180);
  const [stepIdx, setStepIdx] = useState(0);
  const [showReward, setShowReward] = useState(false);

  const activity = activities[activityIdx];
  const Icon = activity.icon;

  // Countdown timer
  useEffect(() => {
    if (timeLeft <= 0 || showReward) return;
    const timer = setTimeout(() => setTimeLeft(t => t - 1), 1000);
    return () => clearTimeout(timer);
  }, [timeLeft, showReward]);

  // Cycle through steps
  useEffect(() => {
    if (showReward) return;
    const interval = setInterval(() => {
      setStepIdx(prev => (prev + 1) % activity.steps.length);
    }, 8000);
    return () => clearInterval(interval);
  }, [activity.steps.length, showReward]);

  const formatTime = (s: number) => `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`;
  const totalDuration = profile.restDuration || 180;
  const progress = 1 - timeLeft / totalDuration;

  const handleComplete = () => {
    completeRestActivity(activity.id as 'eye' | 'stretch' | 'breathe', totalDuration);
    addPoints(10);
    setShowReward(true);
  };

  const handleFinish = () => {
    navigate('/dashboard');
  };

  const handleClose = () => {
    const elapsed = totalDuration - timeLeft;
    if (elapsed >= 30) {
      completeRestActivity(activity.id as 'eye' | 'stretch' | 'breathe', elapsed);
      addPoints(Math.max(1, Math.floor(10 * elapsed / totalDuration)));
    }
    navigate(-1);
  };

  // ===== Reward Screen (PRD 2.3.2 组件2) =====
  if (showReward) {
    return (
      <div className="fixed inset-0 z-[100] bg-white flex flex-col items-center justify-center px-8 text-center">
        {/* Confetti particles */}
        {Array.from({ length: 20 }).map((_, i) => (
          <motion.div
            key={i}
            initial={{ y: -100, x: 0, opacity: 1, rotate: 0 }}
            animate={{ y: '100vh', rotate: 720, opacity: 0 }}
            transition={{ duration: 2 + Math.random() * 2, delay: Math.random() * 0.5 }}
            className="absolute w-3 h-3 rounded-sm"
            style={{
              backgroundColor: ['#4CAF50', '#2196F3', '#FF9800', '#F44336', '#9C27B0', '#FFEB3B'][i % 6],
              left: `${10 + Math.random() * 80}%`,
            }}
          />
        ))}

        <motion.div
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ type: 'spring', stiffness: 200, damping: 15, delay: 0.3 }}
          className="z-10"
        >
          <div className="text-6xl mb-4">🎉</div>
          <h1 className="text-[24px] font-bold text-gray-900 mb-2">休息完成！</h1>
          <p className="text-[14px] text-gray-500 mb-6">你做得很棒，眼睛和身体都感谢你</p>

          <div className="flex gap-4 justify-center mb-8">
            <div className="card p-4 flex flex-col items-center">
              <span className="text-2xl mb-1">⭐</span>
              <span className="text-[20px] font-bold text-accent">+10</span>
              <span className="text-[11px] text-gray-500">积分</span>
            </div>
            <div className="card p-4 flex flex-col items-center">
              <span className="text-2xl mb-1">🛡️</span>
              <span className="text-[14px] font-bold text-primary">健康守护者</span>
              <span className="text-[11px] text-gray-500">徽章</span>
            </div>
          </div>

          <button onClick={handleFinish} className="btn-primary w-full max-w-xs mx-auto">
            继续使用
          </button>
        </motion.div>
      </div>
    );
  }

  // ===== Main Rest Mode (PRD 6.2.3) =====
  return (
    <div
      className="fixed inset-0 z-[100] flex flex-col text-white"
      style={{ background: bgGradients[profile.selectedBackground] || bgGradients[0] }}
    >
      {/* Ambient overlay */}
      <div className="absolute inset-0 bg-black/20" />

      {/* Top bar */}
      <div className="relative z-10 flex items-center justify-between px-5 pt-14 pb-4">
        <button onClick={handleClose} className="w-10 h-10 rounded-full bg-white/15 flex items-center justify-center backdrop-blur-sm">
          <X size={20} />
        </button>
        <span className="text-[12px] text-white/70 font-medium">休息模式</span>
        <div className="w-10" />
      </div>

      {/* Content */}
      <div className="relative z-10 flex-1 flex flex-col items-center justify-center px-8">
        {/* Timer */}
        <div className="relative mb-10">
          {/* Ring */}
          <svg width="180" height="180" viewBox="0 0 180 180">
            <circle cx="90" cy="90" r="80" fill="none" stroke="rgba(255,255,255,0.15)" strokeWidth="6" />
            <motion.circle
              cx="90" cy="90" r="80"
              fill="none"
              stroke="white"
              strokeWidth="6"
              strokeLinecap="round"
              strokeDasharray={2 * Math.PI * 80}
              strokeDashoffset={2 * Math.PI * 80 * (1 - progress)}
              transform="rotate(-90 90 90)"
              transition={{ duration: 0.5 }}
            />
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-[48px] font-light tracking-tight">{formatTime(timeLeft)}</span>
          </div>
        </div>

        {/* Activity Selector */}
        <div className="flex items-center gap-4 mb-6">
          <button onClick={() => setActivityIdx(i => (i - 1 + activities.length) % activities.length)} className="w-8 h-8 rounded-full bg-white/15 flex items-center justify-center">
            <ChevronLeft size={18} />
          </button>
          <div className="flex items-center gap-2">
            <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ backgroundColor: activity.color + '40' }}>
              <Icon size={22} />
            </div>
            <div>
              <p className="text-[16px] font-medium">{activity.title}</p>
              <p className="text-[12px] text-white/60">{activity.desc}</p>
            </div>
          </div>
          <button onClick={() => setActivityIdx(i => (i + 1) % activities.length)} className="w-8 h-8 rounded-full bg-white/15 flex items-center justify-center">
            <ChevronRight size={18} />
          </button>
        </div>

        {/* Step Guide */}
        <AnimatePresence mode="wait">
          <motion.div
            key={stepIdx}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="bg-white/10 backdrop-blur-sm rounded-2xl px-6 py-4 max-w-xs text-center"
          >
            <p className="text-[11px] text-white/50 mb-1">步骤 {stepIdx + 1}/{activity.steps.length}</p>
            <p className="text-[16px] font-medium leading-relaxed">{activity.steps[stepIdx]}</p>
          </motion.div>
        </AnimatePresence>
      </div>

      {/* Bottom */}
      <div className="relative z-10 px-8 pb-12">
        {/* Progress bar */}
        <div className="w-full h-1.5 bg-white/15 rounded-full overflow-hidden mb-4">
          <motion.div
            className="h-full bg-white rounded-full"
            animate={{ width: `${progress * 100}%` }}
            transition={{ duration: 0.5 }}
          />
        </div>

        {timeLeft <= 0 ? (
          <motion.button
            initial={{ scale: 0.9, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            onClick={handleComplete}
            className="w-full py-4 rounded-2xl bg-white text-primary font-bold text-[16px] flex items-center justify-center gap-2"
          >
            <Check size={20} />
            完成休息
          </motion.button>
        ) : (
          <button onClick={handleClose} className="w-full py-3 text-white/50 text-[14px] text-center">
            提前结束
          </button>
        )}
      </div>
    </div>
  );
}
