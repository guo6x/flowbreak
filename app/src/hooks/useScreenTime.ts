// src/hooks/useScreenTime.ts
// Web-only fallback. On native platforms (Android), real screen time comes
// from NativeFlow.getUsageStats() via UsageStatsManager.
// Tracks real screen time on web via Page Visibility API and idle detection.
import { useState, useEffect, useRef } from 'react';

const IDLE_TIMEOUT_MS = 60_000;

export function useScreenTime(isActive: boolean) {
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [isIdle, setIsIdle] = useState(false);

  const stateRef = useRef({
    pageVisible: !document.hidden,
    isIdle: false,
    lastActivity: Date.now(),
  });

  useEffect(() => {
    if (!isActive) {
      setElapsedSeconds(0);
      setIsIdle(false);
      stateRef.current = { pageVisible: !document.hidden, isIdle: false, lastActivity: Date.now() };
      return;
    }

    stateRef.current.lastActivity = Date.now();

    const interval = setInterval(() => {
      const now = Date.now();
      const idle = now - stateRef.current.lastActivity >= IDLE_TIMEOUT_MS;

      if (idle !== stateRef.current.isIdle) {
        stateRef.current.isIdle = idle;
        setIsIdle(idle);
      }

      if (stateRef.current.pageVisible && !idle) {
        setElapsedSeconds(prev => prev + 1);
      }
    }, 1000);

    const onVisibilityChange = () => {
      stateRef.current.pageVisible = !document.hidden;
    };

    const onActivity = () => {
      stateRef.current.lastActivity = Date.now();
      if (stateRef.current.isIdle) {
        stateRef.current.isIdle = false;
        setIsIdle(false);
      }
    };

    document.addEventListener('visibilitychange', onVisibilityChange);
    document.addEventListener('mousemove', onActivity);
    document.addEventListener('keydown', onActivity);
    document.addEventListener('touchstart', onActivity);

    return () => {
      clearInterval(interval);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      document.removeEventListener('mousemove', onActivity);
      document.removeEventListener('keydown', onActivity);
      document.removeEventListener('touchstart', onActivity);
    };
  }, [isActive]);

  return { elapsedSeconds, isIdle };
}
