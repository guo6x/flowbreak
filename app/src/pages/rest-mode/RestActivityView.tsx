// In-progress rest activity UI: breathing circle, activity selector,
// step guide, and bottom controls (pause / progress / complete / exit).
// Pure presentation — all state and callbacks arrive via props.

import { motion, AnimatePresence } from 'framer-motion';
import {
  X, Eye, StretchHorizontal, Wind,
  ChevronLeft, ChevronRight, Check, Play, Pause, Volume2, VolumeX,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { RestActivity, AmbientParticle } from './types';
import { themeGlows } from './themeData';
import { formatTime } from './colorUtils';
import RestExitDialog from './RestExitDialog';

const iconMap: Record<string, LucideIcon> = {
  eye: Eye,
  stretch: StretchHorizontal,
  breathe: Wind,
};

export interface RestActivityViewProps {
  // Theme
  selectedBackground: number;
  ambientParticles: AmbientParticle[];
  // Activity
  activity: RestActivity;
  activityIdx: number;
  slideDirection: 1 | -1;
  // Timer
  timeLeft: number;
  progress: number;
  breathColor: string;
  // Steps
  stepIdx: number;
  stepProgress: number;
  // Controls
  isMuted: boolean;
  isPaused: boolean;
  isNative: boolean;
  operationError: string;
  completing: boolean;
  confirmingEarlyExit: boolean;
  closing: boolean;
  // Callbacks
  onClose: () => void;
  onToggleMute: () => void;
  onTogglePause: () => void;
  onSwitchActivity: (dir: 1 | -1) => void;
  onComplete: () => void;
  onCancelEarlyExit: () => void;
  onConfirmEarlyExit: () => void;
}

export default function RestActivityView(props: RestActivityViewProps) {
  const {
    selectedBackground,
    ambientParticles,
    activity,
    activityIdx,
    slideDirection,
    timeLeft,
    progress,
    breathColor,
    stepIdx,
    stepProgress,
    isMuted,
    isPaused,
    isNative,
    operationError,
    completing,
    confirmingEarlyExit,
    closing,
    onClose,
    onToggleMute,
    onTogglePause,
    onSwitchActivity,
    onComplete,
    onCancelEarlyExit,
    onConfirmEarlyExit,
  } = props;

  const selectedTheme = themeGlows[selectedBackground] || themeGlows[0];
  const Icon = iconMap[activity.iconKey] || Eye;

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
        <button onClick={onClose} className="w-10 h-10 rounded-full bg-white/15 flex items-center justify-center backdrop-blur-sm">
          <X size={20} />
        </button>
        <span className="text-[12px] text-white/70 font-medium">休息模式</span>
        <button
          onClick={onToggleMute}
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
          <button onClick={() => onSwitchActivity(-1)} className="w-8 h-8 rounded-full bg-white/15 flex items-center justify-center">
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

          <button onClick={() => onSwitchActivity(1)} className="w-8 h-8 rounded-full bg-white/15 flex items-center justify-center">
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
              onClick={onTogglePause}
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
              onClick={onComplete}
              disabled={completing}
              className="w-full py-4 rounded-2xl bg-white text-primary font-bold text-[16px] flex items-center justify-center gap-2 disabled:opacity-60"
            >
              <Check size={20} />
              {completing ? '正在解锁...' : operationError ? '重试解锁' : '完成休息'}
            </motion.button>
          </>
        ) : confirmingEarlyExit ? (
          <RestExitDialog
            timeLeft={timeLeft}
            closing={closing}
            onCancel={onCancelEarlyExit}
            onConfirm={onConfirmEarlyExit}
          />
        ) : (
          <button onClick={onClose} disabled={closing} className="w-full py-3 rounded-xl bg-white/10 text-white/60 text-[14px] text-center active:bg-white/20 transition-colors disabled:opacity-50">
            {closing ? '正在退出...' : `还有 ${formatTime(timeLeft)}，提前结束（不会解锁）`}
          </button>
        )}
      </div>
    </div>
  );
}
