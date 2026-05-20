// src/App.tsx
import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Capacitor } from '@capacitor/core';
import { useStore } from './hooks/useStore';
import { calculateFatigueScore, getInterventionLevel, InterventionLevel } from './backend/fatigueEngine';
import { startReminderLoop } from './backend/reminderScheduler';
import { NativeFlow } from './backend/nativeFlow';
import { DEFAULT_TARGET_APPS, getAppName } from './backend/appNames';
import InterventionOverlay from './components/InterventionOverlay';
import BottomNav from './components/BottomNav';
import './App.css';

const Onboarding = lazy(() => import('./pages/Onboarding'));
const Login = lazy(() => import('./pages/Login'));
const Permissions = lazy(() => import('./pages/Permissions'));
const Personalize = lazy(() => import('./pages/Personalize'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Statistics = lazy(() => import('./pages/Statistics'));
const Achievements = lazy(() => import('./pages/Achievements'));
const Profile = lazy(() => import('./pages/Profile'));
const RestMode = lazy(() => import('./pages/RestMode'));
const ReminderSettings = lazy(() => import('./pages/ReminderSettings'));

function LoadingPage() {
  return (
    <div className="min-h-dvh flex items-center justify-center bg-gray-100">
      <div className="text-[13px] text-gray-500">正在加载...</div>
    </div>
  );
}

// ============================================================
// GlobalMonitor — runs screen time tracking, fatigue detection,
// and intervention overlay across ALL pages (not just Dashboard)
// ============================================================
function GlobalMonitor() {
  const location = useLocation();
  const isMonitoring = useStore(s => s.isMonitoring);
  const profile = useStore(s => s.profile);
  const snoozeUntil = useStore(s => s.snoozeUntil);
  const logIntervention = useStore(s => s.logIntervention);
  const setFatigue = useStore(s => s.setFatigue);
  const setCurrentAppName = useStore(s => s.setCurrentAppName);
  const setSnoozeUntil = useStore(s => s.setSnoozeUntil);
  const continuousSeconds = useStore(s => s.continuousSessionSeconds);
  const incrementContinuousSession = useStore(s => s.incrementContinuousSession);
  const resetContinuousSession = useStore(s => s.resetContinuousSession);
  const snoozeContinuousSession = useStore(s => s.snoozeContinuousSession);

  const lastActiveTimeRef = useRef(Date.now());
  const [isDocumentVisible, setIsDocumentVisible] = useState(true);
  const exitTimerRef = useRef<any>(null);

  // Web fallback: activity detection & page visibility
  useEffect(() => {
    if (Capacitor.isNativePlatform()) return;

    const handleVisibility = () => setIsDocumentVisible(!document.hidden);
    const updateActivity = () => {
      lastActiveTimeRef.current = Date.now();
    };

    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('mousemove', updateActivity);
    window.addEventListener('keydown', updateActivity);
    window.addEventListener('mousedown', updateActivity);
    window.addEventListener('touchstart', updateActivity);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('mousemove', updateActivity);
      window.removeEventListener('keydown', updateActivity);
      window.removeEventListener('mousedown', updateActivity);
      window.removeEventListener('touchstart', updateActivity);
    };
  }, []);

  // Web fallback: 30 seconds pause grace period for anti-cheat reset
  useEffect(() => {
    if (Capacitor.isNativePlatform()) return;

    const isInTargetScenario = isMonitoring && location.pathname !== '/rest';

    if (location.pathname === '/rest') {
      resetContinuousSession();
      if (exitTimerRef.current) {
        clearTimeout(exitTimerRef.current);
        exitTimerRef.current = null;
      }
      return;
    }

    if (!isInTargetScenario) {
      if (!exitTimerRef.current) {
        exitTimerRef.current = setTimeout(() => {
          resetContinuousSession();
          exitTimerRef.current = null;
        }, 30000);
      }
    } else {
      if (exitTimerRef.current) {
        clearTimeout(exitTimerRef.current);
        exitTimerRef.current = null;
      }
    }
  }, [isMonitoring, location.pathname, resetContinuousSession]);

  useEffect(() => {
    return () => {
      if (exitTimerRef.current) {
        clearTimeout(exitTimerRef.current);
      }
    };
  }, []);

  const prevLevelRef = useRef<InterventionLevel>('NONE');
  const [now, setNow] = useState(Date.now());

  // Tick now every 10s to reactivate overlay after snooze
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 10000);
    return () => clearInterval(timer);
  }, []);

  // Auto-start native service & session on mount
  useEffect(() => {
    if (!profile.onboardingDone) return;
    
    useStore.getState().startSession();
    if (Capacitor.isNativePlatform()) {
      NativeFlow.startService({
        limitMinutes: profile.sessionLimit,
        apps: DEFAULT_TARGET_APPS,
      }).catch(() => {});
    }
  }, [profile.onboardingDone, profile.sessionLimit]);

  // Sync native service when monitoring toggles
  useEffect(() => {
    if (!Capacitor.isNativePlatform() || !profile.onboardingDone) return;
    if (isMonitoring) {
      NativeFlow.saveSettings({ limitMinutes: profile.sessionLimit, targetApps: DEFAULT_TARGET_APPS }).catch(() => {});
      NativeFlow.startService({
        limitMinutes: profile.sessionLimit,
        apps: DEFAULT_TARGET_APPS,
      }).catch(() => {});
    } else {
      NativeFlow.stopService().catch(() => {});
    }
  }, [isMonitoring, profile.onboardingDone, profile.sessionLimit]);

  // Feed screen time to store every 5s
  useEffect(() => {
    if (Capacitor.isNativePlatform()) {
      if (!isMonitoring) return;
      const timer = setInterval(async () => {
        try {
          // 1. Update current app name for display
          let currentPackage = '';
          try {
            const app = await NativeFlow.getCurrentApp();
            currentPackage = app.packageName;
            setCurrentAppName(getAppName(currentPackage));
          } catch { /* ignore */ }

          // 2. Directly set total screen time from system for statistics
          const result = await NativeFlow.getUsageStats();
          const safeTotal = Math.min(result.screenTimeSeconds, 86400);
          useStore.getState().setScreenTime(safeTotal);

          // 4. Poll fatigue level from native service (truth source)
          const fatigue = await NativeFlow.getCurrentFatigueLevel();
          const levelMap: Record<number, InterventionLevel> = {
            0: 'NONE',
            1: 'PERCEPTION',
            2: 'COGNITION',
            3: 'ACTION'
          };
          // Sync native level and continuous seconds to store
          setFatigue(fatigue.level * 0.33, levelMap[fatigue.level] || 'NONE');
          useStore.getState().setContinuousSessionSeconds(fatigue.minutes * 60);
        } catch {
          // Silent — system stats may not be available yet
        }
      }, 5000);
      return () => clearInterval(timer);
    }

    // Web fallback
    const isInTargetScenario = isMonitoring && location.pathname !== '/rest';

    if (!isInTargetScenario) {
      return;
    }

    const timer = setInterval(() => {
      // 活性判定：页面不可见，或者超过 60 秒无任何键鼠交互 -> 挂起累加
      const isIdle = Date.now() - lastActiveTimeRef.current > 60000;
      if (!isDocumentVisible || isIdle) {
        return; 
      }

      useStore.getState().addScreenTime(5, '浏览器');
      incrementContinuousSession(5);
    }, 5000);
    return () => clearInterval(timer);
  }, [isMonitoring, location.pathname, incrementContinuousSession, isDocumentVisible]);

  // Service health check — restart native service if it died
  useEffect(() => {
    if (!Capacitor.isNativePlatform() || !isMonitoring) return;
    const healthTimer = setInterval(() => {
      NativeFlow.startService({
        limitMinutes: profile.sessionLimit,
        apps: DEFAULT_TARGET_APPS,
      }).catch(() => {});
    }, 30_000);
    return () => clearInterval(healthTimer);
  }, [isMonitoring, profile.sessionLimit]);

  // Compute fatigue from continuous session time (NOT total screen time)
  // Web-only logic: Native uses polling from getCurrentFatigueLevel above
  useEffect(() => {
    if (!isMonitoring || Capacitor.isNativePlatform()) {
      if (!isMonitoring) {
        setFatigue(0, 'NONE');
        prevLevelRef.current = 'NONE';
      }
      return;
    }
    const mins = continuousSeconds / 60;
    const metrics = { usageDuration: mins, timeFactor: new Date().getHours() };
    const s = calculateFatigueScore(metrics);
    const l = getInterventionLevel(s);
    setFatigue(s, l);
  }, [isMonitoring, continuousSeconds, setFatigue]);

  const level = useStore(s => s.fatigueLevel);
  const score = useStore(s => s.fatigueScore);

  // Intervention notifications
  useEffect(() => {
    if (level === 'NONE') {
      prevLevelRef.current = level;
      return;
    }
    if (now < snoozeUntil) return;
    if (prevLevelRef.current !== level) {
      logIntervention(level);
      if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
        const msg =
          level === 'ACTION' ? '疲劳指数较高，建议立刻休息3分钟。' :
          level === 'COGNITION' ? '认知疲劳上升，建议短暂放松。' :
          '出现轻度疲劳，注意眼部休息。';
        new Notification('FlowBreak 提醒', { body: msg });
      }
      prevLevelRef.current = level;
    }
  }, [level, snoozeUntil, now, logIntervention]);

  const showOverlay = isMonitoring && level !== 'NONE' && now >= snoozeUntil;

  if (!showOverlay) return null;

  return (
    <InterventionOverlay
      level={level}
      score={score}
      elapsed={continuousSeconds}
      onDismiss={() => setSnoozeUntil(Date.now() + 10 * 60 * 1000)}
      onSnooze={() => {
        const snoozeLimit = 10 * 60 * 1000;
        setSnoozeUntil(Date.now() + snoozeLimit);
        if (Capacitor.isNativePlatform()) {
          NativeFlow.snoozeService().catch(() => {});
        } else {
          snoozeContinuousSession(600); // 扣除 10 分钟连续时间
        }
      }}
    />
  );
}

function MainLayout() {
  return (
    <div className="relative min-h-dvh">
      <Outlet />
      <BottomNav />
    </div>
  );
}

function AppRouter() {
  const done = useStore(s => s.profile.onboardingDone);
  const navigate = useNavigate();

  useEffect(() => {
    const handler = (e: Event) => {
      const path = (e as CustomEvent).detail;
      if (typeof path === 'string') navigate(path);
    };
    window.addEventListener('flow-navigate', handler);
    return () => window.removeEventListener('flow-navigate', handler);
  }, [navigate]);

  useEffect(() => {
    startReminderLoop(() => {
      if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
        new Notification('FlowBreak 提醒', { body: '该休息一下了', icon: '/favicon.png' });
      }
      navigate('/rest');
    });
  }, [navigate]);

  return (
    <Suspense fallback={<LoadingPage />}>
      <GlobalMonitor />
      <Routes>
        <Route path="/onboarding" element={<Onboarding />} />
        <Route path="/login" element={<Login />} />
        <Route path="/permissions" element={<Permissions />} />
        <Route path="/personalize" element={<Personalize />} />

        <Route element={<MainLayout />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/stats" element={<Statistics />} />
          <Route path="/achievements" element={<Achievements />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/reminder-settings" element={<ReminderSettings />} />
        </Route>

        <Route path="/rest" element={<RestMode />} />
        <Route path="*" element={<Navigate to={done ? '/dashboard' : '/onboarding'} replace />} />
      </Routes>
    </Suspense>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppRouter />
    </BrowserRouter>
  );
}
