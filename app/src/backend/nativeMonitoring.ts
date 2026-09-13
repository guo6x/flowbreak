import { NativeFlow } from './nativeFlow';

/** Synchronize the native protection intent through the single native API path. */
export async function syncNativeMonitoring(
  enabled: boolean,
  sessionLimit: number,
  targetApps: string[],
): Promise<void> {
  if (!enabled) {
    await NativeFlow.stopService();
    return;
  }
  if (targetApps.length === 0) {
    throw new Error('请先选择至少一个受限应用。');
  }
  await NativeFlow.saveSettings({
    limitMinutes: sessionLimit,
    targetApps,
    monitoringEnabled: true,
  });
  await NativeFlow.startService({
    limitMinutes: sessionLimit,
    apps: targetApps,
    monitoringEnabled: true,
  });
}
