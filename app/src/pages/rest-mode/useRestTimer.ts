// Rest countdown timer hook.
//
// Behaviour preserved exactly from the original RestMode.tsx:
//  - Native: derives remaining seconds from a wall-clock `restEndsAt` timestamp
//    via setInterval(1s) so backgrounding the WebView cannot shorten a rest.
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
  /** Wall-clock end timestamp (ms) from the native service, or null on web. */
  restEndsAt: number | null;
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
  const { totalDuration, initialSeconds, restEndsAt, isNative, active, isPaused } = options;
  const [timeLeft, setTimeLeft] = useState(initialSeconds);

  useEffect(() => {
    if (!active) return;
    if (isPaused && !isNative) return;

    if (isNative && restEndsAt !== null) {
      const update = () => setTimeLeft(Math.max(0, Math.ceil((restEndsAt - Date.now()) / 1000)));
      update();
      const timer = setInterval(update, 1000);
      return () => clearInterval(timer);
    }
    if (timeLeft <= 0) return;
    const timer = setTimeout(() => setTimeLeft(t => t - 1), 1000);
    return () => clearTimeout(timer);
  }, [timeLeft, active, isPaused, isNative, restEndsAt]);

  const progress = 1 - timeLeft / totalDuration;
  return { timeLeft, setTimeLeft, progress };
}
