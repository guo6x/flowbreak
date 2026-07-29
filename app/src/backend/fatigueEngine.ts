// src/backend/fatigueEngine.ts
// PRD 3.2 疲劳度计算算法 - 简化2维版本
// 浏览器口径为降级预览，真机以原生百分比为准

export type InterventionLevel = 'NONE' | 'PERCEPTION' | 'COGNITION' | 'ACTION';

export function getLevelByPercent(percent: number): InterventionLevel {
  if (percent >= 120) return 'ACTION';
  if (percent >= 100) return 'COGNITION';
  if (percent >= 80) return 'PERCEPTION';
  return 'NONE';
}
