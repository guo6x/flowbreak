// Native-mode concurrency tests for the RestMode completion flow.
//
// These tests verify the synchronous `completionInFlightRef` lock by
// rendering RestMode in native mode (Capacitor.isNativePlatform() returns
// true) with `completeRestAndUnlock` backed by a manually-controlled
// pending Promise. While the first call is still pending, a second click
// must NOT trigger a second native request. After the Promise resolves,
// the reward view appears and no further settlement is possible.
//
// A second test verifies the failure path: when the first native call
// rejects, the in-flight lock is released so the user can tap "重试解锁",
// and the second call succeeds. Total native calls = 2 (one failed, one
// successful), and settlement happens exactly once.

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import type { ReactNode } from 'react';

// --- Mocks -------------------------------------------------------------
// vi.mock factories are hoisted; all symbols they reference must be
// created with vi.hoisted() so they exist at mock-evaluation time.

const {
  mockCompleteRest,
  mockSetBlockState,
  mockNavigate,
  mockCompleteRestAndUnlock,
  mockBeginRest,
  mockGetBlockState,
  mockCancelRest,
  mockStore,
  useStoreMock,
} = vi.hoisted(() => {
  const mockCompleteRest = vi.fn();
  const mockSetBlockState = vi.fn();
  const mockNavigate = vi.fn();
  const mockCompleteRestAndUnlock = vi.fn();
  const mockBeginRest = vi.fn();
  const mockGetBlockState = vi.fn();
  const mockCancelRest = vi.fn();
  const mockStore = {
    profile: {
      restDuration: 2, // keep the test fast
      selectedBackground: 0,
      onboardingDone: true,
    },
    completeRestActivity: mockCompleteRest,
    setBlockState: mockSetBlockState,
    achievements: [] as Array<{ id: string; unlocked: boolean; title: string }>,
    points: 0,
    streak: 0,
  };
  const useStoreMock = Object.assign(
    vi.fn(<T,>(selector: (s: typeof mockStore) => T): T => selector(mockStore)),
    {
      getState: () => mockStore,
      setState: (patch: Partial<typeof mockStore>) => {
        Object.assign(mockStore, patch);
      },
    },
  );
  return {
    mockCompleteRest,
    mockSetBlockState,
    mockNavigate,
    mockCompleteRestAndUnlock,
    mockBeginRest,
    mockGetBlockState,
    mockCancelRest,
    mockStore,
    useStoreMock,
  };
});

vi.mock('../../../hooks/useStore', () => ({ useStore: useStoreMock }));

vi.mock('@capacitor/core', () => ({
  Capacitor: {
    isNativePlatform: () => true,
  },
}));

vi.mock('react-router', () => ({
  useNavigate: () => mockNavigate,
  useLocation: () => ({ state: {} }),
}));

vi.mock('../../../backend/nativeFlow', () => ({
  NativeFlow: {
    beginRest: mockBeginRest,
    cancelRest: mockCancelRest,
    completeRestAndUnlock: mockCompleteRestAndUnlock,
    getBlockState: mockGetBlockState,
  },
}));

// --- Subject ------------------------------------------------------------

import RestMode from '../../RestMode';

function withRouter(node: ReactNode) {
  return node;
}

// Set up the native rest bootstrap so the page reaches the "完成休息"
// state. The bootstrap calls beginRest() then getBlockState(); we return
// a rest window that has already elapsed so the timer reads zero.
function seedNativeRest(now: number) {
  mockBeginRest.mockResolvedValue(undefined);
  mockGetBlockState.mockResolvedValue({
    state: 'RESTING',
    sessionSeconds: 0,
    graceUntil: 0,
    blockedPackage: '',
    restStartedAt: now - 2000, // started 2s ago
    restRequiredSeconds: 2,    // 2s rest → already elapsed
  });
}

// Flush microtasks (pending promise continuations) without advancing
// fake timers. This lets the native bootstrap (beginRest + getBlockState)
// and the handleComplete async path settle between controlled timer
// advances.
function flushMicrotasks() {
  return vi.advanceTimersByTimeAsync(0);
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date(2025, 0, 1, 0, 0, 0));
  mockCompleteRest.mockReset();
  mockSetBlockState.mockReset();
  mockNavigate.mockReset();
  mockCompleteRestAndUnlock.mockReset();
  mockBeginRest.mockReset();
  mockGetBlockState.mockReset();
  mockCancelRest.mockReset();
  mockStore.achievements = [];
  mockStore.points = 0;
  mockStore.streak = 0;
});

afterEach(() => {
  vi.useRealTimers();
});

describe('RestMode completion flow (native concurrency)', () => {
  it('fires completeRestAndUnlock exactly once even if the button is clicked twice while pending', async () => {
    const now = Date.now();
    seedNativeRest(now);

    // First call returns a pending Promise we control.
    let resolveFirst!: (value: {
      graceUntil: number;
      points: number;
      streak: number;
      achievement: string;
    }) => void;
    const pendingComplete = new Promise<{
      graceUntil: number;
      points: number;
      streak: number;
      achievement: string;
    }>((resolve) => {
      resolveFirst = resolve;
    });
    mockCompleteRestAndUnlock.mockReturnValue(pendingComplete);

    render(withRouter(<RestMode />));

    // Let the native bootstrap (beginRest + getBlockState) flush.
    await act(async () => {
      await flushMicrotasks();
    });

    const completeButton = screen.getByRole('button', { name: /完成休息/ });
    expect(completeButton).toBeInTheDocument();

    // First click — enters the async path, native call pending.
    fireEvent.click(completeButton);
    // Second click while the first native call is still pending. The
    // synchronous `completionInFlightRef` must block this from issuing a
    // second completeRestAndUnlock.
    fireEvent.click(completeButton);

    expect(mockCompleteRestAndUnlock).toHaveBeenCalledTimes(1);
    expect(mockCompleteRestAndUnlock).toHaveBeenCalledWith({
      activity: 'eye',
      duration: 2,
    });

    // Resolve the pending native completion.
    await act(async () => {
      resolveFirst({
        graceUntil: now + 10 * 60 * 1000,
        points: 42,
        streak: 3,
        achievement: 'health_guardian',
      });
      await flushMicrotasks();
    });

    // Settlement happened exactly once: one setBlockState, one points write.
    expect(mockSetBlockState).toHaveBeenCalledTimes(1);
    expect(mockSetBlockState).toHaveBeenCalledWith(
      'GRACE',
      now + 10 * 60 * 1000,
      '',
    );
    expect(mockStore.points).toBe(42);
    expect(mockStore.streak).toBe(3);

    // Reward view appears. Reveal the 500ms content delay.
    act(() => vi.advanceTimersByTime(600));
    expect(screen.getByText('休息完成！')).toBeInTheDocument();

    // Still exactly one native call after everything settles.
    expect(mockCompleteRestAndUnlock).toHaveBeenCalledTimes(1);
  });

  it('releases the in-flight lock on failure so the user can retry, then succeeds exactly once', async () => {
    const now = Date.now();
    seedNativeRest(now);

    // First call rejects.
    mockCompleteRestAndUnlock.mockRejectedValueOnce(new Error('network'));

    render(withRouter(<RestMode />));

    await act(async () => {
      await flushMicrotasks();
    });

    const completeButton = screen.getByRole('button', { name: /完成休息/ });
    fireEvent.click(completeButton);

    // Wait for the rejection to flush and the error state to render.
    await act(async () => {
      await flushMicrotasks();
    });

    // After failure the operationError is set and the button reads "重试解锁".
    const retryButton = screen.getByRole('button', { name: '重试解锁' });
    expect(retryButton).toBeInTheDocument();

    // The first (failed) call has happened. Settlement has NOT happened.
    expect(mockCompleteRestAndUnlock).toHaveBeenCalledTimes(1);
    expect(mockSetBlockState).not.toHaveBeenCalled();

    // Second call succeeds.
    mockCompleteRestAndUnlock.mockResolvedValueOnce({
      graceUntil: now + 10 * 60 * 1000,
      points: 7,
      streak: 1,
      achievement: '',
    });

    fireEvent.click(retryButton);

    await act(async () => {
      await flushMicrotasks();
    });

    // Two native calls total (one failed, one succeeded).
    expect(mockCompleteRestAndUnlock).toHaveBeenCalledTimes(2);

    // Settlement happened exactly once (on the second, successful call).
    expect(mockSetBlockState).toHaveBeenCalledTimes(1);
    expect(mockStore.points).toBe(7);
    expect(mockStore.streak).toBe(1);

    // Reward view appears.
    act(() => vi.advanceTimersByTime(600));
    expect(screen.getByText('休息完成！')).toBeInTheDocument();

    // The "完成休息"/"重试解锁" button is gone — cannot settle again.
    expect(screen.queryByRole('button', { name: /完成休息|重试解锁/ })).toBeNull();

    // No further native calls.
    expect(mockCompleteRestAndUnlock).toHaveBeenCalledTimes(2);
  });
});
