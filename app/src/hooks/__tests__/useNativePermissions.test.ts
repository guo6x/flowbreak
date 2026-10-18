import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Capacitor } from '@capacitor/core';
import { NativeFlow, PermissionState } from '../../backend/nativeFlow';
import { useNativePermissions } from '../useNativePermissions';

vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: vi.fn() },
}));

vi.mock('../../backend/nativeFlow', () => ({
  NativeFlow: {
    checkPermissions: vi.fn(),
    addListener: vi.fn(),
  },
}));

const permissionState: PermissionState = {
  hasUsageStats: false,
  hasOverlay: false,
  isIgnoringBattery: false,
  hasNotification: false,
  hasAccessibility: false,
  isDomestic: true,
  channel: 'domestic',
  manufacturer: 'generic',
  unsupportedDevice: false,
  protectionRuntimeAvailable: true,
};

describe('useNativePermissions', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(Capacitor.isNativePlatform).mockReturnValue(true);
    vi.mocked(NativeFlow.checkPermissions).mockResolvedValue(permissionState);
    vi.mocked(NativeFlow.addListener).mockResolvedValue({
      remove: vi.fn().mockResolvedValue(undefined),
    } as never);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('keeps the initial loading state but does not flash it for background polling', async () => {
    let resolvePoll: ((value: PermissionState) => void) | undefined;
    vi.mocked(NativeFlow.checkPermissions)
      .mockResolvedValueOnce(permissionState)
      .mockImplementationOnce(() => new Promise(resolve => { resolvePoll = resolve; }));

    const { result, unmount } = renderHook(() => useNativePermissions(true));
    expect(result.current.checking).toBe(true);

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(result.current.checking).toBe(false);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(NativeFlow.checkPermissions).toHaveBeenCalledTimes(2);
    expect(result.current.checking).toBe(false);

    await act(async () => {
      resolvePoll?.(permissionState);
      await Promise.resolve();
    });
    expect(result.current.checking).toBe(false);
    unmount();
  });
});
