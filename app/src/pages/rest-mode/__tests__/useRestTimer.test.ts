// Unit tests for the useRestTimer hook.
//
// These tests pin down the exact timer semantics inherited from the original
// RestMode.tsx page so the refactor cannot regress them:
//   - Web mode decrements via setTimeout(1s) re-armed on each tick.
//   - Native mode derives remaining seconds from a wall-clock restEndsAt.
//   - Pause stops the web timer; native has no pause.
//   - The hook does NOT auto-fire a completion callback when the timer
//     reaches zero (the page surfaces a "完成休息" button the user must tap;
//     the page's `completing` state guards re-entry).

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useRestTimer } from '../useRestTimer';

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useRestTimer — web mode', () => {
  it('exposes the initial remaining seconds and a derived progress', () => {
    const { result } = renderHook(() =>
      useRestTimer({
        totalDuration: 60,
        initialSeconds: 60,
        restEndsAt: null,
        isNative: false,
        active: true,
        isPaused: false,
      }),
    );
    expect(result.current.timeLeft).toBe(60);
    // progress = 1 - timeLeft / totalDuration
    expect(result.current.progress).toBeCloseTo(0, 5);
  });

  it('decrements timeLeft by 1 each second when active and not paused', () => {
    const { result } = renderHook(() =>
      useRestTimer({
        totalDuration: 60,
        initialSeconds: 60,
        restEndsAt: null,
        isNative: false,
        active: true,
        isPaused: false,
      }),
    );
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(result.current.timeLeft).toBe(59);

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(result.current.timeLeft).toBe(58);
  });

  it('does not decrement while paused', () => {
    const { result } = renderHook(() =>
      useRestTimer({
        totalDuration: 60,
        initialSeconds: 60,
        restEndsAt: null,
        isNative: false,
        active: true,
        isPaused: true,
      }),
    );
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(result.current.timeLeft).toBe(60);
  });

  it('resumes decrementing when pause is cleared', () => {
    const { result, rerender } = renderHook(
      ({ isPaused }) =>
        useRestTimer({
          totalDuration: 60,
          initialSeconds: 60,
          restEndsAt: null,
          isNative: false,
          active: true,
          isPaused,
        }),
      { initialProps: { isPaused: true } },
    );

    // Paused — no movement.
    act(() => vi.advanceTimersByTime(3000));
    expect(result.current.timeLeft).toBe(60);

    // Resume — timer should start ticking again.
    rerender({ isPaused: false });
    act(() => vi.advanceTimersByTime(1000));
    expect(result.current.timeLeft).toBe(59);
  });

  it('stops decrementing when active becomes false (showReward)', () => {
    const { result, rerender } = renderHook(
      ({ active }) =>
        useRestTimer({
          totalDuration: 60,
          initialSeconds: 60,
          restEndsAt: null,
          isNative: false,
          active,
          isPaused: false,
        }),
      { initialProps: { active: true } },
    );

    // Advance one tick at a time so the re-armed setTimeout is scheduled
    // within act()'s flush cycle.
    act(() => vi.advanceTimersByTime(1000));
    expect(result.current.timeLeft).toBe(59);
    act(() => vi.advanceTimersByTime(1000));
    expect(result.current.timeLeft).toBe(58);

    rerender({ active: false });
    act(() => vi.advanceTimersByTime(5000));
    expect(result.current.timeLeft).toBe(58);
  });

  it('does not fire any callback when the timer reaches zero', () => {
    // The original page surfaced a "完成休息" button at timeLeft<=0 and only
    // completed on user click. The hook preserves that contract: no
    // auto-completion side-effect at zero.
    const { result } = renderHook(() =>
      useRestTimer({
        totalDuration: 2,
        initialSeconds: 2,
        restEndsAt: null,
        isNative: false,
        active: true,
        isPaused: false,
      }),
    );

    act(() => vi.advanceTimersByTime(1000));
    expect(result.current.timeLeft).toBe(1);

    act(() => vi.advanceTimersByTime(1000));
    expect(result.current.timeLeft).toBe(0);

    // Wait another tick — nothing changes, no callback was registered.
    act(() => vi.advanceTimersByTime(3000));
    expect(result.current.timeLeft).toBe(0);
  });

  it('clamps progress to 1 once the timer reaches zero', () => {
    const { result } = renderHook(() =>
      useRestTimer({
        totalDuration: 2,
        initialSeconds: 2,
        restEndsAt: null,
        isNative: false,
        active: true,
        isPaused: false,
      }),
    );

    // Tick one second at a time so the re-armed setTimeout is scheduled
    // inside act()'s flush window.
    act(() => vi.advanceTimersByTime(1000));
    expect(result.current.timeLeft).toBe(1);
    act(() => vi.advanceTimersByTime(1000));
    expect(result.current.timeLeft).toBe(0);
    // progress = 1 - 0/2 = 1
    expect(result.current.progress).toBe(1);
  });

  it('stops ticking after unmount', () => {
    const { result, unmount } = renderHook(() =>
      useRestTimer({
        totalDuration: 60,
        initialSeconds: 60,
        restEndsAt: null,
        isNative: false,
        active: true,
        isPaused: false,
      }),
    );

    unmount();
    // Advancing time after unmount must not throw (no setState on unmounted).
    act(() => vi.advanceTimersByTime(10000));
    // result.current is the last rendered snapshot; it stays at 60.
    expect(result.current.timeLeft).toBe(60);
  });
});

describe('useRestTimer — native mode', () => {
  it('derives remaining seconds from restEndsAt via wall-clock time', () => {
    const now = Date.now();
    vi.setSystemTime(now);

    const { result } = renderHook(() =>
      useRestTimer({
        totalDuration: 60,
        initialSeconds: 60,
        restEndsAt: now + 60_000,
        isNative: true,
        active: true,
        isPaused: false,
      }),
    );

    // On mount the hook computes Math.max(0, Math.ceil((restEndsAt - now)/1000)).
    expect(result.current.timeLeft).toBe(60);

    // Advance wall clock by 5 seconds — timeLeft should drop to 55.
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(result.current.timeLeft).toBe(55);
  });

  it('does not pause on native even if isPaused is true (native has no pause button)', () => {
    const now = Date.now();
    vi.setSystemTime(now);

    const { result } = renderHook(() =>
      useRestTimer({
        totalDuration: 60,
        initialSeconds: 60,
        restEndsAt: now + 60_000,
        isNative: true,
        active: true,
        isPaused: true, // ignored on native
      }),
    );

    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(result.current.timeLeft).toBe(57);
  });

  it('clamps to zero once restEndsAt has passed', () => {
    const now = Date.now();
    vi.setSystemTime(now);

    const { result } = renderHook(() =>
      useRestTimer({
        totalDuration: 10,
        initialSeconds: 10,
        restEndsAt: now + 10_000,
        isNative: true,
        active: true,
        isPaused: false,
      }),
    );

    act(() => {
      vi.advanceTimersByTime(15_000);
    });
    expect(result.current.timeLeft).toBe(0);
  });
});
