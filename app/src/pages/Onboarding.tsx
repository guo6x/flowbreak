// src/pages/Onboarding.tsx
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { motion, AnimatePresence } from 'framer-motion';
import { Leaf, Layers, Activity, ChevronRight } from 'lucide-react';

const slides = [
  {
    icon: Leaf,
    color: '#4CAF50',
    title: '自然唤醒',
    subtitle: '从刷视频里醒过来',
    desc: '不打断、不惩罚、不制造内疚：先用轻提醒帮你觉察，再在连续使用过量后引导休息。',
  },
  {
    icon: Layers,
    color: '#2196F3',
    title: '三层渐进式干预',
    subtitle: '温和而有效',
    desc: '从感知层的微光提示，到认知层的智能提醒，再到行为层的温和引导，强度逐步提升',
  },
  {
    icon: Activity,
    color: '#FF9800',
    title: '从上头里拉回来',
    subtitle: '按连续使用时长渐进介入',
    desc: '同一组应用共享连续使用限额：80% 先提醒，100% 再提示，120% 才进入休息引导。没有未经验证的“疲劳准确率”承诺。',
  },
];

export default function Onboarding() {
  const [current, setCurrent] = useState(0);
  const navigate = useNavigate();

  const next = () => {
    if (current < slides.length - 1) {
      setCurrent(current + 1);
    } else {
      navigate('/permissions');
    }
  };

  const skip = () => {
    if (current < slides.length - 1) {
      setCurrent(slides.length - 1);
    } else {
      navigate('/permissions');
    }
  };

  const slide = slides[current];
  const Icon = slide.icon;

  return (
    <div className="flex flex-col min-h-dvh px-8 pt-16 pb-12">
      {/* Skip button */}
      <div className="flex justify-end">
        <button onClick={skip} className="btn-text text-gray-500">跳过</button>
      </div>

      {/* Content */}
      <div className="flex-1 flex flex-col items-center justify-center">
        <AnimatePresence mode="wait">
          <motion.div
            key={current}
            initial={{ opacity: 0, x: 60 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -60 }}
            transition={{ duration: 0.35 }}
            className="flex flex-col items-center text-center"
          >
            {/* Icon circle */}
            <div
              className="w-32 h-32 rounded-full flex items-center justify-center mb-10"
              style={{ backgroundColor: slide.color + '18' }}
            >
              <Icon size={56} style={{ color: slide.color }} strokeWidth={1.5} />
            </div>

            <h1 className="text-[24px] font-bold text-gray-900 mb-2">{slide.title}</h1>
            <p className="text-[16px] font-medium mb-4" style={{ color: slide.color }}>{slide.subtitle}</p>
            <p className="text-[14px] text-gray-500 leading-relaxed max-w-[280px]">{slide.desc}</p>
          </motion.div>
        </AnimatePresence>
      </div>

      {/* Dots + Button */}
      <div className="flex flex-col items-center gap-8">
        {/* Dots */}
        <div className="flex gap-2">
          {slides.map((_, i) => (
            <div
              key={i}
              className={`h-2 rounded-full transition-all duration-300 ${
                i === current ? 'w-6' : 'w-2'
              }`}
              style={{ backgroundColor: i === current ? slide.color : '#E0E0E0' }}
            />
          ))}
        </div>

        {/* Next button */}
        <button
          onClick={next}
          className="w-full flex items-center justify-center gap-2 btn-primary"
          style={{ backgroundColor: slide.color }}
        >
          <span>{current === slides.length - 1 ? '开始使用' : '下一步'}</span>
          <ChevronRight size={18} />
        </button>
      </div>
    </div>
  );
}
