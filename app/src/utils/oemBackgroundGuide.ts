export interface OemBackgroundGuide {
  label: string;
  steps: readonly string[];
}

// Manual search hints, not a compatibility allowlist or proof of granted settings.
// Private settings activities may disappear between OS versions; always retain
// these platform-level instructions alongside the best-effort native shortcut.
export function getOemBackgroundGuide(manufacturer?: string): OemBackgroundGuide {
  const brand = (manufacturer || '').trim().toLowerCase();
  const matches = (...names: string[]) => names.some(name => brand.includes(name));
  if (matches('xiaomi', 'redmi', 'blackshark')) return {
    label: '小米 / 红米 / 黑鲨',
    steps: [
      '设置中搜索「后台自启动」或「自启动管理」，允许 FlowBreak 自启动。',
      '在 FlowBreak 应用信息的电池 / 省电策略中，若有「无限制」选项，可按需选择。',
    ],
  };
  if (matches('huawei', 'honor')) return {
    label: matches('honor') ? '荣耀' : '华为',
    steps: [
      '设置中搜索「应用启动管理」，找到 FlowBreak，关闭自动管理并允许后台活动。',
      '若有电池优化列表，找到 FlowBreak 并选择不允许优化；不要清除应用或系统管理器的数据。',
    ],
  };
  if (matches('oppo', 'oplus', 'oneplus', 'realme')) return {
    label: 'OPPO / 一加 / realme',
    steps: [
      '在 FlowBreak 应用信息的电池使用情况 / 耗电管理中，查找并按需允许后台活动。',
      '设置中搜索「自启动」；若当前系统提供该开关，为 FlowBreak 开启。找不到时使用应用电池设置。',
    ],
  };
  if (matches('samsung')) return {
    label: '三星',
    steps: [
      '在「电池和设备维护 → 电池 → 后台使用限制」中，将 FlowBreak 移出休眠 / 深度休眠应用。',
      '若系统提供「从不休眠的应用」，可将 FlowBreak 加入；它与电池优化豁免不是同一个开关。',
    ],
  };

  const families: [string[], string][] = [
    [['meizu'], '魅族'], [['asus'], '华硕'], [['zte', 'nubia'], '中兴 / 努比亚'],
    [['lenovo', 'motorola'], '联想 / 摩托罗拉'], [['letv', 'leeco'], '乐视'],
  ];
  const label = families.find(([names]) => matches(...names))?.[1] || '系统';
  return {
    label,
    steps: [
      '在系统设置的应用列表中找到 FlowBreak，查看电池 / 后台运行设置，避免选择「受限制」。',
      '若系统另有自启动、后台管理或休眠名单，可搜索这些关键词；没有对应选项时无需反复授权。',
    ],
  };
}
