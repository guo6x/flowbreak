// Unit tests for the pure colour/time helpers in colorUtils.ts.

import { describe, it, expect } from 'vitest';
import { lerpColor, formatTime } from '../colorUtils';

describe('lerpColor', () => {
  it('returns c1 unchanged at t = 0', () => {
    expect(lerpColor('#000000', '#ffffff', 0)).toBe('rgb(0,0,0)');
  });

  it('returns c2 unchanged at t = 1', () => {
    expect(lerpColor('#000000', '#ffffff', 1)).toBe('rgb(255,255,255)');
  });

  it('returns the midpoint at t = 0.5', () => {
    expect(lerpColor('#000000', '#ffffff', 0.5)).toBe('rgb(128,128,128)');
  });

  it('preserves the original unbounded behaviour for t > 1', () => {
    // The original implementation did NOT clamp t; callers (e.g. breathColor
    // with progress > 1) rely on the extrapolated value. Lock that in.
    expect(lerpColor('#000000', '#ffffff', 2)).toBe('rgb(510,510,510)');
  });

  it('preserves the original unbounded behaviour for t < 0', () => {
    expect(lerpColor('#000000', '#ffffff', -1)).toBe('rgb(-255,-255,-255)');
  });

  it('interpolates each channel independently', () => {
    // #4CAF50 -> #A5D6A7 at t=0.5
    // R: 0x4C=76  -> 0xA5=165  mid = (76+165)/2 = 120.5 -> 121 (rounded)
    // G: 0xAF=175 -> 0xD6=214  mid = (175+214)/2 = 194.5 -> 195 (rounded)
    // B: 0x50=80  -> 0xA7=167  mid = (80+167)/2 = 123.5 -> 124 (rounded)
    expect(lerpColor('#4CAF50', '#A5D6A7', 0.5)).toBe('rgb(121,195,124)');
  });
});

describe('formatTime', () => {
  it('formats zero as 0:00', () => {
    expect(formatTime(0)).toBe('0:00');
  });

  it('formats seconds under a minute with leading zero', () => {
    expect(formatTime(5)).toBe('0:05');
    expect(formatTime(9)).toBe('0:09');
  });

  it('formats minutes:seconds correctly', () => {
    expect(formatTime(65)).toBe('1:05');
    expect(formatTime(180)).toBe('3:00');
    expect(formatTime(599)).toBe('9:59');
  });

  it('formats the canonical 3-minute rest duration', () => {
    expect(formatTime(180)).toBe('3:00');
  });
});
