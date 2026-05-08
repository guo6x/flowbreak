// src/backend/fatigueEngine.ts
// PRD 3.2 疲劳度计算算法 - 精确还原

export interface FatigueMetrics {
  interactionFrequency: number;  // 次/分钟 (正常 1-5)
  videoSpeed: number;            // 倍速 (正常 0.75-1.5)
  appSwitchFrequency: number;    // 次/分钟 (正常 0.5-2)
  screenBrightness: number;      // 百分比 (正常 30-80)
  usageDuration: number;         // 分钟
  timeFactor: number;            // 当前小时 (0-23)
}

export const INITIAL_METRICS: FatigueMetrics = {
  interactionFrequency: 3,
  videoSpeed: 1.0,
  appSwitchFrequency: 1,
  screenBrightness: 50,
  usageDuration: 0,
  timeFactor: new Date().getHours(),
};

function normalizeMetric(metric: keyof FatigueMetrics, value: number): number {
  switch (metric) {
    case 'interactionFrequency':
      return Math.max(0, 1 - value / 5);
    case 'videoSpeed':
      return Math.max(0, (value - 1.5) / 1.5);
    case 'appSwitchFrequency':
      return Math.max(0, 1 - value / 2);
    case 'screenBrightness':
      return Math.max(0, (value / 100 - 0.8) / 0.2);
    case 'usageDuration':
      return Math.min(1, value / 60);
    case 'timeFactor':
      if (value >= 22 || value <= 2) return 0.8;
      if (value >= 13 && value <= 15) return 0.6;
      return 0.2;
    default:
      return 0;
  }
}

export function calculateFatigueScore(metrics: FatigueMetrics): number {
  const weights: Record<keyof FatigueMetrics, number> = {
    interactionFrequency: 0.3,
    videoSpeed: 0.2,
    appSwitchFrequency: 0.15,
    screenBrightness: 0.1,
    usageDuration: 0.15,
    timeFactor: 0.1,
  };

  let score = 0;
  for (const key of Object.keys(weights) as Array<keyof FatigueMetrics>) {
    score += normalizeMetric(key, metrics[key]) * weights[key];
  }
  return Math.min(1, Math.max(0, score));
}

export type InterventionLevel = 'NONE' | 'PERCEPTION' | 'COGNITION' | 'ACTION';

export function getInterventionLevel(score: number): InterventionLevel {
  if (score < 0.3) return 'NONE';
  if (score < 0.5) return 'PERCEPTION';
  if (score < 0.7) return 'COGNITION';
  return 'ACTION';
}
