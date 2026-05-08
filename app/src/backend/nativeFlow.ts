import { registerPlugin, PluginListenerHandle } from '@capacitor/core';

export interface NativeFlowPlugin {
  checkPermissions(): Promise<{ hasUsageStats: boolean; hasOverlay: boolean }>;
  requestUsageStatsPermission(): Promise<void>;
  requestOverlayPermission(): Promise<void>;
  getUsageStats(): Promise<{ screenTimeSeconds: number }>;
  startService(): Promise<void>;
  stopService(): Promise<void>;
  addListener(
    eventName: 'permissionsChanged',
    listenerFunc: (info: { hasUsageStats: boolean; hasOverlay: boolean }) => void,
  ): Promise<PluginListenerHandle>;
}

export const NativeFlow = registerPlugin<NativeFlowPlugin>('NativeFlow');
