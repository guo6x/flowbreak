// src/components/InterventionOverlay.tsx
// PRD 第二章 三层渐进式唤醒机制
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router';
import { InterventionLevel } from '../backend/fatigueEngine';
import { Clock, Eye, Brain, AlertTriangle, X } from 'lucide-react';
import { useEffect, useState } from 'react';

interface Props {
  level: InterventionLevel;
  elapsed: number;
  onDismiss: () => void;
  onSnooze?: () => void;
}

export default function InterventionOverlay({ level, elapsed, onDismiss, onSnooze }: Props) {
  const navigate = useNavigate();
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    setDismissed(false);
  }, [level]);

  if (level === 'NONE' || dismissed) return null;

  const elapsedMin = Math.floor(elapsed / 60);

  // 1. 根据当前唤醒层级，设定常驻呼吸灯的周期、透明度与警示色彩 (PRD 2.1 - 2.3)
  let glowColor = '#A5D6A7'; // 感知层：绿色
  let glowDuration = 3.0;
  let glowOpacity = [0.1, 0.3, 0.1];

  if (level === 'COGNITION') {
    glowColor = '#FF9800'; // 认知层：橙黄色
    glowDuration = 2.5;
    glowOpacity = [0.2, 0.45, 0.2];
  } else if (level === 'ACTION') {
    glowColor = '#F44336'; // 行为层：深红色
    glowDuration = 1.8;
    glowOpacity = [0.3, 0.65, 0.3];
  }

  return (
    <>
      {/* 边缘呼吸灯：全层级常驻组件，渐进式变色与变频 (PRD 2.1.1) */}
      <motion.div
        className="fixed inset-0 pointer-events-none z-[60]"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
      >
        {/* Top edge */}
        <motion.div
          animate={{ opacity: glowOpacity }}
          transition={{ duration: glowDuration, repeat: Infinity }}
          className="absolute top-0 left-0 right-0 h-[2.5px]"
          style={{ background: `linear-gradient(90deg, transparent, ${glowColor}, transparent)` }}
        />
        {/* Bottom edge */}
        <motion.div
          animate={{ opacity: glowOpacity }}
          transition={{ duration: glowDuration, repeat: Infinity, delay: glowDuration / 2 }}
          className="absolute bottom-0 left-0 right-0 h-[2.5px]"
          style={{ background: `linear-gradient(90deg, transparent, ${glowColor}, transparent)` }}
        />
        {/* Left edge */}
        <motion.div
          animate={{ opacity: glowOpacity }}
          transition={{ duration: glowDuration, repeat: Infinity, delay: glowDuration / 4 }}
          className="absolute top-0 bottom-0 left-0 w-[2.5px]"
          style={{ background: `linear-gradient(180deg, transparent, ${glowColor}, transparent)` }}
        />
        {/* Right edge */}
        <motion.div
          animate={{ opacity: glowOpacity }}
          transition={{ duration: glowDuration, repeat: Infinity, delay: (glowDuration * 3) / 4 }}
          className="absolute top-0 bottom-0 right-0 w-[2.5px]"
          style={{ background: `linear-gradient(180deg, transparent, ${glowColor}, transparent)` }}
        />
      </motion.div>

      {/* 角落时间指示器：只在轻度感知或预警状态下展示 (PRD 2.1.1) */}
      {(level === 'PERCEPTION' || level === 'COGNITION') && (
        <motion.div
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          className="fixed bottom-28 right-4 z-[61] flex items-center gap-2 bg-gray-900/85 backdrop-blur-sm px-4 py-2.5 rounded-full cursor-pointer shadow-md select-none border border-gray-800"
          onClick={() => {
            onSnooze?.();
            setDismissed(true);
          }}
        >
          {level === 'PERCEPTION' ? (
            <Eye size={15} className="text-green-400 animate-pulse" />
          ) : (
            <Brain size={15} className="text-orange-400 animate-pulse" />
          )}
          <span className="text-[12px] text-gray-100 font-medium">已用 {elapsedMin} 分钟，注意休息</span>
        </motion.div>
      )}

      {/* 认知提示卡片：仅在认知层弹出，与呼吸灯/指示器完美叠加 (PRD 2.2.1) */}
      {level === 'COGNITION' && (
        <motion.div
          initial={{ y: 100, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ type: 'spring', damping: 20 }}
          className="fixed bottom-28 left-4 right-4 z-[62] bg-white rounded-2xl p-5 shadow-lg border border-gray-100"
          style={{ boxShadow: '0 4px 16px rgba(0,0,0,0.08)' }}
        >
          <button onClick={() => setDismissed(true)} aria-label="关闭提示" className="absolute top-3 right-3 text-gray-400 hover:text-gray-600 active:text-gray-800">
            <X size={18} />
          </button>
          <div className="flex items-start gap-3">
            <div className="w-10 h-10 rounded-xl bg-accent/10 flex items-center justify-center shrink-0">
              <Brain size={22} className="text-accent" />
            </div>
            <div className="flex-1">
              <h3 className="text-[16px] font-bold text-gray-900 mb-1">
                你已经连续观看了 {elapsedMin} 分钟
              </h3>
              <p className="text-[14px] text-gray-500 leading-relaxed">
                眼睛需要休息一下，看看远处的绿色植物 🌿
              </p>
            </div>
          </div>
          <div className="flex gap-2 mt-4">
            <button
              onClick={() => {
                onDismiss();
                setDismissed(true);
              }}
              className="flex-1 py-2.5 rounded-xl bg-gray-100 text-[14px] text-gray-700 font-medium hover:bg-gray-200 active:bg-gray-300 transition-colors"
            >
              稍后提醒
            </button>
            <button
              onClick={() => {
                setDismissed(true);
                navigate('/rest');
              }}
              className="flex-1 py-2.5 rounded-xl bg-primary text-[14px] text-white font-medium hover:bg-primary-dark active:bg-primary transition-colors"
            >
              去休息
            </button>
          </div>
        </motion.div>
      )}

      {/* 行为层强力阻断：全屏遮罩与视频暂停建议卡片叠加 (PRD 2.3.1) */}
      {level === 'ACTION' && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="fixed inset-0 z-[70] bg-black/60 backdrop-blur-md flex items-center justify-center px-6"
        >
          {/* Action Suggestion Card */}
          <motion.div
            initial={{ scale: 0.85, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ type: 'spring', damping: 15, delay: 0.1 }}
            className="bg-white rounded-3xl p-6 w-full max-w-[350px]"
            style={{ boxShadow: '0 8px 32px rgba(0,0,0,0.15)' }}
          >
            <div className="flex flex-col items-center text-center mb-6">
              <div className="w-16 h-16 rounded-full bg-error/10 flex items-center justify-center mb-4">
                <AlertTriangle size={32} className="text-error" />
              </div>
              <h2 className="text-[20px] font-bold text-gray-900 mb-1">是时候休息一下了</h2>
              <p className="text-[14px] text-gray-500">推荐以下 3 分钟恢复活动</p>
            </div>

            {/* Activity Options */}
            <div className="flex flex-col gap-2 mb-6">
              {[
                { icon: Eye, label: '眼部放松', desc: '转动眼球 + 看远处', color: '#4CAF50' },
                { icon: Brain, label: '身体拉伸', desc: '颈部 + 肩部拉伸', color: '#2196F3' },
                { icon: Clock, label: '深呼吸', desc: '4-7-8 呼吸法', color: '#FF9800' },
              ].map((opt, i) => {
                const OptIcon = opt.icon;
                return (
                  <button
                    key={i}
                    onClick={() => {
                      onDismiss();
                      navigate('/rest', { state: { activityIdx: i } });
                    }}
                    className="flex items-center gap-3 p-3 rounded-xl border border-gray-200 hover:border-gray-300 active:bg-gray-50 transition-colors text-left"
                  >
                    <div className="w-9 h-9 rounded-lg flex items-center justify-center" style={{ backgroundColor: opt.color + '15' }}>
                      <OptIcon size={18} style={{ color: opt.color }} />
                    </div>
                    <div>
                      <p className="text-[14px] font-medium text-gray-900">{opt.label}</p>
                      <p className="text-[12px] text-gray-500">{opt.desc}</p>
                    </div>
                  </button>
                );
              })}
            </div>

            {/* Action Buttons */}
            <button
              onClick={() => { onDismiss(); navigate('/rest'); }}
              className="btn-primary w-full mb-2"
            >
              开始休息
            </button>
          </motion.div>
        </motion.div>
      )}
    </>
  );
}
