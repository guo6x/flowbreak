import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import RestMode from '../RestMode';
import { NativeFlow } from '../../backend/nativeFlow';
import { useStore } from '../../hooks/useStore';

const mockNavigate = vi.fn();

vi.mock('react-router', async () => {
  const actual = await vi.importActual<typeof import('react-router')>('react-router');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => true } }));

vi.mock('../../backend/nativeFlow', () => ({
  NativeFlow: {
    beginRest: vi.fn(),
    getBlockState: vi.fn(),
    cancelRest: vi.fn(),
  },
}));

vi.mock('../rest-mode/useRestAudio', () => ({ useRestAudio: () => ({ stopAmbient: vi.fn() }) }));
vi.mock('../rest-mode/ambientAudio', () => ({ playChime: vi.fn() }));
vi.mock('../rest-mode/RestActivityView', () => ({
  default: ({
    operationError,
    confirmingEarlyExit,
    onClose,
    onConfirmEarlyExit,
  }: {
    operationError: string;
    confirmingEarlyExit: boolean;
    onClose: () => void;
    onConfirmEarlyExit: () => void;
  }) => (
    <div>
      <div>{operationError}</div>
      <button onClick={onClose}>request early exit</button>
      {confirmingEarlyExit && (
        <button onClick={onConfirmEarlyExit}>confirm early exit</button>
      )}
    </div>
  ),
}));
vi.mock('../rest-mode/RestCompleteView', () => ({ default: () => <div>rest complete</div> }));

const resting = {
  state: 'RESTING' as const,
  sessionSeconds: 0,
  graceUntil: 0,
  blockedPackage: '',
  restStartedAt: Date.now(),
  restRequiredSeconds: 180,
  restSessionId: 101,
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
    mockNavigate.mockReset();
    vi.mocked(NativeFlow.beginRest).mockReset().mockResolvedValue({
      beginRequestToken: 'begin-request-101',
    });
    vi.mocked(NativeFlow.getBlockState).mockReset();
    vi.mocked(NativeFlow.cancelRest).mockReset().mockResolvedValue(undefined);
    useStore.setState({ blockState: 'RESTING', graceUntil: 0 });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
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

  it('does not accept RESTING without a committed start time', async () => {
    vi.mocked(NativeFlow.getBlockState)
      .mockResolvedValueOnce({ ...resting, restStartedAt: 0 })
      .mockResolvedValue(resting);
    await mountRest();
    await act(async () => { await vi.advanceTimersByTimeAsync(300); });
    expect(NativeFlow.getBlockState).toHaveBeenCalledTimes(2);
    expect(mockNavigate).not.toHaveBeenCalled();
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

  it('keeps the REST page open when native cancellation cannot be confirmed', async () => {
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
