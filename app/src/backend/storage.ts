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
  targetApps: string[];
  allowEmergencyUnlock: boolean;
  strongBlockingEnabled: boolean;
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
  MONITORING_ENABLED: 'fb_monitoring_enabled',
  REFLECTIONS: 'fb_reflections',
};

const DEFAULT_PROFILE: UserProfile = {
  name: '',
  type: 'student',
  dailyGoal: 240,
  sessionLimit: 25,
  restDuration: 180,
  selectedBackground: 0,
  onboardingDone: false,
  targetApps: [],
  allowEmergencyUnlock: true,
  strongBlockingEnabled: false,
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
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
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
  if (stats.restCount >= 1 && stats.totalScreenTime > 0 && stats.totalScreenTime <= goalSeconds) unlockIfNeeded('perfect_day');
}

function appendActivity(event: ActivityEvent) {
  const all = getJSON<Record<string, ActivityEvent[]>>(STORAGE_KEYS.ACTIVITY, {});
  const key = dayKey(new Date(event.at));
  const current = all[key] || [];

  if (current.length > 0 && event.type === 'screen') {
    const last = current[current.length - 1];
    if (last.type === 'screen' && last.app === event.app) {
      const lastTime = new Date(last.at).getTime();
      const currTime = new Date(event.at).getTime();
      if (currTime - lastTime < 60000) {
        last.durationSec += event.durationSec;
        last.at = event.at;
        all[key] = current;
        setJSON(STORAGE_KEYS.ACTIVITY, all);
        return;
      }
    }
  }

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
  return { ...DEFAULT_PROFILE, ...getJSON(STORAGE_KEYS.PROFILE, DEFAULT_PROFILE) };
}

export function saveProfile(patch: Partial<UserProfile>) {
  const current = getProfile();
  setJSON(STORAGE_KEYS.PROFILE, { ...current, ...patch });
}

export function isOnboardingDone(): boolean {
  return getProfile().onboardingDone;
}

export function getMonitoringEnabled(): boolean {
  return getJSON(STORAGE_KEYS.MONITORING_ENABLED, true);
}

export function saveMonitoringEnabled(enabled: boolean) {
  setJSON(STORAGE_KEYS.MONITORING_ENABLED, enabled);
}

export type DailyReflection = 'helped' | 'neutral' | 'not_helped';

export function getTodayReflection(): DailyReflection | '' {
  return getJSON<Record<string, DailyReflection>>(STORAGE_KEYS.REFLECTIONS, {})[dayKey()] || '';
}

export function saveTodayReflection(value: DailyReflection) {
  const all = getJSON<Record<string, DailyReflection>>(STORAGE_KEYS.REFLECTIONS, {});
  all[dayKey()] = value;
  setJSON(STORAGE_KEYS.REFLECTIONS, all);
}

export function getReflectionCounts(days = 7) {
  const all = getJSON<Record<string, DailyReflection>>(STORAGE_KEYS.REFLECTIONS, {});
  const result = { helpedDays: 0, neutralDays: 0, notHelpedDays: 0 };
  for (let i = 0; i < days; i++) {
    const date = new Date();
    date.setDate(date.getDate() - i);
    const value = all[dayKey(date)];
    if (value === 'helped') result.helpedDays++;
    else if (value === 'neutral') result.neutralDays++;
    else if (value === 'not_helped') result.notHelpedDays++;
  }
  return result;
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
  // 调用方负责判断是否为目标应用（native 端通过 targetApps 过滤，web 端通过 demo 池控制）
  // 内联 stats 更新，避免 touchTodayStats + updateTodayStats 双重读写
  const all = getAllStats();
  const key = dayKey();
  const current = all[key] || createEmptyDailyStats(key);
  all[key] = {
    ...current,
    totalScreenTime: current.totalScreenTime + seconds,
    videoTime: current.videoTime + seconds,
  };
  setAllStats(all);
  appendActivity({
    id: makeId(),
    at: new Date().toISOString(),
    type: 'screen',
    app,
    durationSec: seconds,
  });
  evaluateAchievements();
}

export function setTodayScreenTime(seconds: number) {
  const key = dayKey();
  const all = getAllStats();
  const current = all[key] || createEmptyDailyStats(key);
  all[key] = {
    ...current,
    totalScreenTime: seconds,
    // videoTime 只记录目标应用使用时长，不在这里用 totalScreenTime 兜底
    // 如果当天没有目标应用使用记录，videoTime 保持 0
    videoTime: current.videoTime,
  };
  setAllStats(all);
  evaluateAchievements();
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
    .map(([name, secs]) => ({
      name,
      raw: secs / total,
      value: Math.floor((secs / total) * 100),
    }))
    .sort((a, b) => b.raw - a.raw)
    .slice(0, 5);

  // 最大余数法：确保百分比总和恰好为 100，避免 Math.round 导致 sum > 100
  const sumVal = mapped.reduce((sum, item) => sum + item.value, 0);
  let remainder = 100 - sumVal;
  if (mapped.length > 0 && remainder !== 0) {
    // 按 raw 的小数部分降序，把余数分配给小数部分最大的项
    const fractional = [...mapped]
      .map((item, idx) => ({ idx, frac: item.raw * 100 - Math.floor(item.raw * 100) }))
      .sort((a, b) => b.frac - a.frac);
    let i = 0;
    while (remainder > 0 && i < fractional.length) {
      mapped[fractional[i].idx].value += 1;
      remainder -= 1;
      i += 1;
    }
    i = 0;
    while (remainder < 0 && i < fractional.length) {
      mapped[fractional[i].idx].value -= 1;
      remainder += 1;
      i += 1;
    }
  }
  return mapped.map(({ name, value }) => ({ name, value }));
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
  evaluateAchievements();
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
  addPoints(10);
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

export function exportLegacyPayload(): Record<string, unknown> {
  return {
    profile: getProfile(),
    stats: getAllStats(),
    achievements: getAchievements(),
    points: getPoints(),
    streak: getStreak(),
    activities: getJSON(STORAGE_KEYS.ACTIVITY, {}),
  };
}

export function clearAllLocalData() {
  Object.values(STORAGE_KEYS).forEach(key => localStorage.removeItem(key));
  // These are presentation-only keys rather than part of STORAGE_KEYS, but
  // clearing data must also reset their associated local behavior.
  localStorage.removeItem('fb_reminder_schedule');
  localStorage.removeItem('last_stats_view_date');
}
