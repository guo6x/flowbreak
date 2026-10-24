import { motion, useReducedMotion } from 'framer-motion';
import { useNavigate } from 'react-router';
import type { InterventionLevel } from '../backend/fatigueEngine';
import { ArrowRight, Brain, Eye, Leaf, StretchHorizontal, Wind, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';

interface Props {
  level: InterventionLevel;
  elapsed: number;
  onDismiss: () => void;
  onSnooze?: () => void;
}

const suggestedActivities = [
  { icon: Eye, label: '眼部放松', detail: '把视线，从屏幕移向远处', color: 'text-primary', background: 'bg-primary/8' },
  { icon: StretchHorizontal, label: '身体拉伸', detail: '轻轻舒展一下颈肩', color: 'text-secondary', background: 'bg-secondary/8' },
  { icon: Wind, label: '深呼吸', detail: '让呼吸回到舒服的节奏', color: 'text-primary', background: 'bg-primary/8' },
];

export default function InterventionOverlay({ level, elapsed, onDismiss, onSnooze }: Props) {
  const navigate = useNavigate();
  const reduceMotion = useReducedMotion();
  const [dismissed, setDismissed] = useState(false);
  const actionRef = useRef<HTMLDivElement>(null);

  useEffect(() => { setDismissed(false); }, [level]);
  useEffect(() => {
    if (level !== 'ACTION' || dismissed) return;
    const previousFocus = document.activeElement;
    actionRef.current?.querySelector<HTMLButtonElement>('button')?.focus();
    return () => { if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus(); };
  }, [level, dismissed]);

  const keepActionFocus = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'Tab') return;
    const buttons = actionRef.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)');
    if (!buttons?.length) return;
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  };

  if (level === 'NONE' || dismissed) return null;
  const elapsedMin = Math.floor(elapsed / 60);

  if (level === 'PERCEPTION') return <motion.button initial={reduceMotion ? false : { opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} onClick={() => { onSnooze?.(); setDismissed(true); }} className="fixed bottom-28 right-4 left-4 sm:left-auto z-[61] flex items-center justify-center gap-2.5 bg-primary-dark text-white px-5 py-3.5 rounded-2xl shadow-lg border border-white/10 text-left" aria-label={`已用 ${elapsedMin} 分钟，注意休息。关闭这次轻提醒`}><Eye size={19} className="shrink-0" /><span className="text-[13px] leading-relaxed">已用 {elapsedMin} 分钟，给眼睛一点留白</span></motion.button>;

  if (level === 'COGNITION') return (
    <motion.section initial={reduceMotion ? false : { y: 24, opacity: 0 }} animate={{ y: 0, opacity: 1 }} className="fixed bottom-28 left-4 right-4 z-[62] bg-white rounded-[24px] p-5 border border-gray-200/80 shadow-xl max-w-[420px] mx-auto" aria-labelledby="cognition-title">
      <button onClick={() => setDismissed(true)} aria-label="关闭提示" className="absolute top-2 right-2 w-11 h-11 rounded-full text-gray-600 flex items-center justify-center"><X size={18} /></button>
      <div className="flex items-start gap-3 pr-6"><span className="w-11 h-11 rounded-2xl bg-accent/10 flex items-center justify-center shrink-0"><Brain size={22} className="text-accent" aria-hidden="true" /></span><div><h2 id="cognition-title" className="text-[17px] font-semibold text-gray-900 leading-snug">已经连续用了 {elapsedMin} 分钟</h2><p className="text-[13px] text-gray-600 leading-relaxed mt-2">不妨停一下，看看远处，让注意力回到自己身上。</p></div></div>
      <div className="flex gap-3 mt-5"><button onClick={() => { onDismiss(); setDismissed(true); }} className="flex-1 min-h-12 rounded-xl bg-gray-100 text-[14px] text-gray-700 font-semibold">稍后提醒</button><button onClick={() => { setDismissed(true); navigate('/rest'); }} className="flex-1 min-h-12 rounded-xl bg-primary text-[14px] text-white font-semibold">去休息</button></div>
    </motion.section>
  );

  return (
    <motion.div ref={actionRef} initial={reduceMotion ? false : { opacity: 0 }} animate={{ opacity: 1 }} className="fixed inset-0 z-[70] bg-[#F4F7F3] overflow-y-auto overscroll-contain" role="dialog" aria-modal="true" aria-labelledby="action-title" aria-describedby="action-description" onKeyDown={keepActionFocus}>
      <div className="min-h-dvh flex flex-col justify-center px-5 py-10 w-full max-w-[440px] mx-auto">
        <div className="w-17 h-17 rounded-[24px] bg-primary/10 flex items-center justify-center mx-auto mb-6"><Leaf size={34} className="text-primary" strokeWidth={1.5} aria-hidden="true" /></div>
        <p className="text-center text-[11px] tracking-[0.2em] text-primary font-semibold mb-3">LET THE MOMENT BREATHE</p>
        <h2 id="action-title" className="text-[27px] font-bold text-gray-900 text-center tracking-tight">是时候休息一下了</h2>
        <p id="action-description" className="text-[14px] text-gray-600 leading-relaxed text-center mt-3 mb-7">已经连续使用 {elapsedMin} 分钟。<br />给自己一点时间，选一种舒服的方式。</p>
        <div className="flex flex-col gap-3 mb-6">{suggestedActivities.map(({ icon: Icon, label, detail, color, background }, index) => <button key={label} onClick={() => { onDismiss(); navigate('/rest', { state: { activityIdx: index } }); }} className="card flex items-center gap-3.5 p-4 text-left"><span className={`w-11 h-11 rounded-2xl flex items-center justify-center shrink-0 ${background}`}><Icon size={21} className={color} aria-hidden="true" /></span><span className="flex-1"><span className="block text-[15px] font-semibold text-gray-900">{label}</span><span className="block text-[12px] text-gray-600 mt-1">{detail}</span></span><ArrowRight size={17} className="text-gray-500" aria-hidden="true" /></button>)}</div>
        <button onClick={() => { onDismiss(); navigate('/rest'); }} className="btn-primary w-full">开始休息</button>
        <p className="text-[12px] text-gray-600 text-center mt-4 leading-relaxed">完成设置的休息时长后，可获得 10 分钟访问窗口。</p>
      </div>
    </motion.div>
  );
}
