// src/pages/RestMode.tsx
// PRD 2.3.2 + 6.2.3 休息引导全屏页面
import { useState, useEffect, useRef, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Eye, StretchHorizontal, Wind, ChevronLeft, ChevronRight, Check, Play, Pause } from 'lucide-react';
import { useStore } from '../hooks/useStore';

// Lazy singleton AudioContext to prevent leaks
let globalAudioCtx: AudioContext | null = null;
function getAudioCtx() {
  if (!globalAudioCtx) {
    globalAudioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
  }
  return globalAudioCtx;
}

function lerpColor(c1: string, c2: string, t: number): string {
  const r1 = parseInt(c1.slice(1, 3), 16);
  const g1 = parseInt(c1.slice(3, 5), 16);
  const b1 = parseInt(c1.slice(5, 7), 16);
  const r2 = parseInt(c2.slice(1, 3), 16);
  const g2 = parseInt(c2.slice(3, 5), 16);
  const b2 = parseInt(c2.slice(5, 7), 16);
  return `rgb(${Math.round(r1 + (r2 - r1) * t)},${Math.round(g1 + (g2 - g1) * t)},${Math.round(b1 + (b2 - b1) * t)})`;
}

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

const themedParticles: Record<number, string[]> = {
  0: ['🍃', '🌿', '🪴', '🍂', '🌱', '🪵', '🌳'],
  1: ['🫧', '🐚', '🪸', '💧', '🫧', '🌊', '🐠'],
  2: ['❄️', '🏔️', '⛰️', '❄️', '🌨️', '❄️', '🗻'],
  3: ['🌸', '🌺', '🪷', '🌷', '💮', '🌼', '🏵️'],
  4: ['✨', '🔥', '💫', '🌟', '🕯️', '✨', '🌅'],
};

const calmColor = '#A5D6A7';

export default function RestMode() {
  const navigate = useNavigate();
  const profile = useStore(s => s.profile);
  const completeRest = useStore(s => s.completeRestActivity);

  function useChime() {
    const playChime = (freq: number, duration: number) => {
      try {
        const ctx = getAudioCtx();
        if (ctx.state === 'suspended') ctx.resume();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.type = 'sine';
        osc.frequency.value = freq;
        gain.gain.setValueAtTime(0.15, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration);
        osc.start(ctx.currentTime);
        osc.stop(ctx.currentTime + duration);
      } catch { /* audio not available */ }
    };
    return { playChime };
  }

  const { playChime } = useChime();

  const [activityIdx, setActivityIdx] = useState(0);
  const [timeLeft, setTimeLeft] = useState(profile.restDuration || 180);
  const [isPaused, setIsPaused] = useState(false);
  const [stepIdx, setStepIdx] = useState(0);
  const [showReward, setShowReward] = useState(false);
  const [slideDirection, setSlideDirection] = useState<1 | -1>(1);
  const [stepProgress, setStepProgress] = useState(0);
  const [rewardNumber, setRewardNumber] = useState(0);
  const [rewardContentVisible, setRewardContentVisible] = useState(false);
  const stepIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const activity = activities[activityIdx];
  const Icon = activity.icon;

  // Resume context on mount
  useEffect(() => {
    getAudioCtx().resume().catch(() => {});
  }, []);

  // Countdown timer
  useEffect(() => {
    if (timeLeft <= 0 || showReward || isPaused) return;
    const timer = setTimeout(() => setTimeLeft(t => t - 1), 1000);
    return () => clearTimeout(timer);
  }, [timeLeft, showReward, isPaused]);

  // Cycle through steps every 6 seconds
  useEffect(() => {
    if (showReward || isPaused) return;
    stepIntervalRef.current = setInterval(() => {
      setStepIdx(prev => (prev + 1) % activity.steps.length);
    }, 6000);
    return () => {
      if (stepIntervalRef.current) clearInterval(stepIntervalRef.current);
    };
  }, [activity.steps.length, showReward, isPaused]);

  // Step progress bar (fills over 6 seconds, resets when step changes)
  useEffect(() => {
    setStepProgress(0);
  }, [stepIdx]);

  useEffect(() => {
    if (showReward || isPaused) return;
    const timer = setInterval(() => {
      setStepProgress(p => (p >= 100 ? 100 : p + 100 / 60)); // 60 ticks over 6s = 100ms each
    }, 100);
    return () => clearInterval(timer);
  }, [showReward, stepIdx, isPaused]);

  // Chime on step change
  useEffect(() => {
    if (!showReward) {
      playChime(500 + stepIdx * 40, 0.25);
    }
  }, [stepIdx]);

  // Reward delay + counting animation
  useEffect(() => {
    if (!showReward) return;
    const delay = setTimeout(() => setRewardContentVisible(true), 500);
    return () => clearTimeout(delay);
  }, [showReward]);

  useEffect(() => {
    if (!rewardContentVisible || rewardNumber >= 10) return;
    const timer = setTimeout(() => setRewardNumber(r => Math.min(r + 1, 10)), 60);
    return () => clearTimeout(timer);
  }, [rewardContentVisible, rewardNumber]);

  const formatTime = (s: number) => `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`;
  const totalDuration = profile.restDuration || 180;
  const progress = 1 - timeLeft / totalDuration;

  // Lerped breath color from activity color to calm green
  const breathColor = useMemo(() => lerpColor(activity.color, calmColor, progress), [activity.color, progress]);

  const handleComplete = () => {
    // Play completion fanfare
    playChime(523, 0.2);
    setTimeout(() => playChime(659, 0.2), 150);
    setTimeout(() => playChime(784, 0.3), 300);
    completeRest(activity.id as 'eye' | 'stretch' | 'breathe', totalDuration);
    setShowReward(true);
  };

  const handleFinish = () => {
    navigate('/dashboard');
  };

  const handleClose = () => {
    const elapsed = totalDuration - timeLeft;
    if (elapsed >= 30) {
      completeRest(activity.id as 'eye' | 'stretch' | 'breathe', elapsed);
    }
    navigate(-1);
  };

  const switchActivity = (dir: 1 | -1) => {
    setSlideDirection(dir);
    setActivityIdx(i => (i + dir + activities.length) % activities.length);
    setStepIdx(0);
  };

  // ===== Reward Screen =====
  if (showReward) {
    return (
      <div className="fixed inset-0 z-[100] bg-white flex flex-col items-center justify-center px-8 text-center overflow-hidden">
        {/* Confetti particles — larger and more colorful */}
        {Array.from({ length: 25 }).map((_, i) => (
          <motion.div
            key={i}
            initial={{ y: -120, x: 0, opacity: 1, rotate: 0, scale: 1 }}
            animate={{ y: '100vh', rotate: 720 + Math.random() * 360, opacity: 0, scale: 0.5 }}
            transition={{ duration: 2.5 + Math.random() * 2, delay: Math.random() * 0.6 }}
            className="absolute w-4 h-4 rounded-sm"
            style={{
              backgroundColor: [
                '#4CAF50', '#2196F3', '#FF9800', '#F44336', '#9C27B0', '#FFEB3B',
                '#00BCD4', '#E91E63', '#8BC34A', '#FF5722', '#3F51B5', '#FFC107',
              ][i % 12],
              left: `${8 + Math.random() * 84}%`,
            }}
          />
        ))}

        {rewardContentVisible && (
          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ type: 'spring', stiffness: 200, damping: 15 }}
            className="z-10"
          >
            <div className="text-6xl mb-4">🎉</div>
            <h1 className="text-[24px] font-bold text-gray-900 mb-2">休息完成！</h1>
            <p className="text-[14px] text-gray-500 mb-6">你做得很棒，眼睛和身体都感谢你</p>

            <div className="flex gap-4 justify-center mb-8">
              <div className="card p-4 flex flex-col items-center">
                <span className="text-2xl mb-1">⭐</span>
                <span className="text-[20px] font-bold text-accent">+{rewardNumber}</span>
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
        )}
      </div>
    );
  }

  // ===== Main Rest Mode =====
  return (
    <div
      className="fixed inset-0 z-[100] flex flex-col text-white overflow-hidden"
      style={{ background: bgGradients[profile.selectedBackground] || bgGradients[0] }}
    >
      {/* Ambient overlay */}
      <div className="absolute inset-0 bg-black/20" />

      {/* Floating ambient particles */}
      {(themedParticles[profile.selectedBackground] || themedParticles[0]).map((emoji, i) => (
        <motion.div
          key={i}
          animate={{
            y: ['110vh', '-10vh'],
            x: [0, (Math.random() - 0.5) * 60],
            rotate: [0, Math.random() * 360],
            opacity: [0.6, 0]
          }}
          transition={{
            duration: 8 + Math.random() * 7,
            repeat: Infinity,
            delay: Math.random() * 5,
            ease: 'linear'
          }}
          className="absolute text-2xl"
          style={{ left: `${8 + Math.random() * 84}%` }}
        >
          {emoji}
        </motion.div>
      ))}

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
        {/* Breathing Circle Timer */}
        <div className="relative mb-10" style={{ width: 180, height: 180 }}>
          {/* Glow behind breathing circle */}
          <motion.div
            className="absolute inset-0 rounded-full"
            animate={{ scale: [1, 1.15, 1], opacity: [0.2, 0.35, 0.2] }}
            transition={{ duration: 4, repeat: Infinity, ease: 'easeInOut' }}
            style={{
              background: breathColor + '30',
              filter: 'blur(20px)',
            }}
          />

          {/* Breathing ring */}
          <motion.div
            className="absolute inset-0 rounded-full border-[6px]"
            animate={{ scale: [1, 1.08, 1] }}
            transition={{ duration: 4, repeat: Infinity, ease: 'easeInOut' }}
            style={{
              borderColor: breathColor,
              boxShadow: `0 0 40px ${breathColor}40, inset 0 0 20px ${breathColor}15`,
            }}
          />

          {/* Time display */}
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <motion.span
              key={timeLeft}
              initial={{ scale: 1.05 }}
              animate={{ scale: 1 }}
              className="text-[48px] font-light tracking-tight"
            >
              {formatTime(timeLeft)}
            </motion.span>
          </div>
        </div>

        {/* Activity Selector */}
        <div className="flex items-center gap-4 mb-6">
          <button onClick={() => switchActivity(-1)} className="w-8 h-8 rounded-full bg-white/15 flex items-center justify-center">
            <ChevronLeft size={18} />
          </button>

          <AnimatePresence mode="wait" custom={slideDirection}>
            <motion.div
              key={activityIdx}
              custom={slideDirection}
              variants={{
                enter: (d: 1 | -1) => ({ x: d * 60, opacity: 0 }),
                center: { x: 0, opacity: 1 },
                exit: (d: 1 | -1) => ({ x: d * -60, opacity: 0 }),
              }}
              initial="enter"
              animate="center"
              exit="exit"
              transition={{ duration: 0.25, ease: 'easeOut' }}
              className="flex items-center gap-2"
            >
              {/* Icon with pulse on change */}
              <motion.div
                key={activityIdx + '-icon'}
                initial={{ scale: 0.5 }}
                animate={{ scale: [0.5, 1.3, 1] }}
                transition={{ duration: 0.35 }}
                className="w-10 h-10 rounded-xl flex items-center justify-center"
                style={{ backgroundColor: activity.color + '40' }}
              >
                <Icon size={22} />
              </motion.div>
              <div>
                <p className="text-[16px] font-medium">{activity.title}</p>
                <p className="text-[12px] text-white/60">{activity.desc}</p>
              </div>
            </motion.div>
          </AnimatePresence>

          <button onClick={() => switchActivity(1)} className="w-8 h-8 rounded-full bg-white/15 flex items-center justify-center">
            <ChevronRight size={18} />
          </button>
        </div>

        {/* Step Guide */}
        <AnimatePresence mode="wait">
          <motion.div
            key={stepIdx}
            initial={{ opacity: 0, y: 10, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -10 }}
            className="bg-white/10 backdrop-blur-sm rounded-2xl px-6 py-4 max-w-xs text-center overflow-hidden relative"
          >
            <p className="text-[11px] text-white/50 mb-1">步骤 {stepIdx + 1}/{activity.steps.length}</p>
            <p className="text-[16px] font-medium leading-relaxed">{activity.steps[stepIdx]}</p>

            {/* Step progress bar */}
            <div className="absolute left-0 right-0 bottom-0 h-0.5 bg-white/10">
              <motion.div
                className="h-full bg-white/50 rounded-full"
                animate={{ width: `${stepProgress}%` }}
                transition={{ duration: 0 }}
              />
            </div>
          </motion.div>
        </AnimatePresence>
      </div>

      {/* Bottom */}
      <div className="relative z-10 px-8 pb-12">
        <div className="flex items-center gap-4 mb-4">
          {/* Pause/Play Button */}
          <button
            onClick={() => setIsPaused(!isPaused)}
            className="w-10 h-10 rounded-full bg-white/20 flex items-center justify-center backdrop-blur-sm shrink-0 active:scale-90 transition-transform"
          >
            {isPaused ? <Play size={18} fill="currentColor" /> : <Pause size={18} fill="currentColor" />}
          </button>

          {/* Progress bar */}
          <div className="flex-1 h-1.5 bg-white/15 rounded-full overflow-hidden">
            <motion.div
              className="h-full bg-white rounded-full"
              animate={{ width: `${progress * 100}%` }}
              transition={{ duration: 0.5 }}
            />
          </div>
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
            还有 {formatTime(timeLeft)}，确定提前结束吗？
          </button>
        )}
      </div>
    </div>
  );
}
