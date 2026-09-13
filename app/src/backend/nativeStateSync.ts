import { getAppName } from './appNames';
import { NativeFlow, type BlockState } from './nativeFlow';
import type { InterventionLevel } from './fatigueEngine';
import { useStore } from '../hooks/useStore';

/** Apply one authoritative native snapshot to the WebView store. */
export function applyNativeState(
  app: { packageName: string },
  result: { screenTimeSeconds: number },
  block: {
    state: BlockState;
    sessionSeconds: number;
    graceUntil: number;
    blockedPackage: string;
  },
  sessionLimit: number,
): void {
  const store = useStore.getState();
  store.setCurrentAppName(getAppName(app.packageName));

  const safeTotal = Math.max(0, result.screenTimeSeconds);
  if (safeTotal !== store.todayStats.totalScreenTime) {
    store.setScreenTime(safeTotal);
  }

  const levelMap: Partial<Record<BlockState, InterventionLevel>> = {
    PERCEPTION: 'PERCEPTION',
    COGNITION: 'COGNITION',
    BLOCKED: 'ACTION',
  };
  const normalizedScore = Math.min(
    1,
    Math.max(0, block.sessionSeconds / 60 / Math.max(1, sessionLimit)),
  );
  store.setFatigue(normalizedScore, levelMap[block.state] || 'NONE');
  store.setContinuousSessionSeconds(block.sessionSeconds);
  store.setBlockState(block.state, block.graceUntil, block.blockedPackage);
}

/**
 * Poll native state independently of local Zustand monitoring intent. Native
 * remains the source of truth when the WebView and service temporarily diverge.
 */
export function startNativeStatePolling(
  sessionLimit: number,
  intervalMs = 10_000,
): () => void {
  let active = true;
  const syncNativeState = async () => {
    try {
      const [app, result, block] = await Promise.all([
        NativeFlow.getCurrentApp(),
        NativeFlow.getUsageStats(),
        NativeFlow.getBlockState(),
      ]);
      if (!active) return;
      applyNativeState(app, result, block, sessionLimit);
    } catch {
      // Permission and service startup can briefly lag the WebView.
    }
  };

  void syncNativeState();
  const timer = window.setInterval(() => { void syncNativeState(); }, intervalMs);
  return () => {
    active = false;
    window.clearInterval(timer);
  };
}
