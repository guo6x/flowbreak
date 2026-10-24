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
  mockBeginRest.mockResolvedValue({ beginRequestToken: 'begin-request-101' });
  mockGetBlockState.mockResolvedValue({
    state: 'RESTING',
    sessionSeconds: 0,
    graceUntil: 0,
    blockedPackage: '',
    restStartedAt: now - 2000, // started 2s ago
    restRequiredSeconds: 2,    // 2s rest → already elapsed
    restSessionId: 101,
    restClockValid: true,
    restRemainingMs: 0,
  });
}

function nonRestingBeginState(status: 'ACCEPTED' | 'REJECTED' = 'ACCEPTED') {
  return {
    state: 'IDLE',
    sessionSeconds: 0,
    graceUntil: 0,
    blockedPackage: '',
    restStartedAt: 0,
    restRequiredSeconds: 0,
    restSessionId: 0,
    restClockValid: false,
    restRemainingMs: 0,
    restBeginRequestToken: 'begin-request-101',
    restBeginRequestStatusToken: 'begin-request-101',
    restBeginRequestStatus: status,
  };
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
  mockCancelRest.mockResolvedValue(undefined);
  mockStore.profile.restDuration = 2;
  mockStore.achievements = [];
  mockStore.points = 0;
  mockStore.streak = 0;
});

afterEach(() => {
  vi.useRealTimers();
});

describe('RestMode completion flow (native concurrency)', () => {
  it('does not show a countdown or completion controls before native RESTING is confirmed', async () => {
    mockBeginRest.mockResolvedValue({ beginRequestToken: 'begin-request-101' });
    mockGetBlockState.mockReturnValue(new Promise(() => {}));

    const { unmount } = render(withRouter(<RestMode />));
    await act(async () => { await flushMicrotasks(); });

    expect(screen.getByText('等待设备确认休息已启动')).toBeInTheDocument();
    expect(screen.queryByRole('timer', { name: '剩余休息时间' })).not.toBeInTheDocument();
    expect(screen.queryByRole('progressbar', { name: '休息进度' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /完成休息|重试解锁|提前结束/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '取消启动并返回' })).toBeInTheDocument();

    unmount();
  });

  it('keeps a stable non-RESTING snapshot without presenting it as an active rest session', async () => {
    mockBeginRest.mockResolvedValue({ beginRequestToken: 'begin-request-101' });
    mockGetBlockState
      .mockResolvedValueOnce(nonRestingBeginState())
      .mockResolvedValueOnce(nonRestingBeginState());

    render(withRouter(<RestMode />));
    await act(async () => { await flushMicrotasks(); });

    expect(screen.getByRole('alert')).toHaveTextContent('已接受休息请求，但当前休息状态随后结束或发生变化');
    expect(screen.getByText('休息尚未确认')).toBeInTheDocument();
    expect(screen.queryByRole('timer', { name: '剩余休息时间' })).not.toBeInTheDocument();
    expect(screen.queryByRole('progressbar', { name: '休息进度' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /完成休息|重试解锁|提前结束/ })).not.toBeInTheDocument();
    expect(mockGetBlockState).toHaveBeenCalledTimes(2);
  });

  it('shows the actual beginRest rejection without a fabricated timer or early-exit claim', async () => {
    mockBeginRest.mockRejectedValue(new Error('PROTECTION_RUNTIME_UNHEALTHY'));

    render(withRouter(<RestMode />));
    await act(async () => { await flushMicrotasks(); });

    expect(screen.getByRole('alert')).toHaveTextContent('保护服务状态已变化，休息没有启动');
    expect(screen.getByText('休息尚未确认')).toBeInTheDocument();
    expect(screen.queryByRole('timer', { name: '剩余休息时间' })).not.toBeInTheDocument();
    expect(screen.queryByRole('progressbar', { name: '休息进度' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /完成休息|重试解锁|提前结束/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '返回面板' })).toBeInTheDocument();
  });

  it('stops an unconfirmed native start and cancels if its token arrives later', async () => {
    let resolveBegin!: (value: { beginRequestToken: string }) => void;
    mockBeginRest.mockReturnValue(new Promise(resolve => { resolveBegin = resolve; }));
    mockGetBlockState.mockReturnValue(new Promise(() => {}));

    render(withRouter(<RestMode />));
    await act(async () => { await flushMicrotasks(); });
    expect(screen.getByText('正在连接设备保护服务')).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });

    expect(screen.getByText('休息尚未确认')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('设备尚未确认休息是否启动');
    expect(screen.getByRole('button', { name: '返回面板' })).toBeInTheDocument();
    expect(screen.queryByRole('timer', { name: '剩余休息时间' })).not.toBeInTheDocument();
    expect(mockCancelRest).not.toHaveBeenCalled();

    await act(async () => {
      resolveBegin({ beginRequestToken: 'begin-late-token' });
      await flushMicrotasks();
    });
    expect(mockCancelRest).toHaveBeenCalledWith({ beginRequestToken: 'begin-late-token' });
  });

  it('returns after timeout auto-cancel without sending a duplicate native cancellation', async () => {
    mockBeginRest.mockResolvedValue({ beginRequestToken: 'begin-request-101' });
    mockGetBlockState.mockReturnValue(new Promise(() => {}));

    render(withRouter(<RestMode />));
    await act(async () => { await flushMicrotasks(); });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
      await flushMicrotasks();
    });

    expect(mockCancelRest).toHaveBeenCalledTimes(1);
    expect(mockCancelRest).toHaveBeenCalledWith({ beginRequestToken: 'begin-request-101' });
    fireEvent.click(screen.getByRole('button', { name: '返回面板' }));
    expect(screen.getByRole('group', { name: '设备尚未确认休息' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '取消启动并返回' }));
    await act(async () => { await flushMicrotasks(); });

    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
    expect(mockCancelRest).toHaveBeenCalledTimes(1);
  });

  it('hides zero-time and exit-completion states while a confirmed RESTING clock is recovering', async () => {
    mockBeginRest.mockResolvedValue({ beginRequestToken: 'begin-request-101' });
    mockGetBlockState.mockResolvedValue({
      state: 'RESTING',
      sessionSeconds: 2,
      graceUntil: 0,
      blockedPackage: '',
      restStartedAt: Date.now() - 2_000,
      restRequiredSeconds: 2,
      restSessionId: 101,
      restClockValid: false,
      restRemainingMs: 0,
    });

    render(withRouter(<RestMode />));
    await act(async () => { await flushMicrotasks(); });

    expect(screen.getByText('正在恢复休息计时')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('休息计时暂时无法确认');
    expect(screen.queryByRole('timer', { name: '剩余休息时间' })).not.toBeInTheDocument();
    expect(screen.queryByRole('progressbar', { name: '休息进度' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /完成休息|重试解锁|提前结束/ })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '结束休息并返回' }));
    expect(screen.getByRole('group', { name: '计时正在恢复核验' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '结束休息并返回' })).toBeInTheDocument();
  });

  it('hides the timer when a confirmed RESTING clock becomes invalid and restores it after a valid sample', async () => {
    const now = Date.now();
    mockStore.profile.restDuration = 60;
    const snapshot = (restClockValid: boolean, restRemainingMs: number) => ({
      state: 'RESTING',
      sessionSeconds: 2,
      graceUntil: 0,
      blockedPackage: '',
      restStartedAt: now - 2_000,
      restRequiredSeconds: 60,
      restSessionId: 101,
      restClockValid,
      restRemainingMs,
    });
    mockBeginRest.mockResolvedValue({ beginRequestToken: 'begin-request-101' });
    mockGetBlockState
      .mockResolvedValueOnce(snapshot(true, 40_000))
      .mockResolvedValueOnce(snapshot(false, 0))
      .mockResolvedValueOnce(snapshot(true, 35_000));

    render(withRouter(<RestMode />));
    await act(async () => { await flushMicrotasks(); });

    expect(screen.getByRole('timer', { name: '剩余休息时间' })).toHaveTextContent('0:40');
    expect(screen.getByRole('progressbar', { name: '休息进度' })).toHaveAttribute('aria-valuenow', '33');

    await act(async () => {
      window.dispatchEvent(new Event('focus'));
      await flushMicrotasks();
    });

    expect(screen.getByText('正在恢复休息计时')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('休息计时暂时无法确认');
    expect(screen.queryByRole('timer', { name: '剩余休息时间' })).not.toBeInTheDocument();
    expect(screen.queryByRole('progressbar', { name: '休息进度' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /完成休息|重试解锁|提前结束/ })).not.toBeInTheDocument();

    await act(async () => {
      window.dispatchEvent(new Event('focus'));
      await flushMicrotasks();
    });

    expect(screen.getByRole('timer', { name: '剩余休息时间' })).toHaveTextContent('0:35');
    expect(screen.getByRole('progressbar', { name: '休息进度' })).toHaveAttribute('aria-valuenow', '42');
    expect(screen.queryByText('正在恢复休息计时')).not.toBeInTheDocument();

    await act(async () => { await vi.advanceTimersByTimeAsync(1_000); });

    expect(screen.getByRole('timer', { name: '剩余休息时间' })).toHaveTextContent('0:34');
    expect(screen.getByRole('progressbar', { name: '休息进度' })).toHaveAttribute('aria-valuenow', '43');
    expect(mockGetBlockState).toHaveBeenCalledTimes(3);
  });

  it('allows safe exit before the begin token arrives and cancels once it does', async () => {
    let resolveBegin!: (value: { beginRequestToken: string }) => void;
    mockBeginRest.mockReturnValue(new Promise(resolve => { resolveBegin = resolve; }));
    const { unmount } = render(withRouter(<RestMode />));

    await act(async () => { await flushMicrotasks(); });
    fireEvent.click(screen.getByRole('button', { name: '取消启动并返回' }));
    expect(screen.getByRole('group', { name: '设备尚未确认休息' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '取消启动并返回' }));
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');

    unmount();
    await act(async () => {
      resolveBegin({ beginRequestToken: 'begin-request-101' });
      await flushMicrotasks();
    });
    expect(mockCancelRest).toHaveBeenCalledWith({ beginRequestToken: 'begin-request-101' });
  });

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
      restSessionId: 101,
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
