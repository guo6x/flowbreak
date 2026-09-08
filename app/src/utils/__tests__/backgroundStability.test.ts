import { describe, expect, it } from 'vitest';
import {
  requiresBackgroundStabilityPermission,
  requiresUnsupportedOemFailClosed,
} from '../backgroundStability';

describe('requiresUnsupportedOemFailClosed', () => {
  it('fails closed for vivo and iQOO manufacturers', () => {
    expect(requiresUnsupportedOemFailClosed('vivo')).toBe(true);
    expect(requiresUnsupportedOemFailClosed('VIVO')).toBe(true);
    expect(requiresUnsupportedOemFailClosed('iQOO')).toBe(true);
  });

  it('does not generalize fail-closed behavior to unverified manufacturers', () => {
    expect(requiresUnsupportedOemFailClosed('xiaomi')).toBe(false);
    expect(requiresUnsupportedOemFailClosed('redmi')).toBe(false);
    expect(requiresUnsupportedOemFailClosed('samsung')).toBe(false);
    expect(requiresUnsupportedOemFailClosed('oppo')).toBe(false);
    expect(requiresUnsupportedOemFailClosed('honor')).toBe(false);
    expect(requiresUnsupportedOemFailClosed(undefined)).toBe(false);
  });
});

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
