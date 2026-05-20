import { registerPlugin, PluginListenerHandle } from '@capacitor/core';

export interface NativeFlowPlugin {
  checkPermissions(): Promise<{ hasUsageStats: boolean; hasOverlay: boolean; isIgnoringBattery: boolean; hasNotification: boolean }>;
  requestUsageStatsPermission(): Promise<void>;
  requestOverlayPermission(): Promise<void>;
  getUsageStats(): Promise<{ screenTimeSeconds: number }>;
  startService(options?: { limitMinutes?: number; apps?: string[] }): Promise<void>;
  stopService(): Promise<void>;
  snoozeService(): Promise<void>;
  getCurrentApp(): Promise<{ packageName: string }>;
  getCurrentFatigueLevel(): Promise<{ level: number; minutes: number }>;
  saveSettings(options: {
    limitMinutes: number;
    targetApps: string[];
    reminderEnabled?: boolean;
    reminderIntervalMinutes?: number;
    reminderStartHour?: number;
    reminderEndHour?: number;
  }): Promise<void>;
  loadSettings(): Promise<{ limitMinutes: number; targetApps: string }>;
  getAppUsageList(): Promise<{ apps: Array<{ packageName: string; totalTimeSeconds: number }> }>;
  requestIgnoreBatteryOptimizations(): Promise<void>;
  requestNotificationPermission(): Promise<void>;
  addListener(
    eventName: 'permissionsChanged',
    listenerFunc: (info: { hasUsageStats: boolean; hasOverlay: boolean; isIgnoringBattery: boolean; hasNotification: boolean }) => void,
  ): Promise<PluginListenerHandle>;
}

export const NativeFlow = registerPlugin<NativeFlowPlugin>('NativeFlow');
