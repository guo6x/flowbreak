import { describe, expect, it } from 'vitest';
import { getOemBackgroundGuide } from '../oemBackgroundGuide';

describe('OEM background guidance', () => {
  it.each([
    ['Xiaomi', '小米 / 红米 / 黑鲨'], ['redmi', '小米 / 红米 / 黑鲨'],
    ['blackshark', '小米 / 红米 / 黑鲨'], ['HUAWEI', '华为'],
    ['HONOR', '荣耀'], ['hihonor', '荣耀'],
    ['OPPO', 'OPPO / 一加 / realme'], ['oplus', 'OPPO / 一加 / realme'],
    ['oneplus', 'OPPO / 一加 / realme'], ['realme', 'OPPO / 一加 / realme'],
    ['samsung', '三星'], ['meizu', '魅族'], ['asus', '华硕'],
    ['zte', '中兴 / 努比亚'], ['nubia', '中兴 / 努比亚'],
    ['lenovo', '联想 / 摩托罗拉'], ['motorola', '联想 / 摩托罗拉'],
    ['letv', '乐视'], ['leeco', '乐视'],
  ])('provides manual steps for %s without claiming verified support', (manufacturer, label) => {
    const guide = getOemBackgroundGuide(manufacturer);
    expect(guide.label).toBe(label);
    expect(guide.steps.length).toBeGreaterThanOrEqual(2);
    expect(guide.steps.join(' ')).toContain('FlowBreak');
    expect(guide.steps.join(' ')).not.toMatch(/保证|已兼容|已验证/);
  });

  it('uses sleep-list guidance rather than inventing Samsung autostart', () => {
    const text = getOemBackgroundGuide(' samsung ').steps.join(' ');
    expect(text).toContain('深度休眠');
    expect(text).toContain('从不休眠');
    expect(text).not.toContain('自启动');
  });

  it.each([undefined, '', 'Google', 'new-vendor'])('retains platform fallback for %s', manufacturer => {
    const guide = getOemBackgroundGuide(manufacturer);
    expect(guide.label).toBe('系统');
    expect(guide.steps.join(' ')).toContain('受限制');
  });
});
