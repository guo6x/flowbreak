/**
 * 保护状态纯函数，用于计算 Dashboard 展示所需信息。
 * 不访问 Store、DOM 或任何异步资源。
 */

import type { BlockState } from '../backend/nativeFlow';

/** 阶段中文映射（用户友好）。 */
export const STATE_LABELS: Record<BlockState, string> = {
  IDLE: '正常使用',
  PERCEPTION: '轻提醒阶段',
  COGNITION: '强提醒阶段',
  BLOCKED: '已进入休息引导',
  RESTING: '正在休息',
  GRACE: '访问窗口',
};

/** 下一阶段阈值信息。 */
export interface NextThreshold {
  label: string;
  remainingSeconds: number;
  /** 是否已经进入休息引导（无需再显示倒计时到下一阶段） */
  blocked: boolean;
}

/**
 * 根据当前 session 秒数和限额分钟数计算下一阶段阈值。
 * 阈值规则（与 BlockStateMachine 一致）：
 * - 80% → PERCEPTION（轻提醒）
 * - 100% → COGNITION（强提醒）
 * - 120% → BLOCKED（休息引导）
 */
export function getNextThreshold(
  sessionSeconds: number,
  limitMinutes: number
): NextThreshold {
  const limitSeconds = limitMinutes * 60;
  const ratio = limitSeconds > 0 ? sessionSeconds / limitSeconds : 0;

  if (ratio >= 1.20) {
    return { label: '已进入休息引导', remainingSeconds: 0, blocked: true };
  }
  if (ratio >= 1.00) {
    return {
      label: '进入休息引导',
      remainingSeconds: Math.ceil(limitSeconds * 1.20 - sessionSeconds),
      blocked: false,
    };
  }
  if (ratio >= 0.80) {
    return {
      label: '进入强提醒',
      remainingSeconds: Math.ceil(limitSeconds - sessionSeconds),
      blocked: false,
    };
  }
  return {
    label: '进入轻提醒',
    remainingSeconds: Math.ceil(limitSeconds * 0.80 - sessionSeconds),
    blocked: false,
  };
}

/** 格式化剩余秒数为易读字符串。 */
export function formatRemainingTime(totalSeconds: number): string {
  if (totalSeconds <= 0) return '即将触发';
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes >= 60) {
    const hours = Math.floor(minutes / 60);
    const remainMin = minutes % 60;
    return remainMin > 0 ? `${hours} 小时 ${remainMin} 分钟` : `${hours} 小时`;
  }
  if (minutes > 0) {
    return seconds > 0 ? `${minutes} 分 ${seconds} 秒` : `${minutes} 分钟`;
  }
  return `${seconds} 秒`;
}

/** 格式化剩余秒数为倒计时（MM:SS）。 */
export function formatCountdown(totalSeconds: number): string {
  if (totalSeconds < 0) return '00:00';
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

/** 根据限额计算各阶段精确时间（供设置页展示）。 */
export interface LimitTranslations {
  perceptionMinutes: number;
  cognitionMinutes: number;
  blockedMinutes: number;
}

export function translateLimit(limitMinutes: number): LimitTranslations {
  const limitSeconds = limitMinutes * 60;
  const perception = Math.round((limitSeconds * 0.80) / 60);
  const cognition = Math.round(limitSeconds / 60);
  const blocked = Math.round((limitSeconds * 1.20) / 60);
  return {
    perceptionMinutes: perception,
    cognitionMinutes: cognition,
    blockedMinutes: blocked,
  };
}

/** 保护状态卡视图模型。 */
export interface ProtectionViewModel {
  /** 是否正在监控 */
  monitoring: boolean;
  /** 服务是否出错 */
  hasError: boolean;
  errorMessage: string;
  /** 当前状态标签 */
  stateLabel: string;
  /** 当前状态 raw 值 */
  blockState: BlockState;
  /** 连续使用分钟数（近似） */
  sessionMinutes: number;
  /** 连续使用秒数 */
  sessionSeconds: number;
  /** 限额分钟数 */
  limitMinutes: number;
  /** 下一阶段信息 */
  nextThreshold: NextThreshold;
  /** 访问窗口剩余秒数（仅 GRACE） */
  graceRemainingSeconds: number;
  /** 是否缺少关键权限 */
  missingPermissions: boolean;
  /** 是否无受限应用 */
  noTargetApps: boolean;
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
  noTargetApps: boolean;
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
    noTargetApps,
  } = params;

  const hasError = isMonitoring && !!serviceError;
  const model: ProtectionViewModel = {
    monitoring: isMonitoring,
    hasError,
    errorMessage: serviceError,
    stateLabel: STATE_LABELS[blockState] || '未知',
    blockState,
    sessionMinutes: Math.floor(sessionSeconds / 60),
    sessionSeconds,
    limitMinutes,
    nextThreshold: getNextThreshold(sessionSeconds, limitMinutes),
    graceRemainingSeconds: graceUntil > 0 ? Math.max(0, Math.ceil((graceUntil - now) / 1000)) : 0,
    missingPermissions,
    noTargetApps,
  };

  return model;
}
