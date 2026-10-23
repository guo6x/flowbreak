import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import InterventionOverlay from '../InterventionOverlay';

const navigate = vi.hoisted(() => vi.fn());
vi.mock('react-router', () => ({ useNavigate: () => navigate }));
beforeEach(() => { navigate.mockReset(); });

describe('InterventionOverlay', () => {
  it('keeps the perception reminder non-blocking and keyboard-operable', () => {
    const snooze = vi.fn();
    const dismiss = vi.fn();
    render(<InterventionOverlay level="PERCEPTION" elapsed={1200} onDismiss={dismiss} onSnooze={snooze} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /关闭这次轻提醒/ }));
    expect(snooze).toHaveBeenCalledTimes(1);
    expect(dismiss).not.toHaveBeenCalled();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('preserves cognition reminder choices without stacking a second reminder over the card', () => {
    const dismiss = vi.fn();
    render(<InterventionOverlay level="COGNITION" elapsed={1500} onDismiss={dismiss} />);
    expect(screen.getByRole('heading', { name: '已经连续用了 25 分钟' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '稍后提醒' }));
    expect(dismiss).toHaveBeenCalledTimes(1);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('keeps action-dialog focus within its choices and has no dismiss shortcut', () => {
    render(<InterventionOverlay level="ACTION" elapsed={1800} onDismiss={vi.fn()} />);
    expect(screen.getByRole('dialog', { name: '是时候休息一下了' })).toHaveAttribute('aria-modal', 'true');
    expect(screen.queryByRole('button', { name: '关闭提示' })).not.toBeInTheDocument();
    const first = screen.getByRole('button', { name: /眼部放松/ });
    const last = screen.getByRole('button', { name: '开始休息' });
    expect(first).toHaveFocus();
    first.focus();
    fireEvent.keyDown(first, { key: 'Tab', shiftKey: true });
    expect(last).toHaveFocus();
    fireEvent.keyDown(last, { key: 'Tab' });
    expect(first).toHaveFocus();
  });

  it('preserves selected activity routing from the blocking action', () => {
    const dismiss = vi.fn();
    render(<InterventionOverlay level="ACTION" elapsed={1800} onDismiss={dismiss} />);
    fireEvent.click(screen.getByRole('button', { name: /身体拉伸/ }));
    expect(dismiss).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith('/rest', { state: { activityIdx: 1 } });
  });
});
