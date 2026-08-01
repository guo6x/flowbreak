import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { motion } from 'framer-motion';
import { Moon, Focus, Clock, Shield, Zap, Play, ChevronRight, AlertTriangle } from 'lucide-react';
import { useStore } from '../hooks/useStore';
import { DailyReflection, getTodayActivities, getTodayReflection, saveTodayReflection } from '../backend/storage';
import { Capacitor } from '@capacitor/core';
import { NativeFlow } from '../backend/nativeFlow';
import { getAppName } from '../backend/appNames';
import { useNativePermissions } from '../hooks/useNativePermissions';

function formatMinutes(seconds: number) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}小时${m}分`;
  return `${m}分钟`;
}

function formatDuration(seconds: number) {
  if (seconds >= 60) return `${Math.floor(seconds / 60)}分钟`;
  return `${seconds}秒`;
}

function formatGoal(minutes: number) {
  if (minutes < 60) return `${minutes}分钟`;
  const hours = minutes / 60;
  if (Number.isInteger(hours)) return `${hours}小时`;
  return `${hours.toFixed(1)}小时`;
}

export default function Dashboard() {
  const navigate = useNavigate();
  const profile = useStore(s => s.profile);
  const stats = useStore(s => s.todayStats);
  const isMonitoring = useStore(s => s.isMonitoring);
  const setMonitoring = useStore(s => s.setMonitoring);
  const score = useStore(s => s.fatigueScore);
  const level = useStore(s => s.fatigueLevel);
  const currentAppName = useStore(s => s.currentAppName);
  const continuousSessionSeconds = useStore(s => s.continuousSessionSeconds);
  const blockState = useStore(s => s.blockState);
  const graceUntil = useStore(s => s.graceUntil);
  const blockedPackage = useStore(s => s.blockedPackage);
  const serviceError = useStore(s => s.serviceError);
  const [now, setNow] = useState(Date.now());
  const [summary, setSummary] = useState({
    blockCount: stats.interventionCount,
    restCount: stats.restCount,
    unlockSeconds: stats.restCount * 10 * 60,
    pullbackOutcomeCount: 0,
    successfulPullbackCount: 0,
    postRestReturnCount: 0,
    postRestTargetSeconds: 0,
    reflectionValue: 0,
  });
  const [reflection, setReflection] = useState<DailyReflection | ''>(getTodayReflection());
  const [reflectionError, setReflectionError] = useState('');
  const [summaryError, setSummaryError] = useState(false);
  const [toggling, setToggling] = useState(false);
  const { isNative, permissions } = useNativePermissions();
  const missingCritical = isNative && isMonitoring && (!permissions.hasUsageStats || !permissions.hasOverlay);
  const missingCriticalLabel = !permissions.hasUsageStats
    ? '使用情况访问'
    : '悬浮窗';

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    let active = true;
    const refreshSummary = () => {
      if (Capacitor.isNativePlatform()) {
        NativeFlow.getDashboardSummary().then(result => {
          if (!active) return;
          setSummary(result);
          setSummaryError(false);
        }).catch(() => {
          if (active) setSummaryError(true);
        });
      } else {
        setSummary({
          blockCount: stats.interventionCount,
          restCount: stats.restCount,
          unlockSeconds: stats.restCount * 10 * 60,
          pullbackOutcomeCount: 0,
          successfulPullbackCount: 0,
          postRestReturnCount: 0,
          postRestTargetSeconds: 0,
          reflectionValue: reflection === 'helped' ? 3 : reflection === 'neutral' ? 2 : reflection === 'not_helped' ? 1 : 0,
        });
      }
    };
    refreshSummary();
    const summaryTimer = Capacitor.isNativePlatform()
      ? setInterval(refreshSummary, 15_000)
      : undefined;
    return () => {
      active = false;
      clearInterval(timer);
      if (summaryTimer) clearInterval(summaryTimer);
    };
  }, [stats.interventionCount, stats.restCount, profile.restDuration]);

  useEffect(() => {
    if (!Capacitor.isNativePlatform() || summary.reflectionValue === 0) return;
    setReflection(summary.reflectionValue === 3 ? 'helped' : summary.reflectionValue === 2 ? 'neutral' : 'not_helped');
  }, [summary.reflectionValue]);

  const submitReflection = async (value: DailyReflection) => {
    setReflection(value);
    setSummary(current => ({
      ...current,
      reflectionValue: value === 'helped' ? 3 : value === 'neutral' ? 2 : 1,
    }));
    setReflectionError('');
    saveTodayReflection(value);
    if (!Capacitor.isNativePlatform()) return;
    try {
      await NativeFlow.saveDailyReflection({ value });
    } catch {
      setReflectionError('反馈暂时未写入本机，请再点一次。');
    }
  };

  const handleToggleMonitoring = async () => {
    if (toggling) return;
    setToggling(true);
    try {
      // setMonitoring 内部会触发 native 服务启停，await 一下让 effect 跑完
      setMonitoring(!isMonitoring);
      // 给 native 服务一点时间，避免快速连点导致反复启停
      await new Promise(r => setTimeout(r, 300));
    } finally {
      setToggling(false);
    }
  };

  const goalSeconds = profile.dailyGoal * 60;
  const progress = Math.min(1, stats.totalScreenTime / goalSeconds);
  const graceSeconds = Math.max(0, Math.ceil((graceUntil - now) / 1000));
  const displayedRestCount = Capacitor.isNativePlatform() ? summary.restCount : stats.restCount;
  const displayedInterventionCount = Capacitor.isNativePlatform() ? summary.blockCount : stats.interventionCount;

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

  return (
    <div className="flex flex-col pb-24 px-5 pt-6 no-scrollbar overflow-y-auto min-h-dvh">
      <div className="flex items-center justify-between mb-6">
        <div>
          <p className="text-[12px] text-gray-500">{new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })}</p>
          <h1 className="text-[24px] font-bold text-gray-900">仪表盘</h1>
        </div>
        <div className="flex items-center gap-2">
          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            className="flex flex-col items-end"
          >
            <div className="flex items-center gap-1.5 bg-primary/10 px-3 py-1.5 rounded-full">
              <div className={`w-2 h-2 rounded-full ${isMonitoring ? 'bg-primary animate-pulse' : 'bg-gray-400'}`} />
              <span className="text-[11px] text-primary font-medium">{isMonitoring ? '监控中' : '已暂停'}</span>
            </div>
            {currentAppName && isMonitoring && (
              <span className="text-[11px] text-gray-500 mt-1">当前: {currentAppName}</span>
            )}
          </motion.div>
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
              <p className="text-white/70 text-[12px] mb-1">今日目标应用时间</p>
              <h2 className="text-[40px] font-light leading-none">{formatMinutes(stats.totalScreenTime)}</h2>
            </div>
            <div className="text-right">
              <p className="text-white/70 text-[12px] mb-1">目标</p>
              <p className="text-[16px] font-medium">{formatGoal(profile.dailyGoal)}</p>
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
              <span className="text-[12px] text-white/80">休息 {displayedRestCount} 次</span>
            </div>
            <div className="flex items-center gap-1.5">
              <Shield size={14} className="text-white/70" />
              <span className="text-[12px] text-white/80">阻断 {displayedInterventionCount} 次</span>
            </div>
            <div className="flex items-center gap-1.5">
              <Zap size={14} className="text-white/70" />
              <span className="text-[12px] text-white/80">疲劳 {(score * 100).toFixed(0)}%</span>
            </div>
          </div>
        </div>
      </motion.div>

      <div className="grid grid-cols-3 gap-2 mb-5">
        <div className="card p-3">
          <p className="text-[10px] text-gray-500">今日阻断</p>
          <p className="text-[20px] font-bold mt-1">{summaryError ? '--' : summary.blockCount}</p>
        </div>
        <div className="card p-3">
          <p className="text-[10px] text-gray-500">休息解锁</p>
          <p className="text-[20px] font-bold mt-1">{summaryError ? '--' : `${Math.round(summary.unlockSeconds / 60)}分`}</p>
        </div>
        <div className="card p-3">
          <p className="text-[10px] text-gray-500">访问窗口</p>
          <p className="text-[20px] font-bold mt-1">
            {graceSeconds > 0 ? `${Math.floor(graceSeconds / 60)}:${String(graceSeconds % 60).padStart(2, '0')}` : '--'}
          </p>
        </div>
      </div>

      <div className="card p-4 mb-5" data-testid="daily-reflection">
        <p className="text-[14px] font-bold text-gray-900">今天 FlowBreak 有帮你从刷视频里醒过来吗？</p>
        <p className="text-[11px] text-gray-500 mt-1">每天只记一次感受，可随时修改，只保存在本机。</p>
        <div className="grid grid-cols-3 gap-2 mt-3">
          {([
            ['helped', '有帮助'],
            ['neutral', '一般'],
            ['not_helped', '没帮助'],
          ] as const).map(([value, label]) => (
            <button
              key={value}
              data-testid={`reflection-${value}`}
              onClick={() => void submitReflection(value)}
              className={`rounded-xl py-2.5 text-[12px] font-medium border transition-colors ${
                reflection === value
                  ? 'bg-primary text-white border-primary'
                  : 'bg-white text-gray-600 border-gray-200'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        {reflectionError && <p className="text-[11px] text-error mt-2">{reflectionError}</p>}
      </div>

      {blockState === 'BLOCKED' && (
        <button onClick={() => navigate('/rest')} className="w-full mb-5 p-4 rounded-2xl bg-error/5 border border-error/20 text-left">
          <p className="text-[14px] font-bold text-error">{getAppName(blockedPackage) || '目标应用'} 已阻断</p>
          <p className="text-[12px] text-gray-600 mt-1">本次连续使用 {Math.floor(continuousSessionSeconds / 60)} 分钟，完成 {Math.round(profile.restDuration / 60)} 分钟休息后解锁。</p>
        </button>
      )}

      {/* Action buttons */}
      <div className="flex gap-3 mb-6">
        <motion.button
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          whileTap={{ scale: 0.95 }}
          onClick={() => navigate('/rest')}
          className="flex-1 card flex items-center gap-3 p-4 bg-secondary/5 border border-secondary/20"
        >
          <div className="w-10 h-10 rounded-xl bg-secondary/10 flex items-center justify-center">
            <Moon size={20} className="text-secondary" />
          </div>
          <div className="text-left">
            <p className="text-[14px] font-medium text-gray-900">开始休息</p>
            <p className="text-[11px] text-gray-500">
              {level === 'NONE' ? '主动休息' : level === 'ACTION' ? '强烈建议休息' : level === 'COGNITION' ? '认知疲劳' : '轻度疲劳'}
            </p>
          </div>
        </motion.button>

        <motion.button
          whileTap={{ scale: 0.95 }}
          onClick={handleToggleMonitoring}
          disabled={toggling}
          className="flex-1 card flex items-center gap-3 p-4 disabled:opacity-60"
        >
          <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${
            isMonitoring ? 'bg-error/10' : 'bg-accent/10'
          }`}>
            {isMonitoring ? <Focus size={20} className="text-error" /> : <Play size={20} className="text-accent" />}
          </div>
          <div className="text-left">
            <p className="text-[14px] font-medium text-gray-900">{isMonitoring ? '停止监控' : '开始监控'}</p>
            <p className="text-[11px] text-gray-500">
              {toggling ? '正在切换...' : isMonitoring ? `连续 ${Math.floor(continuousSessionSeconds / 60)}分钟` : '已暂停 - 点击恢复'}
            </p>
          </div>
        </motion.button>
      </div>
      {serviceError && (
        <div className="mb-5 rounded-xl border border-error/20 bg-error/5 px-4 py-3 text-[12px] text-error">
          {serviceError}
        </div>
      )}

      {missingCritical && (
        <button
          onClick={() => navigate('/permissions')}
          className="mb-5 w-full rounded-xl border border-error/30 bg-error/5 px-4 py-3 text-left flex items-center gap-3"
        >
          <AlertTriangle size={18} className="text-error shrink-0" />
          <div className="flex-1">
            <p className="text-[13px] font-medium text-error">{missingCriticalLabel}权限已失效</p>
            <p className="text-[11px] text-gray-600 mt-0.5">点击重新授权，监控才能继续生效</p>
          </div>
          <ChevronRight size={16} className="text-error shrink-0" />
        </button>
      )}

      {/* Fatigue level indicator */}
      {level === 'NONE' && (
        <div className="card p-4 mb-4 flex items-center gap-3 bg-primary/5 border border-primary/10">
          <Zap size={18} className="text-primary" />
          <div>
            <p className="text-[13px] font-medium text-gray-900">状态良好</p>
            <p className="text-[12px] text-gray-500">未检测到明显疲劳，继续保持</p>
          </div>
        </div>
      )}

      <div className="flex items-center justify-between mb-3 mt-2">
        <h3 className="text-[16px] font-bold text-gray-900">今日活动</h3>
        <button onClick={() => navigate('/stats')} className="text-[12px] text-primary font-medium flex items-center gap-0.5">
          查看全部 <ChevronRight size={14} />
        </button>
      </div>

      <div className="card p-4 mb-4">
        {timeline.length === 0 && (
          <div className="flex flex-col items-center py-10">
            <div className="relative mb-4">
              <div className="w-20 h-20 rounded-full bg-gradient-to-br from-primary/10 to-secondary/10 flex items-center justify-center">
                <Clock size={32} className="text-primary/40" />
              </div>
              {isMonitoring && (
                <div className="absolute -bottom-1 -right-1 w-7 h-7 rounded-full bg-primary/10 flex items-center justify-center">
                  <div className="w-2 h-2 rounded-full bg-primary animate-pulse" />
                </div>
              )}
            </div>
            <p className="text-[14px] font-medium text-gray-700 mb-1">
              {isMonitoring ? '正在记录活动' : '监控已暂停'}
            </p>
            <p className="text-[12px] text-gray-400 text-center leading-relaxed max-w-[220px]">
              {isMonitoring 
                ? '使用设备时，这里会自动显示你的应用使用记录'
                : '点击上方按钮开启监控，以记录你的健康使用情况'}
            </p>
          </div>
        )}
        {timeline.map((item, i) => (
          <div key={`${item.time}-${i}`} className="flex items-center gap-3 py-2.5 border-b border-gray-300/30 last:border-b-0">
            <span className="text-[12px] text-gray-400 w-11 shrink-0">{item.time}</span>
            <div className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: item.color }} />
            <span className="text-[14px] text-gray-900 flex-1 truncate">{item.app}</span>
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
              {level === 'ACTION' ? '需要立即休息' :
               level === 'COGNITION' ? '认知疲劳升高' : '轻度疲劳'}
            </p>
            <p className="text-[12px] text-gray-500">疲劳指数 {(score * 100).toFixed(0)}%</p>
          </div>
          <button onClick={() => navigate('/rest')} className="px-3 py-1.5 bg-primary text-white rounded-full text-[12px] font-medium">
            去休息
          </button>
        </motion.div>
      )}
    </div>
  );
}
