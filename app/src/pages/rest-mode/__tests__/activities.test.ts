// Unit tests for the static rest-activity configuration.

import { describe, it, expect } from 'vitest';
import { activities, getActivityByIndex, findActivityById } from '../activities';
import type { RestActivityId } from '../types';

describe('activities configuration', () => {
  it('exposes the three canonical rest activities', () => {
    expect(activities).toHaveLength(3);
    const ids = activities.map(a => a.id);
    expect(ids).toEqual(['eye', 'stretch', 'breathe']);
  });

  it('every activity has a unique id', () => {
    const ids = activities.map(a => a.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('every activity has a positive step list', () => {
    for (const a of activities) {
      expect(a.steps.length).toBeGreaterThan(0);
    }
  });

  it('every activity has a non-empty title and description', () => {
    for (const a of activities) {
      expect(a.title.length).toBeGreaterThan(0);
      expect(a.desc.length).toBeGreaterThan(0);
    }
  });

  it('every activity has a #RRGGBB colour', () => {
    for (const a of activities) {
      expect(a.color).toMatch(/^#[0-9a-fA-F]{6}$/);
    }
  });

  it('every activity has a recognised iconKey', () => {
    const validKeys = ['eye', 'stretch', 'breathe'];
    for (const a of activities) {
      expect(validKeys).toContain(a.iconKey);
    }
  });
});

describe('getActivityByIndex', () => {
  it('returns the activity at a valid index', () => {
    expect(getActivityByIndex(0).id).toBe('eye');
    expect(getActivityByIndex(1).id).toBe('stretch');
    expect(getActivityByIndex(2).id).toBe('breathe');
  });

  it('wraps negative indices', () => {
    // -1 -> last activity
    expect(getActivityByIndex(-1).id).toBe('breathe');
  });

  it('wraps indices beyond the length', () => {
    expect(getActivityByIndex(3).id).toBe('eye');
    expect(getActivityByIndex(5).id).toBe('breathe');
  });

  it('returns the eye activity for the page default index 0', () => {
    // The page initialises activityIdx from location.state.activityIdx ?? 0.
    // The default-landing activity must remain 'eye'.
    expect(getActivityByIndex(0).id).toBe('eye');
  });
});

describe('findActivityById', () => {
  it('finds each activity by id', () => {
    const ids: RestActivityId[] = ['eye', 'stretch', 'breathe'];
    for (const id of ids) {
      const found = findActivityById(id);
      expect(found).toBeDefined();
      expect(found?.id).toBe(id);
    }
  });

  it('returns undefined for an unknown id', () => {
    // Cast so we can probe the runtime behaviour without weakening the type.
    expect(findActivityById('unknown' as RestActivityId)).toBeUndefined();
  });
});
