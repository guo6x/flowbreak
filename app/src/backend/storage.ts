// src/backend/storage.ts
// 本地持久化层 - MVP 数据模型与业务规则


export interface UserProfile {
  name: string;
  type: 'student' | 'worker' | 'other';
  dailyGoal: number; // 分钟
  sessionLimit: number; // 分钟
  restDuration: number; // 秒
  selectedBackground: number;
  onboardingDone: boolean;
}

export interface DailyStats {
  date: string;
  totalScreenTime: number; // 秒
  videoTime: number; // 秒
  restCount: number;
  interventionCount: number;
  focusMinutes: number;
}

export interface Achievement {
  id: string;
  title: string;
  description: string;
  icon: string;
  unlocked: boolean;
  unlockedAt?: string;
}

export interface ActivityEvent {
  id: string;
  at: string;
  type: 'screen' | 'rest' | 'intervention';
  app: string;
  durationSec: number;
}

type ActivityCounters = {
  eye: number;
  breathe: number;
  statsView: number;
};

const STORAGE_KEYS = {
  PROFILE: 'fb_profile',
  STATS: 'fb_stats',
  ACHIEVEMENTS: 'fb_achievements',
  STREAK: 'fb_streak',
  POINTS: 'fb_points',
  SESSION_START: 'fb_session_start',
  ACTIVITY: 'fb_activity',
  LAST_REST_DAY: 'fb_last_rest_day',
  COUNTERS: 'fb_counters',
};

const DEFAULT_PROFILE: UserProfile = {
  name: '',
  type: 'student',
  dailyGoal: 240,
  sessionLimit: 25,
  restDuration: 180,
  selectedBackground: 0,
  onboardingDone: false,
};

const DEFAULT_COUNTERS: ActivityCounters = {
  eye: 0,
  breathe: 0,
  statsView: 0,
};

const DEFAULT_ACHIEVEMENTS: Achievement[] = [
  { id: 'health_guardian', title: '健康守护者', description: '完成首次休息', icon: '🛡️', unlocked: false },
  { id: 'week_streak', title: '一周坚持', description: '连续7天完成休息目标', icon: '🔥', unlocked: false },
  { id: 'early_bird', title: '早起鸟儿', description: '上午10点前完成首次休息', icon: '🐦', unlocked: false },
  { id: 'zen_master', title: '禅定大师', description: '完成20次深呼吸练习', icon: '🧘', unlocked: false },
  { id: 'eye_care', title: '护眼达人', description: '完成10次眼部放松', icon: '👁️', unlocked: false },
  { id: 'perfect_day', title: '完美一天', description: '一天内屏幕时间低于目标', icon: '⭐', unlocked: false },
  { id: 'steady_pace', title: '稳步前行', description: '连续3天完成所有休息提醒', icon: '🚶', unlocked: false },
  { id: 'awareness', title: '自我觉察', description: '查看统计页面10次', icon: '📊', unlocked: false },
];

function getJSON<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function setJSON(key: string, value: unknown) {
  localStorage.setItem(key, JSON.stringify(value));
}

function makeId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

function dayKey(d = new Date()): string {
  return d.toISOString().split('T')[0];
}

function createEmptyDailyStats(date: string): DailyStats {
  return { date, totalScreenTime: 0, videoTime: 0, restCount: 0, interventionCount: 0, focusMinutes: 0 };
}

function getAllStats(): Record<string, DailyStats> {
  return getJSON<Record<string, DailyStats>>(STORAGE_KEYS.STATS, {});
}

function setAllStats(stats: Record<string, DailyStats>) {
  setJSON(STORAGE_KEYS.STATS, stats);
}

function touchTodayStats(): DailyStats {
  const stats = getAllStats();
  const key = dayKey();
  const current = stats[key] || createEmptyDailyStats(key);
  stats[key] = current;
  setAllStats(stats);
  return current;
}

function getCounters(): ActivityCounters {
  return getJSON(STORAGE_KEYS.COUNTERS, DEFAULT_COUNTERS);
}

function setCounters(counters: ActivityCounters) {
  setJSON(STORAGE_KEYS.COUNTERS, counters);
}

function unlockIfNeeded(id: string) {
  unlockAchievement(id);
}

function evaluateAchievements() {
  const stats = getTodayStats();
  const counters = getCounters();
  const streak = getStreak();
  const goalSeconds = getProfile().dailyGoal * 60;

  if (stats.restCount >= 1) unlockIfNeeded('health_guardian');
  if (streak >= 3) unlockIfNeeded('steady_pace');
  if (streak >= 7) unlockIfNeeded('week_streak');
  if (counters.eye >= 10) unlockIfNeeded('eye_care');
  if (counters.breathe >= 20) unlockIfNeeded('zen_master');
  if (counters.statsView >= 10) unlockIfNeeded('awareness');
  if (stats.restCount >= 1 && new Date().getHours() < 10) unlockIfNeeded('early_bird');
  if (stats.restCount >= 1 && stats.totalScreenTime >= 3600 && stats.totalScreenTime <= goalSeconds) unlockIfNeeded('perfect_day');
}

function appendActivity(event: ActivityEvent) {
  const all = getJSON<Record<string, ActivityEvent[]>>(STORAGE_KEYS.ACTIVITY, {});
  const key = dayKey(new Date(event.at));
  const current = all[key] || [];
  current.push(event);
  all[key] = current.slice(-200);

  // 只保留最近 7 天的活动数据，防止 localStorage 无限增长
  const keys = Object.keys(all).sort();
  if (keys.length > 7) {
    for (const old of keys.slice(0, keys.length - 7)) {
      delete all[old];
    }
  }

  setJSON(STORAGE_KEYS.ACTIVITY, all);
}

// ===== Profile =====
export function getProfile(): UserProfile {
  return getJSON(STORAGE_KEYS.PROFILE, DEFAULT_PROFILE);
}

export function saveProfile(patch: Partial<UserProfile>) {
  const current = getProfile();
  setJSON(STORAGE_KEYS.PROFILE, { ...current, ...patch });
}

export function isOnboardingDone(): boolean {
  return getProfile().onboardingDone;
}

// ===== Daily Stats =====
export function getTodayStats(): DailyStats {
  const key = dayKey();
  return getAllStats()[key] || createEmptyDailyStats(key);
}

export function updateTodayStats(patch: Partial<DailyStats>) {
  const all = getAllStats();
  const key = dayKey();
  const current = all[key] || createEmptyDailyStats(key);
  all[key] = { ...current, ...patch };
  setAllStats(all);
  evaluateAchievements();
}

export function incrementStat(field: 'restCount' | 'interventionCount') {
  const all = getAllStats();
  const key = dayKey();
  const current = all[key] || createEmptyDailyStats(key);
  current[field] += 1;
  all[key] = current;
  setAllStats(all);
  evaluateAchievements();
}

export function addScreenTime(seconds: number, app = '视频应用') {
  const isTarget = ['抖音', 'B站', '快手', '微信', '小红书'].includes(app) || 
                   app === '浏览器' || 
                   app === '视频应用';
  if (!isTarget) return;

  const stats = touchTodayStats();
  updateTodayStats({
    totalScreenTime: stats.totalScreenTime + seconds,
    videoTime: stats.videoTime + seconds,
  });
  appendActivity({
    id: makeId(),
    at: new Date().toISOString(),
    type: 'screen',
    app,
    durationSec: seconds,
  });
}

export function setTodayScreenTime(seconds: number) {
  const key = dayKey();
  const all = getAllStats();
  const current = all[key] || createEmptyDailyStats(key);
  all[key] = {
    ...current,
    totalScreenTime: seconds,
    videoTime: Math.max(current.videoTime, seconds), // Native already filters in getUsageStats fix
  };
  setAllStats(all);
}

export function getWeekStats(): DailyStats[] {
  const all = getAllStats();
  const result: DailyStats[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const key = dayKey(d);
    result.push(all[key] || createEmptyDailyStats(key));
  }
  return result;
}

export function getMonthStats(days = 30): DailyStats[] {
  const all = getAllStats();
  const result: DailyStats[] = [];
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const key = dayKey(d);
    result.push(all[key] || createEmptyDailyStats(key));
  }
  return result;
}

// ===== Activity =====
export function getTodayActivities(): ActivityEvent[] {
  const all = getJSON<Record<string, ActivityEvent[]>>(STORAGE_KEYS.ACTIVITY, {});
  return all[dayKey()] || [];
}

export function getAppDistribution(): Array<{ name: string; value: number }> {
  const screenEvents = getTodayActivities().filter(e => e.type === 'screen');
  const total = screenEvents.reduce((sum, e) => sum + e.durationSec, 0);
  if (total === 0) return [];

  const grouped = new Map<string, number>();
  for (const e of screenEvents) {
    grouped.set(e.app, (grouped.get(e.app) || 0) + e.durationSec);
  }

  const mapped = [...grouped.entries()]
    .map(([name, secs]) => ({ name, value: Math.round((secs / total) * 100) }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 5);

  const sumVal = mapped.reduce((sum, item) => sum + item.value, 0);
  if (sumVal > 0 && sumVal !== 100 && mapped.length > 0) {
    mapped[0].value += (100 - sumVal);
  }
  return mapped;
}

export function logIntervention(level: string) {
  incrementStat('interventionCount');
  appendActivity({
    id: makeId(),
    at: new Date().toISOString(),
    type: 'intervention',
    app: `干预-${level}`,
    durationSec: 0,
  });
}

export function completeRestActivity(type: 'eye' | 'stretch' | 'breathe', durationSec: number) {
  incrementStat('restCount');
  appendActivity({
    id: makeId(),
    at: new Date().toISOString(),
    type: 'rest',
    app: type === 'eye' ? '眼部放松' : type === 'stretch' ? '身体拉伸' : '深呼吸',
    durationSec,
  });

  const counters = getCounters();
  if (type === 'eye') counters.eye += 1;
  if (type === 'breathe') counters.breathe += 1;
  setCounters(counters);

  updateStreakOnRest();
  addPoints(10); // Award 10 points for completing a rest activity
}

export function markStatsViewed() {
  const counters = getCounters();
  counters.statsView += 1;
  setCounters(counters);
  evaluateAchievements();
}

function updateStreakOnRest() {
  const today = dayKey();
  const yesterdayDate = new Date();
  yesterdayDate.setDate(yesterdayDate.getDate() - 1);
  const yesterday = dayKey(yesterdayDate);
  const lastRestDay = getJSON<string>(STORAGE_KEYS.LAST_REST_DAY, '');
  const currentStreak = getStreak();

  if (lastRestDay === today) return;
  if (lastRestDay === yesterday) {
    setStreak(currentStreak + 1);
  } else {
    setStreak(1);
  }
  setJSON(STORAGE_KEYS.LAST_REST_DAY, today);
}

// ===== Achievements =====
export function getAchievements(): Achievement[] {
  return getJSON(STORAGE_KEYS.ACHIEVEMENTS, DEFAULT_ACHIEVEMENTS);
}

export function unlockAchievement(id: string): Achievement | null {
  const achievements = getAchievements();
  const idx = achievements.findIndex(a => a.id === id);
  if (idx === -1 || achievements[idx].unlocked) return null;
  achievements[idx].unlocked = true;
  achievements[idx].unlockedAt = new Date().toISOString();
  setJSON(STORAGE_KEYS.ACHIEVEMENTS, achievements);
  return achievements[idx];
}

// ===== Points & Streak =====
export function getPoints(): number {
  return getJSON(STORAGE_KEYS.POINTS, 0);
}

export function addPoints(n: number) {
  setJSON(STORAGE_KEYS.POINTS, Math.max(0, getPoints() + n));
}

export function getStreak(): number {
  return getJSON(STORAGE_KEYS.STREAK, 0);
}

export function setStreak(n: number) {
  setJSON(STORAGE_KEYS.STREAK, Math.max(0, n));
}

// ===== Session Tracking =====
export function startSession() {
  setJSON(STORAGE_KEYS.SESSION_START, Date.now());
}

export function getSessionDuration(): number {
  const start = getJSON(STORAGE_KEYS.SESSION_START, Date.now());
  return Math.floor((Date.now() - start) / 1000);
}
