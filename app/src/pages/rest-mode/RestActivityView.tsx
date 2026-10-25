// Presentational rest experience. Timing, native validation, and settlement
// remain in RestMode and useRestTimer.
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion';
import { X, Eye, StretchHorizontal, Wind, ChevronLeft, ChevronRight, Check, Play, Pause, Volume2, VolumeX } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { RestActivity, AmbientParticle } from './types';
import { themeGlows } from './themeData';
import { formatTime } from './colorUtils';
import RestExitDialog from './RestExitDialog';

const iconMap: Record<string, LucideIcon> = { eye: Eye, stretch: StretchHorizontal, breathe: Wind };

export interface RestActivityViewProps {
  selectedBackground: number;
  ambientParticles: AmbientParticle[];
  activity: RestActivity;
  activityIdx: number;
  slideDirection: 1 | -1;
  timeLeft: number;
  progress: number;
  breathColor: string;
  stepIdx: number;
  stepProgress: number;
  isMuted: boolean;
  isPaused: boolean;
  isNative: boolean;
  restClockValid?: boolean;
  restTimerReady?: boolean;
  nativeRestObserved?: boolean;
  nativeStartPending?: boolean;
  nativeBeginTokenAvailable?: boolean;
  operationError: string;
  completing: boolean;
  confirmingEarlyExit: boolean;
  closing: boolean;
  onClose: () => void;
  onToggleMute: () => void;
  onTogglePause: () => void;
  onSwitchActivity: (dir: 1 | -1) => void;
  onComplete: () => void;
  onCancelEarlyExit: () => void;
  onConfirmEarlyExit: () => void;
}

export default function RestActivityView({ selectedBackground, ambientParticles, activity, activityIdx, slideDirection, timeLeft, progress, breathColor, stepIdx, stepProgress, isMuted, isPaused, isNative, restClockValid = true, restTimerReady = !isNative, nativeRestObserved = false, nativeStartPending = false, nativeBeginTokenAvailable = false, operationError, completing, confirmingEarlyExit, closing, onClose, onToggleMute, onTogglePause, onSwitchActivity, onComplete, onCancelEarlyExit, onConfirmEarlyExit }: RestActivityViewProps) {
  const reduceMotion = useReducedMotion();
  const selectedTheme = themeGlows[selectedBackground] || themeGlows[0];
  const Icon = iconMap[activity.iconKey] || Eye;
  const busy = completing || closing;
  const canComplete = !isNative || (restTimerReady && restClockValid);
  const nativeRestUnconfirmed = isNative && !restTimerReady;
  const nativeStartupTitle = nativeStartPending
    ? nativeBeginTokenAvailable ? '等待设备确认休息已启动' : '正在连接设备保护服务'
    : nativeRestObserved ? '正在恢复休息计时' : '休息尚未确认';
  const nativeStartupDescription = nativeStartPending
    ? '设备确认前不会开始计时，也不会开放完成入口。'
    : nativeRestObserved
      ? '设备已报告休息进行中，但计时暂时无法确认。恢复前不会显示倒计时或开放完成入口。'
      : '设备尚未确认休息已启动；请返回面板核对保护状态。';

  return (
    <div className="fixed inset-0 z-[100] text-white overflow-y-auto overscroll-contain [&_button:focus-visible]:outline-white" style={{ background: selectedTheme.baseBg }} data-testid="rest-activity-view">
      <div className="fixed inset-0 overflow-hidden pointer-events-none" aria-hidden="true">
        <motion.div animate={reduceMotion ? undefined : { x: [-30, 50, -30], y: [-20, 40, -20], scale: [1, 1.12, 1] }} transition={{ duration: 28, repeat: Infinity, ease: 'easeInOut' }} className="absolute w-[360px] h-[360px] rounded-full -left-24 top-[12%]" style={{ background: selectedTheme.colors[0], filter: 'blur(90px)' }} />
        <motion.div animate={reduceMotion ? undefined : { x: [20, -50, 20], y: [40, -30, 40] }} transition={{ duration: 32, repeat: Infinity, ease: 'easeInOut' }} className="absolute w-[400px] h-[400px] rounded-full -right-28 bottom-[5%]" style={{ background: selectedTheme.colors[1], filter: 'blur(110px)' }} />
        <div className="absolute inset-0 bg-black/15" />
        {!reduceMotion ? ambientParticles.slice(0, 5).map((particle, index) => <motion.div key={index} animate={{ y: ['110vh', '-10vh'], x: [0, particle.x], opacity: [0, 0.22, 0] }} transition={{ duration: particle.duration + 10, repeat: Infinity, delay: particle.delay, ease: 'linear' }} className="absolute w-1 h-1 rounded-full bg-white" style={{ left: `${particle.left}%` }} />) : null}
      </div>

      <div className="relative z-10 min-h-dvh flex flex-col w-full max-w-[620px] mx-auto">
        <header className="flex items-center justify-between px-5 pb-5" style={{ paddingTop: 'max(1.25rem, env(safe-area-inset-top))' }}>
          <button onClick={onClose} disabled={busy} aria-label="退出休息模式" className="w-11 h-11 rounded-full border border-white/15 bg-white/8 flex items-center justify-center disabled:opacity-40"><X size={20} /></button>
          <span className="text-[12px] tracking-[0.18em] text-white/85 font-medium">给自己一点留白</span>
          <button onClick={onToggleMute} aria-label={isMuted ? '取消静音' : '静音'} aria-pressed={isMuted} className="w-11 h-11 rounded-full border border-white/15 bg-white/8 flex items-center justify-center"><span className="sr-only">环境音</span>{isMuted ? <VolumeX size={20} /> : <Volume2 size={20} />}</button>
        </header>

        <main className="flex-1 flex flex-col items-center justify-center px-5 py-5 sm:py-8">
          {nativeRestUnconfirmed ? (
            <div className="relative mb-7 flex items-center justify-center rounded-full border border-white/15 bg-white/5 px-7 text-center shadow-[0_0_50px_rgba(255,255,255,0.04)]" style={{ width: 'clamp(148px, 25vh, 196px)', height: 'clamp(148px, 25vh, 196px)' }}>
              <div role="status" aria-live="polite" className="space-y-2">
                <p className="text-[14px] font-semibold leading-relaxed">{nativeStartupTitle}</p>
                <p className="text-[11px] text-white/75 leading-relaxed">{nativeStartupDescription}</p>
              </div>
            </div>
          ) : (
            <div className="relative mb-7" style={{ width: 'clamp(148px, 25vh, 196px)', height: 'clamp(148px, 25vh, 196px)' }}>
              <motion.div aria-hidden="true" className="absolute -inset-4 rounded-full" animate={reduceMotion ? undefined : { scale: [1, 1.12, 1], opacity: [0.12, 0.25, 0.12] }} transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }} style={{ background: breathColor + '30', filter: 'blur(20px)' }} />
              <div aria-hidden="true" className="absolute -inset-3 rounded-full border border-white/10" />
              <motion.div aria-hidden="true" className="absolute inset-0 rounded-full border-[3px]" animate={reduceMotion ? undefined : { scale: [1, 1.045, 1] }} transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }} style={{ borderColor: breathColor, boxShadow: `0 0 50px ${breathColor}18, inset 0 0 24px ${breathColor}08` }} />
              <div className="absolute inset-0 flex flex-col items-center justify-center">
                <p className="text-[11px] text-white/75 tracking-[0.14em] mb-1">{timeLeft <= 0 ? (canComplete ? '可以完成休息了' : '正在恢复保护状态') : isPaused ? '已暂停' : '剩余休息时间'}</p>
                <span role="timer" aria-label="剩余休息时间" aria-live="off" className="text-[49px] leading-tight font-light tracking-[-0.04em] tabular-nums">{formatTime(timeLeft)}</span>
              </div>
            </div>
          )}

          <div className="flex items-center justify-between gap-3 w-full max-w-[360px] mb-5">
            <button onClick={() => onSwitchActivity(-1)} disabled={busy} aria-label="上一个休息活动" className="w-11 h-11 shrink-0 rounded-full border border-white/15 bg-white/5 flex items-center justify-center disabled:opacity-40"><ChevronLeft size={20} /></button>
            <AnimatePresence mode="wait" custom={slideDirection}>
              <motion.div key={activityIdx} custom={slideDirection} variants={{ enter: (direction: 1 | -1) => ({ x: reduceMotion ? 0 : direction * 24, opacity: 0 }), center: { x: 0, opacity: 1 }, exit: (direction: 1 | -1) => ({ x: reduceMotion ? 0 : direction * -24, opacity: 0 }) }} initial="enter" animate="center" exit="exit" transition={{ duration: reduceMotion ? 0 : 0.18 }} className="flex flex-col items-center gap-2.5 min-w-0 text-center">
                <span className="w-10 h-10 rounded-2xl bg-white/10 flex items-center justify-center"><Icon size={22} aria-hidden="true" /></span>
                <div><h1 className="text-[22px] font-semibold tracking-tight">{activity.title}</h1><p className="text-[12px] text-white/80 mt-1 leading-relaxed">{activity.desc}</p></div>
              </motion.div>
            </AnimatePresence>
            <button onClick={() => onSwitchActivity(1)} disabled={busy} aria-label="下一个休息活动" className="w-11 h-11 shrink-0 rounded-full border border-white/15 bg-white/5 flex items-center justify-center disabled:opacity-40"><ChevronRight size={20} /></button>
          </div>
          <div className="flex gap-1.5 mb-5" aria-label={`第 ${activityIdx + 1} 个休息活动，共 3 个`}>{[0, 1, 2].map(index => <span key={index} className={`h-1 rounded-full ${index === activityIdx ? 'w-5 bg-white/85' : 'w-1.5 bg-white/25'}`} />)}</div>

          <div className="w-full max-w-[360px] rounded-[22px] border border-white/15 bg-white/8 px-5 py-5 text-center relative overflow-hidden min-h-[110px]">
            <p className="text-[11px] text-white/75 mb-2 tracking-wide">步骤 {stepIdx + 1}/{activity.steps.length}</p>
            <AnimatePresence mode="wait"><motion.p key={`${activity.id}-${stepIdx}`} initial={{ opacity: 0, y: reduceMotion ? 0 : 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: reduceMotion ? 0 : 0.15 }} className="text-[17px] font-medium leading-relaxed">{activity.steps[stepIdx]}</motion.p></AnimatePresence>
            <div aria-hidden="true" className="absolute left-0 right-0 bottom-0 h-0.5 bg-white/8"><div className="h-full bg-white/50" style={{ width: `${stepProgress}%` }} /></div>
          </div>
          <p className="text-[11px] text-white/65 mt-3 text-center">按舒服的幅度进行，不适时停止动作。</p>
        </main>

        <footer className="px-5 pt-3 w-full max-w-[400px] mx-auto" style={{ paddingBottom: 'max(1.5rem, env(safe-area-inset-bottom))' }}>
          {!nativeRestUnconfirmed ? (
            <div className="flex items-center gap-3 mb-4">
              {!isNative ? <button onClick={onTogglePause} disabled={busy || timeLeft <= 0} aria-label={isPaused ? '继续休息计时' : '暂停休息计时'} aria-pressed={isPaused} className="w-11 h-11 rounded-full border border-white/15 bg-white/10 flex items-center justify-center shrink-0 disabled:opacity-40">{isPaused ? <Play size={17} fill="currentColor" /> : <Pause size={17} fill="currentColor" />}</button> : null}
              <div className="flex-1"><div className="flex justify-between text-[11px] text-white/75 mb-2"><span>{isNative ? '计时由设备校验' : 'Web 预览计时'}</span><span>{Math.round(progress * 100)}%</span></div><div role="progressbar" aria-label="休息进度" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progress * 100)} className="h-1 bg-white/15 rounded-full overflow-hidden"><div className="h-full bg-white/85 rounded-full" style={{ width: `${progress * 100}%` }} /></div></div>
            </div>
          ) : null}
          {operationError ? <p role="alert" className="mb-3 rounded-xl border border-white/20 bg-black/15 px-4 py-3 text-center text-[13px] text-white leading-relaxed">{operationError}</p> : null}
          {nativeRestUnconfirmed
            ? confirmingEarlyExit
              ? <RestExitDialog timeLeft={timeLeft} closing={closing} exitContext={nativeStartPending || !nativeRestObserved ? 'unconfirmed' : 'recovering'} onCancel={onCancelEarlyExit} onConfirm={onConfirmEarlyExit} />
              : <button onClick={onClose} disabled={closing} className="w-full min-h-12 py-3 px-3 rounded-xl border border-white/10 bg-white/5 text-white/85 text-[12px] text-center leading-relaxed disabled:opacity-50">{closing ? '正在退出...' : nativeStartPending ? '取消启动并返回' : nativeRestObserved ? '结束休息并返回' : '返回面板'}</button>
            : timeLeft <= 0 && canComplete
              ? <button onClick={onComplete} disabled={completing} className="w-full min-h-13 rounded-2xl bg-white text-primary-dark font-bold text-[16px] flex items-center justify-center gap-2 disabled:opacity-60"><Check size={20} />{completing ? '正在解锁...' : operationError ? '重试解锁' : '完成休息'}</button>
              : timeLeft <= 0
                ? <p role="status" className="w-full rounded-2xl border border-white/20 bg-black/15 px-4 py-3 text-center text-[13px] text-white/85">休息计时正在重新核验，请稍候。</p>
                : confirmingEarlyExit
                  ? <RestExitDialog timeLeft={timeLeft} closing={closing} onCancel={onCancelEarlyExit} onConfirm={onConfirmEarlyExit} />
                  : <button onClick={onClose} disabled={closing} className="w-full min-h-12 py-3 px-3 rounded-xl border border-white/10 bg-white/5 text-white/85 text-[12px] text-center leading-relaxed disabled:opacity-50">{closing ? '正在退出...' : `还有 ${formatTime(timeLeft)}，提前结束（不会解锁）`}</button>}
        </footer>
      </div>
    </div>
  );
}
