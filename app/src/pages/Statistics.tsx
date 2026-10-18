import { useEffect, useMemo, useState } from 'react';
import { AreaChart, Area, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { ChartNoAxesCombined, ChevronRight, Clock3, Moon, RefreshCw, Shield, Smartphone } from 'lucide-react';
import { Capacitor } from '@capacitor/core';
import { getMonthStats, getTodayActivities, getTodayStats, getWeekStats } from '../backend/storage';
import { NativeFlow } from '../backend/nativeFlow';
import { getAppName } from '../backend/appNames';
import { formatUsageTime, getUsageShares, nonNegativeNumber, type AppUsageShare } from '../utils/statistics';

const PERIODS = [
  { label: '今日', days: 1 },
  { label: '近7天', days: 7 },
  { label: '近30天', days: 30 },
];
const BAR_COLORS = ['#247353', '#367C98', '#8A7550', '#7C7893', '#6D8F78', '#6F8795', '#A38B72', '#9AA79A'];
const CHART_TOOLTIP_STYLE = { backgroundColor: '#fff', border: '1px solid #DFE8DF', borderRadius: 14, fontSize: 12 };

type StatisticsDay = {
  date: string;
  totalScreenTime: number;
  restCount: number;
  interventionCount: number;
};
type NativeHistory = {
  period: number;
  status: 'loading' | 'success' | 'error';
  data: StatisticsDay[];
};
type NativeApps = {
  status: 'loading' | 'success' | 'error';
  data: AppUsageShare[];
};

function DataMessage({
  title,
  description,
  loading = false,
  onRetry,
}: {
  title: string;
  description: string;
  loading?: boolean;
  onRetry?: () => void;
}) {
  return (
    <div className="flex flex-col items-center justify-center px-4 py-8 text-center" role={onRetry ? 'alert' : 'status'}>
      <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-2xl bg-primary/8 text-primary">
        {loading ? <RefreshCw size={20} className="motion-safe:animate-spin" aria-hidden="true" /> : <ChartNoAxesCombined size={21} aria-hidden="true" />}
      </div>
      <p className="text-[14px] font-semibold text-gray-900">{title}</p>
      <p className="mt-1 max-w-[280px] text-[12px] leading-relaxed text-gray-500">{description}</p>
      {onRetry ? (
        <button onClick={onRetry} className="mt-3 inline-flex min-h-11 items-center gap-1.5 rounded-xl bg-primary/8 px-4 text-[13px] font-semibold text-primary">
          <RefreshCw size={14} aria-hidden="true" />重试
        </button>
      ) : null}
    </div>
  );
}

export default function Statistics() {
  const isNative = Capacitor.isNativePlatform();
  const [period, setPeriod] = useState(1);
  const [historyAttempt, setHistoryAttempt] = useState(0);
  const [appsAttempt, setAppsAttempt] = useState(0);
  const [nativeHistory, setNativeHistory] = useState<NativeHistory>({ period: 1, status: 'loading', data: [] });
  const [nativeApps, setNativeApps] = useState<NativeApps>({ status: 'loading', data: [] });

  useEffect(() => {
    if (!isNative) return;
    let active = true;
    setNativeHistory({ period, status: 'loading', data: [] });
    NativeFlow.getStatisticsHistory({ days: PERIODS[period].days }).then(result => {
      if (!active) return;
      setNativeHistory({
        period,
        status: 'success',
        data: result.days.map(day => ({
          date: day.date,
          totalScreenTime: nonNegativeNumber(day.screenTimeSeconds),
          restCount: nonNegativeNumber(day.restCount),
          interventionCount: nonNegativeNumber(day.interventionCount),
        })),
      });
    }).catch(() => {
      if (active) setNativeHistory({ period, status: 'error', data: [] });
    });
    return () => { active = false; };
  }, [isNative, period, historyAttempt]);

  useEffect(() => {
    // The native per-app API is today-only. Never present it as a weekly/monthly breakdown.
    if (!isNative || period !== 0) return;
    let active = true;
    setNativeApps({ status: 'loading', data: [] });
    NativeFlow.getAppUsageList().then(result => {
      if (!active) return;
      setNativeApps({
        status: 'success',
        data: getUsageShares(result.apps.map(app => ({
          id: app.packageName,
          name: getAppName(app.packageName),
          seconds: app.totalTimeSeconds,
        }))),
      });
    }).catch(() => {
      if (active) setNativeApps({ status: 'error', data: [] });
    });
    return () => { active = false; };
  }, [isNative, period, appsAttempt]);

  const historyStatus = isNative
    ? nativeHistory.period === period ? nativeHistory.status : 'loading'
    : 'success';
  const sourceData = useMemo<StatisticsDay[]>(() => {
    if (isNative) return nativeHistory.period === period && nativeHistory.status === 'success' ? nativeHistory.data : [];
    if (period === 0) return [getTodayStats()];
    return period === 2 ? getMonthStats() : getWeekStats();
  }, [isNative, period, nativeHistory]);
  const appDistribution = useMemo(() => {
    if (period !== 0) return [];
    if (isNative) return nativeApps.status === 'success' ? nativeApps.data : [];
    return getUsageShares(getTodayActivities()
      .filter(activity => activity.type === 'screen')
      .map(activity => ({ id: activity.app, name: activity.app, seconds: activity.durationSec })));
  }, [isNative, period, nativeApps]);

  const chartData = sourceData.map(day => ({
    day: day.date.slice(5).replace('-', '/'),
    screenTime: Number((day.totalScreenTime / 60).toFixed(1)),
    rest: day.restCount,
  }));
  const totalScreenSeconds = sourceData.reduce((sum, day) => sum + day.totalScreenTime, 0);
  const totalRest = sourceData.reduce((sum, day) => sum + day.restCount, 0);
  const totalIntervention = sourceData.reduce((sum, day) => sum + day.interventionCount, 0);
  const hasAnyData = totalScreenSeconds > 0 || totalRest > 0 || totalIntervention > 0;
  const avgSeconds = sourceData.length > 0 ? totalScreenSeconds / sourceData.length : 0;
  const periodLabel = PERIODS[period].label;
  const ready = historyStatus === 'success';
  const retryHistory = () => {
    setNativeHistory({ period, status: 'loading', data: [] });
    setHistoryAttempt(attempt => attempt + 1);
  };
  const retryApps = () => {
    setNativeApps({ status: 'loading', data: [] });
    setAppsAttempt(attempt => attempt + 1);
  };

  return (
    <div className="flex min-h-dvh flex-col overflow-y-auto px-5 pt-7 pb-28 no-scrollbar">
      <header className="mb-6">
        <p className="mb-1.5 text-[11px] font-semibold tracking-[0.16em] text-primary">YOUR EVERYDAY, IN BALANCE</p>
        <h1 className="text-[28px] font-bold tracking-tight text-gray-900">使用统计</h1>
        <p className="mt-2 text-[13px] leading-relaxed text-gray-500">看见使用习惯，也看见每一次主动停下。</p>
      </header>

      <div className="mb-5 flex rounded-2xl bg-gray-100 p-1.5" role="group" aria-label="统计时间范围">
        {PERIODS.map((item, index) => (
          <button
            key={item.days}
            onClick={() => setPeriod(index)}
            aria-pressed={period === index}
            className={`min-h-11 flex-1 rounded-xl text-[13px] font-semibold transition-colors ${period === index ? 'bg-white text-primary shadow-sm' : 'text-gray-500'}`}
          >
            {item.label}
          </button>
        ))}
      </div>

      <section className="card-lg mb-4 p-5" aria-label={`${periodLabel}使用概览`} aria-busy={historyStatus === 'loading'}>
        <div className="mb-5 flex items-center justify-between gap-3">
          <span className="text-[13px] font-semibold text-gray-700">{periodLabel} · 目标应用时间</span>
          <span className="rounded-full bg-primary/8 px-2.5 py-1 text-[10px] font-semibold text-primary">{isNative ? '设备本机记录' : '浏览器本地记录'}</span>
        </div>
        {historyStatus === 'loading' ? (
          <DataMessage loading title="正在读取本机统计" description="稍等一下，正在整理使用与休息记录。" />
        ) : historyStatus === 'error' ? (
          <DataMessage title="暂时无法读取统计" description="已保存的本机记录不会被清除，请稍后重试。" onRetry={retryHistory} />
        ) : (
          <>
            <p className="text-[36px] font-semibold leading-tight tracking-tight text-primary" data-testid="statistics-total-time">{formatUsageTime(totalScreenSeconds)}</p>
            <p className="mt-1.5 text-[12px] text-gray-500">{period === 0 ? '今日零点至现在' : `截至今日的最近 ${PERIODS[period].days} 天`}</p>
            <div className="mt-5 grid grid-cols-2 gap-3 border-t border-gray-100 pt-4">
              <div className="flex items-center gap-2.5">
                <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-secondary/8 text-secondary"><Moon size={17} aria-hidden="true" /></span>
                <div><p className="text-[11px] text-gray-500">完成休息</p><p className="text-[18px] font-semibold text-gray-900">{totalRest}<span className="ml-1 text-[11px] font-normal text-gray-500">次</span></p></div>
              </div>
              <div className="flex items-center gap-2.5">
                <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary/8 text-primary"><Shield size={17} aria-hidden="true" /></span>
                <div><p className="text-[11px] text-gray-500">干预记录</p><p className="text-[18px] font-semibold text-gray-900">{totalIntervention}<span className="ml-1 text-[11px] font-normal text-gray-500">次</span></p></div>
              </div>
            </div>
            {!hasAnyData ? <p className="mt-4 rounded-xl bg-gray-50 p-3 text-[12px] leading-relaxed text-gray-500">这段时间还没有记录。开启保护后，目标应用的使用与休息会慢慢出现在这里。</p> : null}
          </>
        )}
      </section>

      {ready && hasAnyData && period !== 0 ? (
        <section className="card mb-4 p-5" aria-label="目标应用时间趋势">
          <div className="mb-4 flex items-center justify-between"><h2 className="text-[15px] font-semibold text-gray-900">使用时间趋势</h2><span className="text-[11px] text-gray-500">分钟 / 天</span></div>
          <div className="h-44" role="img" aria-label={`${periodLabel}目标应用时间趋势，日均${formatUsageTime(avgSeconds)}`}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 10, right: 5, left: 5, bottom: 0 }}>
                <defs><linearGradient id="statistics-usage-gradient" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#247353" stopOpacity={0.2} /><stop offset="100%" stopColor="#247353" stopOpacity={0.01} /></linearGradient></defs>
                <XAxis dataKey="day" tick={{ fontSize: 10, fill: '#68776F' }} axisLine={false} tickLine={false} minTickGap={20} />
                <YAxis hide />
                <Tooltip contentStyle={CHART_TOOLTIP_STYLE} formatter={(value) => [`${value} 分钟`, '目标应用时间']} />
                <Area type="monotone" dataKey="screenTime" stroke="#247353" strokeWidth={2.5} fill="url(#statistics-usage-gradient)" isAnimationActive={false} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          <p className="mt-3 flex items-center gap-1.5 text-[12px] text-gray-500"><Clock3 size={13} aria-hidden="true" />日均 {formatUsageTime(avgSeconds)}</p>
        </section>
      ) : null}

      <section className="card mb-4 p-5" aria-label="应用使用分布" aria-busy={period === 0 && isNative && nativeApps.status === 'loading'}>
        <div className="mb-1 flex items-center gap-2"><Smartphone size={17} className="text-primary" aria-hidden="true" /><h2 className="text-[15px] font-semibold text-gray-900">应用使用分布</h2></div>
        <p className="text-[12px] leading-relaxed text-gray-500">{period === 0 ? '今日已选目标应用 · 按使用时长占比' : '当前仅提供今日的逐应用明细。'}</p>
        {period !== 0 ? (
          <div className="mt-4 rounded-2xl bg-gray-50 p-4">
            <p className="text-[12px] leading-relaxed text-gray-500">近7天与近30天可查看总时长与休息趋势；逐应用明细请切换到今日。</p>
            <button onClick={() => setPeriod(0)} className="mt-2 inline-flex min-h-11 items-center gap-1 text-[13px] font-semibold text-primary">查看今日分布<ChevronRight size={15} aria-hidden="true" /></button>
          </div>
        ) : isNative && nativeApps.status === 'loading' ? (
          <DataMessage loading title="正在读取应用明细" description="仅统计你选择的目标应用。" />
        ) : isNative && nativeApps.status === 'error' ? (
          <DataMessage title="暂时无法读取应用明细" description="这不会影响已保存的记录。请稍后再试一次。" onRetry={retryApps} />
        ) : appDistribution.length === 0 ? (
          <DataMessage title="今天还没有应用使用记录" description="使用已选目标应用后，这里会显示时长与占比。" />
        ) : (
          <ul className="mt-5 space-y-4" aria-label="今日应用占比">
            {appDistribution.map((app, index) => (
              <li key={app.id} data-testid="app-usage-share">
                <div className="mb-1.5 flex items-baseline justify-between gap-3"><span className="min-w-0 truncate text-[13px] font-medium text-gray-900" title={app.name}>{app.name}</span><span className="shrink-0 text-[11px] text-gray-500">{formatUsageTime(app.seconds)}<span className="ml-2 inline-block w-9 text-right font-semibold text-gray-700">{app.percentage}%</span></span></div>
                <div className="h-1.5 overflow-hidden rounded-full bg-gray-100" aria-hidden="true"><div className="h-full rounded-full" style={{ width: `${app.percentage}%`, backgroundColor: BAR_COLORS[index % BAR_COLORS.length] }} /></div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {ready && hasAnyData && period !== 0 ? (
        <section className="card mb-4 p-5" aria-label="休息完成情况">
          <div className="mb-4 flex items-center justify-between"><h2 className="text-[15px] font-semibold text-gray-900">休息的节奏</h2><span className="text-[11px] text-gray-500">次 / 天</span></div>
          {totalRest === 0 ? <p className="py-6 text-center text-[12px] text-gray-500">这段时间还没有完成休息的记录。</p> : (
            <div className="h-32" role="img" aria-label={`${periodLabel}共完成${totalRest}次休息`}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData} margin={{ top: 4, right: 5, left: 5, bottom: 0 }}>
                  <XAxis dataKey="day" tick={{ fontSize: 10, fill: '#68776F' }} axisLine={false} tickLine={false} minTickGap={20} />
                  <YAxis hide />
                  <Tooltip contentStyle={CHART_TOOLTIP_STYLE} formatter={(value) => [`${value} 次`, '完成休息']} />
                  <Bar dataKey="rest" fill="#367C98" radius={[5, 5, 0, 0]} maxBarSize={20} isAnimationActive={false} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </section>
      ) : null}

      <p className="px-2 py-2 text-center text-[11px] leading-relaxed text-gray-500">记录只保存在本机。数字用于了解习惯，不是健康评分。</p>
    </div>
  );
}
