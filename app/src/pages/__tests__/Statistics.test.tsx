import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Capacitor } from '@capacitor/core';
import { NativeFlow } from '../../backend/nativeFlow';
import { getMonthStats, getTodayActivities, getTodayStats, getWeekStats } from '../../backend/storage';
import Statistics from '../Statistics';

vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: vi.fn(() => true) } }));
vi.mock('../../backend/nativeFlow', () => ({ NativeFlow: { getStatisticsHistory: vi.fn(), getAppUsageList: vi.fn() } }));
vi.mock('../../backend/appNames', () => ({ getAppName: (name: string) => name }));
vi.mock('../../backend/storage', () => ({
  getTodayStats: vi.fn(), getWeekStats: vi.fn(), getMonthStats: vi.fn(), getTodayActivities: vi.fn(),
}));
vi.mock('recharts', () => ({
  ResponsiveContainer: () => <div data-testid="statistics-chart" />,
  AreaChart: () => null, Area: () => null, BarChart: () => null, Bar: () => null,
  XAxis: () => null, YAxis: () => null, Tooltip: () => null,
}));

type History = Awaited<ReturnType<typeof NativeFlow.getStatisticsHistory>>;
function history(seconds = 600): History {
  return { days: [{
    date: '2026-10-18', screenTimeSeconds: seconds, restCount: 2, interventionCount: 3,
    blockCount: 3, unlockSeconds: 1200, pullbackOutcomeCount: 0, successfulPullbackCount: 0,
    postRestReturnCount: 0, postRestTargetSeconds: 0, reflectionValue: 0,
  }] };
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(resolver => { resolve = resolver; });
  return { promise, resolve };
}
function today() {
  fireEvent.click(screen.getByRole('button', { name: '今日' }));
}

describe('Statistics native data integrity and states', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(true);
    vi.mocked(NativeFlow.getStatisticsHistory).mockResolvedValue(history());
    vi.mocked(NativeFlow.getAppUsageList).mockResolvedValue({ apps: [] });
    const local = { date: '2026-10-18', totalScreenTime: 72000, restCount: 99, interventionCount: 99, videoTime: 0, focusMinutes: 0 };
    vi.mocked(getTodayStats).mockReturnValue(local);
    vi.mocked(getWeekStats).mockReturnValue([local]);
    vi.mocked(getMonthStats).mockReturnValue([local]);
    vi.mocked(getTodayActivities).mockReturnValue([{ id: 'old', at: '', type: 'screen', app: '不应显示的浏览器记录', durationSec: 72000 }]);
  });

  it('shows loading rather than local fallback or zero totals before native history resolves', async () => {
    const request = deferred<History>();
    vi.mocked(NativeFlow.getStatisticsHistory).mockReturnValue(request.promise);
    render(<Statistics />);
    expect(screen.getByText('正在读取本机统计')).toBeInTheDocument();
    expect(screen.queryByTestId('statistics-total-time')).not.toBeInTheDocument();
    expect(getWeekStats).not.toHaveBeenCalled();
    await act(async () => request.resolve(history(600)));
    expect(screen.getByTestId('statistics-total-time')).toHaveTextContent('10分钟');
    expect(screen.queryByText('20小时')).not.toBeInTheDocument();
  });

  it('shows an explicit history error and retries the same range without local fallback', async () => {
    vi.mocked(NativeFlow.getStatisticsHistory).mockRejectedValueOnce(new Error('bridge unavailable')).mockResolvedValueOnce(history(1200));
    render(<Statistics />);
    expect(await screen.findByText('暂时无法读取统计')).toBeInTheDocument();
    expect(screen.queryByTestId('statistics-total-time')).not.toBeInTheDocument();
    expect(getWeekStats).not.toHaveBeenCalled();
    fireEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: '重试' }));
    expect(await screen.findByTestId('statistics-total-time')).toHaveTextContent('20分钟');
    expect(NativeFlow.getStatisticsHistory).toHaveBeenNthCalledWith(2, { days: 7 });
  });

  it('keeps an empty native result empty instead of using old browser records', async () => {
    vi.mocked(NativeFlow.getStatisticsHistory).mockResolvedValue({ days: [] });
    render(<Statistics />);
    expect(await screen.findByTestId('statistics-total-time')).toHaveTextContent('0分钟');
    expect(screen.getByText(/这段时间还没有记录/)).toBeInTheDocument();
    today();
    expect(await screen.findByText('今天还没有应用使用记录')).toBeInTheDocument();
    expect(screen.queryByText('不应显示的浏览器记录')).not.toBeInTheDocument();
    expect(getTodayActivities).not.toHaveBeenCalled();
  });

  it('does not request or label today-only app data as a 7 or 30-day distribution', async () => {
    render(<Statistics />);
    await screen.findByTestId('statistics-total-time');
    expect(screen.getByText('当前仅提供今日的逐应用明细。')).toBeInTheDocument();
    expect(NativeFlow.getAppUsageList).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '近30天' }));
    await waitFor(() => expect(NativeFlow.getStatisticsHistory).toHaveBeenLastCalledWith({ days: 30 }));
    expect(NativeFlow.getAppUsageList).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '查看今日分布' }));
    await waitFor(() => expect(NativeFlow.getStatisticsHistory).toHaveBeenLastCalledWith({ days: 1 }));
    expect(NativeFlow.getAppUsageList).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: '今日' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('shows app-loading and app-error independently from the loaded summary and supports retry', async () => {
    const request = deferred<Awaited<ReturnType<typeof NativeFlow.getAppUsageList>>>();
    vi.mocked(NativeFlow.getAppUsageList).mockReturnValueOnce(request.promise);
    render(<Statistics />);
    today();
    expect(screen.getByText('正在读取应用明细')).toBeInTheDocument();
    await act(async () => request.resolve({ apps: [] }));
    expect(screen.getByText('今天还没有应用使用记录')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '近7天' }));
    vi.mocked(NativeFlow.getAppUsageList).mockRejectedValueOnce(new Error('apps failed')).mockResolvedValueOnce({ apps: [{ packageName: '真实应用', totalTimeSeconds: 60 }] });
    today();
    expect(await screen.findByText('暂时无法读取应用明细')).toBeInTheDocument();
    expect(screen.getByTestId('statistics-total-time')).toHaveTextContent('10分钟');
    expect(getTodayActivities).not.toHaveBeenCalled();
    fireEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: '重试' }));
    expect(await screen.findByText('真实应用')).toBeInTheDocument();
    expect(screen.getByText('100%')).toBeInTheDocument();
  });

  it('keeps partial success when only the history query fails', async () => {
    vi.mocked(NativeFlow.getStatisticsHistory).mockRejectedValue(new Error('history failed'));
    vi.mocked(NativeFlow.getAppUsageList).mockResolvedValue({ apps: [{ packageName: '今日应用', totalTimeSeconds: 300 }] });
    render(<Statistics />);
    today();
    expect(await screen.findByText('暂时无法读取统计')).toBeInTheDocument();
    expect(await screen.findByText('今日应用')).toBeInTheDocument();
    expect(screen.queryByTestId('statistics-total-time')).not.toBeInTheDocument();
  });

  it('discards slow history responses from a previously selected period', async () => {
    const week = deferred<History>();
    const month = deferred<History>();
    vi.mocked(NativeFlow.getStatisticsHistory).mockImplementation(({ days }) => days === 7 ? week.promise : month.promise);
    render(<Statistics />);
    fireEvent.click(screen.getByRole('button', { name: '近30天' }));
    await act(async () => month.resolve(history(1800)));
    expect(screen.getByTestId('statistics-total-time')).toHaveTextContent('30分钟');
    await act(async () => week.resolve(history(600)));
    expect(screen.getByTestId('statistics-total-time')).toHaveTextContent('30分钟');
  });

  it('discards app responses after leaving today', async () => {
    const request = deferred<Awaited<ReturnType<typeof NativeFlow.getAppUsageList>>>();
    vi.mocked(NativeFlow.getAppUsageList).mockReturnValueOnce(request.promise);
    render(<Statistics />);
    today();
    fireEvent.click(screen.getByRole('button', { name: '近7天' }));
    await act(async () => request.resolve({ apps: [{ packageName: '已过时应用', totalTimeSeconds: 600 }] }));
    expect(screen.queryByText('已过时应用')).not.toBeInTheDocument();
    expect(screen.getByText('当前仅提供今日的逐应用明细。')).toBeInTheDocument();
  });

  it('includes other apps and preserves truthful percentages for more than eight apps', async () => {
    vi.mocked(NativeFlow.getAppUsageList).mockResolvedValue({ apps: Array.from({ length: 10 }, (_, index) => ({ packageName: `应用${index}`, totalTimeSeconds: 60 })) });
    render(<Statistics />);
    today();
    const rows = await screen.findAllByTestId('app-usage-share');
    expect(rows).toHaveLength(8);
    expect(within(rows[0]).getByText('10%')).toBeInTheDocument();
    expect(within(rows[7]).getByText('其他应用')).toBeInTheDocument();
    expect(within(rows[7]).getByText('30%')).toBeInTheDocument();
    const sum = rows.reduce((total, row) => total + Number(within(row).getByText(/^\d+%$/).textContent?.replace('%', '')), 0);
    expect(sum).toBe(100);
  });

  it('normalizes invalid native counts and distinguishes sub-minute use from no use', async () => {
    const response = history(20);
    response.days[0].restCount = Number.NaN;
    response.days[0].interventionCount = -1;
    vi.mocked(NativeFlow.getStatisticsHistory).mockResolvedValue(response);
    render(<Statistics />);
    expect(await screen.findByTestId('statistics-total-time')).toHaveTextContent('不足1分钟');
    expect(screen.queryByText(/NaN/)).not.toBeInTheDocument();
    expect(screen.queryByText(/这段时间还没有记录/)).not.toBeInTheDocument();
  });

  it('uses local records only when actually running in the browser', async () => {
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(false);
    vi.mocked(getTodayActivities).mockReturnValue(Array.from({ length: 10 }, (_, index) => ({ id: String(index), at: '', type: 'screen', app: `浏览器应用${index}`, durationSec: 60 })));
    render(<Statistics />);
    expect(screen.getByTestId('statistics-total-time')).toHaveTextContent('20小时');
    expect(screen.getByText('浏览器本地记录')).toBeInTheDocument();
    today();
    expect(screen.getAllByTestId('app-usage-share')).toHaveLength(8);
    expect(screen.getByText('其他应用')).toBeInTheDocument();
    expect(NativeFlow.getStatisticsHistory).not.toHaveBeenCalled();
    expect(NativeFlow.getAppUsageList).not.toHaveBeenCalled();
  });
});
