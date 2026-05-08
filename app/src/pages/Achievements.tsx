// src/pages/Achievements.tsx
import { motion } from 'framer-motion';
import { Flame, Star, Award } from 'lucide-react';
import { useAchievements } from '../hooks/useStore';

export default function Achievements() {
  const { achievements, points, streak } = useAchievements();

  const unlockedCount = achievements.filter(a => a.unlocked).length;

  return (
    <div className="flex flex-col pb-24 px-5 pt-6 no-scrollbar overflow-y-auto min-h-dvh">
      {/* Header */}
      <h1 className="text-[24px] font-bold text-gray-900 mb-6">成就</h1>

      {/* Stats Cards */}
      <div className="flex gap-3 mb-6">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0 }}
          className="flex-1 card p-4 flex flex-col items-center"
        >
          <div className="w-10 h-10 rounded-full bg-accent/10 flex items-center justify-center mb-2">
            <Flame size={22} className="text-accent" />
          </div>
          <span className="text-[24px] font-bold text-gray-900">{streak}</span>
          <span className="text-[11px] text-gray-500">连续打卡</span>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="flex-1 card p-4 flex flex-col items-center"
        >
          <div className="w-10 h-10 rounded-full bg-primary/10 flex items-center justify-center mb-2">
            <Star size={22} className="text-primary" />
          </div>
          <span className="text-[24px] font-bold text-gray-900">{points}</span>
          <span className="text-[11px] text-gray-500">积分</span>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="flex-1 card p-4 flex flex-col items-center"
        >
          <div className="w-10 h-10 rounded-full bg-secondary/10 flex items-center justify-center mb-2">
            <Award size={22} className="text-secondary" />
          </div>
          <span className="text-[24px] font-bold text-gray-900">{unlockedCount}/{achievements.length}</span>
          <span className="text-[11px] text-gray-500">已解锁</span>
        </motion.div>
      </div>

      {/* Progress */}
      <div className="card p-4 mb-6">
        <div className="flex items-center justify-between mb-2">
          <span className="text-[14px] font-medium text-gray-900">收集进度</span>
          <span className="text-[12px] text-gray-500">{Math.round(unlockedCount / achievements.length * 100)}%</span>
        </div>
        <div className="w-full h-2.5 bg-gray-100 rounded-full overflow-hidden">
          <motion.div
            initial={{ width: 0 }}
            animate={{ width: `${(unlockedCount / achievements.length) * 100}%` }}
            transition={{ duration: 0.8 }}
            className="h-full rounded-full bg-gradient-to-r from-primary to-secondary"
          />
        </div>
      </div>

      {/* Badge Wall */}
      <h3 className="text-[16px] font-bold text-gray-900 mb-3">徽章墙</h3>
      <div className="grid grid-cols-2 gap-3">
        {achievements.map((ach, i) => (
          <motion.div
            key={ach.id}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: i * 0.05 }}
            className={`card p-4 flex flex-col items-center text-center ${
              !ach.unlocked ? 'opacity-40 grayscale' : ''
            }`}
          >
            <span className="text-4xl mb-2">{ach.icon}</span>
            <span className="text-[14px] font-medium text-gray-900 mb-0.5">{ach.title}</span>
            <span className="text-[11px] text-gray-500 leading-tight">{ach.description}</span>
            {!ach.unlocked && (
              <span className="text-[10px] text-accent mt-2 font-medium">🔒 待解锁</span>
            )}
            {ach.unlocked && ach.unlockedAt && (
              <span className="text-[10px] text-primary mt-2 font-medium">
                ✓ {new Date(ach.unlockedAt).toLocaleDateString('zh-CN')}
              </span>
            )}
          </motion.div>
        ))}
      </div>
    </div>
  );
}
