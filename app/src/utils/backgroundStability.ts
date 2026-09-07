const BACKGROUND_STABILITY_MANUFACTURERS = ['vivo', 'iqoo'] as const;

/**
 * OriginOS can freeze a monitoring process that has a foreground service
 * unless the owner allows background high-power operation. Keep this list
 * narrow until another OEM has device evidence for the same failure mode.
 */
export function requiresBackgroundStabilityPermission(manufacturer?: string): boolean {
  const normalized = (manufacturer || '').trim().toLowerCase();
  return BACKGROUND_STABILITY_MANUFACTURERS.some(brand => normalized.includes(brand));
}
