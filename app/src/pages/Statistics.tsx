import { useEffect, useMemo, useState } from 'react';
import { AreaChart, Area, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { Capacitor } from '@capacitor/core';
import { getAppDistribution, getMonthStats, getTodayStats, getWeekStats } from '../backend/storage';
import { NativeFlow } from '../backend/nativeFlow';
import { DEFAULT_TARGET_APPS, getAppName } from '../backend/appNames';
import { useStore } from '../hooks/useStore';

const PERIOD_TABS = ['今日', '本周', '本月'];
const barColors = ['#4CAF50', '#2196F3', '#FF9800', '#9C27B0', '#F44336', '#607D8B', '#795548', '#009688'];

export default function Statistics() {
  const [period, setPeriod] = useState(1);
  const markStatsViewed = useStore(s => s.markStatsViewed);
  const [nativeApps, setNativeApps] = useState<Array<{ name: string; value: number }>>([]);

  useEffect(() => {
    const today = new Date().toISOString().split('T')[0];
    const lastView = localStorage.getItem('last_stats_view_date');
    if (lastView !== today) {
      markStatsViewed();
      localStorage.setItem('last_stats_view_date', today);
    }
  }, [markStatsViewed]);

  useEffect(() => {
    if (!Capacitor.isNativePlatform() || period !== 0) return;
    NativeFlow.getAppUsageList().then(result => {
      const targetAppsList = result.apps.filter(a => DEFAULT_TARGET_APPS.includes(a.packageName));
      const total = targetAppsList.reduce((sum, a) => sum + a.totalTimeSeconds, 0);
      if (total === 0) {
        setNativeApps([]);
        return;
      }
      const mapped = targetAppsList
        .map(a => ({
          name: getAppName(a.packageName),
          value: Math.round((a.totalTimeSeconds / total) * 100),
        }))
        .sort((a, b) => b.value - a.value)
        .slice(0, 8);

      const sumVal = mapped.reduce((sum, item) => sum + item.value, 0);
      if (sumVal > 0 && sumVal !== 100 && mapped.length > 0) {
        mapped[0].value += (100 - sumVal);
      }
      setNativeApps(mapped);
    }).catch(() => {});
  }, [period]);

  const sourceData = useMemo(() => {
    if (period === 0) return [getTodayStats()];
    if (period === 2) return getMonthStats();
    return getWeekStats();
  }, [period]);

  const chartData = sourceData.map(d => ({
    day: d.date.slice(5),
    screenTime: Math.round(d.totalScreenTime / 60),
    rest: d.restCount,
  }));

  const useNative = Capacitor.isNativePlatform() && nativeApps.length > 0;

  const appDistribution = useMemo(() => {
    if (useNative) return nativeApps;
    const dist = getAppDistribution();
    if (dist.length === 0) return [{ name: '暂无数据', value: 100 }];
    return dist.slice(0, 8);
  }, [period, useNative, nativeApps]);

  const maxAppValue = Math.max(...appDistribution.map(d => d.value), 1);

  const totalScreenSeconds = sourceData.reduce((a, b) => a + b.totalScreenTime, 0);
  const totalRest = sourceData.reduce((a, b) => a + b.restCount, 0);
  const totalIntervention = sourceData.reduce((a, b) => a + b.interventionCount, 0);
  const avgMinutes = sourceData.length > 0 ? Math.round(totalScreenSeconds / sourceData.length / 60) : 0;

  return (
    <div className="flex flex-col pb-24 px-5 pt-6 no-scrollbar overflow-y-auto min-h-dvh">
      <h1 className="text-[24px] font-bold text-gray-900 mb-6">统计</h1>

      <div className="flex bg-gray-100 rounded-xl p-1 mb-6">
        {PERIOD_TABS.map((tab, i) => (
          <button
            key={i}
            onClick={() => setPeriod(i)}
            className={`flex-1 py-2 rounded-lg text-[14px] font-medium transition-all ${
              period === i ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Screen Time Trend */}
      <div className="card p-5 mb-4">
        <h3 className="text-[16px] font-bold text-gray-900 mb-1">目标应用时间趋势</h3>
        <p className="text-[12px] text-gray-500 mb-4">
          {period === 0 ? '今日（分钟）' : `最近 ${sourceData.length} 天（分钟）`}
        </p>
        {period === 0 ? (
          <div className="flex items-center gap-6 h-44 justify-center">
            <div className="text-center">
              <p className="text-[36px] font-bold text-primary">{chartData[0]?.screenTime || 0}</p>
              <p className="text-[13px] text-gray-500 mt-1">分钟目标应用时间</p>
            </div>
            <div className="w-px h-16 bg-gray-200" />
            <div className="flex flex-col gap-3">
              <div className="flex items-center gap-2">
                <div className="w-2.5 h-2.5 rounded-full bg-secondary" />
                <span className="text-[12px] text-gray-500">休息 {chartData[0]?.rest || 0} 次</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-2.5 h-2.5 rounded-full bg-accent" />
                <span className="text-[12px] text-gray-500">
                  干预 {sourceData[0]?.interventionCount || 0} 次
                </span>
              </div>
            </div>
          </div>
        ) : (
          <div className="h-44">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData}>
                <defs>
                  <linearGradient id="grad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#4CAF50" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#4CAF50" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="day" tick={{ fontSize: 11, fill: '#9E9E9E' }} axisLine={false} tickLine={false} />
                <YAxis hide />
                <Tooltip contentStyle={{ backgroundColor: '#fff', border: '1px solid #E0E0E0', borderRadius: 12, fontSize: 12 }} />
                <Area type="monotone" dataKey="screenTime" stroke="#4CAF50" strokeWidth={2.5} fill="url(#grad)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      {/* App Usage Distribution - Horizontal bars instead of pie */}
      <div className="card p-5 mb-4">
        <h3 className="text-[16px] font-bold text-gray-900 mb-1">应用使用分布</h3>
        <p className="text-[12px] text-gray-500 mb-4">
          {useNative ? '设备实时数据' : '应用占比'}
        </p>
        {appDistribution.length === 0 || appDistribution[0].value === 0 ? (
          <div className="flex items-center justify-center h-24">
            <p className="text-[14px] text-gray-400">暂无使用数据</p>
          </div>
        ) : (
          <div className="flex flex-col gap-2.5">
            {appDistribution.map((app, i) => (
              <div key={i} className="flex items-center gap-2">
                <span className="text-[12px] text-gray-600 w-16 shrink-0 truncate" title={app.name}>
                  {app.name}
                </span>
                <div className="flex-1 h-5 bg-gray-100 rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full transition-all duration-500"
                    style={{
                      width: `${Math.max((app.value / maxAppValue) * 100, 2)}%`,
                      backgroundColor: barColors[i % barColors.length],
                    }}
                  />
                </div>
                <span className="text-[12px] font-medium text-gray-700 w-10 text-right">{app.value}%</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Rest Completion */}
      <div className="card p-5 mb-4">
        <h3 className="text-[16px] font-bold text-gray-900 mb-1">休息完成情况</h3>
        <p className="text-[12px] text-gray-500 mb-4">当前周期</p>
        {period === 0 ? (
          <div className="flex items-center justify-center h-28">
            <div className="text-center">
              <p className="text-[36px] font-bold text-secondary">{chartData[0]?.rest || 0}</p>
              <p className="text-[13px] text-gray-500 mt-1">次休息</p>
            </div>
          </div>
        ) : (
          <div className="h-36">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData}>
                <XAxis dataKey="day" tick={{ fontSize: 11, fill: '#9E9E9E' }} axisLine={false} tickLine={false} />
                <YAxis hide />
                <Tooltip contentStyle={{ backgroundColor: '#fff', border: '1px solid #E0E0E0', borderRadius: 12, fontSize: 12 }} />
                <Bar dataKey="rest" fill="#2196F3" radius={[6, 6, 0, 0]} barSize={24} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      {/* Detail Data */}
      <div className="card p-4">
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">详细数据</h3>
        {[
          { label: '总目标应用时间', value: totalScreenSeconds >= 3600 ? `${(totalScreenSeconds / 3600).toFixed(1)}h` : `${Math.round(totalScreenSeconds / 60)}分`, color: '#4CAF50' },
          { label: '总休息次数', value: `${totalRest}次`, color: '#2196F3' },
          { label: '总干预次数', value: `${totalIntervention}次`, color: '#F44336' },
          { label: '日均目标应用时间', value: `${avgMinutes}分钟`, color: '#FF9800' },
        ].map((item, i) => (
          <div key={i} className="flex items-center py-3 border-b border-gray-300/30 last:border-b-0">
            <div className="w-2.5 h-2.5 rounded-full mr-3" style={{ backgroundColor: item.color }} />
            <span className="text-[14px] text-gray-700 flex-1">{item.label}</span>
            <span className="text-[14px] font-medium text-gray-900">{item.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
