import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Moon, Focus, Clock, Shield, Zap, Play, ChevronRight } from 'lucide-react';
import { useTodayStats, useFatigueEngine } from '../hooks/useStore';
import {
  addScreenTime,
  getProfile,
  getTodayActivities,
  logIntervention,
  startSession,
} from '../backend/storage';
import InterventionOverlay from '../components/InterventionOverlay';
import { NativeFlow } from '../backend/nativeFlow';
import { Capacitor } from '@capacitor/core';

function formatMinutes(seconds: number) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

function formatDuration(seconds: number) {
  if (seconds >= 60) return `${Math.round(seconds / 60)}分钟`;
  return `${seconds}秒`;
}

// Demo simulation: 模拟数据，仅在非原生平台时使用
const DEMO_APP_POOL = ['抖音', 'B站', '微信视频号', '微博', '小红书'];

export default function Dashboard() {
  const navigate = useNavigate();
  const { stats, refresh } = useTodayStats();
  const profile = getProfile();
  const [isMonitoring, setIsMonitoring] = useState(false);
  const [snoozeUntil, setSnoozeUntil] = useState(0);
  const { score, level, elapsed } = useFatigueEngine(isMonitoring);

  const prevLevelRef = useRef(level);
  const appCursorRef = useRef(0);

  useEffect(() => {
    if (!isMonitoring) return;
    startSession();
    if (Capacitor.isNativePlatform()) {
      NativeFlow.startService().catch(() => undefined);
    }

    const timer = setInterval(() => {
      const appName = DEMO_APP_POOL[appCursorRef.current % DEMO_APP_POOL.length];
      addScreenTime(5, appName);
      appCursorRef.current += 1;
      refresh();
    }, 5000);
    return () => {
      clearInterval(timer);
      if (Capacitor.isNativePlatform()) {
        NativeFlow.stopService().catch(() => undefined);
      }
    };
  }, [isMonitoring, refresh]);

  useEffect(() => {
    if (level === 'NONE') {
      prevLevelRef.current = level;
      return;
    }
    if (Date.now() < snoozeUntil) return;
    if (prevLevelRef.current !== level) {
      logIntervention(level);
      if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
        const msg =
          level === 'ACTION' ? '疲劳指数较高，建议立刻休息3分钟。' :
          level === 'COGNITION' ? '认知疲劳上升，建议短暂放松。' :
          '出现轻度疲劳，注意眼部休息。';
        new Notification('FlowBreak 提醒', { body: msg });
      }
      refresh();
      prevLevelRef.current = level;
    }
  }, [level, snoozeUntil, refresh]);

  const goalSeconds = profile.dailyGoal * 60;
  const progress = Math.min(1, stats.totalScreenTime / goalSeconds);

  const timeline = useMemo(() => {
    const activities = getTodayActivities();
    return activities
      .slice(-8)
      .reverse()
      .map(item => ({
        time: new Date(item.at).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
        app: item.app,
        duration: formatDuration(item.durationSec),
        color:
          item.type === 'rest' ? '#2196F3' :
          item.type === 'intervention' ? '#F44336' :
          '#4CAF50',
      }));
  }, [stats.totalScreenTime, stats.restCount, stats.interventionCount]);

  const showOverlay = isMonitoring && level !== 'NONE' && Date.now() >= snoozeUntil;

  return (
    <div className="flex flex-col pb-24 px-5 pt-6 no-scrollbar overflow-y-auto min-h-dvh">
      <div className="flex items-center justify-between mb-6">
        <div>
          <p className="text-[12px] text-gray-500">{new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })}</p>
          <h1 className="text-[24px] font-bold text-gray-900">仪表盘</h1>
        </div>
        <div className="flex items-center gap-2">
          {isMonitoring && (
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              className="flex items-center gap-1.5 bg-primary/10 px-3 py-1.5 rounded-full"
            >
              <div className="w-2 h-2 rounded-full bg-primary animate-pulse" />
              <span className="text-[11px] text-primary font-medium">监控中</span>
            </motion.div>
          )}
        </div>
      </div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="card-lg p-5 mb-5 bg-gradient-to-br from-primary to-primary-dark text-white relative overflow-hidden"
      >
        <div className="absolute top-0 right-0 w-32 h-32 rounded-full bg-white/10 -translate-y-10 translate-x-10" />
        <div className="absolute bottom-0 left-0 w-20 h-20 rounded-full bg-white/5 translate-y-8 -translate-x-8" />

        <div className="relative z-10">
          <div className="flex items-start justify-between mb-4">
            <div>
              <p className="text-white/70 text-[12px] mb-1">今日屏幕时间</p>
              <h2 className="text-[40px] font-light leading-none">{formatMinutes(stats.totalScreenTime)}</h2>
            </div>
            <div className="text-right">
              <p className="text-white/70 text-[12px] mb-1">目标</p>
              <p className="text-[16px] font-medium">{profile.dailyGoal / 60}小时</p>
            </div>
          </div>

          <div className="w-full h-2 bg-white/20 rounded-full overflow-hidden mb-4">
            <motion.div
              initial={{ width: 0 }}
              animate={{ width: `${progress * 100}%` }}
              transition={{ duration: 1, ease: 'easeOut' }}
              className={`h-full rounded-full ${progress > 0.9 ? 'bg-red-300' : 'bg-white'}`}
            />
          </div>

          <div className="flex justify-between">
            <div className="flex items-center gap-1.5">
              <Moon size={14} className="text-white/70" />
              <span className="text-[12px] text-white/80">休息 {stats.restCount} 次</span>
            </div>
            <div className="flex items-center gap-1.5">
              <Shield size={14} className="text-white/70" />
              <span className="text-[12px] text-white/80">干预 {stats.interventionCount} 次</span>
            </div>
            <div className="flex items-center gap-1.5">
              <Zap size={14} className="text-white/70" />
              <span className="text-[12px] text-white/80">疲劳 {(score * 100).toFixed(0)}%</span>
            </div>
          </div>
        </div>
      </motion.div>

      <div className="flex gap-3 mb-6">
        <motion.button
          whileTap={{ scale: 0.95 }}
          onClick={() => navigate('/rest')}
          className="flex-1 card flex items-center gap-3 p-4"
        >
          <div className="w-10 h-10 rounded-xl bg-secondary/10 flex items-center justify-center">
            <Moon size={20} className="text-secondary" />
          </div>
          <div className="text-left">
            <p className="text-[14px] font-medium text-gray-900">开始休息</p>
            <p className="text-[11px] text-gray-500">恢复活动</p>
          </div>
        </motion.button>

        <motion.button
          whileTap={{ scale: 0.95 }}
          onClick={() => setIsMonitoring(v => !v)}
          className="flex-1 card flex items-center gap-3 p-4"
        >
          <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${
            isMonitoring ? 'bg-error/10' : 'bg-accent/10'
          }`}>
            {isMonitoring ? <Focus size={20} className="text-error" /> : <Play size={20} className="text-accent" />}
          </div>
          <div className="text-left">
            <p className="text-[14px] font-medium text-gray-900">{isMonitoring ? '停止监控' : '开始监控'}</p>
            <p className="text-[11px] text-gray-500">{isMonitoring ? `已运行 ${Math.floor(elapsed / 60)}分钟` : '疲劳检测'}</p>
          </div>
        </motion.button>
      </div>

      <div className="flex items-center justify-between mb-3">
        <h3 className="text-[16px] font-bold text-gray-900">今日活动</h3>
        <button onClick={() => navigate('/stats')} className="text-[12px] text-primary font-medium flex items-center gap-0.5">
          查看全部 <ChevronRight size={14} />
        </button>
      </div>

      <div className="card p-4 mb-4">
        {timeline.length === 0 && (
          <div className="flex flex-col items-center py-8">
            <div className="w-16 h-16 rounded-full bg-gray-100 flex items-center justify-center mb-3">
              <Clock size={28} className="text-gray-300" />
            </div>
            <p className="text-[14px] text-gray-500 mb-1">暂无活动记录</p>
            <p className="text-[12px] text-gray-400">点击"开始监控"后，这里会展示实时活动轨迹</p>
          </div>
        )}
        {timeline.map((item, i) => (
          <div key={`${item.time}-${i}`} className="flex items-center gap-3 py-2.5 border-b border-gray-300/30 last:border-b-0">
            <span className="text-[12px] text-gray-400 w-11 shrink-0">{item.time}</span>
            <div className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: item.color }} />
            <span className="text-[14px] text-gray-900 flex-1">{item.app}</span>
            <span className="text-[12px] text-gray-500">{item.duration}</span>
          </div>
        ))}
      </div>

      {isMonitoring && level !== 'NONE' && (
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className={`card p-4 flex items-center gap-3 ${
            level === 'ACTION' ? 'bg-error/5 border border-error/20' :
            level === 'COGNITION' ? 'bg-accent/5 border border-accent/20' :
            'bg-primary/5 border border-primary/20'
          }`}
        >
          <Clock size={20} className={
            level === 'ACTION' ? 'text-error' :
            level === 'COGNITION' ? 'text-accent' : 'text-primary'
          } />
          <div className="flex-1">
            <p className="text-[14px] font-medium text-gray-900">
              {level === 'ACTION' ? '⚠️ 需要立即休息' :
               level === 'COGNITION' ? '🧠 认知疲劳升高' : '👁️ 轻度疲劳'}
            </p>
            <p className="text-[12px] text-gray-500">疲劳指数 {(score * 100).toFixed(0)}%</p>
          </div>
          <button onClick={() => navigate('/rest')} className="px-3 py-1.5 bg-primary text-white rounded-full text-[12px] font-medium">
            去休息
          </button>
        </motion.div>
      )}

      {showOverlay && (
        <InterventionOverlay
          level={level}
          score={score}
          elapsed={elapsed}
          onDismiss={() => {
            logIntervention(`${level}-confirm`);
            refresh();
          }}
          onSnooze={() => {
            setSnoozeUntil(Date.now() + 10 * 60 * 1000);
          }}
        />
      )}
    </div>
  );
}
