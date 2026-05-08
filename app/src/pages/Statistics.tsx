import { useEffect, useMemo, useState } from 'react';
import { AreaChart, Area, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import { getAppDistribution, getMonthStats, getTodayStats, getWeekStats, markStatsViewed } from '../backend/storage';

const PERIOD_TABS = ['今日', '本周', '本月'];
const pieColors = ['#212121', '#2196F3', '#4CAF50', '#FF9800', '#9E9E9E'];

export default function Statistics() {
  const [period, setPeriod] = useState(1);

  useEffect(() => {
    markStatsViewed();
  }, []);

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

  const appDistribution = useMemo(() => {
    const dist = getAppDistribution();
    if (dist.length === 0) {
      return [
        { name: '暂无数据', value: 100, color: '#E0E0E0' },
      ];
    }
    return dist.map((item, i) => ({ ...item, color: pieColors[i % pieColors.length] }));
  }, [period, sourceData.length, chartData.length]);

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

      <div className="card p-5 mb-4">
        <h3 className="text-[16px] font-bold text-gray-900 mb-1">屏幕时间趋势</h3>
        <p className="text-[12px] text-gray-500 mb-4">{period === 0 ? '今日（分钟）' : `最近 ${sourceData.length} 天（分钟）`}</p>
        {period === 0 ? (
          <div className="flex items-center justify-center h-44">
            <div className="text-center">
              <p className="text-[36px] font-bold text-primary">{chartData[0]?.screenTime || 0}</p>
              <p className="text-[13px] text-gray-500 mt-1">分钟屏幕时间</p>
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

      <div className="card p-5 mb-4">
        <h3 className="text-[16px] font-bold text-gray-900 mb-4">应用使用分布</h3>
        {period > 0 && (
          <p className="text-[12px] text-gray-400 mb-3">仅显示今日数据</p>
        )}
        <div className="flex items-center gap-4">
          <div className="w-32 h-32">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={appDistribution} cx="50%" cy="50%" innerRadius={30} outerRadius={55} dataKey="value" strokeWidth={2} stroke="#fff">
                  {appDistribution.map((entry, i) => (
                    <Cell key={i} fill={entry.color} />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="flex-1 flex flex-col gap-2">
            {appDistribution.map((app, i) => (
              <div key={i} className="flex items-center gap-2">
                <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: app.color }} />
                <span className="text-[13px] text-gray-700 flex-1">{app.name}</span>
                <span className="text-[13px] font-medium text-gray-900">{app.value}%</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="card p-5 mb-4">
        <h3 className="text-[16px] font-bold text-gray-900 mb-1">休息完成情况</h3>
        <p className="text-[12px] text-gray-500 mb-4">当前周期</p>
        {period === 0 ? (
          <div className="flex items-center justify-center h-36">
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

      <div className="card p-4">
        <h3 className="text-[16px] font-bold text-gray-900 mb-3">详细数据</h3>
        {[
          { label: '总屏幕时间', value: totalScreenSeconds >= 3600 ? `${(totalScreenSeconds / 3600).toFixed(1)}小时` : `${Math.round(totalScreenSeconds / 60)}分钟`, icon: '📱' },
          { label: '总休息次数', value: `${totalRest}次`, icon: '🧘' },
          { label: '总干预次数', value: `${totalIntervention}次`, icon: '🛡️' },
          { label: '日均屏幕时间', value: `${avgMinutes}分钟`, icon: '📊' },
        ].map((item, i) => (
          <div key={i} className="flex items-center py-3 border-b border-gray-300/30 last:border-b-0">
            <span className="text-lg mr-3">{item.icon}</span>
            <span className="text-[14px] text-gray-700 flex-1">{item.label}</span>
            <span className="text-[14px] font-medium text-gray-900">{item.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
