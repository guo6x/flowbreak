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
import { useNavigate, useLocation } from 'react-router';
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

const NATIVE_BEGIN_CONFIRMATION_TIMEOUT_MS = 30_000;

function cancelNativeRestOnce(
  beginRequestToken: string,
  cancelledRestRef: { current: boolean },
  cancellationPromiseRef: { current: Promise<void> | null },
): Promise<void> {
  if (cancelledRestRef.current) return Promise.resolve();
  if (cancellationPromiseRef.current) return cancellationPromiseRef.current;

  const cancellation = NativeFlow.cancelRest({ beginRequestToken });
  cancellationPromiseRef.current = cancellation;
  void cancellation.then(
    () => {
      cancelledRestRef.current = true;
      if (cancellationPromiseRef.current === cancellation) {
        cancellationPromiseRef.current = null;
      }
    },
    () => {
      if (cancellationPromiseRef.current === cancellation) {
        cancellationPromiseRef.current = null;
      }
    },
  );
  return cancellation;
}

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
  const [nativeRemainingMs, setNativeRemainingMs] = useState<number | null>(null);
  const [restClockValid, setRestClockValid] = useState(!isNative);
  const [restReady, setRestReady] = useState(!isNative);
  const [nativeRestConfirmed, setNativeRestConfirmed] = useState(false);
  const [nativeRestObserved, setNativeRestObserved] = useState(false);
  const [nativeStartPending, setNativeStartPending] = useState(isNative);
  const [nativeBeginTokenAvailable, setNativeBeginTokenAvailable] = useState(false);
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
  // Synchronous re-entrancy guard for the async completion flow. `completing`
  // state is rendered asynchronously and cannot reliably prevent two rapid
  // clicks from both entering `handleComplete` before the state commits.
  // This ref is set synchronously at the entry of `handleComplete` and is
  // only released on the failure path (so the user can retry). On success it
  // stays true for the lifetime of this page instance, paired with
  // `completedRestRef`, to guarantee settlement happens exactly once.
  const completionInFlightRef = useRef(false);
  // Fanfare timeouts created in handleComplete. Tracked so that unmount
  // during the 300ms chime sequence cannot fire setState on an unmounted
  // component or leak pending callbacks.
  const fanfareTimeoutsRef = useRef<ReturnType<typeof setTimeout>[]>([]);
  const cancelledRestRef = useRef(false);
  const restCancellationPromiseRef = useRef<Promise<void> | null>(null);
  const beginRequestRejectedRef = useRef(false);
  const beginRequestTokenRef = useRef<string | null>(null);
  const restSessionIdRef = useRef<number | null>(null);

  // ===== Timer hook =====
  const { timeLeft, setTimeLeft, progress } = useRestTimer({
    totalDuration,
    initialSeconds: totalDuration,
    nativeRemainingMs,
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
    let startupFinished = false;
    let observedResting = false;
    let beginRequestToken: string | null = null;
    let cancelWhenBeginResolves = false;
    let startupTimeout: number;
    const finishStartup = () => {
      startupFinished = true;
      window.clearTimeout(startupTimeout);
    };
    const cancelBoundRequest = (token: string) => {
      void cancelNativeRestOnce(
        token,
        cancelledRestRef,
        restCancellationPromiseRef,
      ).catch(() => {});
    };
    startupTimeout = window.setTimeout(() => {
      if (!active || startupFinished) return;
      finishStartup();
      setNativeStartPending(false);
      if (observedResting) {
        // A confirmed RESTING session with an invalid clock is not a failed
        // start. Keep reconciling it and never cancel it just because clock
        // recovery exceeded the startup timeout.
        setOperationError(
          '设备已报告休息进行中，但计时持续无法确认；完成入口仍保持关闭。请等待计时恢复，或确认退出休息。',
        );
        return;
      }
      active = false;
      setOperationError('设备尚未确认休息是否启动，计时没有开始。请返回面板核对保护状态后再试。');
      if (beginRequestToken) cancelBoundRequest(beginRequestToken);
      else cancelWhenBeginResolves = true;
    }, NATIVE_BEGIN_CONFIRMATION_TIMEOUT_MS);
    const restoreOrStartRest = async () => {
      try {
        // beginRest() only enqueues a service command. Its promise resolution
        // (or a stale BLOCKED snapshot) is not evidence that REST has started.
        const begin = await NativeFlow.beginRest();
        beginRequestToken = begin.beginRequestToken;
        beginRequestTokenRef.current = beginRequestToken;
        if (!active) {
          if (cancelWhenBeginResolves) cancelBoundRequest(beginRequestToken);
          return;
        }
        setNativeBeginTokenAvailable(true);
        let attempt = 0;
        let graceObservedAt: number | null = null;
        let observedInvalidRestClock = false;
        // Monitor work can exceed six seconds while reconciling an Android
        // execution gap. Keep waiting for authoritative RESTING while this
        // page is open, backing off to avoid tight polling.
        while (active) {
          let state = await NativeFlow.getBlockState();
          if (!active) return;
          if (state.restBeginRequestToken
              && state.restBeginRequestToken !== beginRequestToken) {
            finishStartup();
            beginRequestRejectedRef.current = true;
            cancelledRestRef.current = true;
            setNativeStartPending(false);
            setBlockState(state.state, state.graceUntil, state.blockedPackage);
            setRestReady(false);
            setOperationError('当前休息请求已被新的页面请求接管，请返回面板确认最新状态。');
            return;
          }
          if (state.restBeginRequestStatusToken === beginRequestToken
              && state.restBeginRequestStatus === 'REJECTED') {
            finishStartup();
            beginRequestRejectedRef.current = true;
            cancelledRestRef.current = true;
            setNativeStartPending(false);
            setBlockState(state.state, state.graceUntil, state.blockedPackage);
            setRestReady(false);
            const rejectionMessage = state.restBeginRequestErrorCode === 'UNSUPPORTED_DEVICE'
              ? '当前设备不在保护支持范围内，休息模式没有启动。'
              : state.restBeginRequestErrorCode === 'PROTECTION_RUNTIME_UNHEALTHY'
                ? '保护服务状态已变化，休息没有启动。请返回面板检查后重试。'
                : '原生服务未能开始休息，本次状态未改变。请返回面板重试。';
            setOperationError(rejectionMessage);
            return;
          }
          if (state.restBeginRequestStatusToken === beginRequestToken
              && state.restBeginRequestStatus === 'ACCEPTED'
              && state.state !== 'RESTING') {
            // The status receipt and block state are read from separate
            // SharedPreferences keys, so a single snapshot may straddle the
            // session commit. Confirm a second complete snapshot before
            // treating ACCEPTED + non-RESTING as a terminal transition.
            const confirmedState = await NativeFlow.getBlockState();
            if (!active) return;
            if (confirmedState.restBeginRequestToken
                && confirmedState.restBeginRequestToken !== beginRequestToken) {
              finishStartup();
              beginRequestRejectedRef.current = true;
              cancelledRestRef.current = true;
              setNativeStartPending(false);
              setBlockState(
                confirmedState.state,
                confirmedState.graceUntil,
                confirmedState.blockedPackage,
              );
              setRestReady(false);
              setOperationError('当前休息请求已被新的页面请求接管，请返回面板确认最新状态。');
              return;
            }
            if (confirmedState.restBeginRequestStatusToken === beginRequestToken
                && confirmedState.restBeginRequestStatus === 'REJECTED') {
              finishStartup();
              beginRequestRejectedRef.current = true;
              cancelledRestRef.current = true;
              setNativeStartPending(false);
              setBlockState(
                confirmedState.state,
                confirmedState.graceUntil,
                confirmedState.blockedPackage,
              );
              setRestReady(false);
              setOperationError('原生服务未能开始休息，本次状态未改变。请返回面板重试。');
              return;
            }
            state = confirmedState;
            if (state.state !== 'RESTING'
                && state.restBeginRequestStatusToken === beginRequestToken
                && state.restBeginRequestStatus === 'ACCEPTED') {
              finishStartup();
              beginRequestRejectedRef.current = true;
              cancelledRestRef.current = true;
              setNativeStartPending(false);
              setBlockState(state.state, state.graceUntil, state.blockedPackage);
              setRestReady(false);
              setOperationError(
                '原生服务已接受休息请求，但当前休息状态随后结束或发生变化。请返回面板确认保护状态。',
              );
              return;
            }
          }
          if (state.state === 'RESTING') {
            observedResting = true;
            setNativeRestObserved(true);
          }
          if (state.state === 'RESTING'
              && (state.restClockValid !== true
                  || typeof state.restRemainingMs !== 'number'
                  || !Number.isFinite(state.restRemainingMs))) {
            observedInvalidRestClock = true;
            setNativeStartPending(false);
            setRestClockValid(false);
            setNativeRemainingMs(0);
            setTimeLeft(0);
            setRestReady(false);
            setOperationError('休息计时暂时无法确认，正在恢复保护状态。');
            await new Promise<void>(resolve => window.setTimeout(resolve, 500));
            continue;
          }
          if (observedInvalidRestClock && state.state !== 'RESTING') {
            finishStartup();
            cancelledRestRef.current = true;
            setBlockState(state.state, state.graceUntil, state.blockedPackage);
            navigate('/dashboard', { replace: true });
            return;
          }
          if (state.state === 'RESTING'
              && state.restStartedAt > 0
              && state.restRequiredSeconds > 0
              && typeof state.restSessionId === 'number'
              && state.restSessionId > 0) {
            finishStartup();
            restSessionIdRef.current = state.restSessionId;
            const remainingMs = state.restRemainingMs as number;
            setNativeStartPending(false);
            setRestClockValid(true);
            setNativeRemainingMs(remainingMs);
            setTimeLeft(Math.max(0, Math.ceil(remainingMs / 1000)));
            setOperationError('');
            setRestReady(true);
            setNativeRestConfirmed(true);
            return;
          }
          if (state.state === 'GRACE') {
            // BEGIN_REST is asynchronous, so tolerate a short-lived snapshot
            // of the prior GRACE state. If it remains authoritative, leave
            // the rest screen and let the dashboard render the grace window.
            graceObservedAt ??= Date.now();
            if (Date.now() - graceObservedAt >= 6_000) {
              finishStartup();
              setBlockState(state.state, state.graceUntil, state.blockedPackage);
              navigate('/dashboard', { replace: true });
              return;
            }
          } else {
            graceObservedAt = null;
          }
          if (attempt === 24) {
            setOperationError('原生休息仍在启动，正在等待确认。');
          }
          const intervalMs = attempt < 24 ? 250 : 2_000;
          attempt++;
          await new Promise<void>(resolve => window.setTimeout(resolve, intervalMs));
        }
      } catch (error) {
        if (!active) return;
        finishStartup();
        setNativeStartPending(false);
        const requestRejectedBeforeToken = beginRequestToken === null;
        if (requestRejectedBeforeToken) {
          beginRequestRejectedRef.current = true;
          cancelledRestRef.current = true;
          setNativeStartPending(false);
        }
        if (error instanceof Error && error.message.includes('PROTECTION_RUNTIME_UNHEALTHY')) {
          setOperationError('保护服务状态已变化，休息没有启动。请返回面板检查后重试。');
        } else if (requestRejectedBeforeToken) {
          setOperationError('原生服务未接受休息请求，本次休息没有启动。请返回面板检查或重试。');
        } else {
          setOperationError('休息请求已发送，但暂时无法读取原生状态。请返回面板确认；退出时会尝试安全取消。');
        }
      }
    };
    void restoreOrStartRest();
    return () => {
      active = false;
      window.clearTimeout(startupTimeout);
      if (!completedRestRef.current && !cancelledRestRef.current) {
        if (beginRequestToken) cancelBoundRequest(beginRequestToken);
        else cancelWhenBeginResolves = true;
      }
    };
  }, []);

  // The native service can cancel REST while the WebView is backgrounded.
  // Reconcile on resume and periodically while visible; the displayed timer
  // follows native monotonic remaining-time samples, never Date.now().
  // Keep observing the confirmed session even when an invalid sample stops
  // its timer. `restReady` must not control this recovery effect's lifetime.
  useEffect(() => {
    if (!isNative || !nativeRestConfirmed || showReward) return;
    let active = true;
    let checking = false;
    const reconcileNativeRest = async () => {
      if (!active || checking || completedRestRef.current
          || completionInFlightRef.current || cancelledRestRef.current
          || document.visibilityState === 'hidden') return;
      checking = true;
      try {
        const nativeState = await NativeFlow.getBlockState();
        if (!active) return;
        if (nativeState.state === 'RESTING'
            && (nativeState.restSessionId !== restSessionIdRef.current
                || nativeState.restClockValid !== true
                || typeof nativeState.restRemainingMs !== 'number'
                || !Number.isFinite(nativeState.restRemainingMs))) {
          setRestClockValid(false);
          setNativeRemainingMs(0);
          setTimeLeft(0);
          setRestReady(false);
          setOperationError('休息计时暂时无法确认，正在恢复保护状态。');
          return;
        }
        if (nativeState.state !== 'RESTING') {
          // A native transition already happened. Avoid a second CANCEL_REST
          // during unmount that could overwrite a legitimate GRACE/IDLE state.
          cancelledRestRef.current = true;
          setBlockState(nativeState.state, nativeState.graceUntil, nativeState.blockedPackage);
          navigate('/dashboard', { replace: true });
        } else {
          const remainingMs = nativeState.restRemainingMs as number;
          setRestClockValid(true);
          setNativeRemainingMs(remainingMs);
          setTimeLeft(Math.max(0, Math.ceil(remainingMs / 1000)));
          setRestReady(true);
          setOperationError('');
        }
      } catch {
        if (active) setOperationError('暂时无法确认休息状态，请返回后重试。');
      } finally {
        checking = false;
      }
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') void reconcileNativeRest();
    };
    const onFocus = () => { void reconcileNativeRest(); };
    document.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('focus', onFocus);
    const timer = window.setInterval(() => { void reconcileNativeRest(); }, 2_000);
    return () => {
      active = false;
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      window.removeEventListener('focus', onFocus);
    };
  }, [isNative, nativeRestConfirmed, showReward, navigate, setBlockState]);

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
        '#247353', '#367C98', '#B7CDBB', '#C9B48D', '#80A69A', '#A4BECB',
      ][i % 6],
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

  // ===== Fanfare timeout cleanup on unmount =====
  // The chime sequence in handleComplete schedules up to two follow-up
  // timeouts. If the component unmounts mid-sequence (e.g. user navigates
  // away), clear them to avoid leaking pending callbacks.
  useEffect(() => {
    return () => {
      fanfareTimeoutsRef.current.forEach(t => clearTimeout(t));
      fanfareTimeoutsRef.current = [];
    };
  }, []);

  // ===== Completion flow =====
  // Re-entry guard: `completionInFlightRef` is a synchronous ref-based lock
  // that prevents two rapid clicks from both entering the async completion
  // path before the `completing` state has a chance to commit in the UI.
  // `completedRestRef` is the permanent settlement marker — once true, the
  // reward has been credited and no further completion is allowed.
  const handleComplete = async () => {
    if (isNative && !restClockValid) {
      setOperationError('休息计时暂时无法确认，保护状态正在恢复。');
      return;
    }
    if (completionInFlightRef.current || completedRestRef.current) {
      return;
    }
    completionInFlightRef.current = true;
    setCompleting(true);
    setOperationError('');
    // Play completion fanfare. Track the timeouts so unmount during the
    // 300ms sequence cannot leak pending callbacks.
    if (!isMuted) {
      playChime(523, 0.2);
      fanfareTimeoutsRef.current.push(
        setTimeout(() => playChime(659, 0.2), 150),
      );
      fanfareTimeoutsRef.current.push(
        setTimeout(() => playChime(784, 0.3), 300),
      );
    }

    // 完成时完全终止背景音乐
    stopAmbient();

    if (isNative) {
      try {
        const result = await NativeFlow.completeRestAndUnlock({
          activity: activity.id,
          duration: totalDuration,
          restSessionId: restSessionIdRef.current ?? 0,
        });
        setBlockState('GRACE', result.graceUntil, '');
        useStore.setState({ points: result.points, streak: result.streak });
        setRewardBadgeTitle(result.achievement === 'health_guardian' ? '健康守护者' : null);
        completedRestRef.current = true;
      } catch {
        // Release the in-flight lock so the user can tap "重试解锁".
        // `completedRestRef` stays false — settlement has not happened.
        completionInFlightRef.current = false;
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
      // Web path also marks settlement so re-entry is blocked identically
      // to the native path.
      completedRestRef.current = true;
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
        if (!beginRequestRejectedRef.current) {
          const beginRequestToken = beginRequestTokenRef.current;
          if (beginRequestToken) {
            await cancelNativeRestOnce(
              beginRequestToken,
              cancelledRestRef,
              restCancellationPromiseRef,
            );
          }
          // If BEGIN_REST has not returned a token yet, navigating away lets
          // the effect cleanup cancel the request as soon as the token arrives.
        }
      } else {
        // Web 端：已休息 30 秒以上时记录部分休息
        const elapsed = totalDuration - timeLeft;
        if (elapsed >= 30) {
          completeRest(activity.id, elapsed);
        }
      }
      navigate('/dashboard');
    } catch (error) {
      if (isNative) {
        const beginRequestToken = beginRequestTokenRef.current;
        if (beginRequestToken) {
          try {
            const nativeState = await NativeFlow.getBlockState();
            if (nativeState.state !== 'RESTING'
                && (!nativeState.restBeginRequestToken
                    || nativeState.restBeginRequestToken === beginRequestToken)
                && nativeState.restBeginRequestStatusToken === beginRequestToken
                && nativeState.restBeginRequestStatus === 'REJECTED') {
              // The service authoritatively rejected this start and confirms
              // there is no REST session to cancel; a stale cancel receipt
              // must not trap the user on the page.
              beginRequestRejectedRef.current = true;
              cancelledRestRef.current = true;
              setBlockState(
                nativeState.state,
                nativeState.graceUntil,
                nativeState.blockedPackage,
              );
              navigate('/dashboard');
              return;
            }
          } catch {
            // Keep the original cancellation error unless native state can
            // positively prove that this request was rejected without REST.
          }
        }
      }
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
      restClockValid={restClockValid}
      restTimerReady={restReady}
      nativeRestObserved={nativeRestObserved}
      nativeStartPending={nativeStartPending}
      nativeBeginTokenAvailable={nativeBeginTokenAvailable}
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
