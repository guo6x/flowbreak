// src/App.tsx
import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet, useNavigate, useLocation } from 'react-router';
import { MotionConfig } from 'framer-motion';
import { Capacitor } from '@capacitor/core';
import { useStore } from './hooks/useStore';
import { getLevelByPercent, InterventionLevel } from './backend/fatigueEngine';
import { NativeFlow } from './backend/nativeFlow';
import { getAppName } from './backend/appNames';
import { exportLegacyPayload } from './backend/storage';
import InterventionOverlay from './components/InterventionOverlay';
import BottomNav from './components/BottomNav';
import './App.css';

const Onboarding = lazy(() => import('./pages/Onboarding'));
const Login = lazy(() => import('./pages/Login'));
const Permissions = lazy(() => import('./pages/Permissions'));
const Personalize = lazy(() => import('./pages/Personalize'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Statistics = lazy(() => import('./pages/Statistics'));
const Profile = lazy(() => import('./pages/Profile'));
const RestMode = lazy(() => import('./pages/RestMode'));
const TargetApps = lazy(() => import('./pages/TargetApps'));
const BlockingSettings = lazy(() => import('./pages/BlockingSettings'));
const PrivacyAndData = lazy(() => import('./pages/PrivacyAndData'));
const ValidationAndDiagnostics = lazy(() => import('./pages/ValidationAndDiagnostics'));

function LoadingPage() {
  return (
    <div className="min-h-dvh flex items-center justify-center bg-gray-100">
      <div className="text-[13px] text-gray-500">正在加载...</div>
    </div>
  );
}

// 设置/引导页面路径——在这些页面上不累加连续使用时长
const SETTINGS_PATHS = ['/profile', '/target-apps', '/blocking-settings', '/privacy', '/validation', '/permissions', '/personalize', '/login', '/onboarding', '/settings'];

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
  const setBlockState = useStore(s => s.setBlockState);
  const setServiceError = useStore(s => s.setServiceError);
  const targetApps = profile.targetApps || [];

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

    const isOnSettingsPage = SETTINGS_PATHS.includes(location.pathname);
    const isInTargetScenario = isMonitoring && location.pathname !== '/rest' && !isOnSettingsPage;

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

  // Sync native service when monitoring toggles
  useEffect(() => {
    if (!profile.onboardingDone) return;
    if (!Capacitor.isNativePlatform()) {
      if (isMonitoring) useStore.getState().startSession();
      return;
    }

    let cancelled = false;
    const syncService = async () => {
      try {
        if (isMonitoring) {
          if (targetApps.length === 0) {
            throw new Error('请先选择至少一个受限应用。');
          }
          await NativeFlow.saveSettings({ limitMinutes: profile.sessionLimit, targetApps });
          await NativeFlow.startService({
            limitMinutes: profile.sessionLimit,
            apps: targetApps,
            monitoringEnabled: true,
          });
          useStore.getState().startSession();
        } else {
          await NativeFlow.stopService();
        }
        if (!cancelled && isMonitoring) setServiceError('');
      } catch (error) {
        if (cancelled) return;
        const message = error instanceof Error && error.message
          ? error.message
          : isMonitoring ? '保护服务启动失败，请检查权限后重试。' : '保护服务停止失败，请重试。';
        setServiceError(message);
        if (isMonitoring) useStore.getState().setMonitoring(false);
      }
    };
    void syncService();
    return () => {
      cancelled = true;
    };
  }, [isMonitoring, profile.onboardingDone, profile.sessionLimit, targetApps, setServiceError]);

  // Native owns UsageEvents scanning. The WebView only reads its persisted
  // state so the UI never starts a second competing usage tracker.
  useEffect(() => {
    if (Capacitor.isNativePlatform()) {
      if (!isMonitoring) return;
      let active = true;
      const syncNativeState = async () => {
        try {
          const [app, result, block] = await Promise.all([
            NativeFlow.getCurrentApp(),
            NativeFlow.getUsageStats(),
            NativeFlow.getBlockState(),
          ]);
          if (!active) return;
          setCurrentAppName(getAppName(app.packageName));
          const safeTotal = Math.max(0, result.screenTimeSeconds);
          const currentStats = useStore.getState().todayStats;
          if (safeTotal !== currentStats.totalScreenTime) {
            useStore.getState().setScreenTime(safeTotal);
          }
          const levelMap: Partial<Record<typeof block.state, InterventionLevel>> = {
            PERCEPTION: 'PERCEPTION',
            COGNITION: 'COGNITION',
            BLOCKED: 'ACTION',
          };
          // Sync native level and continuous seconds to store
          const normalizedScore = Math.min(
            1,
            Math.max(0, block.sessionSeconds / 60 / Math.max(1, profile.sessionLimit)),
          );
          setFatigue(normalizedScore, levelMap[block.state] || 'NONE');
          useStore.getState().setContinuousSessionSeconds(block.sessionSeconds);
          setBlockState(block.state, block.graceUntil, block.blockedPackage);
        } catch {
          // Permission and service startup can briefly lag the WebView.
        }
      };
      void syncNativeState();
      const timer = setInterval(() => { void syncNativeState(); }, 10_000);
      return () => {
        active = false;
        clearInterval(timer);
      };
    }

    // Web fallback
    const isOnSettingsPage = SETTINGS_PATHS.includes(location.pathname);
    const isInTargetScenario = isMonitoring && location.pathname !== '/rest' && !isOnSettingsPage;

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
  }, [
    isMonitoring,
    location.pathname,
    incrementContinuousSession,
    isDocumentVisible,
    profile.sessionLimit,
    setCurrentAppName,
    setFatigue,
    setBlockState,
  ]);

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
    const limit = profile.sessionLimit || 60;
    const percent = (mins / limit) * 100;
    const l = getLevelByPercent(percent);
    const s = Math.min(1, Math.max(0, mins / limit));
    setFatigue(s, l);
  }, [isMonitoring, continuousSeconds, profile.sessionLimit, setFatigue]);

  const level = useStore(s => s.fatigueLevel);

  // Intervention notifications
  useEffect(() => {
    if (Capacitor.isNativePlatform()) {
      prevLevelRef.current = level;
      return;
    }
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

  const showOverlay = !Capacitor.isNativePlatform()
    && isMonitoring
    && level !== 'NONE'
    && now >= snoozeUntil
    && location.pathname !== '/rest';

  if (!showOverlay) return null;

  return (
    <InterventionOverlay
      level={level}
      elapsed={continuousSeconds}
      onDismiss={() => setSnoozeUntil(Date.now() + 10 * 60 * 1000)}
      onSnooze={() => {
        const snoozeLimit = 10 * 60 * 1000;
        setSnoozeUntil(Date.now() + snoozeLimit);
        snoozeContinuousSession(600);
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
  const updateProfile = useStore(s => s.updateProfile);
  const navigate = useNavigate();

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    NativeFlow.migrateLegacyData({ payload: exportLegacyPayload() }).catch(() => {});
    Promise.all([NativeFlow.loadSettings(), NativeFlow.getDashboardSummary()]).then(([settings, summary]) => {
      updateProfile({
        sessionLimit: settings.limitMinutes,
        restDuration: settings.restDuration,
        targetApps: settings.targetApps,
        allowEmergencyUnlock: settings.allowEmergencyUnlock,
        strongBlockingEnabled: settings.strongBlockingEnabled,
      });
      useStore.setState({ points: summary.points, streak: summary.streak });
    }).catch(() => {});
  }, [updateProfile]);

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    let active = true;
    const consumePendingNavigation = async () => {
      try {
        const { path } = await NativeFlow.consumePendingNavigation();
        if (active && (path === '/rest' || path === '/dashboard')) navigate(path, { replace: true });
      } catch {
        // Navigation is a convenience path; the native blocker remains active
        // if a bridge call cannot be made during startup.
      }
    };
    const handler = () => { void consumePendingNavigation(); };
    window.addEventListener('flow-navigate', handler);
    void consumePendingNavigation();
    return () => {
      active = false;
      window.removeEventListener('flow-navigate', handler);
    };
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
          <Route path="/profile" element={<Profile />} />
          <Route path="/target-apps" element={<TargetApps />} />
          <Route path="/blocking-settings" element={<BlockingSettings />} />
          <Route path="/privacy" element={<PrivacyAndData />} />
          <Route path="/validation" element={<ValidationAndDiagnostics />} />
        </Route>

        <Route path="/rest" element={<RestMode />} />
        <Route path="*" element={<Navigate to={done ? '/dashboard' : '/onboarding'} replace />} />
      </Routes>
    </Suspense>
  );
}

export default function App() {
  return (
    <MotionConfig reducedMotion="user">
      <BrowserRouter>
        <AppRouter />
      </BrowserRouter>
    </MotionConfig>
  );
}
