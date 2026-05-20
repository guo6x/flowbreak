// Manages periodic rest reminders via setInterval + Web Notification
// On native platforms, the foreground service handles this independently
// This is a web fallback + settings manager

export interface ReminderSchedule {
  enabled: boolean;
  intervalMinutes: number;
  startHour: number;
  endHour: number;
  quietWeekends: boolean;
}

const DEFAULT_SCHEDULE: ReminderSchedule = {
  enabled: true,
  intervalMinutes: 30,
  startHour: 8,
  endHour: 22,
  quietWeekends: true,
};

const STORAGE_KEY = 'fb_reminder_schedule';

export function getSchedule(): ReminderSchedule {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : DEFAULT_SCHEDULE;
  } catch {
    return DEFAULT_SCHEDULE;
  }
}

export function saveSchedule(schedule: ReminderSchedule) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(schedule));
}

export function shouldRemind(schedule: ReminderSchedule): boolean {
  if (!schedule.enabled) return false;
  const now = new Date();
  const hour = now.getHours();
  const day = now.getDay();
  if (schedule.quietWeekends && (day === 0 || day === 6)) return false;
  if (hour < schedule.startHour || hour >= schedule.endHour) return false;
  return true;
}

let intervalId: ReturnType<typeof setInterval> | null = null;
let lastRemindMinute = -1;

export function startReminderLoop(onRemind: () => void) {
  stopReminderLoop();
  const schedule = getSchedule();
  if (!schedule.enabled) return;

  lastRemindMinute = -1;
  intervalId = setInterval(() => {
    const s = getSchedule();
    if (!shouldRemind(s)) return;

    const now = new Date();
    const minuteOfDay = now.getHours() * 60 + now.getMinutes();
    const lastRemind = lastRemindMinute;
    if (lastRemind < 0 || minuteOfDay - lastRemind >= s.intervalMinutes) {
      lastRemindMinute = minuteOfDay;
      onRemind();
    }
  }, 60_000);
}

export function stopReminderLoop() {
  if (intervalId !== null) {
    clearInterval(intervalId);
    intervalId = null;
  }
}
