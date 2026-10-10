import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import RestMode from '../RestMode';
import { NativeFlow } from '../../backend/nativeFlow';
import { useStore } from '../../hooks/useStore';

const mockNavigate = vi.fn();

vi.mock('react-router', async () => {
  const actual = await vi.importActual<typeof import('react-router')>('react-router');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => true },
}));

vi.mock('../../backend/nativeFlow', () => ({
  NativeFlow: {
    beginRest: vi.fn(),
    getBlockState: vi.fn(),
    cancelRest: vi.fn(),
  },
}));

vi.mock('../rest-mode/useRestAudio', () => ({
  useRestAudio: () => ({ stopAmbient: vi.fn() }),
}));

vi.mock('../rest-mode/ambientAudio', () => ({
  playChime: vi.fn(),
}));

vi.mock('../rest-mode/RestActivityView', () => ({
  default: () => <div data-testid="rest-view" />,
}));

vi.mock('../rest-mode/RestCompleteView', () => ({
  default: () => <div data-testid="rest-complete" />,
}));

const resting = {
  state: 'RESTING' as const,
  sessionSeconds: 0,
  graceUntil: 0,
  blockedPackage: '',
  restStartedAt: Date.now(),
  restRequiredSeconds: 180,
};

const cancelled = { ...resting, state: 'IDLE' as const, restStartedAt: 0, restRequiredSeconds: 0 };

async function mountRest() {
  await act(async () => {
    render(<MemoryRouter initialEntries={['/rest']}><RestMode /></MemoryRouter>);
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe('RestMode native REST reconciliation', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockNavigate.mockReset();
    vi.mocked(NativeFlow.beginRest).mockReset().mockResolvedValue(undefined);
    vi.mocked(NativeFlow.getBlockState).mockReset();
    vi.mocked(NativeFlow.cancelRest).mockReset().mockResolvedValue(undefined);
    useStore.setState({ blockState: 'RESTING', graceUntil: 0 });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('waits for the service command to establish RESTING before showing countdown', async () => {
    vi.mocked(NativeFlow.getBlockState)
      .mockResolvedValueOnce({ ...resting, state: 'BLOCKED', restStartedAt: 0, restRequiredSeconds: 0 })
      .mockResolvedValue(resting);
    await mountRest();
    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(1);
    // The first response is a stale persisted BLOCKED state, not a cancel.
    expect(mockNavigate).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });

    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(2);
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('does not trust a native RESTING state without a committed start timestamp', async () => {
    vi.mocked(NativeFlow.getBlockState)
      .mockResolvedValueOnce({ ...resting, restStartedAt: 0 })
      .mockResolvedValue(resting);
    await mountRest();
    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(1);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });
    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(2);
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('leaves the stale countdown when native anti-cheat already cancelled REST', async () => {
    vi.mocked(NativeFlow.getBlockState)
      .mockResolvedValueOnce(resting)
      .mockResolvedValueOnce(cancelled);
    await mountRest();
    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2_100);
    });

    expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true });
    expect(useStore.getState().blockState).toBe('IDLE');
    cleanup();
    expect(NativeFlow.cancelRest).not.toHaveBeenCalled();
  });

  it('does not leave REST when the native session remains RESTING', async () => {
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue(resting);
    await mountRest();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(4_100);
    });

    expect(NativeFlow.getBlockState).toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('checks again on WebView focus, not just on a countdown tick', async () => {
    vi.mocked(NativeFlow.getBlockState)
      .mockResolvedValueOnce(resting)
      .mockResolvedValueOnce(cancelled);
    await mountRest();

    await act(async () => {
      window.dispatchEvent(new Event('focus'));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true });
  });
});
