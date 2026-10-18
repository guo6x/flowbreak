import { motion, useReducedMotion } from 'framer-motion';
import { ArrowRight, Check, Clock3, Leaf, ShieldCheck } from 'lucide-react';
import type { ConfettiPiece } from './types';

export interface RestCompleteViewProps {
  confetti: ConfettiPiece[];
  rewardContentVisible: boolean;
  rewardBadgeTitle: string | null;
  onFinish: () => void;
}

export default function RestCompleteView({ confetti, rewardContentVisible, rewardBadgeTitle, onFinish }: RestCompleteViewProps) {
  const reduceMotion = useReducedMotion();
  return (
    <div className="fixed inset-0 z-[100] bg-[#F4F7F3] overflow-y-auto text-center">
      <div aria-hidden="true" className="fixed top-0 inset-x-0 h-[45vh] bg-gradient-to-b from-primary/8 to-transparent pointer-events-none" />
      {!reduceMotion ? <div className="fixed inset-0 overflow-hidden pointer-events-none" aria-hidden="true">{confetti.slice(0, 14).map((item, index) => <motion.div key={index} initial={{ y: -80, opacity: 0.7, rotate: 0 }} animate={{ y: '100vh', rotate: item.rotate, opacity: 0 }} transition={{ duration: item.duration + 1, delay: item.delay }} className="absolute w-2 h-3 rounded-sm" style={{ backgroundColor: item.color, left: `${item.left}%` }} />)}</div> : null}
      <main className="relative min-h-dvh flex flex-col items-center justify-center px-6 py-12 w-full max-w-[460px] mx-auto">
        {rewardContentVisible ? <motion.div initial={reduceMotion ? false : { opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.35 }} className="w-full">
          <div className="relative mx-auto w-24 h-24 rounded-[32px] bg-primary/10 flex items-center justify-center mb-7"><Leaf size={43} strokeWidth={1.5} className="text-primary" aria-hidden="true" /><span className="absolute -bottom-1 -right-1 w-8 h-8 rounded-full bg-primary text-white border-4 border-[#F4F7F3] flex items-center justify-center"><Check size={16} aria-hidden="true" /></span></div>
          <p className="text-[11px] tracking-[0.18em] font-semibold text-primary mb-3">A LITTLE SPACE, A FRESH START</p>
          <h1 className="text-[30px] font-bold tracking-tight text-gray-900 mb-3">休息完成！</h1>
          <p className="text-[14px] text-gray-600 leading-relaxed max-w-[280px] mx-auto">你为自己留出了一点时间。<br />带着这份从容，继续接下来的生活。</p>
          <div className="grid grid-cols-2 gap-3 my-8 text-left">
            <div className="card p-4"><Clock3 size={20} className="text-secondary mb-4" aria-hidden="true" /><p className="text-[23px] font-semibold tracking-tight text-gray-900">10 分钟</p><p className="text-[12px] text-gray-600 mt-1">目标应用访问窗口</p></div>
            <div className="card p-4"><ShieldCheck size={20} className="text-primary mb-4" aria-hidden="true" /><p className="text-[16px] font-semibold leading-7 text-gray-900">{rewardBadgeTitle || '休息已记录'}</p><p className="text-[12px] text-gray-600 mt-1">{rewardBadgeTitle ? '新徽章' : '健康进度'}</p></div>
          </div>
          <button onClick={onFinish} className="btn-primary w-full flex items-center justify-center gap-2">继续使用<ArrowRight size={18} aria-hidden="true" /></button>
          <p className="text-[12px] text-gray-600 mt-4">访问窗口结束后，保护会继续。</p>
        </motion.div> : <p role="status" className="text-[14px] text-primary">休息已完成，正在整理记录...</p>}
      </main>
    </div>
  );
}
