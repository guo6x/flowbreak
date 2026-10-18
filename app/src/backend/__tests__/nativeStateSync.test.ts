import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NativeFlow } from '../nativeFlow';
import { startNativeStatePolling } from '../nativeStateSync';
import { useStore } from '../../hooks/useStore';

vi.mock('../nativeFlow', () => ({
  NativeFlow: {
    getCurrentApp: vi.fn(),
    getUsageStats: vi.fn(),
    getBlockState: vi.fn(),
  },
}));

function resetStore() {
  const current = useStore.getState();
  useStore.setState({
    profile: {
      ...current.profile,
      onboardingDone: false,
      sessionLimit: 15,
      targetApps: ['tv.danmaku.bili'],
    },
    isMonitoring: false,
    todayStats: { ...current.todayStats, totalScreenTime: 0 },
    currentAppName: '',
    continuousSessionSeconds: 0,
    fatigueScore: 0,
    fatigueLevel: 'NONE',
    blockState: 'IDLE',
    graceUntil: 0,
    blockedPackage: '',
    serviceError: '',
  });
}

describe('native state polling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    resetStore();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('continues polling and applies recovered native state when local monitoring is false', async () => {
    vi.mocked(NativeFlow.getCurrentApp).mockResolvedValue({
      packageName: 'tv.danmaku.bili',
    });
    vi.mocked(NativeFlow.getUsageStats).mockResolvedValue({
      screenTimeSeconds: 42,
    });
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue({
      state: 'BLOCKED',
      sessionSeconds: 123,
      graceUntil: 0,
      blockedPackage: 'tv.danmaku.bili',
      restStartedAt: 0,
      restRequiredSeconds: 0,
    });

    const stopPolling = startNativeStatePolling(15, 10_000);
    await vi.waitFor(() => {
      const state = useStore.getState();
      expect(state.isMonitoring).toBe(false);
      expect(state.currentAppName).toBe('B站');
      expect(state.todayStats.totalScreenTime).toBe(42);
      expect(state.continuousSessionSeconds).toBe(123);
      expect(state.blockState).toBe('BLOCKED');
      expect(state.blockedPackage).toBe('tv.danmaku.bili');
      expect(state.fatigueLevel).toBe('ACTION');
    });

    vi.mocked(NativeFlow.getUsageStats).mockResolvedValue({ screenTimeSeconds: 49 });
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue({
      state: 'IDLE',
      sessionSeconds: 0,
      graceUntil: 0,
      blockedPackage: '',
      restStartedAt: 0,
      restRequiredSeconds: 0,
    });
    await vi.advanceTimersByTimeAsync(10_000);
    await vi.waitFor(() => {
      expect(useStore.getState().todayStats.totalScreenTime).toBe(49);
      expect(useStore.getState().continuousSessionSeconds).toBe(0);
      expect(useStore.getState().blockState).toBe('IDLE');
      expect(useStore.getState().fatigueLevel).toBe('NONE');
    });

    expect(NativeFlow.getCurrentApp).toHaveBeenCalledTimes(2);
    expect(NativeFlow.getUsageStats).toHaveBeenCalledTimes(2);
    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(2);
    stopPolling();
  });

  it('keeps a paused native snapshot paused and does not start the service', async () => {
    vi.mocked(NativeFlow.getCurrentApp).mockResolvedValue({ packageName: '' });
    vi.mocked(NativeFlow.getUsageStats).mockResolvedValue({ screenTimeSeconds: 7 });
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue({
      state: 'IDLE',
      sessionSeconds: 0,
      graceUntil: 0,
      blockedPackage: '',
      restStartedAt: 0,
      restRequiredSeconds: 0,
    });

    const stopPolling = startNativeStatePolling(15, 10_000);
    await vi.waitFor(() => {
      expect(useStore.getState().blockState).toBe('IDLE');
      expect(useStore.getState().isMonitoring).toBe(false);
      expect(useStore.getState().fatigueLevel).toBe('NONE');
    });
    stopPolling();
  });
});
