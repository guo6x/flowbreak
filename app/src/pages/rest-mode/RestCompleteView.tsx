// Full-screen reward / completion view shown after a successful rest.
// Renders confetti, the completion message, reward cards, and the
// "继续使用" button. All data is passed via props — no business logic.

import { motion } from 'framer-motion';
import type { ConfettiPiece } from './types';

export interface RestCompleteViewProps {
  confetti: ConfettiPiece[];
  rewardContentVisible: boolean;
  rewardBadgeTitle: string | null;
  onFinish: () => void;
}

export default function RestCompleteView({
  confetti,
  rewardContentVisible,
  rewardBadgeTitle,
  onFinish,
}: RestCompleteViewProps) {
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

          <button onClick={onFinish} className="btn-primary w-full max-w-xs mx-auto">
            继续使用
          </button>
        </motion.div>
      )}
    </div>
  );
}
