// Pure color and time formatting utilities. No React, no browser globals.

/**
 * Linearly interpolate between two hex colors (`#RRGGBB`).
 *
 * Behaviour preserved from the original RestMode.tsx: `t` is NOT clamped,
 * so callers relying on the unbounded extrapolation (e.g. progress > 1)
 * continue to get the same rgb() string they used to.
 */
export function lerpColor(c1: string, c2: string, t: number): string {
  const r1 = parseInt(c1.slice(1, 3), 16);
  const g1 = parseInt(c1.slice(3, 5), 16);
  const b1 = parseInt(c1.slice(5, 7), 16);
  const r2 = parseInt(c2.slice(1, 3), 16);
  const g2 = parseInt(c2.slice(3, 5), 16);
  const b2 = parseInt(c2.slice(5, 7), 16);
  return `rgb(${Math.round(r1 + (r2 - r1) * t)},${Math.round(g1 + (g2 - g1) * t)},${Math.round(b1 + (b2 - b1) * t)})`;
}

/**
 * Format a duration in seconds as `M:SS`.
 */
export function formatTime(s: number): string {
  return `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`;
}
