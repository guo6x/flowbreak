// src/pages/RestMode.tsx
// PRD 2.3.2 + 6.2.3 休息引导全屏页面
//
// Refactored into a thin orchestration layer. Visuals live in
// ./rest-mode/RestActivityView.tsx and ./rest-mode/RestCompleteView.tsx,
// ambient audio in ./rest-mode/ambientAudio.ts + useRestAudio.ts,
// the countdown timer in ./rest-mode/useRestTimer.ts, static activity
// data in ./rest-mode/activities.ts, theme data in ./rest-mode/themeData.ts,
// and pure colour/time helpers in ./rest-mode/colorUtils.ts.
//
// Behaviour (timing, audio, vibration, native calls, rewards, exit flow,
// browser fallback) is preserved exactly from the previous monolithic page.

import { useState, useEffect, useRef, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Capacitor } from '@capacitor/core';
import { useStore } from '../hooks/useStore';
import { NativeFlow } from '../backend/nativeFlow';

import { activities, getActivityByIndex } from './rest-mode/activities';
import { themedParticles, calmColor } from './rest-mode/themeData';
import { lerpColor } from './rest-mode/colorUtils';
import { playChime } from './rest-mode/ambientAudio';
import { useRestTimer } from './rest-mode/useRestTimer';
import { useRestAudio } from './rest-mode/useRestAudio';
import RestActivityView from './rest-mode/RestActivityView';
import RestCompleteView from './rest-mode/RestCompleteView';

export default function RestMode() {
  const navigate = useNavigate();
  const location = useLocation();
  const profile = useStore(s => s.profile);
  const completeRest = useStore(s => s.completeRestActivity);
  const setBlockState = useStore(s => s.setBlockState);

  const initialActivityIdx = (location.state as { activityIdx?: number })?.activityIdx ?? 0;
  const isNative = Capacitor.isNativePlatform();
  const totalDuration = profile.restDuration || 180;

  // ===== Page state =====
  const [activityIdx, setActivityIdx] = useState(initialActivityIdx);
  const [restEndsAt, setRestEndsAt] = useState<number | null>(null);
  const [restReady, setRestReady] = useState(!isNative);
  const [isPaused, setIsPaused] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const [stepIdx, setStepIdx] = useState(0);
  const [showReward, setShowReward] = useState(false);
  const [slideDirection, setSlideDirection] = useState<1 | -1>(1);
  const [stepProgress, setStepProgress] = useState(0);
  const [rewardBadgeTitle, setRewardBadgeTitle] = useState<string | null>(null);
  const [rewardContentVisible, setRewardContentVisible] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [operationError, setOperationError] = useState('');
  const [closing, setClosing] = useState(false);
  const [confirmingEarlyExit, setConfirmingEarlyExit] = useState(false);

  // ===== Refs =====
  const stepIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const completedRestRef = useRef(false);
  const cancelledRestRef = useRef(false);

  // ===== Timer hook =====
  const { timeLeft, setTimeLeft, progress } = useRestTimer({
    totalDuration,
    initialSeconds: totalDuration,
    restEndsAt,
    isNative,
    active: !showReward && restReady,
    isPaused,
  });

  // ===== Audio hook =====
  const { stopAmbient } = useRestAudio({
    isMuted,
    isPaused,
    stopOnReward: showReward,
  });

  // ===== Native rest bootstrap =====
  useEffect(() => {
    if (!isNative) return;
    let active = true;
    const restoreOrStartRest = async () => {
      try {
        await NativeFlow.beginRest();
        const state = await NativeFlow.getBlockState();
        if (!active) return;
        const requiredSeconds = state.restRequiredSeconds || profile.restDuration || 180;
        const startedAt = state.restStartedAt || Date.now();
        const endsAt = startedAt + requiredSeconds * 1000;
        setRestEndsAt(endsAt);
        setTimeLeft(Math.max(0, Math.ceil((endsAt - Date.now()) / 1000)));
        setRestReady(true);
      } catch {
        if (active) setOperationError('休息模式启动失败，请返回后重试。');
      }
    };
    void restoreOrStartRest();
    return () => {
      active = false;
      if (!completedRestRef.current && !cancelledRestRef.current) {
        void NativeFlow.cancelRest().catch(() => {});
      }
    };
  }, []);

  // ===== Static activity & derived visuals =====
  const activity = getActivityByIndex(activityIdx);
  const ambientParticles = useMemo(
    () => (themedParticles[profile.selectedBackground] || themedParticles[0]).map((emoji) => ({
      emoji,
      x: (Math.random() - 0.5) * 60,
      rotate: Math.random() * 360,
      duration: 8 + Math.random() * 7,
      delay: Math.random() * 5,
      left: 8 + Math.random() * 84,
    })),
    [profile.selectedBackground],
  );
  const confetti = useMemo(
    () => Array.from({ length: 25 }, (_, i) => ({
      rotate: 720 + Math.random() * 360,
      duration: 2.5 + Math.random() * 2,
      delay: Math.random() * 0.6,
      left: 8 + Math.random() * 84,
      color: [
        '#4CAF50', '#2196F3', '#FF9800', '#F44336', '#9C27B0', '#FFEB3B',
        '#00BCD4', '#E91E63', '#8BC34A', '#FF5722', '#3F51B5', '#FFC107',
      ][i % 12],
    })),
    [],
  );
  const breathColor = useMemo(
    () => lerpColor(activity.color, calmColor, progress),
    [activity.color, progress],
  );

  // ===== Step cycle (6s per step) =====
  useEffect(() => {
    if (showReward || isPaused) return;
    stepIntervalRef.current = setInterval(() => {
      setStepIdx(prev => (prev + 1) % activity.steps.length);
    }, 6000);
    return () => {
      if (stepIntervalRef.current) clearInterval(stepIntervalRef.current);
    };
  }, [activity.steps.length, showReward, isPaused, activityIdx]);

  // ===== Step progress bar (resets on step change, fills over 6s) =====
  useEffect(() => {
    setStepProgress(0);
  }, [stepIdx]);

  useEffect(() => {
    if (showReward || isPaused) return;
    const timer = setInterval(() => {
      setStepProgress(p => (p >= 100 ? 100 : p + 100 / 60)); // 60 ticks over 6s = 100ms each
    }, 100);
    return () => clearInterval(timer);
  }, [showReward, stepIdx, isPaused]);

  // ===== Chime + light vibration on step change =====
  useEffect(() => {
    if (!showReward && !isMuted) {
      playChime(500 + stepIdx * 40, 0.25);
    }
    // 步骤切换时轻震动，帮助用户感知节奏变化
    if (!showReward && !isPaused && typeof navigator !== 'undefined' && navigator.vibrate) {
      try { navigator.vibrate(30); } catch { /* ignore */ }
    }
  }, [stepIdx, showReward, isMuted, isPaused]);

  // ===== 呼吸节奏震动：4 秒周期，吸气开始时轻震一下，与呼吸圆圈动画同步 =====
  useEffect(() => {
    if (showReward || isPaused) return;
    const interval = setInterval(() => {
      if (typeof navigator !== 'undefined' && navigator.vibrate) {
        try { navigator.vibrate(50); } catch { /* ignore */ }
      }
    }, 4000);
    return () => clearInterval(interval);
  }, [showReward, isPaused]);

  // ===== Reward delay + counting animation =====
  useEffect(() => {
    if (!showReward) return;
    const delay = setTimeout(() => setRewardContentVisible(true), 500);
    return () => clearTimeout(delay);
  }, [showReward]);

  // ===== Completion flow =====
  // Re-entry guard: the `completing` state already prevents double-clicks,
  // and `completedRestRef` ensures the native complete call only fires once
  // even if a re-render or callback retriggers handleComplete.
  const handleComplete = async () => {
    if (completing || completedRestRef.current) return;
    setCompleting(true);
    setOperationError('');
    // Play completion fanfare
    if (!isMuted) {
      playChime(523, 0.2);
      setTimeout(() => playChime(659, 0.2), 150);
      setTimeout(() => playChime(784, 0.3), 300);
    }

    // 完成时完全终止背景音乐
    stopAmbient();

    if (isNative) {
      try {
        const result = await NativeFlow.completeRestAndUnlock({
          activity: activity.id,
          duration: totalDuration,
        });
        setBlockState('GRACE', result.graceUntil, '');
        useStore.setState({ points: result.points, streak: result.streak });
        setRewardBadgeTitle(result.achievement === 'health_guardian' ? '健康守护者' : null);
        completedRestRef.current = true;
      } catch {
        setOperationError('解锁失败，休息记录尚未提交，请点击重试。');
        setCompleting(false);
        return;
      }
    } else {
      setBlockState('GRACE', Date.now() + 10 * 60 * 1000, '');
      const beforeAchievements = useStore.getState().achievements;
      completeRest(activity.id, totalDuration);
      const afterState = useStore.getState();
      const newlyUnlocked = afterState.achievements.find(
        achievement => achievement.unlocked &&
          !beforeAchievements.find(before => before.id === achievement.id)?.unlocked,
      );
      setRewardBadgeTitle(newlyUnlocked?.title ?? null);
    }
    setShowReward(true);
    setCompleting(false);
  };

  const handleFinish = () => {
    navigate('/dashboard');
  };

  // ===== Early-exit flow =====
  const handleClose = async () => {
    if (closing) return;
    // 第一次点击：弹出二次确认
    if (!confirmingEarlyExit) {
      setConfirmingEarlyExit(true);
      return;
    }
    setConfirmingEarlyExit(false);
    setClosing(true);
    try {
      if (isNative) {
        await NativeFlow.cancelRest();
        cancelledRestRef.current = true;
      } else {
        // Web 端：已休息 30 秒以上时记录部分休息
        const elapsed = totalDuration - timeLeft;
        if (elapsed >= 30) {
          completeRest(activity.id, elapsed);
        }
      }
      navigate('/dashboard');
    } catch (error) {
      setOperationError(error instanceof Error ? error.message : '退出休息模式失败，请重试。');
    } finally {
      setClosing(false);
    }
  };

  const handleCancelEarlyExit = () => {
    if (closing) return;
    setConfirmingEarlyExit(false);
  };

  const switchActivity = (dir: 1 | -1) => {
    setSlideDirection(dir);
    setActivityIdx(i => (i + dir + activities.length) % activities.length);
    setStepIdx(0);
  };

  // ===== Render =====
  if (showReward) {
    return (
      <RestCompleteView
        confetti={confetti}
        rewardContentVisible={rewardContentVisible}
        rewardBadgeTitle={rewardBadgeTitle}
        onFinish={handleFinish}
      />
    );
  }

  return (
    <RestActivityView
      selectedBackground={profile.selectedBackground}
      ambientParticles={ambientParticles}
      activity={activity}
      activityIdx={activityIdx}
      slideDirection={slideDirection}
      timeLeft={timeLeft}
      progress={progress}
      breathColor={breathColor}
      stepIdx={stepIdx}
      stepProgress={stepProgress}
      isMuted={isMuted}
      isPaused={isPaused}
      isNative={isNative}
      operationError={operationError}
      completing={completing}
      confirmingEarlyExit={confirmingEarlyExit}
      closing={closing}
      onClose={handleClose}
      onToggleMute={() => setIsMuted(!isMuted)}
      onTogglePause={() => setIsPaused(!isPaused)}
      onSwitchActivity={switchActivity}
      onComplete={handleComplete}
      onCancelEarlyExit={handleCancelEarlyExit}
      onConfirmEarlyExit={handleClose}
    />
  );
}
