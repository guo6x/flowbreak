// src/hooks/useStore.ts
// Reactive store wrapping storage layer — all UI reads go through here.
import { create } from 'zustand';
import * as storage from '../backend/storage';
import { InterventionLevel } from '../backend/fatigueEngine';
import type { BlockState } from '../backend/nativeFlow';

// Re-export types
export type { InterventionLevel };
export type { UserProfile, DailyStats, Achievement, ActivityEvent } from '../backend/storage';

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
  blockState: BlockState;
  graceUntil: number;
  blockedPackage: string;
  serviceError: string;

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
  setBlockState: (state: BlockState, graceUntil: number, blockedPackage: string) => void;
  setServiceError: (message: string) => void;
}

const initialProfile = storage.getProfile();

export const useStore = create<AppState>((_set, _get) => ({
  profile: initialProfile,
  todayStats: storage.getTodayStats(),
  achievements: storage.getAchievements(),
  points: storage.getPoints(),
  streak: storage.getStreak(),

  isMonitoring: initialProfile.onboardingDone
    && initialProfile.targetApps.length > 0
    && storage.getMonitoringEnabled(),
  snoozeUntil: 0,
  fatigueScore: 0,
  fatigueLevel: 'NONE' as InterventionLevel,
  currentAppName: '',
  continuousSessionSeconds: 0,
  blockState: 'IDLE',
  graceUntil: 0,
  blockedPackage: '',
  serviceError: '',

  updateProfile: (patch) => {
    storage.saveProfile(patch);
    _set({ profile: storage.getProfile() });
  },

  addScreenTime: (seconds, app) => {
    storage.addScreenTime(seconds, app);
    _set({
      todayStats: storage.getTodayStats(),
      achievements: storage.getAchievements(),
      points: storage.getPoints(),
      streak: storage.getStreak(),
    });
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
    _set({
      todayStats: storage.getTodayStats(),
      achievements: storage.getAchievements(),
      points: storage.getPoints(),
    });
  },

  markStatsViewed: () => {
    storage.markStatsViewed();
    _set({
      achievements: storage.getAchievements(),
      points: storage.getPoints(),
    });
  },

  startSession: () => storage.startSession(),

  getSessionDuration: () => storage.getSessionDuration(),

  unlockAchievement: (id) => {
    const result = storage.unlockAchievement(id);
    _set({ achievements: storage.getAchievements(), points: storage.getPoints() });
    return result;
  },

  setMonitoring: (v) => {
    storage.saveMonitoringEnabled(v);
    _set({ isMonitoring: v, currentAppName: v ? _get().currentAppName : '' });
  },
  setSnoozeUntil: (ts) => _set({ snoozeUntil: ts }),
  setFatigue: (score, level) => _set({ fatigueScore: score, fatigueLevel: level }),
  setCurrentAppName: (name) => _set({ currentAppName: name }),

  setScreenTime: (seconds) => {
    storage.setTodayScreenTime(seconds);
    _set({
      todayStats: storage.getTodayStats(),
      achievements: storage.getAchievements(),
      points: storage.getPoints(),
    });
  },
  setBlockState: (blockState, graceUntil, blockedPackage) => _set({
    blockState,
    graceUntil,
    blockedPackage,
  }),
  setServiceError: (serviceError) => _set({ serviceError }),
}));
