/**
 * 保护状态纯函数，用于计算 Dashboard 展示所需信息。
 * 不访问 Store、DOM 或任何异步资源。
 */

import type { BlockState } from "../backend/nativeFlow";

/** 阶段中文映射（用户友好）。 */
export const STATE_LABELS: Record<BlockState, string> = {
  IDLE: "正常使用",
  PERCEPTION: "轻提醒阶段",
  COGNITION: "强提醒阶段",
  BLOCKED: "已进入休息引导",
  RESTING: "正在休息",
  GRACE: "访问窗口",
};

/** 下一阶段阈值信息。 */
export interface NextThreshold {
  label: string;
  remainingSeconds: number;
  blocked: boolean;
}

export function getNextThreshold(
  sessionSeconds: number,
  limitMinutes: number
): NextThreshold {
  const limitSeconds = limitMinutes * 60;
  if (limitSeconds <= 0) {
    return { label: "未设置限额", remainingSeconds: 0, blocked: false };
  }
  const ratio = sessionSeconds / limitSeconds;
  if (ratio >= 1.20) {
    return { label: "已进入休息引导", remainingSeconds: 0, blocked: true };
  }
  if (ratio >= 1.00) {
    return {
      label: "进入休息引导",
      remainingSeconds: Math.ceil(limitSeconds * 1.20 - sessionSeconds),
      blocked: false,
    };
  }
  if (ratio >= 0.80) {
    return {
      label: "进入强提醒",
      remainingSeconds: Math.ceil(limitSeconds - sessionSeconds),
      blocked: false,
    };
  }
  return {
    label: "进入轻提醒",
    remainingSeconds: Math.ceil(limitSeconds * 0.80 - sessionSeconds),
    blocked: false,
  };
}

export function formatRemainingTime(totalSeconds: number): string {
  if (totalSeconds <= 0) return "即将触发";
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  if (m >= 60) {
    const h = Math.floor(m / 60);
    const rm = m % 60;
    return rm > 0 ? `${h} 小时 ${rm} 分钟` : `${h} 小时`;
  }
  if (m > 0) {
    return s > 0 ? `${m} 分 ${s} 秒` : `${m} 分钟`;
  }
  return `${s} 秒`;
}

export function formatCountdown(totalSeconds: number): string {
  if (totalSeconds < 0) return "00:00";
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return String(m).padStart(2, "0") + ":" + String(s).padStart(2, "0");
}

export interface LimitTranslations {
  perceptionSeconds: number;
  cognitionSeconds: number;
  blockedSeconds: number;
  perceptionMinutes: number;
  cognitionMinutes: number;
  blockedMinutes: number;
  perceptionLabel: string;
  cognitionLabel: string;
  blockedLabel: string;
}

export function translateLimit(limitMinutes: number): LimitTranslations {
  const limitSeconds = limitMinutes * 60;
  const perceptionSeconds = Math.round(limitSeconds * 0.80);
  const cognitionSeconds = limitSeconds;
  const blockedSeconds = Math.round(limitSeconds * 1.20);
  const perceptionMinutes = Math.round(perceptionSeconds / 60);
  const cognitionMinutes = Math.round(cognitionSeconds / 60);
  const blockedMinutes = Math.round(blockedSeconds / 60);
  return {
    perceptionSeconds,
    cognitionSeconds,
    blockedSeconds,
    perceptionMinutes,
    cognitionMinutes,
    blockedMinutes,
    perceptionLabel: `约${perceptionMinutes}分钟`,
    cognitionLabel: `约${cognitionMinutes}分钟`,
    blockedLabel: `约${blockedMinutes}分钟`,
  };
}

export interface ProtectionViewModel {
  monitoring: boolean;
  hasError: boolean;
  errorMessage: string;
  stateLabel: string;
  blockState: BlockState;
  sessionMinutes: number;
  sessionSeconds: number;
  limitMinutes: number;
  nextThreshold: NextThreshold;
  graceRemainingSeconds: number;
  missingPermissions: boolean;
  missingPermissionLabel: string;
  noTargetApps: boolean;
  currentAppName: string;
}

export function getProtectionViewModel(params: {
  isMonitoring: boolean;
  serviceError: string;
  blockState: BlockState;
  sessionSeconds: number;
  limitMinutes: number;
  graceUntil: number;
  now: number;
  missingPermissions: boolean;
  missingPermissionLabel: string;
  noTargetApps: boolean;
  currentAppName: string;
}): ProtectionViewModel {
  const {
    isMonitoring,
    serviceError,
    blockState,
    sessionSeconds,
    limitMinutes,
    graceUntil,
    now,
    missingPermissions,
    missingPermissionLabel,
    noTargetApps,
    currentAppName,
  } = params;

  const hasError = isMonitoring && !!serviceError;
  const noThreshold: BlockState[] = ["BLOCKED", "RESTING", "GRACE"];
  const showThreshold = !noThreshold.includes(blockState);

  return {
    monitoring: isMonitoring,
    hasError,
    errorMessage: serviceError,
    stateLabel: STATE_LABELS[blockState] || "未知",
    blockState,
    sessionMinutes: Math.floor(sessionSeconds / 60),
    sessionSeconds,
    limitMinutes,
    nextThreshold: showThreshold
      ? getNextThreshold(sessionSeconds, limitMinutes)
      : { label: "", remainingSeconds: 0, blocked: false },
    graceRemainingSeconds: blockState === "GRACE" && graceUntil > 0
      ? Math.max(0, Math.ceil((graceUntil - now) / 1000))
      : 0,
    missingPermissions,
    missingPermissionLabel: missingPermissionLabel || "",
    noTargetApps,
    currentAppName: currentAppName || "",
  };
}