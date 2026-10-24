import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { motion } from 'framer-motion';
import { Moon, Clock, Shield, ChevronRight, Pause, Play, ArrowUpRight, Smartphone } from 'lucide-react';
import { useStore } from '../hooks/useStore';
import { DailyReflection, getTodayActivities, getTodayReflection, saveTodayReflection } from '../backend/storage';
import { Capacitor } from '@capacitor/core';
import { NativeFlow, NativeProtectionStatus } from '../backend/nativeFlow';
import { syncNativeMonitoring } from '../backend/nativeMonitoring';
import { useNativePermissions } from '../hooks/useNativePermissions';
import { getProtectionViewModel, formatRemainingTime, formatCountdown } from '../utils/protectionStatus';
import { formatUsageTime, nonNegativeNumber } from '../utils/statistics';
import {
  UNSUPPORTED_DEVICE_DETAIL,
  UNSUPPORTED_DEVICE_MESSAGE,
} from '../utils/backgroundStability';

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

const NATIVE_STATUS_LABELS: Record<NativeProtectionStatus['status'], string> = {
  PAUSED: '已暂停',
  UNCONFIGURED: '未配置',
  UNSUPPORTED: '设备不支持',
  STARTING_OR_UNCONFIRMED: '正在确认',
  ACTIVE: '已开启',
  DEGRADED: '保护降级',
};

const NATIVE_STATUS_CLASSES: Record<NativeProtectionStatus['status'], string> = {
  PAUSED: 'bg-gray-100 text-gray-500',
  UNCONFIGURED: 'bg-amber-100 text-amber-700',
  UNSUPPORTED: 'bg-red-100 text-red-700',
  STARTING_OR_UNCONFIRMED: 'bg-amber-100 text-amber-700',
  ACTIVE: 'bg-green-100 text-green-700',
  DEGRADED: 'bg-red-100 text-red-700',
};

function nativeStatusDisplayKey(
  status: NativeProtectionStatus | null
): NativeProtectionStatus['status'] | null {
  if (!status) return null;
  // The core boolean is authoritative. A malformed or racing ACTIVE response
  // must never make the UI claim that protection is currently operational.
  if (status.status === 'ACTIVE' && status.coreProtectionOperational !== true) {
    return 'STARTING_OR_UNCONFIRMED';
  }
  return status.status;
}

function nativeStatusReason(reason: string) {
  const labels: Record<string, string> = {
    USAGE_ACCESS_MISSING: '使用情况访问权限已失效',
    OVERLAY_MISSING: '悬浮窗权限已失效',
    SERVICE_NOT_RUNNING: '保护服务当前未运行，保护尚未生效',
    MONITOR_THREAD_NOT_ALIVE: '保护监控线程当前不可用，保护尚未生效',
    HEARTBEAT_STALE: '保护服务心跳已过期，保护尚未生效',
    ACCESSIBILITY_MISSING: '无障碍强阻断不可用，仅保留核心悬浮窗保护',
    ACCESSIBILITY_SERVICE_NOT_CONNECTED: '无障碍强阻断尚未运行，核心悬浮窗保护仍有效',
    MONITORING_DISABLED: '保护监控当前已暂停',
    NO_TARGETS: '尚未配置受限应用',
    NOT_CONFIGURED: '尚未完成保护配置',
    TARGET_SET_MISMATCH: '受限应用配置与保护服务不一致，当前保护未生效，请重新开启保护以同步设置',
    MONITORING_STATE_MISMATCH: '保护开关状态与系统服务不一致，当前保护未生效，请重新开启保护',
    LIMIT_MINUTES_MISMATCH: '单次使用时长与保护服务不一致，当前保护未生效，请重新开启保护以同步设置',
    UNSUPPORTED_DEVICE: UNSUPPORTED_DEVICE_MESSAGE,
  };
  return labels[reason] || (reason ? `保护状态：${reason}` : '保护尚未确认');
}


function ProtectionStatusCard({
  now,
  isNative,
  nativeStatus,
  missingPermissions,
  runtimeStatusNeedsAttention,
  missingPermissionLabel,
  currentAppName,
  unsupportedDevice,
}: {
  now: number;
  isNative: boolean;
  nativeStatus: NativeProtectionStatus | null;
  missingPermissions: boolean;
  runtimeStatusNeedsAttention: boolean;
  missingPermissionLabel: string;
  currentAppName: string;
  unsupportedDevice: boolean;
}) {
  const navigate = useNavigate();
  const isMonitoring = useStore(s => s.isMonitoring);
  const serviceError = useStore(s => s.serviceError);
  const graceUntil = useStore(s => s.graceUntil);
  const profile = useStore(s => s.profile);
  const setMonitoring = useStore(s => s.setMonitoring);
  const blockState = useStore(s => s.blockState);
  const continuousSessionSeconds = useStore(s => s.continuousSessionSeconds);

  const handleRetry = () => setMonitoring(true);

  const noTargetApps = (profile.targetApps || []).length === 0;
  const localProtectionActive = isMonitoring && !noTargetApps && !unsupportedDevice;
  const protectionActive = isNative
    ? nativeStatus?.coreProtectionOperational === true
    : localProtectionActive;
  const nativeUnsupported = nativeStatus?.status === 'UNSUPPORTED';
  const displayedUnsupported = unsupportedDevice || nativeUnsupported;
  const nativeStatusKey = nativeStatusDisplayKey(nativeStatus);

  const vm = getProtectionViewModel({
    isMonitoring: protectionActive,
    serviceError,
    blockState,
    sessionSeconds: continuousSessionSeconds,
    limitMinutes: profile.sessionLimit,
    graceUntil, now,
    missingPermissions,
    noTargetApps,
    missingPermissionLabel,
    currentAppName,
  });

  const badgeLabel = isNative
    ? nativeStatusKey ? NATIVE_STATUS_LABELS[nativeStatusKey] : '正在确认'
    : unsupportedDevice ? '设备不支持'
      : !isMonitoring ? '已暂停'
      : noTargetApps ? '未配置'
      : '已开启';
  const badgeClass = isNative
    ? nativeStatusKey ? NATIVE_STATUS_CLASSES[nativeStatusKey] : 'bg-amber-100 text-amber-700'
    : unsupportedDevice ? 'bg-red-100 text-red-700'
      : !isMonitoring ? 'bg-gray-100 text-gray-500'
      : noTargetApps ? 'bg-amber-100 text-amber-700'
      : 'bg-green-100 text-green-700';

  return (
    <section className="card-lg p-5 mb-3" aria-label="保护状态">
      <div className="flex items-center justify-between mb-4">
        <h2 className="flex items-center gap-2 text-[15px] font-semibold"><Shield size={17} className="text-primary" aria-hidden="true" />保护状态</h2>
        <span className={`px-3 py-1 rounded-full text-[12px] font-medium ${badgeClass}`}>
          {badgeLabel}
        </span>
      </div>
      {displayedUnsupported && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 mb-3" role="alert">
          <p className="text-[13px] text-red-700 font-medium">{UNSUPPORTED_DEVICE_MESSAGE}</p>
          <p className="text-[12px] text-red-700 mt-1 leading-relaxed">{UNSUPPORTED_DEVICE_DETAIL}</p>
        </div>
      )}
      {vm.hasError && !displayedUnsupported && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 mb-3" role="alert">
          <p className="text-[13px] text-red-700">{vm.errorMessage}</p>
          <div className="flex gap-2 mt-2">
            <button onClick={() => navigate('/permissions')} className="text-[12px] text-red-600 underline">
              检查权限
            </button>
            <button onClick={handleRetry} className="text-[12px] text-red-600 underline">
              重试开启
            </button>
          </div>
        </div>
      )}
      {(missingPermissions || runtimeStatusNeedsAttention) && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 mb-3" role="alert">
          <p className="text-[13px] text-red-700">{missingPermissionLabel}</p>
          {missingPermissions && (
            <div className="flex gap-2 mt-2">
              <button onClick={() => navigate('/permissions')} className="text-[12px] text-red-600 underline">
                去授权
              </button>
            </div>
          )}
        </div>
      )}
      {isNative && nativeStatus?.strongBlockingRequested && !nativeStatus.strongBlockingOperational && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-3" role="alert">
          <p className="text-[13px] text-amber-700">{nativeStatusReason(nativeStatus.strongBlockingDegradedReason)}</p>
        </div>
      )}
      <div className="space-y-3 text-[13px]">
        <div className="flex justify-between">
          <span className="text-gray-500">当前状态</span>
          <span className="font-medium">{protectionActive ? vm.stateLabel : displayedUnsupported ? '此设备暂不可用' : isNative && nativeStatusKey === null ? '等待设备确认' : '当前未开启保护'}</span>
        </div>
        {protectionActive && blockState !== 'BLOCKED' && blockState !== 'RESTING' && blockState !== 'GRACE' && (
          <div className="flex justify-between">
            <span className="text-gray-500">连续使用</span>
            <span className="font-medium">{vm.sessionMinutes} 分钟</span>
          </div>
        )}
        {protectionActive && blockState !== 'BLOCKED' && blockState !== 'RESTING' && blockState !== 'GRACE' && !vm.nextThreshold.blocked && (
          <div className="flex justify-between">
            <span className="text-gray-500">下一阶段</span>
            <span className="font-medium text-primary">{formatRemainingTime(vm.nextThreshold.remainingSeconds)} 后{vm.nextThreshold.label}</span>
          </div>
        )}
        {protectionActive && blockState === 'BLOCKED' && (
          <div className="flex justify-between">
            <span className="text-gray-500">操作</span>
            <button onClick={() => navigate('/rest')} className="text-[13px] font-medium text-primary underline">
              开始休息
            </button>
          </div>
        )}
        {protectionActive && blockState === 'GRACE' && vm.graceRemainingSeconds > 0 && (
          <div className="flex justify-between">
            <span className="text-gray-500">访问窗口剩余</span>
            <span className="font-medium text-primary">{formatCountdown(vm.graceRemainingSeconds)}</span>
          </div>
        )}
        {protectionActive && currentAppName && (
          <div className="flex justify-between">
            <span className="text-gray-500">当前应用</span>
            <span className="font-medium">{currentAppName}</span>
          </div>
        )}
        {noTargetApps && (
          <div className="flex justify-between items-center">
            <span className="text-gray-500">受限应用</span>
            <button onClick={() => navigate('/target-apps')} className="text-[13px] text-primary underline">
              选择受限应用
            </button>
          </div>
        )}
      </div>
    </section>
  );
}

export default function Dashboard() {
  const navigate = useNavigate();
  const profile = useStore(s => s.profile);
  const stats = useStore(s => s.todayStats);
  const isMonitoring = useStore(s => s.isMonitoring);
  const setMonitoring = useStore(s => s.setMonitoring);
  const setServiceError = useStore(s => s.setServiceError);
  const level = useStore(s => s.fatigueLevel);
  const currentAppName = useStore(s => s.currentAppName);
  const graceUntil = useStore(s => s.graceUntil);
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
  const [summaryLoaded, setSummaryLoaded] = useState(!Capacitor.isNativePlatform());
  const [nativeScreenSeconds, setNativeScreenSeconds] = useState<number | null>(null);
  const [nativeTimeError, setNativeTimeError] = useState(false);
  const [toggling, setToggling] = useState(false);
  const { isNative, permissions } = useNativePermissions();
  const [nativeProtectionStatus, setNativeProtectionStatus] = useState<NativeProtectionStatus | null>(null);
  const noTargetApps = profile.targetApps.length === 0;
  const unsupportedDevice = isNative && (
    permissions.unsupportedDevice === true
    || nativeProtectionStatus?.status === 'UNSUPPORTED'
  );
  const localProtectionActive = isMonitoring && !noTargetApps && !unsupportedDevice;
  const protectionActive = isNative
    ? nativeProtectionStatus?.coreProtectionOperational === true
    : localProtectionActive;
  const nativeMonitoringEnabled = nativeProtectionStatus?.monitoringEnabled ?? isMonitoring;
  const missingPermission = isNative && nativeProtectionStatus
    ? nativeProtectionStatus.reason === 'USAGE_ACCESS_MISSING'
      || nativeProtectionStatus.reason === 'OVERLAY_MISSING'
    : false;
  const missingCritical = isNative && !nativeProtectionStatus && localProtectionActive && (
    !permissions.hasUsageStats
    || !permissions.hasOverlay
  );
  const missingCriticalLabel = !permissions.hasUsageStats
    ? '使用情况访问权限已失效'
    : !permissions.hasOverlay
      ? '悬浮窗权限已失效'
      : '保护权限已失效';

  useEffect(() => {
    if (!isNative) {
      setNativeProtectionStatus(null);
      return;
    }
    let active = true;
    const refresh = () => {
      NativeFlow.getProtectionStatus().then(status => {
        if (active) setNativeProtectionStatus(status);
      }).catch(() => {
        if (active) setNativeProtectionStatus(null);
      });
    };
    refresh();
    const timer = window.setInterval(refresh, 2_000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [isNative]);

  const runtimeStatusNeedsAttention = isNative
    && nativeProtectionStatus !== null
    && !nativeProtectionStatus.coreProtectionOperational
    && nativeProtectionStatus.status !== 'PAUSED'
    && nativeProtectionStatus.status !== 'UNCONFIGURED'
    && nativeProtectionStatus.status !== 'UNSUPPORTED';
  const statusIssueLabel = nativeProtectionStatus
    ? nativeStatusReason(nativeProtectionStatus.reason)
    : missingCriticalLabel;
  const monitoringIntent = isNative ? nativeMonitoringEnabled : isMonitoring;
  const nativeStatusKey = nativeStatusDisplayKey(nativeProtectionStatus);

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    let active = true;
    const refreshSummary = () => {
      if (Capacitor.isNativePlatform()) {
        NativeFlow.getUsageStats().then(result => {
          if (!active) return;
          setNativeScreenSeconds(nonNegativeNumber(result.screenTimeSeconds));
          setNativeTimeError(false);
        }).catch(() => {
          if (!active) return;
          setNativeScreenSeconds(null);
          setNativeTimeError(true);
        });
        NativeFlow.getDashboardSummary().then(result => {
          if (!active) return;
          setSummary(result);
          setSummaryError(false);
          setSummaryLoaded(true);
        }).catch(() => {
          if (active) {
            setSummaryError(true);
            setSummaryLoaded(false);
          }
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
    if (noTargetApps || unsupportedDevice) return;
    const nextMonitoring = !monitoringIntent;
    setToggling(true);
    try {
      // Native status is authoritative. If the local Zustand intent has
      // diverged (for example after a rejected start), changing it to the
      // already-current value would not trigger GlobalMonitor's effect.
      // Invoke the native operation explicitly in that case.
      if (isNative && isMonitoring !== monitoringIntent) {
        await syncNativeMonitoring(nextMonitoring, profile.sessionLimit, profile.targetApps);
        setServiceError('');
      } else {
        setMonitoring(nextMonitoring);
        await new Promise(r => setTimeout(r, 300));
      }
    } catch (error) {
      const message = error instanceof Error && error.message
        ? error.message
        : nextMonitoring ? '保护服务启动失败，请检查权限后重试。' : '保护服务停止失败，请重试。';
      setServiceError(message);
      if (isNative && nextMonitoring) setMonitoring(false);
    } finally {
      setToggling(false);
    }
  };

  const goalSeconds = profile.dailyGoal * 60;
  const dailyScreenSeconds = isNative ? nativeScreenSeconds : stats.totalScreenTime;
  const progress = goalSeconds > 0 && dailyScreenSeconds !== null ? Math.min(1, Math.max(0, dailyScreenSeconds / goalSeconds)) : 0;
  const graceSeconds = Math.max(0, Math.ceil((graceUntil - now) / 1000));
  const summaryUnavailable = isNative && (!summaryLoaded || summaryError);
  const displayedRestCount = summaryUnavailable ? '--' : isNative ? summary.restCount : stats.restCount;
  const displayedInterventionCount = summaryUnavailable ? '--' : isNative ? summary.blockCount : stats.interventionCount;

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
          item.type === 'rest' ? '#367C98' :
          item.type === 'intervention' ? '#A77A48' :
          '#247353',
      }));
  }, [stats.totalScreenTime, stats.restCount, stats.interventionCount]);

  return (
    <div className="flex flex-col pb-28 px-5 pt-7 no-scrollbar overflow-y-auto min-h-dvh">
      <header className="flex items-start justify-between gap-3 mb-6">
        <div>
          <p className="text-[11px] text-gray-500 mb-1.5">{new Date(now).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })}</p>
          <h1 className="text-[24px] min-[380px]:text-[28px] font-bold tracking-tight text-gray-900">把时间，留给自己</h1>
          <p className="mt-2 text-[13px] text-gray-500">从一次轻轻停下开始。</p>
        </div>
        <div className="flex shrink-0 flex-col items-end pt-1">
            <div className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-full ${protectionActive ? 'bg-primary/10 text-primary' : 'bg-gray-100 text-gray-700'}`}>
              <div aria-hidden="true" className={`w-1.5 h-1.5 rounded-full ${protectionActive ? 'bg-primary' : 'bg-gray-400'}`} />
              <span className="text-[10px] font-semibold whitespace-nowrap">
                {unsupportedDevice || nativeProtectionStatus?.status === 'UNSUPPORTED'
                  ? '设备不支持'
                  : isNative
                    ? nativeStatusKey
                      ? NATIVE_STATUS_LABELS[nativeStatusKey]
                      : '正在确认'
                    : noTargetApps ? '未配置' : isMonitoring ? '监控中' : '已暂停'}
              </span>
            </div>
            {protectionActive && currentAppName && (
              <span className="max-w-24 truncate text-[10px] text-gray-500 mt-1">当前: {currentAppName}</span>
            )}
        </div>
      </header>

      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="card-lg p-5 mb-4 relative overflow-hidden"
        style={{ background: 'linear-gradient(135deg, #E5F0E6 0%, #F1F5EC 100%)' }}
      >
        <div aria-hidden="true" className="absolute top-0 right-0 w-36 h-36 rounded-full border-[20px] border-white/35 -translate-y-12 translate-x-12" />

        <div className="relative z-10">
          <div className="flex items-start justify-between gap-3 mb-5">
            <div>
              <p className="text-[#41634C] text-[12px] mb-2 font-medium">今日目标应用时间</p>
              <h2 className="text-[32px] min-[380px]:text-[36px] font-semibold tracking-tight leading-none text-[#1E4D38]" data-testid="dashboard-total-time">{dailyScreenSeconds === null ? '--' : formatUsageTime(dailyScreenSeconds)}</h2>
            </div>
            <div className="text-right">
              <p className="text-[#41634C] text-[11px] mb-1">每日目标</p>
              <p className="text-[14px] font-semibold text-[#1E4D38]">{formatGoal(profile.dailyGoal)}</p>
            </div>
          </div>

          <div className="w-full h-1.5 bg-[#247353]/10 rounded-full overflow-hidden mb-3" role="progressbar" aria-label="今日目标应用使用时间占每日目标" aria-valuemin={0} aria-valuemax={100} aria-valuenow={dailyScreenSeconds === null ? undefined : Math.round(progress * 100)} aria-valuetext={dailyScreenSeconds === null ? '设备记录尚未读取' : undefined}>
            <motion.div
              initial={{ width: 0 }}
              animate={{ width: `${progress * 100}%` }}
              transition={{ duration: 0.6, ease: 'easeOut' }}
              className={`h-full rounded-full ${progress >= 1 ? 'bg-[#A77A48]' : 'bg-[#247353]'}`}
            />
          </div>

          <div className="flex items-center justify-between gap-3 text-[11px] text-[#41634C]">
            <span>{dailyScreenSeconds === null ? nativeTimeError ? '暂时无法读取设备记录' : '正在读取设备记录' : progress >= 1 ? '已达到你设定的每日目标' : '按照自己的节奏，慢慢来'}</span>
            <span className="flex items-center gap-1 shrink-0"><Smartphone size={12} aria-hidden="true" />{profile.targetApps.length} 个目标应用</span>
          </div>
        </div>
      </motion.div>

      <div className="grid grid-cols-3 gap-2.5 mb-5">
        <div className="card p-3.5">
          <Moon size={16} className="text-secondary mb-2" aria-hidden="true" />
          <p className="text-[11px] text-gray-500">完成休息</p>
          <p className="text-[22px] font-semibold mt-0.5 text-gray-900">{displayedRestCount}<span className="ml-1 text-[10px] font-normal text-gray-500">次</span></p>
        </div>
        <div className="card p-3.5">
          <Shield size={16} className="text-primary mb-2" aria-hidden="true" />
          <p className="text-[11px] text-gray-500">今日阻断</p>
          <p className="text-[22px] font-semibold mt-0.5 text-gray-900">{displayedInterventionCount}<span className="ml-1 text-[10px] font-normal text-gray-500">次</span></p>
        </div>
        <div className="card p-3.5">
          <Clock size={16} className="text-[#8A7550] mb-2" aria-hidden="true" />
          <p className="text-[11px] text-gray-500">访问窗口</p>
          <p className="text-[22px] font-semibold mt-0.5 text-gray-900 tabular-nums">
            {protectionActive && graceSeconds > 0 ? `${Math.floor(graceSeconds / 60)}:${String(graceSeconds % 60).padStart(2, '0')}` : '--'}
          </p>
        </div>
      </div>

      {summaryError ? <p className="-mt-2 mb-4 text-[12px] text-gray-500" role="status">今日计数暂未读取成功，稍后会自动重试。</p> : null}

      <ProtectionStatusCard
        now={now}
        isNative={isNative}
        nativeStatus={nativeProtectionStatus}
        missingPermissions={missingCritical || missingPermission}
        runtimeStatusNeedsAttention={runtimeStatusNeedsAttention}
        missingPermissionLabel={statusIssueLabel}
        currentAppName={currentAppName}
        unsupportedDevice={unsupportedDevice}
      />

      <button
        onClick={handleToggleMonitoring}
        disabled={toggling || noTargetApps || unsupportedDevice}
        className={`w-full min-h-12 flex items-center justify-center gap-2 rounded-2xl px-3 py-3 text-[13px] font-semibold transition-colors mb-5 ${
          unsupportedDevice || noTargetApps ? 'bg-gray-100 text-gray-500' : monitoringIntent ? 'bg-white border border-gray-200 text-gray-700' : 'bg-primary text-white'
        }`}
        aria-label={unsupportedDevice ? UNSUPPORTED_DEVICE_MESSAGE : noTargetApps ? '请先选择受限应用' : monitoringIntent ? '暂停保护' : '开启保护'}
        aria-busy={toggling}
      >
        {monitoringIntent ? <Pause size={15} aria-hidden="true" /> : <Play size={15} aria-hidden="true" />}
        {unsupportedDevice ? UNSUPPORTED_DEVICE_MESSAGE : noTargetApps ? '请先选择受限应用' : toggling ? (monitoringIntent ? '正在暂停...' : '正在开启...') : (monitoringIntent ? '暂停保护' : '开启保护')}
      </button>

      <div className="mb-5">
        <motion.button
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          whileTap={{ scale: 0.98 }}
          onClick={() => navigate('/rest')}
          className="w-full card flex items-center gap-3 p-4 border border-secondary/15"
        >
          <div className="w-11 h-11 rounded-2xl bg-secondary/10 flex items-center justify-center shrink-0">
            <Moon size={20} className="text-secondary" aria-hidden="true" />
          </div>
          <div className="text-left">
            <p className="text-[14px] font-semibold text-gray-900">开始休息</p>
            <p className="text-[12px] text-gray-500 mt-0.5">
              {level === 'NONE' ? `${Math.round(profile.restDuration / 60)} 分钟，给自己一个小小的空隙` : level === 'ACTION' ? '连续使用已较久，暂停一下吧' : level === 'COGNITION' ? '已达到连续使用限额，适合休息一下' : '已接近连续使用限额，可以准备休息'}
            </p>
          </div>
          <ArrowUpRight size={19} className="ml-auto text-secondary shrink-0" aria-hidden="true" />
        </motion.button>
      </div>

      <section className="card p-5 mb-4" data-testid="daily-reflection" aria-labelledby="reflection-heading">
        <h2 id="reflection-heading" className="text-[15px] font-semibold text-gray-900">今天的提醒，对你有帮助吗？</h2>
        <p className="text-[12px] leading-relaxed text-gray-500 mt-1.5">没有标准答案。记录今天的感受，可修改，只留在本机。</p>
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
              aria-pressed={reflection === value}
              className={`min-h-11 rounded-xl py-2.5 text-[12px] font-medium border transition-colors ${
                reflection === value
                  ? 'bg-primary text-white border-primary'
                  : 'bg-white text-gray-600 border-gray-200'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        {reflectionError && <p className="text-[12px] text-error mt-2" role="alert">{reflectionError}</p>}
      </section>


      <div className="flex items-center justify-between mb-3 mt-2">
        <h2 className="text-[15px] font-semibold text-gray-900">今日活动</h2>
        <button onClick={() => navigate('/stats')} className="min-h-11 rounded-lg text-[12px] text-primary font-semibold flex items-center gap-0.5">
          查看统计 <ChevronRight size={14} aria-hidden="true" />
        </button>
      </div>

      <div className="card p-4 mb-4">
        {isNative ? (
          <div className="flex items-center gap-3 py-3">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-primary/8"><Clock size={20} className="text-primary" aria-hidden="true" /></div>
            <div><p className="text-[13px] font-medium text-gray-900">把变化，看得更清楚</p><p className="mt-1 text-[12px] leading-relaxed text-gray-500">在统计页查看设备记录的使用时长与休息次数。</p></div>
          </div>
        ) : timeline.length === 0 ? (
          <div className="flex flex-col items-center py-7">
            <div className="relative mb-4">
              <div className="w-16 h-16 rounded-3xl bg-primary/8 flex items-center justify-center">
                <Clock size={26} className="text-primary" aria-hidden="true" />
              </div>
              {protectionActive && (
                <div className="absolute -bottom-1 -right-1 w-7 h-7 rounded-full bg-primary/10 flex items-center justify-center">
                  <div className="w-2 h-2 rounded-full bg-primary" />
                </div>
              )}
            </div>
            <p className="text-[14px] font-medium text-gray-700 mb-1">
              {protectionActive ? '从今天，积累一点改变' : '还没有今日活动'}
            </p>
            <p className="text-[12px] text-gray-500 text-center leading-relaxed max-w-[250px]">
              {protectionActive
                ? '这里会记录浏览器预览中的使用与休息活动。'
                : '开启保护后，使用与休息的记录会出现在这里。'}
            </p>
          </div>
        ) : timeline.map((item, i) => (
          <div key={`${item.time}-${i}`} className="flex items-center gap-3 py-3 border-b border-gray-100 last:border-b-0">
            <span className="text-[11px] text-gray-500 w-11 shrink-0 tabular-nums">{item.time}</span>
            <div className="w-1.5 h-1.5 rounded-full shrink-0" style={{ backgroundColor: item.color }} aria-hidden="true" />
            <span className="text-[14px] text-gray-900 flex-1 truncate">{item.app}</span>
            <span className="text-[12px] text-gray-500">{item.duration}</span>
          </div>
        ))}
      </div>

      {protectionActive && level !== 'NONE' && (
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
              {level === 'ACTION' ? '适合暂停，休息一下' :
               level === 'COGNITION' ? '已达到连续使用限额' : '快到连续使用限额了'}
            </p>
            <p className="text-[12px] text-gray-500">提醒仅根据使用时长，不代表身体疲劳检测。</p>
          </div>
          <button onClick={() => navigate('/rest')} className="px-3 py-1.5 bg-primary text-white rounded-full text-[12px] font-medium">
            去休息
          </button>
        </motion.div>
      )}
    </div>
  );
}
