const BACKGROUND_STABILITY_MANUFACTURERS = ['vivo', 'iqoo'] as const;

export const UNSUPPORTED_DEVICE_MESSAGE = '当前版本暂不支持在此设备上开启保护';
export const UNSUPPORTED_DEVICE_DETAIL =
  '系统后台限制可能导致连续计时停止。为避免显示已保护但实际失效，FlowBreak 暂时不会在此设备上启动保护。';

export function requiresUnsupportedOemFailClosed(manufacturer?: string): boolean {
  const normalized = (manufacturer || '').trim().toLowerCase();
  return BACKGROUND_STABILITY_MANUFACTURERS.some(brand => normalized.includes(brand));
}

/**
 * OriginOS can freeze a monitoring process that has a foreground service
 * unless the owner allows background high-power operation. Keep this list
 * narrow until another OEM has device evidence for the same failure mode.
 */
export function requiresBackgroundStabilityPermission(manufacturer?: string): boolean {
  const normalized = (manufacturer || '').trim().toLowerCase();
  return BACKGROUND_STABILITY_MANUFACTURERS.some(brand => normalized.includes(brand));
}
