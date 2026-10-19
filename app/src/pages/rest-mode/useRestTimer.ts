// Rest countdown timer hook.
//
// Behaviour preserved exactly from the original RestMode.tsx:
//  - Native: anchors the latest native remaining-time sample to performance.now()
//    so wall-clock changes cannot shorten or extend the displayed countdown.
//  - Web: decrements via setTimeout(1s) re-armed on each tick.
//  - Pauses only on web (native has no pause button).
//  - The hook does NOT auto-fire a completion callback when the timer reaches
//    zero. In the original page, completion is triggered by the user tapping
//    the "完成休息" button that appears once `timeLeft <= 0`; the page's own
//    `completing` state guards against re-entry. We keep that contract here.

import { useState, useEffect } from 'react';

export interface UseRestTimerOptions {
  /** Total rest duration in seconds (used for progress calculation). */
  totalDuration: number;
  /** Initial remaining seconds before native sync. */
  initialSeconds: number;
  /** Remaining milliseconds from the monotonic native clock, or null until synced. */
  nativeRemainingMs: number | null;
  /** Whether running on Capacitor native platform. */
  isNative: boolean;
  /** Whether the timer should be ticking (equivalent to !showReward && restReady). */
  active: boolean;
  /** Whether the user pressed pause (web only). */
  isPaused: boolean;
}

export interface UseRestTimerResult {
  timeLeft: number;
  setTimeLeft: React.Dispatch<React.SetStateAction<number>>;
  progress: number;
}

export function useRestTimer(options: UseRestTimerOptions): UseRestTimerResult {
  const {
    totalDuration,
    initialSeconds,
    nativeRemainingMs,
    isNative,
    active,
    isPaused,
  } = options;
  const [timeLeft, setTimeLeft] = useState(initialSeconds);

  useEffect(() => {
    if (!isNative || !active
        || nativeRemainingMs === null || !Number.isFinite(nativeRemainingMs)) return;
    const monotonicEnd = performance.now() + Math.max(0, nativeRemainingMs);
    const update = () => setTimeLeft(Math.max(
      0,
      Math.ceil((monotonicEnd - performance.now()) / 1000),
    ));
    update();
    const timer = setInterval(update, 1000);
    return () => clearInterval(timer);
  }, [isNative, active, nativeRemainingMs]);

  useEffect(() => {
    if (isNative || !active || isPaused || timeLeft <= 0) return;
    const timer = setTimeout(() => setTimeLeft(t => t - 1), 1000);
    return () => clearTimeout(timer);
  }, [timeLeft, active, isPaused, isNative]);

  const progress = 1 - timeLeft / totalDuration;
  return { timeLeft, setTimeLeft, progress };
}
