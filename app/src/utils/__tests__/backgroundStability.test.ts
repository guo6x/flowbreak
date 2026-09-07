import { describe, expect, it } from 'vitest';
import { requiresBackgroundStabilityPermission } from '../backgroundStability';

describe('requiresBackgroundStabilityPermission', () => {
  it('requires the owner permission for vivo and iQOO manufacturers', () => {
    expect(requiresBackgroundStabilityPermission('vivo')).toBe(true);
    expect(requiresBackgroundStabilityPermission('VIVO')).toBe(true);
    expect(requiresBackgroundStabilityPermission('iQOO')).toBe(true);
  });

  it('does not generalize the requirement to unverified manufacturers', () => {
    expect(requiresBackgroundStabilityPermission('samsung')).toBe(false);
    expect(requiresBackgroundStabilityPermission('xiaomi')).toBe(false);
    expect(requiresBackgroundStabilityPermission('')).toBe(false);
    expect(requiresBackgroundStabilityPermission(undefined)).toBe(false);
  });
});
