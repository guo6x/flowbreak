// src/pages/RestMode.tsx
// PRD 2.3.2 + 6.2.3 休息引导全屏页面
import { useState, useEffect, useRef, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Eye, StretchHorizontal, Wind, ChevronLeft, ChevronRight, Check, Play, Pause, Volume2, VolumeX } from 'lucide-react';
import { useStore } from '../hooks/useStore';
import { Capacitor } from '@capacitor/core';
import { NativeFlow } from '../backend/nativeFlow';

// Lazy singleton AudioContext to prevent leaks
let globalAudioCtx: AudioContext | null = null;
function getAudioCtx() {
  if (!globalAudioCtx) {
    globalAudioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
  }
  return globalAudioCtx;
}

const themeGlows: Record<number, { colors: string[]; baseBg: string }> = {
  0: {
    baseBg: 'linear-gradient(135deg, #0f3014 0%, #1b4d22 50%, #2d6a4f 100%)',
    colors: ['rgba(76, 175, 80, 0.45)', 'rgba(129, 199, 132, 0.35)', 'rgba(0, 77, 64, 0.4)']
  },
  1: {
    baseBg: 'linear-gradient(135deg, #0a1f44 0%, #0d3263 50%, #154c79 100%)',
    colors: ['rgba(0, 172, 193, 0.45)', 'rgba(63, 81, 181, 0.35)', 'rgba(128, 222, 234, 0.3)']
  },
  2: {
    baseBg: 'linear-gradient(135deg, #212529 0%, #343a40 50%, #495057 100%)',
    colors: ['rgba(224, 224, 224, 0.25)', 'rgba(159, 168, 218, 0.35)', 'rgba(55, 71, 79, 0.45)']
  },
  3: {
    baseBg: 'linear-gradient(135deg, #2b0f1a 0%, #4a1228 50%, #5c1c38 100%)',
    colors: ['rgba(244, 143, 177, 0.45)', 'rgba(206, 147, 216, 0.35)', 'rgba(255, 224, 130, 0.3)']
  },
  4: {
    baseBg: 'linear-gradient(135deg, #371200 0%, #571e04 50%, #7d2d0b 100%)',
    colors: ['rgba(255, 183, 77, 0.45)', 'rgba(255, 87, 34, 0.35)', 'rgba(103, 58, 183, 0.3)']
  }
};

class AmbientPad {
  private ctx: AudioContext | null = null;
  private oscs: OscillatorNode[] = [];
  private oscGains: GainNode[] = [];
  private filter: BiquadFilterNode | null = null;
  private masterGain: GainNode | null = null;
  private lfo: OscillatorNode | null = null;
  private lfoGain: GainNode | null = null;
  private isPlaying: boolean = false;
  private isMuted: boolean = false;
  private isPaused: boolean = false;
  private targetVolume: number = 0.08;

  constructor() {}

  start() {
    if (this.isPlaying) return;
    try {
      this.ctx = getAudioCtx();
      if (this.ctx.state === 'suspended') {
        this.ctx.resume().catch(() => {});
      }

      this.masterGain = this.ctx.createGain();
      this.masterGain.gain.setValueAtTime(0, this.ctx.currentTime);

      this.filter = this.ctx.createBiquadFilter();
      this.filter.type = 'lowpass';
      this.filter.frequency.setValueAtTime(240, this.ctx.currentTime);
      this.filter.Q.setValueAtTime(1, this.ctx.currentTime);

      const freqs = [110, 164.81, 220, 277.18];
      const types: OscillatorType[] = ['triangle', 'sine', 'sine', 'sine'];

      freqs.forEach((freq, idx) => {
        if (!this.ctx || !this.filter) return;
        const osc = this.ctx.createOscillator();
        const oscGain = this.ctx.createGain();

        osc.type = types[idx] || 'sine';
        osc.frequency.setValueAtTime(freq, this.ctx.currentTime);

        if (idx > 0) {
          osc.detune.setValueAtTime((Math.random() - 0.5) * 8, this.ctx.currentTime);
        }

        const baseGain = idx === 0 ? 0.35 : idx === 1 ? 0.25 : idx === 2 ? 0.2 : 0.15;
        oscGain.gain.setValueAtTime(baseGain, this.ctx.currentTime);

        osc.connect(oscGain);
        oscGain.connect(this.filter);
        osc.start(this.ctx.currentTime);

        this.oscs.push(osc);
        this.oscGains.push(oscGain);
      });

      this.lfo = this.ctx.createOscillator();
      this.lfo.frequency.setValueAtTime(0.08, this.ctx.currentTime);

      this.lfoGain = this.ctx.createGain();
      this.lfoGain.gain.setValueAtTime(35, this.ctx.currentTime);

      this.lfo.connect(this.lfoGain);
      this.lfoGain.connect(this.filter.frequency);
      this.lfo.start(this.ctx.currentTime);

      this.filter.connect(this.masterGain);
      this.masterGain.connect(this.ctx.destination);

      const currentVol = (this.isMuted || this.isPaused) ? 0 : this.targetVolume;
      this.masterGain.gain.linearRampToValueAtTime(currentVol, this.ctx.currentTime + 1.5);

      this.isPlaying = true;
    } catch (e) {
      console.error('Failed to start AmbientPad:', e);
    }
  }

  setMute(mute: boolean) {
    this.isMuted = mute;
    this.updateGain();
  }

  setPause(paused: boolean) {
    this.isPaused = paused;
    this.updateGain();
  }

  private updateGain() {
    if (!this.masterGain || !this.ctx) return;
    const target = (this.isMuted || this.isPaused) ? 0 : this.targetVolume;
    try {
      this.masterGain.gain.cancelScheduledValues(this.ctx.currentTime);
      this.masterGain.gain.setValueAtTime(this.masterGain.gain.value, this.ctx.currentTime);
      this.masterGain.gain.linearRampToValueAtTime(target, this.ctx.currentTime + 0.8);
    } catch (e) {
      console.error('Failed to update Gain:', e);
    }
  }

  stop() {
    if (!this.isPlaying) return;
    try {
      const ctx = this.ctx;
      const masterGain = this.masterGain;
      const filter = this.filter;
      const lfo = this.lfo;
      const lfoGain = this.lfoGain;
      const oscs = this.oscs;
      const oscGains = this.oscGains;

      this.masterGain = null;
      this.filter = null;
      this.lfo = null;
      this.lfoGain = null;
      this.oscs = [];
      this.oscGains = [];
      this.isPlaying = false;

      if (masterGain && ctx) {
        masterGain.gain.cancelScheduledValues(ctx.currentTime);
        masterGain.gain.setValueAtTime(masterGain.gain.value, ctx.currentTime);
        masterGain.gain.linearRampToValueAtTime(0, ctx.currentTime + 0.3);
      }

      const scheduledStop = () => {
        oscs.forEach(osc => {
          try { osc.stop(); } catch {}
          try { osc.disconnect(); } catch {}
        });

        oscGains.forEach(g => {
          try { g.disconnect(); } catch {}
        });

        if (lfo) {
          try { lfo.stop(); } catch {}
          try { lfo.disconnect(); } catch {}
        }
        if (lfoGain) {
          try { lfoGain.disconnect(); } catch {}
        }
        if (filter) {
          try { filter.disconnect(); } catch {}
        }
        if (masterGain) {
          try { masterGain.disconnect(); } catch {}
        }
      };

      setTimeout(scheduledStop, 350);
    } catch (e) {
      console.error('Failed to stop AmbientPad:', e);
    }
  }
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
  const location = useLocation();
  const profile = useStore(s => s.profile);
  const completeRest = useStore(s => s.completeRestActivity);
  const setBlockState = useStore(s => s.setBlockState);

  function useChime() {
    const playChime = (freq: number, duration: number) => {
      try {
        const ctx = getAudioCtx();
        if (ctx.state === 'suspended') ctx.resume().catch(() => {});
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();

        const chimeFilter = ctx.createBiquadFilter();
        chimeFilter.type = 'lowpass';
        chimeFilter.frequency.setValueAtTime(1200, ctx.currentTime);

        osc.connect(gain);
        gain.connect(chimeFilter);
        chimeFilter.connect(ctx.destination);

        osc.type = 'sine';
        osc.frequency.setValueAtTime(freq, ctx.currentTime);

        // 20ms 线性淡入，消去原版中因音频瞬间开启产生的“咔哒”瞬间音
        gain.gain.setValueAtTime(0, ctx.currentTime);
        gain.gain.linearRampToValueAtTime(0.12, ctx.currentTime + 0.02);

        gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration);

        osc.start(ctx.currentTime);
        osc.stop(ctx.currentTime + duration);
        osc.onended = () => {
          try {
            osc.disconnect();
            gain.disconnect();
            chimeFilter.disconnect();
          } catch { /* ignore */ }
        };
      } catch { /* audio not available */ }
    };
    return { playChime };
  }

  const { playChime } = useChime();

  const initialActivityIdx = (location.state as { activityIdx?: number })?.activityIdx ?? 0;
  const isNative = Capacitor.isNativePlatform();
  const [activityIdx, setActivityIdx] = useState(initialActivityIdx);
  const [timeLeft, setTimeLeft] = useState(profile.restDuration || 180);
  const [restEndsAt, setRestEndsAt] = useState<number | null>(null);
  const [restReady, setRestReady] = useState(!isNative);
  const [isPaused, setIsPaused] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const [stepIdx, setStepIdx] = useState(0);
  const [showReward, setShowReward] = useState(false);
  const [slideDirection, setSlideDirection] = useState<1 | -1>(1);
  const [stepProgress, setStepProgress] = useState(0);
  const [rewardBadgeTitle, setRewardBadgeTitle] = useState<string | null>(null);
  const [rewardContentVisible, setRewardContentVisible] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [operationError, setOperationError] = useState('');
  const stepIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const completedRestRef = useRef(false);
  const cancelledRestRef = useRef(false);

  useEffect(() => {
    if (!isNative) return;
    let active = true;
    const restoreOrStartRest = async () => {
      try {
        await NativeFlow.beginRest();
        const state = await NativeFlow.getBlockState();
        if (!active) return;
        const requiredSeconds = state.restRequiredSeconds || profile.restDuration || 180;
        const startedAt = state.restStartedAt || Date.now();
        const endsAt = startedAt + requiredSeconds * 1000;
        setRestEndsAt(endsAt);
        setTimeLeft(Math.max(0, Math.ceil((endsAt - Date.now()) / 1000)));
        setRestReady(true);
      } catch {
        if (active) setOperationError('休息模式启动失败，请返回后重试。');
      }
    };
    void restoreOrStartRest();
    return () => {
      active = false;
      if (!completedRestRef.current && !cancelledRestRef.current) {
        void NativeFlow.cancelRest().catch(() => {});
      }
    };
  }, []);

  const ambientPadRef = useRef<AmbientPad | null>(null);
  useEffect(() => {
    // AmbientPad 必须在 useEffect 中创建，不能在渲染阶段 new
    // React 18 并发渲染可能丢弃渲染树，导致 AudioContext 和 oscillator 节点泄漏
    ambientPadRef.current = new AmbientPad();
    return () => {
      if (ambientPadRef.current) {
        ambientPadRef.current.stop();
        ambientPadRef.current = null;
      }
    };
  }, []);

  const activity = activities[activityIdx];
  const Icon = activity.icon;
  const ambientParticles = useMemo(
    () => (themedParticles[profile.selectedBackground] || themedParticles[0]).map((emoji) => ({
      emoji,
      x: (Math.random() - 0.5) * 60,
      rotate: Math.random() * 360,
      duration: 8 + Math.random() * 7,
      delay: Math.random() * 5,
      left: 8 + Math.random() * 84,
    })),
    [profile.selectedBackground],
  );
  const confetti = useMemo(
    () => Array.from({ length: 25 }, (_, i) => ({
      rotate: 720 + Math.random() * 360,
      duration: 2.5 + Math.random() * 2,
      delay: Math.random() * 0.6,
      left: 8 + Math.random() * 84,
      color: [
        '#4CAF50', '#2196F3', '#FF9800', '#F44336', '#9C27B0', '#FFEB3B',
        '#00BCD4', '#E91E63', '#8BC34A', '#FF5722', '#3F51B5', '#FFC107',
      ][i % 12],
    })),
    [],
  );

  // 音频生命周期管理
  useEffect(() => {
    const pad = ambientPadRef.current;
    if (pad) {
      pad.start();
      pad.setMute(isMuted);
      pad.setPause(isPaused);
    }
    return () => {
      if (pad) {
        pad.stop();
      }
    };
  }, []);

  // 监听暂停状态变化
  useEffect(() => {
    if (ambientPadRef.current) {
      ambientPadRef.current.setPause(isPaused);
    }
  }, [isPaused]);

  // 监听静音状态变化
  useEffect(() => {
    if (ambientPadRef.current) {
      ambientPadRef.current.setMute(isMuted);
    }
  }, [isMuted]);

  // 当进入奖励页面时，确保背景音乐完全淡出终止
  useEffect(() => {
    if (showReward && ambientPadRef.current) {
      ambientPadRef.current.stop();
    }
  }, [showReward]);

  // On Android the native service owns the start timestamp. Rendering derives
  // from wall-clock time so backgrounding the WebView cannot shorten a rest.
  useEffect(() => {
    if (showReward || !restReady || (isPaused && !isNative)) return;
    if (isNative && restEndsAt !== null) {
      const update = () => setTimeLeft(Math.max(0, Math.ceil((restEndsAt - Date.now()) / 1000)));
      update();
      const timer = setInterval(update, 1000);
      return () => clearInterval(timer);
    }
    if (timeLeft <= 0) return;
    const timer = setTimeout(() => setTimeLeft(t => t - 1), 1000);
    return () => clearTimeout(timer);
  }, [timeLeft, showReward, isPaused, isNative, restEndsAt, restReady]);

  // Cycle through steps every 6 seconds
  useEffect(() => {
    if (showReward || isPaused) return;
    stepIntervalRef.current = setInterval(() => {
      setStepIdx(prev => (prev + 1) % activity.steps.length);
    }, 6000);
    return () => {
      if (stepIntervalRef.current) clearInterval(stepIntervalRef.current);
    };
  }, [activity.steps.length, showReward, isPaused, activityIdx]);

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
    if (!showReward && !isMuted) {
      playChime(500 + stepIdx * 40, 0.25);
    }
    // 步骤切换时轻震动，帮助用户感知节奏变化
    if (!showReward && !isPaused && typeof navigator !== 'undefined' && navigator.vibrate) {
      try { navigator.vibrate(30); } catch { /* ignore */ }
    }
  }, [stepIdx, showReward, isMuted, isPaused]);

  // 呼吸节奏震动：4 秒周期，吸气开始时轻震一下，与呼吸圆圈动画同步
  useEffect(() => {
    if (showReward || isPaused) return;
    const interval = setInterval(() => {
      if (typeof navigator !== 'undefined' && navigator.vibrate) {
        try { navigator.vibrate(50); } catch { /* ignore */ }
      }
    }, 4000);
    return () => clearInterval(interval);
  }, [showReward, isPaused]);

  // Reward delay + counting animation
  useEffect(() => {
    if (!showReward) return;
    const delay = setTimeout(() => setRewardContentVisible(true), 500);
    return () => clearTimeout(delay);
  }, [showReward]);

  const formatTime = (s: number) => `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`;
  const totalDuration = profile.restDuration || 180;
  const progress = 1 - timeLeft / totalDuration;

  // Lerped breath color from activity color to calm green
  const breathColor = useMemo(() => lerpColor(activity.color, calmColor, progress), [activity.color, progress]);

  const handleComplete = async () => {
    if (completing) return;
    setCompleting(true);
    setOperationError('');
    // Play completion fanfare
    if (!isMuted) {
      playChime(523, 0.2);
      setTimeout(() => playChime(659, 0.2), 150);
      setTimeout(() => playChime(784, 0.3), 300);
    }

    // 完成时完全终止背景音乐
    if (ambientPadRef.current) {
      ambientPadRef.current.stop();
    }

    if (isNative) {
      try {
        const result = await NativeFlow.completeRestAndUnlock({
          activity: activity.id as 'eye' | 'stretch' | 'breathe',
          duration: totalDuration,
        });
        setBlockState('GRACE', result.graceUntil, '');
        useStore.setState({ points: result.points, streak: result.streak });
        setRewardBadgeTitle(result.achievement === 'health_guardian' ? '健康守护者' : null);
        completedRestRef.current = true;
      } catch {
        setOperationError('解锁失败，休息记录尚未提交，请点击重试。');
        setCompleting(false);
        return;
      }
    } else {
      setBlockState('GRACE', Date.now() + 10 * 60 * 1000, '');
      const beforeAchievements = useStore.getState().achievements;
      completeRest(activity.id as 'eye' | 'stretch' | 'breathe', totalDuration);
      const afterState = useStore.getState();
      const newlyUnlocked = afterState.achievements.find(
        achievement => achievement.unlocked &&
          !beforeAchievements.find(before => before.id === achievement.id)?.unlocked,
      );
      setRewardBadgeTitle(newlyUnlocked?.title ?? null);
    }
    setShowReward(true);
    setCompleting(false);
  };

  const handleFinish = () => {
    navigate('/dashboard');
  };

  const [closing, setClosing] = useState(false);
  const [confirmingEarlyExit, setConfirmingEarlyExit] = useState(false);

  const handleClose = async () => {
    if (closing) return;
    // 第一次点击：弹出二次确认
    if (!confirmingEarlyExit) {
      setConfirmingEarlyExit(true);
      return;
    }
    setConfirmingEarlyExit(false);
    setClosing(true);
    try {
      if (isNative) {
        await NativeFlow.cancelRest();
        cancelledRestRef.current = true;
      } else {
        // Web 端：已休息 30 秒以上时记录部分休息
        const elapsed = totalDuration - timeLeft;
        if (elapsed >= 30) {
          completeRest(activity.id as 'eye' | 'stretch' | 'breathe', elapsed);
        }
      }
      navigate('/dashboard');
    } catch (error) {
      setOperationError(error instanceof Error ? error.message : '退出休息模式失败，请重试。');
    } finally {
      setClosing(false);
    }
  };

  const handleCancelEarlyExit = () => {
    if (closing) return;
    setConfirmingEarlyExit(false);
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
        {confetti.map((item, i) => (
          <motion.div
            key={i}
            initial={{ y: -120, x: 0, opacity: 1, rotate: 0, scale: 1 }}
            animate={{ y: '100vh', rotate: item.rotate, opacity: 0, scale: 0.5 }}
            transition={{ duration: item.duration, delay: item.delay }}
            className="absolute w-4 h-4 rounded-sm"
            style={{
              backgroundColor: item.color,
              left: `${item.left}%`,
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
                <span className="text-2xl mb-1">⏳</span>
                <span className="text-[16px] font-bold text-accent">10 分钟</span>
                <span className="text-[11px] text-gray-500">访问窗口</span>
              </div>
              <div className="card p-4 flex flex-col items-center">
                <span className="text-2xl mb-1">🛡️</span>
                <span className="text-[14px] font-bold text-primary">
                  {rewardBadgeTitle || '休息已记录'}
                </span>
                <span className="text-[11px] text-gray-500">
                  {rewardBadgeTitle ? '新徽章' : '健康进度'}
                </span>
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
  const selectedTheme = themeGlows[profile.selectedBackground] || themeGlows[0];

  return (
    <div
      className="fixed inset-0 z-[100] flex flex-col text-white overflow-hidden"
      style={{ background: selectedTheme.baseBg }}
    >
      {/* Ambient Blurry Glowing Blobs */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none z-0">
        <motion.div
          animate={{
            x: [-40, 80, -40],
            y: [-60, 40, -60],
            scale: [1, 1.2, 1],
          }}
          transition={{
            duration: 25,
            repeat: Infinity,
            ease: "easeInOut",
          }}
          className="absolute w-[350px] h-[350px] rounded-full"
          style={{
            background: selectedTheme.colors[0],
            left: '-10%',
            top: '15%',
            filter: 'blur(90px)',
          }}
        />
        <motion.div
          animate={{
            x: [40, -80, 40],
            y: [60, -40, 60],
            scale: [1.1, 0.9, 1.1],
          }}
          transition={{
            duration: 30,
            repeat: Infinity,
            ease: "easeInOut",
          }}
          className="absolute w-[400px] h-[400px] rounded-full"
          style={{
            background: selectedTheme.colors[1],
            right: '-15%',
            bottom: '10%',
            filter: 'blur(110px)',
          }}
        />
        <motion.div
          animate={{
            x: [-60, 60, -60],
            y: [40, -80, 40],
            scale: [0.9, 1.15, 0.9],
            opacity: [0.3, 0.6, 0.3],
          }}
          transition={{
            duration: 22,
            repeat: Infinity,
            ease: "easeInOut",
          }}
          className="absolute w-[300px] h-[300px] rounded-full"
          style={{
            background: selectedTheme.colors[2],
            left: '30%',
            top: '40%',
            filter: 'blur(100px)',
          }}
        />
      </div>

      {/* Ambient overlay */}
      <div className="absolute inset-0 bg-black/25 z-[1]" />

      {/* Floating ambient particles */}
      {ambientParticles.map((particle, i) => (
        <motion.div
          key={i}
          animate={{
            y: ['110vh', '-10vh'],
            x: [0, particle.x],
            rotate: [0, particle.rotate],
            opacity: [0.6, 0]
          }}
          transition={{
            duration: particle.duration,
            repeat: Infinity,
            delay: particle.delay,
            ease: 'linear'
          }}
          className="absolute text-2xl z-[2]"
          style={{ left: `${particle.left}%` }}
        >
          {particle.emoji}
        </motion.div>
      ))}

      {/* Top bar */}
      <div className="relative z-10 flex items-center justify-between px-5 pt-14 pb-4">
        <button onClick={handleClose} className="w-10 h-10 rounded-full bg-white/15 flex items-center justify-center backdrop-blur-sm">
          <X size={20} />
        </button>
        <span className="text-[12px] text-white/70 font-medium">休息模式</span>
        <button
          onClick={() => setIsMuted(!isMuted)}
          className="w-10 h-10 rounded-full bg-white/15 flex items-center justify-center backdrop-blur-sm active:scale-95 transition-transform"
          aria-label={isMuted ? "取消静音" : "静音"}
        >
          {isMuted ? <VolumeX size={20} /> : <Volume2 size={20} />}
        </button>
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
          {!isNative && (
            <button
              onClick={() => setIsPaused(!isPaused)}
              className="w-10 h-10 rounded-full bg-white/20 flex items-center justify-center backdrop-blur-sm shrink-0 active:scale-90 transition-transform"
            >
              {isPaused ? <Play size={18} fill="currentColor" /> : <Pause size={18} fill="currentColor" />}
            </button>
          )}

          {/* Progress bar */}
          <div className="flex-1 h-1.5 bg-white/15 rounded-full overflow-hidden">
            <motion.div
              className="h-full bg-white rounded-full"
              animate={{ width: `${progress * 100}%` }}
              transition={{ duration: 0.5 }}
            />
          </div>
        </div>

        {operationError && (
          <p className="mb-3 rounded-xl bg-white/10 px-3 py-2 text-center text-[12px] text-white">
            {operationError}
          </p>
        )}
        {timeLeft <= 0 ? (
          <>
            <motion.button
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              onClick={handleComplete}
              disabled={completing}
              className="w-full py-4 rounded-2xl bg-white text-primary font-bold text-[16px] flex items-center justify-center gap-2 disabled:opacity-60"
            >
              <Check size={20} />
              {completing ? '正在解锁...' : operationError ? '重试解锁' : '完成休息'}
            </motion.button>
          </>
        ) : confirmingEarlyExit ? (
          <div className="w-full space-y-2">
            <div className="rounded-2xl bg-white/10 backdrop-blur-sm px-4 py-3 text-center">
              <p className="text-[14px] font-medium text-white">提前结束不会解锁</p>
              <p className="text-[12px] text-white/70 mt-1">剩余 {formatTime(timeLeft)}，目标应用仍将被阻断。坚持完成才能获得 10 分钟访问窗口。</p>
            </div>
            <div className="flex gap-3">
              <button
                onClick={handleCancelEarlyExit}
                disabled={closing}
                className="flex-1 py-3 rounded-xl bg-white/10 text-white text-[14px] font-medium active:bg-white/20 transition-colors disabled:opacity-50"
              >
                继续休息
              </button>
              <button
                onClick={handleClose}
                disabled={closing}
                className="flex-1 py-3 rounded-xl bg-white/20 text-white text-[14px] font-medium active:bg-white/30 transition-colors disabled:opacity-50"
              >
                {closing ? '正在退出...' : '确认提前结束'}
              </button>
            </div>
          </div>
        ) : (
          <button onClick={handleClose} disabled={closing} className="w-full py-3 rounded-xl bg-white/10 text-white/60 text-[14px] text-center active:bg-white/20 transition-colors disabled:opacity-50">
            {closing ? '正在退出...' : `还有 ${formatTime(timeLeft)}，提前结束（不会解锁）`}
          </button>
        )}
      </div>
    </div>
  );
}
