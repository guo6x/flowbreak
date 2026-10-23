import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import Statistics from '../Statistics';
import { NativeFlow } from '../../backend/nativeFlow';

vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => true } }));
vi.mock('../../backend/nativeFlow', () => ({
  NativeFlow: {
    getStatisticsHistory: vi.fn(),
    getAppUsageList: vi.fn(),
  },
}));
vi.mock('../../backend/storage', () => ({
  getMonthStats: vi.fn(() => []),
  getTodayActivities: vi.fn(() => []),
  getTodayStats: vi.fn(() => null),
  getWeekStats: vi.fn(() => []),
}));
vi.mock('../../backend/appNames', () => ({ getAppName: (packageName: string) => packageName }));

const days = [
  {
    date: '2026-10-17', screenTimeSeconds: 600, restCount: 1, interventionCount: 2,
    blockCount: 1, unlockSeconds: 0, pullbackOutcomeCount: 0, successfulPullbackCount: 0,
    postRestReturnCount: 0, postRestTargetSeconds: 0, reflectionValue: 0,
  },
  {
    date: '2026-10-18', screenTimeSeconds: 1_200, restCount: 2, interventionCount: 3,
    blockCount: 2, unlockSeconds: 0, pullbackOutcomeCount: 0, successfulPullbackCount: 0,
    postRestReturnCount: 0, postRestTargetSeconds: 0, reflectionValue: 0,
  },
];
const originalResizeObserver = globalThis.ResizeObserver;
const originalRect = HTMLElement.prototype.getBoundingClientRect;

describe('Statistics real Recharts rendering', () => {
  beforeEach(() => {
    vi.mocked(NativeFlow.getStatisticsHistory).mockReset().mockResolvedValue({ days });
    vi.mocked(NativeFlow.getAppUsageList).mockReset().mockResolvedValue({ apps: [] });
    Object.defineProperty(HTMLElement.prototype, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({
        x: 0, y: 0, top: 0, left: 0, right: 600, bottom: 220,
        width: 600, height: 220, toJSON: () => ({}),
      }),
    });
    class TestResizeObserver {
      constructor(private readonly callback: ResizeObserverCallback) {}
      observe(target: Element) {
        this.callback([{ target, contentRect: { width: 600, height: 220 } } as ResizeObserverEntry], this as unknown as ResizeObserver);
      }
      unobserve() {}
      disconnect() {}
    }
    vi.stubGlobal('ResizeObserver', TestResizeObserver);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    if (originalResizeObserver) vi.stubGlobal('ResizeObserver', originalResizeObserver);
    Object.defineProperty(HTMLElement.prototype, 'getBoundingClientRect', {
      configurable: true,
      value: originalRect,
    });
  });

  it('renders actual SVG area and bar marks without mocking Recharts', async () => {
    render(<Statistics />);
    await screen.findByTestId('statistics-total-time');

    await waitFor(() => {
      expect(screen.getByLabelText('目标应用时间趋势').querySelector('.recharts-surface')).not.toBeNull();
      expect(screen.getByLabelText('目标应用时间趋势').querySelector('.recharts-area')).not.toBeNull();
      expect(screen.getByLabelText('休息完成情况').querySelector('.recharts-surface')).not.toBeNull();
      expect(screen.getByLabelText('休息完成情况').querySelector('.recharts-bar')).not.toBeNull();
    });

    const area = screen.getByLabelText('目标应用时间趋势').querySelector('.recharts-area');
    const bar = screen.getByLabelText('休息完成情况').querySelector('.recharts-bar');
    const areaPath = area?.querySelector('path')?.getAttribute('d') ?? '';
    const barPath = bar?.querySelector('path')?.getAttribute('d') ?? '';
    expect(areaPath).toMatch(/^M\S+/);
    expect(barPath).toMatch(/^M\S+/);
  }, 15_000);
});
