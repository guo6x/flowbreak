export type AppUsage = {
  id: string;
  name: string;
  seconds: number;
};

export type AppUsageShare = AppUsage & { percentage: number };

export function nonNegativeNumber(value: number): number {
  return Number.isFinite(value) ? Math.max(0, value) : 0;
}

/** Keep the complete denominator, including apps outside the visible top list. */
export function getUsageShares(usages: AppUsage[], maxRows = 8): AppUsageShare[] {
  const grouped = new Map<string, AppUsage>();
  for (const usage of usages) {
    const seconds = nonNegativeNumber(usage.seconds);
    if (seconds === 0) continue;
    const previous = grouped.get(usage.id);
    grouped.set(usage.id, { ...usage, seconds: seconds + (previous?.seconds ?? 0) });
  }
  const sorted = [...grouped.values()].sort((a, b) => b.seconds - a.seconds);
  const total = sorted.reduce((sum, usage) => sum + usage.seconds, 0);
  if (total === 0) return [];

  const rowLimit = Math.max(2, Math.floor(maxRows));
  const visible = sorted.length > rowLimit
    ? [
      ...sorted.slice(0, rowLimit - 1),
      {
        id: '__other_apps__',
        name: '其他应用',
        seconds: sorted.slice(rowLimit - 1).reduce((sum, usage) => sum + usage.seconds, 0),
      },
    ]
    : sorted;
  const shares = visible.map(usage => ({
    ...usage,
    percentage: Math.floor(usage.seconds / total * 100),
  }));

  // Largest remainder rounding preserves 100% without inflating the first app.
  const remainder = 100 - shares.reduce((sum, share) => sum + share.percentage, 0);
  const fractionalOrder = visible
    .map((usage, index) => ({ index, fraction: usage.seconds / total * 100 % 1 }))
    .sort((a, b) => b.fraction - a.fraction);
  for (let index = 0; index < remainder; index += 1) {
    shares[fractionalOrder[index].index].percentage += 1;
  }
  return shares;
}

export function formatUsageTime(seconds: number): string {
  const safeSeconds = nonNegativeNumber(seconds);
  if (safeSeconds > 0 && safeSeconds < 60) return '不足1分钟';
  const minutes = Math.floor(safeSeconds / 60);
  if (minutes < 60) return `${minutes}分钟`;
  const remaining = minutes % 60;
  return `${Math.floor(minutes / 60)}小时${remaining > 0 ? `${remaining}分` : ''}`;
}
