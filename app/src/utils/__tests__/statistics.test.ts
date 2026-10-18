import { describe, expect, it } from 'vitest';
import { formatUsageTime, getUsageShares, nonNegativeNumber } from '../statistics';

describe('statistics display calculations', () => {
  it('returns an empty distribution for zero or invalid durations', () => {
    expect(getUsageShares([
      { id: 'a', name: 'A', seconds: 0 },
      { id: 'b', name: 'B', seconds: -10 },
      { id: 'c', name: 'C', seconds: Number.NaN },
      { id: 'd', name: 'D', seconds: Number.POSITIVE_INFINITY },
    ])).toEqual([]);
  });

  it('aggregates duplicate app ids before calculating shares', () => {
    expect(getUsageShares([
      { id: 'a', name: 'A', seconds: 30 },
      { id: 'b', name: 'B', seconds: 40 },
      { id: 'a', name: 'A', seconds: 30 },
    ])).toEqual([
      { id: 'a', name: 'A', seconds: 60, percentage: 60 },
      { id: 'b', name: 'B', seconds: 40, percentage: 40 },
    ]);
  });

  it('keeps separate packages with the same display name', () => {
    expect(getUsageShares([
      { id: 'first.package', name: '同名应用', seconds: 60 },
      { id: 'second.package', name: '同名应用', seconds: 60 },
    ])).toHaveLength(2);
  });

  it('rounds by largest remainder without making the total exceed 100%', () => {
    const shares = getUsageShares([
      { id: 'a', name: 'A', seconds: 1 },
      { id: 'b', name: 'B', seconds: 1 },
      { id: 'c', name: 'C', seconds: 1 },
    ]);
    expect(shares.map(share => share.percentage)).toEqual([34, 33, 33]);
    expect(shares.reduce((sum, share) => sum + share.percentage, 0)).toBe(100);
  });

  it('groups the tail into other apps rather than inflating the top app', () => {
    const shares = getUsageShares(Array.from({ length: 10 }, (_, index) => ({
      id: `app-${index}`, name: `应用${index}`, seconds: 60,
    })));
    expect(shares).toHaveLength(8);
    expect(shares[0].percentage).toBe(10);
    expect(shares[shares.length - 1]).toEqual({ id: '__other_apps__', name: '其他应用', seconds: 180, percentage: 30 });
    expect(shares.reduce((sum, share) => sum + share.percentage, 0)).toBe(100);
    expect(shares.reduce((sum, share) => sum + share.seconds, 0)).toBe(600);
  });

  it('shows all eight apps when none need to be grouped', () => {
    const shares = getUsageShares(Array.from({ length: 8 }, (_, index) => ({
      id: `app-${index}`, name: `应用${index}`, seconds: index + 1,
    })));
    expect(shares).toHaveLength(8);
    expect(shares.some(share => share.name === '其他应用')).toBe(false);
    expect(shares.reduce((sum, share) => sum + share.percentage, 0)).toBe(100);
  });

  it('formats short use honestly and uses readable hours and minutes', () => {
    expect(formatUsageTime(0)).toBe('0分钟');
    expect(formatUsageTime(12)).toBe('不足1分钟');
    expect(formatUsageTime(60)).toBe('1分钟');
    expect(formatUsageTime(3600)).toBe('1小时');
    expect(formatUsageTime(3720)).toBe('1小时2分');
    expect(formatUsageTime(-60)).toBe('0分钟');
  });

  it('does not surface NaN or infinity as a count', () => {
    expect(nonNegativeNumber(Number.NaN)).toBe(0);
    expect(nonNegativeNumber(Number.POSITIVE_INFINITY)).toBe(0);
    expect(nonNegativeNumber(-1)).toBe(0);
    expect(nonNegativeNumber(12)).toBe(12);
  });
});
