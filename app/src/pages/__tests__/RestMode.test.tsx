import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import RestMode from '../RestMode';
import { NativeFlow } from '../../backend/nativeFlow';
import { useStore } from '../../hooks/useStore';

const mockNavigate = vi.fn();
const mockCapacitor = vi.hoisted(() => ({ isNative: true }));

vi.mock('react-router', async () => {
  const actual = await vi.importActual<typeof import('react-router')>('react-router');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => mockCapacitor.isNative } }));

vi.mock('../../backend/nativeFlow', () => ({
  NativeFlow: {
    beginRest: vi.fn(),
    getBlockState: vi.fn(),
    cancelRest: vi.fn(),
    completeRestAndUnlock: vi.fn(),
  },
}));

vi.mock('../rest-mode/useRestAudio', () => ({ useRestAudio: () => ({ stopAmbient: vi.fn() }) }));
vi.mock('../rest-mode/ambientAudio', () => ({ playChime: vi.fn() }));
vi.mock('../rest-mode/RestActivityView', () => ({
  default: ({
    operationError,
    timeLeft,
    restClockValid,
    confirmingEarlyExit,
    onClose,
    onComplete,
    onConfirmEarlyExit,
  }: {
    operationError: string;
    timeLeft: number;
    restClockValid: boolean;
    confirmingEarlyExit: boolean;
    onClose: () => void;
    onComplete: () => void;
    onConfirmEarlyExit: () => void;
  }) => (
    <div>
      <div>{operationError}</div>
      <output aria-label="remaining seconds">{timeLeft}</output>
      <output aria-label="native clock valid">{String(restClockValid)}</output>
      <button onClick={onClose}>request early exit</button>
      <button onClick={onComplete}>complete rest</button>
      {confirmingEarlyExit && (
        <button onClick={onConfirmEarlyExit}>confirm early exit</button>
      )}
    </div>
  ),
}));
vi.mock('../rest-mode/RestCompleteView', () => ({ default: () => <div>rest complete</div> }));

const originalVibrateDescriptor = Object.getOwnPropertyDescriptor(navigator, 'vibrate');
const originalUserActivationDescriptor = Object.getOwnPropertyDescriptor(navigator, 'userActivation');

function setVibrationEnvironment({
  hasBeenActive,
  vibrate,
}: {
  hasBeenActive?: boolean;
  vibrate: ReturnType<typeof vi.fn>;
}) {
  Object.defineProperty(navigator, 'vibrate', { configurable: true, value: vibrate });
  if (hasBeenActive === undefined) {
    Reflect.deleteProperty(navigator, 'userActivation');
  } else {
    Object.defineProperty(navigator, 'userActivation', {
      configurable: true,
      value: { hasBeenActive },
    });
  }
}

function restoreNavigatorProperty(key: 'vibrate' | 'userActivation', descriptor?: PropertyDescriptor) {
  if (descriptor) Object.defineProperty(navigator, key, descriptor);
  else delete (navigator as Navigator & { vibrate?: unknown; userActivation?: unknown })[key];
}

const resting = {
  state: 'RESTING' as const,
  sessionSeconds: 0,
  graceUntil: 0,
  blockedPackage: '',
  restStartedAt: Date.now(),
  restRequiredSeconds: 180,
  restSessionId: 101,
  restClockValid: true,
  restRemainingMs: 180_000,
};
const cancelled = {
  ...resting,
  state: 'IDLE' as const,
  restStartedAt: 0,
  restRequiredSeconds: 0,
};
const grace = {
  ...cancelled,
  state: 'GRACE' as const,
  graceUntil: Date.now() + 60_000,
};

async function mountRest() {
  await act(async () => {
    render(<MemoryRouter initialEntries={['/rest']}><RestMode /></MemoryRouter>);
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe('RestMode native state reconciliation', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockCapacitor.isNative = true;
    mockNavigate.mockReset();
    vi.mocked(NativeFlow.beginRest).mockReset().mockResolvedValue({
      beginRequestToken: 'begin-request-101',
    });
    vi.mocked(NativeFlow.getBlockState).mockReset();
    vi.mocked(NativeFlow.cancelRest).mockReset().mockResolvedValue(undefined);
    vi.mocked(NativeFlow.completeRestAndUnlock).mockReset();
    useStore.setState({ blockState: 'RESTING', graceUntil: 0 });
  });

  afterEach(() => {
    cleanup();
    restoreNavigatorProperty('vibrate', originalVibrateDescriptor);
    restoreNavigatorProperty('userActivation', originalUserActivationDescriptor);
    vi.useRealTimers();
  });

  it('skips browser vibration without sticky user activation', async () => {
    mockCapacitor.isNative = false;
    const vibrate = vi.fn();
    setVibrationEnvironment({ hasBeenActive: false, vibrate });

    await mountRest();
    await act(async () => { await vi.advanceTimersByTimeAsync(4_000); });

    expect(vibrate).not.toHaveBeenCalled();
    expect(screen.getByLabelText('remaining seconds')).toBeInTheDocument();
  });

  it('keeps browser haptics after sticky user activation', async () => {
    mockCapacitor.isNative = false;
    const vibrate = vi.fn().mockReturnValue(true);
    setVibrationEnvironment({ hasBeenActive: true, vibrate });

    await mountRest();
    expect(vibrate).toHaveBeenCalledWith(30);
    await act(async () => { await vi.advanceTimersByTimeAsync(4_000); });

    expect(vibrate).toHaveBeenCalledWith(50);
    expect(screen.getByLabelText('remaining seconds')).toBeInTheDocument();
  });

  it('does not interrupt rest when an activated vibration call throws', async () => {
    mockCapacitor.isNative = false;
    const vibrate = vi.fn(() => { throw new Error('vibration unavailable'); });
    setVibrationEnvironment({ hasBeenActive: true, vibrate });

    await mountRest();
    await act(async () => { await vi.advanceTimersByTimeAsync(4_000); });

    expect(vibrate).toHaveBeenCalledWith(50);
    expect(screen.getByLabelText('remaining seconds')).toBeInTheDocument();
    expect(screen.getByLabelText('native clock valid')).toHaveTextContent('true');
  });

  it('keeps legacy Android vibration when the UserActivation API is unavailable', async () => {
    const vibrate = vi.fn().mockReturnValue(true);
    setVibrationEnvironment({ vibrate });

    await mountRest();
    expect(vibrate).toHaveBeenCalledWith(30);
  });

  it('fails closed for browser vibration when the UserActivation API is unavailable', async () => {
    mockCapacitor.isNative = false;
    const vibrate = vi.fn();
    setVibrationEnvironment({ vibrate });

    await mountRest();
    await act(async () => { await vi.advanceTimersByTimeAsync(4_000); });

    expect(vibrate).not.toHaveBeenCalled();
    expect(screen.getByLabelText('remaining seconds')).toBeInTheDocument();
  });

  it('waits for persisted RESTING instead of treating an enqueued command as started', async () => {
    vi.mocked(NativeFlow.getBlockState)
      .mockResolvedValueOnce({ ...resting, state: 'BLOCKED', restStartedAt: 0, restRequiredSeconds: 0 })
      .mockResolvedValue(resting);
    await mountRest();
    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(1);
    expect(mockNavigate).not.toHaveBeenCalled();

    await act(async () => { await vi.advanceTimersByTimeAsync(300); });
    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(2);
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('allows exit after a timed-out startup cancellation without cancelling twice', async () => {
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue({
      ...cancelled,
      state: 'BLOCKED',
      blockedPackage: 'tv.danmaku.bili',
    });
    await mountRest();

    expect(NativeFlow.beginRest).toHaveBeenCalledTimes(1);
    expect(NativeFlow.cancelRest).not.toHaveBeenCalled();
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });

    expect(screen.getByText(
      '设备尚未确认休息是否启动，计时没有开始。请返回面板核对保护状态后再试。',
    )).toBeInTheDocument();
    expect(NativeFlow.cancelRest).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: 'request early exit' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'confirm early exit' }));
      await Promise.resolve();
    });

    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
    expect(NativeFlow.cancelRest).toHaveBeenCalledTimes(1);
  });

  it('keeps reconciling a confirmed REST session past startup timeout and resumes its timer when the clock recovers', async () => {
    const invalidRest = {
      ...resting,
      restClockValid: false,
      restRemainingMs: 0,
    };
    const recoveredRest = { ...resting, restRemainingMs: 60_000 };
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue(invalidRest);
    await mountRest();

    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });

    expect(screen.getByText('休息计时暂时无法确认，正在恢复保护状态。'))
      .toBeInTheDocument();
    expect(NativeFlow.cancelRest).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();
    const checksAtStartupTimeout = vi.mocked(NativeFlow.getBlockState).mock.calls.length;
    expect(checksAtStartupTimeout).toBeGreaterThan(1);

    // The 30-second startup deadline is not a deadline for an already
    // confirmed REST session. The same session must continue to be polled.
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue(recoveredRest);
    await act(async () => { await vi.advanceTimersByTimeAsync(2_100); });

    expect(vi.mocked(NativeFlow.getBlockState).mock.calls.length)
      .toBeGreaterThan(checksAtStartupTimeout);
    expect(screen.getByLabelText('native clock valid')).toHaveTextContent('true');
    expect(screen.getByLabelText('remaining seconds')).toHaveTextContent('60');
    expect(screen.queryByText(/休息计时暂时无法确认/)).toBeNull();

    await act(async () => { await vi.advanceTimersByTimeAsync(1_000); });
    expect(screen.getByLabelText('remaining seconds')).toHaveTextContent('59');
    expect(NativeFlow.beginRest).toHaveBeenCalledTimes(1);
    expect(NativeFlow.cancelRest).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'request early exit' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'confirm early exit' }));
      await Promise.resolve();
    });

    expect(NativeFlow.cancelRest).toHaveBeenCalledTimes(1);
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
  });

  it('shows the native rejection reason instead of waiting forever', async () => {
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue({
      ...cancelled,
      state: 'BLOCKED',
      blockedPackage: 'tv.danmaku.bili',
      restBeginRequestToken: 'begin-request-101',
      restBeginRequestStatusToken: 'begin-request-101',
      restBeginRequestStatus: 'REJECTED' as const,
      restBeginRequestErrorCode: 'PROTECTION_RUNTIME_UNHEALTHY',
    });
    await mountRest();

    expect(screen.getByText('保护服务状态已变化，休息没有启动。请返回面板检查后重试。'))
      .toBeInTheDocument();
    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(1);
    expect(mockNavigate).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'request early exit' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'confirm early exit' }));
      await Promise.resolve();
    });

    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
    expect(NativeFlow.cancelRest).not.toHaveBeenCalled();
  });

  it.each([
    [
      'service dispatch failure',
      'SERVICE_COMMAND_FAILED',
      '原生服务未接受休息请求，本次休息没有启动。请返回面板检查或重试。',
    ],
    [
      'request persistence failure',
      'PREF_COMMIT_FAILED',
      '原生服务未接受休息请求，本次休息没有启动。请返回面板检查或重试。',
    ],
    [
      'protection runtime rejection',
      'PROTECTION_RUNTIME_UNHEALTHY',
      '保护服务状态已变化，休息没有启动。请返回面板检查后重试。',
    ],
  ])('allows returning to the dashboard after an immediate %s', async (_label, reason, expectedMessage) => {
    vi.mocked(NativeFlow.beginRest).mockRejectedValue(new Error(reason));
    await mountRest();

    expect(screen.getByText(expectedMessage)).toBeInTheDocument();
    expect(NativeFlow.getBlockState).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'request early exit' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'confirm early exit' }));
      await Promise.resolve();
    });

    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
    expect(NativeFlow.cancelRest).not.toHaveBeenCalled();
  });

  it('keeps waiting for authoritative RESTING after a matching ACCEPTED receipt', async () => {
    vi.mocked(NativeFlow.getBlockState)
      .mockResolvedValueOnce({
        ...cancelled,
        state: 'BLOCKED',
        restBeginRequestToken: 'begin-request-101',
        restBeginRequestStatusToken: 'begin-request-101',
        restBeginRequestStatus: 'ACCEPTED',
      })
      .mockResolvedValue({
        ...resting,
        restBeginRequestToken: 'begin-request-101',
        restBeginRequestStatusToken: 'begin-request-101',
        restBeginRequestStatus: 'ACCEPTED',
      });
    await mountRest();

    expect(mockNavigate).not.toHaveBeenCalled();
    expect(screen.queryByText(/休息模式启动失败|保护服务状态已变化/)).toBeNull();
    await act(async () => { await vi.advanceTimersByTimeAsync(300); });

    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(2);
    expect(screen.getByLabelText('remaining seconds')).toHaveTextContent('180');
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('does not trust a rejection receipt for a superseded begin token', async () => {
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue({
      ...cancelled,
      restBeginRequestToken: 'begin-request-new',
      restBeginRequestStatusToken: 'begin-request-old',
      restBeginRequestStatus: 'REJECTED',
      restBeginRequestErrorCode: 'PROTECTION_RUNTIME_UNHEALTHY',
    });
    await mountRest();

    expect(screen.getByText('当前休息请求已被新的页面请求接管，请返回面板确认最新状态。'))
      .toBeInTheDocument();
    expect(NativeFlow.cancelRest).not.toHaveBeenCalled();
  });

  it('does not accept RESTING without a committed start time', async () => {
    vi.mocked(NativeFlow.getBlockState)
      .mockResolvedValueOnce({ ...resting, restStartedAt: 0 })
      .mockResolvedValue(resting);
    await mountRest();
    await act(async () => { await vi.advanceTimersByTimeAsync(300); });
    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(2);
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('does not expose a completed timer when native clock evidence is invalid', async () => {
    const invalidRest = { ...resting, restClockValid: false, restRemainingMs: 0 };
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue(invalidRest);
    await mountRest();

    expect(screen.getByText('休息计时暂时无法确认，正在恢复保护状态。'))
      .toBeInTheDocument();
    expect(mockNavigate).not.toHaveBeenCalled();

    vi.mocked(NativeFlow.getBlockState).mockResolvedValueOnce(cancelled);
    await act(async () => { await vi.advanceTimersByTimeAsync(500); });
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true });
    expect(useStore.getState().blockState).toBe('IDLE');
  });

  it('continues checking when native BEGIN_REST takes longer than six seconds', async () => {
    let checks = 0;
    vi.mocked(NativeFlow.getBlockState).mockImplementation(async () => {
      checks++;
      return checks <= 25
        ? { ...resting, state: 'BLOCKED', restStartedAt: 0, restRequiredSeconds: 0 }
        : resting;
    });
    await mountRest();

    await act(async () => { await vi.advanceTimersByTimeAsync(6_500); });
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(screen.getByText('原生休息仍在启动，正在等待确认。')).toBeInTheDocument();

    await act(async () => { await vi.advanceTimersByTimeAsync(3_000); });
    expect(checks).toBeGreaterThan(25);
    expect(screen.queryByText('原生休息仍在启动，正在等待确认。')).toBeNull();
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('allows a transient prior GRACE snapshot while BEGIN_REST is being applied', async () => {
    vi.mocked(NativeFlow.getBlockState)
      .mockResolvedValueOnce(grace)
      .mockResolvedValue(resting);
    await mountRest();

    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(1);
    await act(async () => { await vi.advanceTimersByTimeAsync(300); });

    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(2);
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(useStore.getState().blockState).toBe('RESTING');
  });

  it('bounds persistent GRACE recovery and returns to the dashboard with authoritative state', async () => {
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue(grace);
    await mountRest();

    await act(async () => { await vi.advanceTimersByTimeAsync(6_500); });

    expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true });
    expect(useStore.getState().blockState).toBe('GRACE');
    expect(useStore.getState().graceUntil).toBe(grace.graceUntil);
    cleanup();
    expect(NativeFlow.cancelRest).toHaveBeenCalledWith({
      beginRequestToken: 'begin-request-101',
    });
  });

  it('reconciles native anti-cheat cancellation and avoids a competing unmount cancel', async () => {
    vi.mocked(NativeFlow.getBlockState).mockResolvedValueOnce(resting).mockResolvedValue(cancelled);
    await mountRest();

    await act(async () => { await vi.advanceTimersByTimeAsync(2_100); });
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true });
    expect(useStore.getState().blockState).toBe('IDLE');
    cleanup();
    expect(NativeFlow.cancelRest).not.toHaveBeenCalled();
  });

  it.each(['IDLE', 'BLOCKED'] as const)(
    'keeps polling after a valid REST clock becomes invalid and reconciles %s without an unmount cancel',
    async (state) => {
      const invalidRest = { ...resting, restClockValid: false, restRemainingMs: 0 };
      vi.mocked(NativeFlow.getBlockState).mockResolvedValueOnce(resting).mockResolvedValue(invalidRest);
      await mountRest();

      await act(async () => { await vi.advanceTimersByTimeAsync(2_100); });
      expect(screen.getByLabelText('native clock valid')).toHaveTextContent('false');
      expect(screen.getByLabelText('remaining seconds')).toHaveTextContent('0');
      fireEvent.click(screen.getByRole('button', { name: 'complete rest' }));
      expect(NativeFlow.completeRestAndUnlock).not.toHaveBeenCalled();

      vi.mocked(NativeFlow.getBlockState).mockResolvedValue({ ...cancelled, state });
      await act(async () => { await vi.advanceTimersByTimeAsync(2_100); });

      expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true });
      expect(useStore.getState().blockState).toBe(state);
      expect(NativeFlow.beginRest).toHaveBeenCalledTimes(1);
      cleanup();
      expect(NativeFlow.cancelRest).not.toHaveBeenCalled();
    },
  );

  it('resumes the same REST session timer and clears the recovery error after a valid-invalid-valid clock sequence', async () => {
    const invalidRest = { ...resting, restClockValid: false, restRemainingMs: 0 };
    vi.mocked(NativeFlow.getBlockState).mockResolvedValueOnce(resting).mockResolvedValue(invalidRest);
    await mountRest();

    await act(async () => { await vi.advanceTimersByTimeAsync(2_100); });
    expect(screen.getByText('休息计时暂时无法确认，正在恢复保护状态。')).toBeInTheDocument();
    expect(screen.getByLabelText('native clock valid')).toHaveTextContent('false');
    fireEvent.click(screen.getByRole('button', { name: 'complete rest' }));
    expect(NativeFlow.completeRestAndUnlock).not.toHaveBeenCalled();

    vi.mocked(NativeFlow.getBlockState).mockResolvedValue({ ...resting, restRemainingMs: 60_000 });
    await act(async () => {
      window.dispatchEvent(new Event('focus'));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(3);
    expect(screen.getByLabelText('native clock valid')).toHaveTextContent('true');
    expect(screen.getByLabelText('remaining seconds')).toHaveTextContent('60');
    expect(screen.queryByText(/休息计时暂时无法确认/)).toBeNull();
    await act(async () => { await vi.advanceTimersByTimeAsync(1_000); });
    expect(screen.getByLabelText('remaining seconds')).toHaveTextContent('59');
    expect(NativeFlow.beginRest).toHaveBeenCalledTimes(1);
    expect(NativeFlow.completeRestAndUnlock).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it.each(['IDLE', 'BLOCKED'] as const)(
    'stops waiting when a matching ACCEPTED receipt is followed by stable %s state',
    async (state) => {
      const endedState = {
        ...cancelled,
        state,
        restBeginRequestToken: '',
        restBeginRequestStatusToken: 'begin-request-101',
        restBeginRequestStatus: 'ACCEPTED' as const,
      };
      vi.mocked(NativeFlow.getBlockState)
        .mockResolvedValueOnce(endedState)
        .mockResolvedValue(endedState);
      await mountRest();

      expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(2);
      expect(useStore.getState().blockState).toBe(state);
      expect(screen.getByText(
        '原生服务已接受休息请求，但当前休息状态随后结束或发生变化。请返回面板确认保护状态。',
      )).toBeInTheDocument();
      expect(mockNavigate).not.toHaveBeenCalled();

      fireEvent.click(screen.getByRole('button', { name: 'request early exit' }));
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'confirm early exit' }));
        await Promise.resolve();
      });
      expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
      cleanup();
      expect(NativeFlow.cancelRest).not.toHaveBeenCalled();
    },
  );

  it('does not resume or settle a different native REST session after clock recovery', async () => {
    const invalidRest = { ...resting, restClockValid: false, restRemainingMs: 0 };
    vi.mocked(NativeFlow.getBlockState).mockResolvedValueOnce(resting).mockResolvedValue(invalidRest);
    await mountRest();
    await act(async () => { await vi.advanceTimersByTimeAsync(2_100); });

    vi.mocked(NativeFlow.getBlockState).mockResolvedValue({ ...resting, restSessionId: 202, restRemainingMs: 60_000 });
    await act(async () => {
      window.dispatchEvent(new Event('focus'));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(3);
    expect(screen.getByLabelText('native clock valid')).toHaveTextContent('false');
    expect(screen.getByLabelText('remaining seconds')).toHaveTextContent('0');
    fireEvent.click(screen.getByRole('button', { name: 'complete rest' }));
    expect(NativeFlow.completeRestAndUnlock).not.toHaveBeenCalled();
    expect(NativeFlow.beginRest).toHaveBeenCalledTimes(1);
  });

  it('keeps the REST page open when native cancellation cannot be confirmed', async () => {
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue(resting);
    vi.mocked(NativeFlow.cancelRest).mockRejectedValue(
      new Error('取消未确认，休息状态未改变。')
    );
    await mountRest();

    fireEvent.click(screen.getByRole('button', { name: 'request early exit' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'confirm early exit' }));
      await Promise.resolve();
    });

    expect(NativeFlow.cancelRest).toHaveBeenCalledWith({
      beginRequestToken: 'begin-request-101',
    });
    expect(screen.getByText('取消未确认，休息状态未改变。')).toBeInTheDocument();
    expect(useStore.getState().blockState).toBe('RESTING');
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('allows exit when cancellation fails but native state confirms the begin request was rejected', async () => {
    const rejectedBegin = {
      ...cancelled,
      state: 'BLOCKED' as const,
      blockedPackage: 'tv.danmaku.bili',
      restBeginRequestToken: 'begin-request-101',
      restBeginRequestStatusToken: 'begin-request-101',
      restBeginRequestStatus: 'REJECTED' as const,
    };
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue({
      ...cancelled,
      state: 'BLOCKED',
      restBeginRequestToken: 'begin-request-101',
    });
    vi.mocked(NativeFlow.cancelRest).mockRejectedValue(
      new Error('休息状态或请求已变化，未退出休息模式，请返回后重试。')
    );
    await mountRest();

    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
    expect(NativeFlow.cancelRest).toHaveBeenCalledTimes(1);

    vi.mocked(NativeFlow.getBlockState).mockResolvedValueOnce(rejectedBegin);
    fireEvent.click(screen.getByRole('button', { name: 'request early exit' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'confirm early exit' }));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(NativeFlow.cancelRest).toHaveBeenCalledTimes(2);
    expect(useStore.getState().blockState).toBe('BLOCKED');
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
  });

  it.each([
    [
      'a mismatched receipt token',
      {
        ...cancelled,
        state: 'BLOCKED' as const,
        restBeginRequestToken: 'begin-request-101',
        restBeginRequestStatusToken: 'begin-request-old',
        restBeginRequestStatus: 'REJECTED' as const,
      },
    ],
    [
      'a matching token with a non-terminal receipt',
      {
        ...cancelled,
        state: 'BLOCKED' as const,
        restBeginRequestToken: 'begin-request-101',
        restBeginRequestStatusToken: 'begin-request-101',
        restBeginRequestStatus: 'PENDING' as const,
      },
    ],
    [
      'a rejection receipt while the native state is still RESTING',
      {
        ...resting,
        restBeginRequestToken: 'begin-request-101',
        restBeginRequestStatusToken: 'begin-request-101',
        restBeginRequestStatus: 'REJECTED' as const,
      },
    ],
    [
      'a stale rejection receipt after another request took over',
      {
        ...cancelled,
        state: 'BLOCKED' as const,
        restBeginRequestToken: 'begin-request-new',
        restBeginRequestStatusToken: 'begin-request-101',
        restBeginRequestStatus: 'REJECTED' as const,
      },
    ],
  ])('does not navigate after cancellation failure with %s', async (_reason, finalState) => {
    vi.mocked(NativeFlow.getBlockState).mockResolvedValue({
      ...cancelled,
      state: 'BLOCKED',
      blockedPackage: 'tv.danmaku.bili',
      restBeginRequestToken: 'begin-request-101',
    });
    vi.mocked(NativeFlow.cancelRest).mockRejectedValue(
      new Error('休息状态或请求已变化，未退出休息模式，请返回后重试。')
    );
    await mountRest();

    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });
    expect(NativeFlow.cancelRest).toHaveBeenCalledTimes(1);
    vi.mocked(NativeFlow.getBlockState).mockResolvedValueOnce(finalState);

    fireEvent.click(screen.getByRole('button', { name: 'request early exit' }));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'confirm early exit' }));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(NativeFlow.cancelRest).toHaveBeenCalledTimes(2);
    expect(screen.getByText('休息状态或请求已变化，未退出休息模式，请返回后重试。'))
      .toBeInTheDocument();
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('checks native state again when the WebView regains focus', async () => {
    vi.mocked(NativeFlow.getBlockState).mockResolvedValueOnce(resting).mockResolvedValue(cancelled);
    await mountRest();

    await act(async () => {
      window.dispatchEvent(new Event('focus'));
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard', { replace: true });
  });

  it('cancels a pending begin with its bound token if the page unmounts before confirmation', async () => {
    let resolveBegin!: (result: { beginRequestToken: string }) => void;
    vi.mocked(NativeFlow.beginRest).mockReturnValue(new Promise(resolve => {
      resolveBegin = resolve;
    }));

    await mountRest();
    cleanup();

    await act(async () => {
      resolveBegin({ beginRequestToken: 'begin-request-pending' });
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(NativeFlow.cancelRest).toHaveBeenCalledWith({
      beginRequestToken: 'begin-request-pending',
    });
  });
});
