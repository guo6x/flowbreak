// src/hooks/useStore.ts
// Reactive store wrapping storage layer — all UI reads go through here.
import { create } from 'zustand';
import { useEffect, useRef } from 'react';
import * as storage from '../backend/storage';
import { FatigueMetrics, InterventionLevel } from '../backend/fatigueEngine';

// Re-export types
export type { FatigueMetrics, InterventionLevel };
export type { UserProfile, DailyStats, Achievement, ActivityEvent } from '../backend/storage';

const DEMO_APP_POOL = ['抖音', 'B站', '快手', '微信', '小红书'];

// ============================================================
// Reactive store — wraps storage, keeps UI in sync
// ============================================================
interface AppState {
  profile: storage.UserProfile;
  todayStats: storage.DailyStats;
  achievements: storage.Achievement[];
  points: number;
  streak: number;

  // Global monitoring state — shared across all pages
  isMonitoring: boolean;
  snoozeUntil: number;
  fatigueScore: number;
  fatigueLevel: InterventionLevel;
  currentAppName: string;
  continuousSessionSeconds: number;

  updateProfile: (patch: Partial<storage.UserProfile>) => void;
  addScreenTime: (seconds: number, app?: string) => void;
  incrementContinuousSession: (seconds: number) => void;
  resetContinuousSession: () => void;
  snoozeContinuousSession: (seconds: number) => void;
  setContinuousSessionSeconds: (seconds: number) => void;
  completeRestActivity: (type: 'eye' | 'stretch' | 'breathe', durationSec: number) => void;
  logIntervention: (level: string) => void;
  markStatsViewed: () => void;
  startSession: () => void;
  getSessionDuration: () => number;
  unlockAchievement: (id: string) => storage.Achievement | null;
  setMonitoring: (v: boolean) => void;
  setSnoozeUntil: (ts: number) => void;
  setFatigue: (score: number, level: InterventionLevel) => void;
  setCurrentAppName: (name: string) => void;
  setScreenTime: (seconds: number) => void;
}

export const useStore = create<AppState>((_set, _get) => ({
  profile: storage.getProfile(),
  todayStats: storage.getTodayStats(),
  achievements: storage.getAchievements(),
  points: storage.getPoints(),
  streak: storage.getStreak(),

  isMonitoring: true,
  snoozeUntil: 0,
  fatigueScore: 0,
  fatigueLevel: 'NONE' as InterventionLevel,
  currentAppName: '',
  continuousSessionSeconds: 0,

  updateProfile: (patch) => {
    storage.saveProfile(patch);
    _set({ profile: storage.getProfile() });
  },

  addScreenTime: (seconds, app) => {
    storage.addScreenTime(seconds, app);
    _set({ todayStats: storage.getTodayStats() });
  },

  incrementContinuousSession: (seconds) => {
    _set((state) => ({ continuousSessionSeconds: state.continuousSessionSeconds + seconds }));
  },

  resetContinuousSession: () => {
    _set({ continuousSessionSeconds: 0 });
  },

  snoozeContinuousSession: (seconds) => {
    _set((state) => ({ continuousSessionSeconds: Math.max(0, state.continuousSessionSeconds - seconds) }));
  },

  setContinuousSessionSeconds: (seconds) => {
    _set({ continuousSessionSeconds: seconds });
  },

  completeRestActivity: (type, durationSec) => {
    storage.completeRestActivity(type, durationSec);
    _set({
      todayStats: storage.getTodayStats(),
      points: storage.getPoints(),
      streak: storage.getStreak(),
      achievements: storage.getAchievements(),
    });
  },

  logIntervention: (level) => {
    storage.logIntervention(level);
    _set({ todayStats: storage.getTodayStats() });
  },

  markStatsViewed: () => {
    storage.markStatsViewed();
    _set({ achievements: storage.getAchievements() });
  },

  startSession: () => storage.startSession(),

  getSessionDuration: () => storage.getSessionDuration(),

  unlockAchievement: (id) => {
    const achievements = _get().achievements;
    const alreadyUnlocked = achievements.find(a => a.id === id)?.unlocked;
    const result = storage.unlockAchievement(id);
    if (result || !alreadyUnlocked) {
      // Award points if newly unlocked, or if called explicitly for an achievement
      // that was unlocked via evaluateAchievements (race condition fix)
      storage.addPoints(10);
      _set({ achievements: storage.getAchievements(), points: storage.getPoints() });
    }
    return result;
  },

  setMonitoring: (v) => _set({ isMonitoring: v, currentAppName: v ? _get().currentAppName : '' }),
  setSnoozeUntil: (ts) => _set({ snoozeUntil: ts }),
  setFatigue: (score, level) => _set({ fatigueScore: score, fatigueLevel: level }),
  setCurrentAppName: (name) => _set({ currentAppName: name }),

  setScreenTime: (seconds) => {
    storage.setTodayScreenTime(seconds);
    _set({ todayStats: storage.getTodayStats() });
  },
}));

// ============================================================
// Demo simulation hook — drives fake screen time on web
// ============================================================
export function useDemoSimulation(isActive: boolean, refreshInterval: number) {
  const cursorRef = useRef(0);

  useEffect(() => {
    if (!isActive) return;

    useStore.getState().startSession();
    const timer = setInterval(() => {
      const appName = DEMO_APP_POOL[cursorRef.current % DEMO_APP_POOL.length];
      useStore.getState().addScreenTime(5, appName);
      cursorRef.current += 1;
    }, refreshInterval);

    return () => clearInterval(timer);
  }, [isActive, refreshInterval]);
}

// ============================================================
// Fatigue engine hook — driven by real screen time from store
// ============================================================
export function useFatigueEngine() {
  const score = useStore(s => s.fatigueScore);
  const level = useStore(s => s.fatigueLevel);
  const continuousSeconds = useStore(s => s.continuousSessionSeconds);

  return { score, level, elapsed: continuousSeconds };
}
