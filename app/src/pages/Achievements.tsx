// src/pages/Achievements.tsx
import { motion } from 'framer-motion';
import { Flame, Star, Award, CheckCircle } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useStore } from '../hooks/useStore';

export default function Achievements() {
  const navigate = useNavigate();
  const achievements = useStore(s => s.achievements);
  const points = useStore(s => s.points);
  const streak = useStore(s => s.streak);
  const restCount = useStore(s => s.todayStats.restCount);

  // Check if already rested today — reactive via store
  const checkedIn = restCount > 0;

  const unlockedCount = achievements.filter(a => a.unlocked).length;
  const progressPercent = Math.round((unlockedCount / achievements.length) * 100);

  const doCheckin = () => {
    if (checkedIn) return;
    navigate('/rest');
  };

  return (
    <div className="flex flex-col pb-24 px-5 pt-6 no-scrollbar overflow-y-auto min-h-dvh">
      <h1 className="text-[24px] font-bold text-gray-900 mb-6">成就</h1>

      {/* Daily Check-in Card */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className={`card p-4 mb-5 ${checkedIn ? 'bg-primary/5 border border-primary/20' : 'bg-gradient-to-r from-accent/10 to-primary/10'}`}
      >
        <div className="flex items-center gap-3">
          <div className={`w-12 h-12 rounded-full flex items-center justify-center ${checkedIn ? 'bg-primary/15' : 'bg-accent/20'}`}>
            <CheckCircle size={26} className={checkedIn ? 'text-primary' : 'text-accent'} />
          </div>
          <div className="flex-1">
            <p className="text-[15px] font-bold text-gray-900">
              {checkedIn ? '今日已打卡' : '每日打卡'}
            </p>
            <p className="text-[12px] text-gray-500 mt-0.5">
              {checkedIn ? '今日已完成休息，连续坚持中' : '完成一次休息引导即可打卡'}
            </p>
          </div>
          {!checkedIn && (
            <button
              onClick={doCheckin}
              className="px-4 py-2 bg-accent text-white rounded-full text-[13px] font-medium"
            >
              去休息
            </button>
          )}
        </div>
      </motion.div>

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
          <span className="text-[11px] text-gray-500">连续天数</span>
          <span className="text-[10px] text-gray-400 mt-0.5">每天打卡累计</span>
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
          <span className="text-[10px] text-gray-400 mt-0.5">解锁成就+10 积分</span>
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

      {/* Points Info */}
      <div className="card p-4 mb-4 bg-gradient-to-r from-yellow-50 to-orange-50 border border-yellow-100">
        <div className="flex items-center gap-3">
          <span className="text-2xl">💡</span>
          <div>
            <p className="text-[13px] font-medium text-gray-900">积分有什么用？</p>
            <p className="text-[12px] text-gray-500 leading-relaxed">
              积分记录你的坚持历程，连续打卡、解锁成就都能获得。未来可兑换专注主题、休息背景等个性化内容。
            </p>
          </div>
        </div>
      </div>

      {/* Collection Progress - fixed visibility */}
      <div className="card p-4 mb-6">
        <div className="flex items-center justify-between mb-3">
          <span className="text-[14px] font-medium text-gray-900">收集进度</span>
          <span className="text-[13px] font-bold text-primary">{progressPercent}%</span>
        </div>
        <div className="w-full h-3 bg-gray-100 rounded-full overflow-hidden">
          <motion.div
            initial={{ width: 0 }}
            animate={{ width: `${progressPercent}%` }}
            transition={{ duration: 0.8 }}
            className="h-full rounded-full bg-gradient-to-r from-primary to-secondary"
            style={{ minWidth: progressPercent > 0 ? '8px' : '0' }}
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
              !ach.unlocked ? 'grayscale-[0.8] opacity-75' : ''
            }`}
          >
            <span className="text-4xl mb-2">{ach.icon}</span>
            <span className="text-[14px] font-medium text-gray-900 mb-0.5">{ach.title}</span>
            <span className="text-[11px] text-gray-500 leading-tight">{ach.description}</span>
            {ach.unlocked && ach.unlockedAt ? (
              <span className="text-[10px] text-primary mt-2 font-medium">
                {new Date(ach.unlockedAt).toLocaleDateString('zh-CN')}
              </span>
            ) : (
              <span className="text-[10px] text-gray-300 mt-2">🔒 未解锁</span>
            )}
          </motion.div>
        ))}
      </div>
    </div>
  );
}
