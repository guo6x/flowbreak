import { registerPlugin, PluginListenerHandle } from '@capacitor/core';

export type BlockState = 'IDLE' | 'PERCEPTION' | 'COGNITION' | 'BLOCKED' | 'RESTING' | 'GRACE';

export interface LaunchableApp {
  packageName: string;
  label: string;
  iconDataUri: string;
}

export interface PermissionState {
  hasUsageStats: boolean;
  hasOverlay: boolean;
  isIgnoringBattery: boolean;
  hasNotification: boolean;
  hasAccessibility: boolean;
  isDomestic: boolean;
  channel: 'play' | 'domestic' | 'base';
  manufacturer?: string;
}

export interface NativeSettings {
  limitMinutes: number;
  restDuration: number;
  allowEmergencyUnlock: boolean;
  strongBlockingEnabled: boolean;
  monitoringEnabled: boolean;
  targetApps: string[];
  channel: string;
}

export interface NativeFlowPlugin {
  checkPermissions(): Promise<PermissionState>;
  requestUsageStatsPermission(): Promise<void>;
  requestOverlayPermission(): Promise<void>;
  requestIgnoreBatteryOptimizations(): Promise<void>;
  requestNotificationPermission(): Promise<void>;
  requestAccessibilityPermission(): Promise<void>;
  openAutoStartSettings(): Promise<void>;
  getUsageStats(): Promise<{ screenTimeSeconds: number }>;
  startService(options?: {
    limitMinutes?: number;
    apps?: string[];
    monitoringEnabled?: boolean;
  }): Promise<void>;
  stopService(): Promise<void>;
  beginRest(): Promise<void>;
  cancelRest(): Promise<void>;
  getCurrentApp(): Promise<{ packageName: string }>;
  getCurrentFatigueLevel(): Promise<{ level: number; minutes: number }>;
  getBlockState(): Promise<{
    state: BlockState;
    sessionSeconds: number;
    graceUntil: number;
    blockedPackage: string;
    restStartedAt: number;
    restRequiredSeconds: number;
  }>;
  completeRestAndUnlock(options: {
    activity: 'eye' | 'stretch' | 'breathe';
    duration: number;
  }): Promise<{ graceUntil: number; points: number; streak: number; achievement: string }>;
  requestEmergencyUnlock(): Promise<{
    allowed: boolean;
    graceUntil: number;
    remainingToday: number;
  }>;
  getLaunchableApps(): Promise<{ apps: LaunchableApp[] }>;
  saveTargetApps(options: { packageNames: string[] }): Promise<void>;
  saveSettings(options: Partial<{
    limitMinutes: number;
    restDuration: number;
    targetApps: string[];
    allowEmergencyUnlock: boolean;
    strongBlockingEnabled: boolean;
    monitoringEnabled: boolean;
  }>): Promise<void>;
  loadSettings(): Promise<NativeSettings>;
  getAppUsageList(): Promise<{
    apps: Array<{ packageName: string; totalTimeSeconds: number }>;
  }>;
  getDashboardSummary(): Promise<{
    blockCount: number;
    restCount: number;
    rescuedSeconds: number;
    unlockSeconds: number;
    points: number;
    streak: number;
    pullbackOutcomeCount: number;
    successfulPullbackCount: number;
    postRestReturnCount: number;
    postRestTargetSeconds: number;
    reflectionValue: number;
  }>;
  getStatisticsHistory(options: { days: number }): Promise<{
    days: Array<{
      date: string;
      screenTimeSeconds: number;
      restCount: number;
      interventionCount: number;
      blockCount: number;
      unlockSeconds: number;
      pullbackOutcomeCount: number;
      successfulPullbackCount: number;
      postRestReturnCount: number;
      postRestTargetSeconds: number;
      reflectionValue: number;
    }>;
  }>;
  getValidationSummary(options: { days: number }): Promise<{
    days: number;
    restCount: number;
    outcomeCount: number;
    successfulPullbackCount: number;
    postRestReturnCount: number;
    postRestTargetSeconds: number;
    helpedDays: number;
    neutralDays: number;
    notHelpedDays: number;
  }>;
  saveDailyReflection(options: { value: 'helped' | 'neutral' | 'not_helped' }): Promise<void>;
  getDiagnostics(): Promise<{
    versionName: string;
    versionCode: number;
    channel: string;
    packageName: string;
    databaseVersion: number;
    serviceAlive: boolean;
    serviceHeartbeatAt: number;
    lastUsageEventAt: number;
    state: BlockState;
    sessionSeconds: number;
    graceUntil: number;
    monitoringEnabled: boolean;
    targetCount: number;
    eventCount: number;
    usageRowCount: number;
    latestEventAt: number;
    permissions: PermissionState;
  }>;
  exportDiagnostics(): Promise<{ json: string }>;
  shareDiagnostics(): Promise<void>;
  migrateLegacyData(options: {
    payload: Record<string, unknown>;
  }): Promise<{ migrated: boolean; version: number }>;
  exportLocalData(options?: { uiData?: Record<string, unknown> }): Promise<{ json: string }>;
  shareLocalData(options?: { uiData?: Record<string, unknown> }): Promise<void>;
  clearLocalData(): Promise<void>;
  getBuildInfo(): Promise<{
    versionName: string;
    versionCode: number;
    channel: string;
    packageName: string;
  }>;
  consumePendingNavigation(): Promise<{ path: string }>;
  getCrashLogs(): Promise<{ logs: Array<{ filename: string; timestamp: number; content: string }> }>;
  clearCrashLogs(): Promise<void>;
  addListener(
    eventName: 'permissionsChanged',
    listenerFunc: (info: PermissionState) => void,
  ): Promise<PluginListenerHandle>;
}

export const NativeFlow = registerPlugin<NativeFlowPlugin>('NativeFlow');
