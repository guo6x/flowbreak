import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import RestActivityView, { type RestActivityViewProps } from '../RestActivityView';
import { activities } from '../activities';

function renderActivity(overrides: Partial<RestActivityViewProps> = {}) {
  const props: RestActivityViewProps = {
    selectedBackground: 0, ambientParticles: [], activity: activities[0], activityIdx: 0, slideDirection: 1,
    timeLeft: 60, progress: 0.5, breathColor: '#B9D8C3', stepIdx: 0, stepProgress: 30,
    isMuted: false, isPaused: false, isNative: false, operationError: '', completing: false, confirmingEarlyExit: false, closing: false,
    onClose: vi.fn(), onToggleMute: vi.fn(), onTogglePause: vi.fn(), onSwitchActivity: vi.fn(), onComplete: vi.fn(), onCancelEarlyExit: vi.fn(), onConfirmEarlyExit: vi.fn(), ...overrides,
  };
  render(<RestActivityView {...props} />);
  return props;
}

describe('RestActivityView accessible controls', () => {
  it('names icon controls and exposes progress without announcing every timer tick', () => {
    const props = renderActivity();
    expect(screen.getByRole('timer', { name: '剩余休息时间' })).toHaveAttribute('aria-live', 'off');
    expect(screen.getByRole('progressbar', { name: '休息进度' })).toHaveAttribute('aria-valuenow', '50');
    fireEvent.click(screen.getByRole('button', { name: '下一个休息活动' }));
    expect(props.onSwitchActivity).toHaveBeenCalledWith(1);
    fireEvent.click(screen.getByRole('button', { name: '退出休息模式' }));
    expect(props.onClose).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '暂停休息计时' }));
    expect(props.onTogglePause).toHaveBeenCalledTimes(1);
  });

  it('never offers a pause control for the native timestamp-based timer', () => {
    renderActivity({ isNative: true });
    expect(screen.queryByRole('button', { name: /休息计时/ })).not.toBeInTheDocument();
    expect(screen.getByText('计时由设备校验')).toBeInTheDocument();
  });

  it('blocks exit while completion is being settled', () => {
    const props = renderActivity({ completing: true, timeLeft: 0, progress: 1 });
    const exit = screen.getByRole('button', { name: '退出休息模式' });
    expect(exit).toBeDisabled();
    fireEvent.click(exit);
    expect(props.onClose).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: '正在解锁...' })).toBeDisabled();
  });

  it('keeps early-exit consequences visible and both choices available', () => {
    const props = renderActivity({ confirmingEarlyExit: true });
    expect(screen.getByRole('group', { name: '提前结束不会解锁' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '继续休息' }));
    expect(props.onCancelEarlyExit).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: '确认提前结束' }));
    expect(props.onConfirmEarlyExit).toHaveBeenCalledTimes(1);
  });
});
