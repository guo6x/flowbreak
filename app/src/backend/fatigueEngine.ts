// src/backend/fatigueEngine.ts
// PRD 3.2 疲劳度计算算法 - 简化2维版本

export interface FatigueMetrics {
  usageDuration: number;         // 分钟
  timeFactor: number;            // 当前小时 (0-23)
}

export const INITIAL_METRICS: FatigueMetrics = {
  usageDuration: 0,
  timeFactor: new Date().getHours(),
};

function normalizeMetric(metric: keyof FatigueMetrics, value: number): number {
  switch (metric) {
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
    usageDuration: 0.7,
    timeFactor: 0.3,
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
