// src/pages/Onboarding.tsx
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { motion, useReducedMotion } from 'framer-motion';
import { Leaf, Layers, Activity, ChevronRight, LockKeyhole } from 'lucide-react';
import Brand from '../components/Brand';

const slides = [
  {
    icon: Leaf,
    color: '#247353',
    title: '自然唤醒',
    subtitle: '从刷视频里醒过来',
    desc: '刷着刷着，时间就过去了。先用轻提醒帮你觉察，再在连续使用过量后引导休息。不惩罚，也不制造内疚。',
    note: '把注意力，慢慢还给自己。',
  },
  {
    icon: Layers,
    color: '#367C98',
    title: '三层渐进式干预',
    subtitle: '先提醒，再给自己一个暂停',
    desc: '接近限额时轻提醒，达到限额时再提示。持续使用到限额的 120% 时阻断目标应用，完成休息后再继续。',
    note: '轻提醒 → 强提醒 → 休息引导',
  },
  {
    icon: Activity,
    color: '#9A693E',
    title: '从上头里拉回来',
    subtitle: '让休息，成为日常的一小步',
    desc: '选好想少刷一点的应用，再设定连续使用限额和 2–5 分钟的休息。不需要账号，记录只留在你的手机里。',
    note: '仅支持 Android · 无账号 · 数据本地保存',
  },
];

export default function Onboarding() {
  const [current, setCurrent] = useState(0);
  const navigate = useNavigate();
  const reduceMotion = useReducedMotion();

  const next = () => {
    if (current < slides.length - 1) {
      setCurrent(value => value + 1);
    } else {
      navigate('/permissions');
    }
  };

  const skip = () => {
    navigate('/permissions', { replace: true });
  };

  const slide = slides[current];
  const Icon = slide.icon;

  return (
    <div className="flex flex-col min-h-dvh px-6 pt-7 pb-8 sm:px-8">
      <div className="flex items-center justify-between gap-4">
        <Brand />
        <button onClick={skip} className="min-h-[44px] px-4 rounded-full text-[14px] font-medium text-gray-600 hover:bg-white">跳过</button>
      </div>

      <div className="flex-1 flex flex-col items-center justify-center py-10 sm:py-14">
          <motion.div
            key={current}
            initial={reduceMotion ? false : { opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
            className="flex flex-col items-center text-center w-full"
          >
            <div className="relative flex items-center justify-center w-56 h-56 mb-8" aria-hidden="true">
              <div className="absolute inset-0 rounded-[42%_58%_55%_45%] rotate-12" style={{ backgroundColor: slide.color + '0B' }} />
              <div className="absolute inset-5 rounded-full border" style={{ borderColor: slide.color + '24' }} />
              <div className="w-32 h-32 rounded-[2.5rem] bg-white flex items-center justify-center shadow-sm -rotate-6">
                <Icon size={58} style={{ color: slide.color }} strokeWidth={1.4} className="rotate-6" />
              </div>
              <span className="absolute right-4 top-8 w-6 h-6 rounded-full" style={{ backgroundColor: slide.color + '24' }} />
              <span className="absolute left-6 bottom-5 w-3 h-3 rounded-full" style={{ backgroundColor: slide.color + '60' }} />
            </div>

            <p className="text-[12px] font-semibold tracking-[0.2em] text-gray-500 mb-4">给注意力一个温柔的转弯</p>
            <h1 className="text-[30px] sm:text-[34px] font-bold tracking-tight text-gray-900 mb-3">{slide.title}</h1>
            <p className="text-[16px] font-medium mb-5" style={{ color: slide.color }}>{slide.subtitle}</p>
            <p className="text-[14px] text-gray-600 leading-7 max-w-[340px]">{slide.desc}</p>
            <p className="text-[13px] text-gray-600 mt-6 px-4 py-3 rounded-2xl border border-white bg-white/70">{slide.note}</p>
          </motion.div>
      </div>

      <div className="flex flex-col items-center gap-4">
        <div className="flex items-center gap-1" aria-label="引导进度">
          {slides.map((_, i) => (
            <button
              key={i}
              onClick={() => setCurrent(i)}
              aria-label={`跳转到第 ${i + 1} 页`}
              aria-current={i === current ? 'step' : undefined}
              className="w-[44px] h-[44px] flex items-center justify-center rounded-full"
            >
              <span className={`block h-1.5 rounded-full transition-all ${i === current ? 'w-6' : 'w-1.5'}`} style={{ backgroundColor: i === current ? slide.color : '#CFDAD1' }} />
            </button>
          ))}
        </div>

        <button
          onClick={next}
          className="w-full flex items-center justify-center gap-2 btn-primary"
        >
          <span>{current === slides.length - 1 ? '开始使用' : '下一步'}</span>
          <ChevronRight size={18} aria-hidden="true" />
        </button>
        <p className="text-[12px] text-gray-500 flex items-center gap-1.5"><LockKeyhole size={12} aria-hidden="true" />不需要登录，你的数据只属于你</p>
      </div>
    </div>
  );
}
