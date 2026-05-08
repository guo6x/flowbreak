// src/hooks/useStore.ts
import { useState, useEffect, useCallback } from 'react';
import * as storage from '../backend/storage';
import { calculateFatigueScore, getInterventionLevel, INITIAL_METRICS, FatigueMetrics, InterventionLevel } from '../backend/fatigueEngine';
import { Capacitor } from '@capacitor/core';
import { NativeFlow } from '../backend/nativeFlow';

// ===== Fatigue Engine Hook =====
export function useFatigueEngine(isActive: boolean) {
  const [score, setScore] = useState(0);
  const [level, setLevel] = useState<InterventionLevel>('NONE');
  const [, setMetrics] = useState<FatigueMetrics>({ ...INITIAL_METRICS });
  const [elapsed, setElapsed] = useState(0); // seconds of active session

  const reset = useCallback(() => {
    setMetrics({ ...INITIAL_METRICS, usageDuration: 0 });
    setScore(0);
    setLevel('NONE');
    setElapsed(0);
  }, []);

  useEffect(() => {
    if (!isActive) {
      reset();
      return;
    }

    const interval = setInterval(async () => {
      let realMins = 0;
      let simulated = false;

      if (Capacitor.isNativePlatform()) {
        try {
          const stats = await NativeFlow.getUsageStats();
          realMins = stats.screenTimeSeconds / 60;
        } catch (e) {
          simulated = true;
        }
      } else {
        simulated = true;
      }

      setElapsed(prev => {
        const next = prev + 3;
        const mins = simulated ? (next / 60) : realMins;

        // Simulate behavior degrading (for demo purposes)
        setMetrics(m => {
          const updated = { ...m, usageDuration: mins, timeFactor: new Date().getHours() };
          if (mins > 10) updated.interactionFrequency = Math.max(0.2, m.interactionFrequency - 0.02);
          if (mins > 15) updated.appSwitchFrequency = Math.max(0.05, m.appSwitchFrequency - 0.01);
          const s = calculateFatigueScore(updated);
          setScore(s);
          setLevel(getInterventionLevel(s));
          return updated;
        });

        return simulated ? next : Math.floor(realMins * 60);
      });
    }, 3000);

    return () => clearInterval(interval);
  }, [isActive]);

  return { score, level, elapsed, reset };
}

// ===== Profile Hook =====
export function useProfile() {
  const [profile, setProfile] = useState(storage.getProfile());

  const update = useCallback((patch: Partial<storage.UserProfile>) => {
    storage.saveProfile(patch);
    setProfile(storage.getProfile());
  }, []);

  return { profile, update };
}

// ===== Stats Hook =====
export function useTodayStats() {
  const [stats, setStats] = useState(storage.getTodayStats());

  const refresh = useCallback(() => {
    setStats(storage.getTodayStats());
  }, []);

  useEffect(() => {
    const interval = setInterval(refresh, 5000);
    return () => clearInterval(interval);
  }, [refresh]);

  return { stats, refresh };
}

// ===== Achievements Hook =====
export function useAchievements() {
  const [achievements, setAchievements] = useState(storage.getAchievements());
  const [points, setPoints] = useState(storage.getPoints());
  const [streak, setStreak] = useState(storage.getStreak());

  const refresh = useCallback(() => {
    setAchievements(storage.getAchievements());
    setPoints(storage.getPoints());
    setStreak(storage.getStreak());
  }, []);

  const unlock = useCallback((id: string) => {
    const result = storage.unlockAchievement(id);
    if (result) {
      storage.addPoints(10);
      refresh();
    }
    return result;
  }, [refresh]);

  return { achievements, points, streak, unlock, refresh };
}
