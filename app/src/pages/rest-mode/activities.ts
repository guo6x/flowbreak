// Static rest-activity configuration. Pure data — no React, no browser side-effects.
import type { RestActivity, RestActivityId } from './types';

export const activities: RestActivity[] = [
  {
    id: 'eye',
    iconKey: 'eye',
    title: '眼部放松',
    desc: '转动眼球 + 看远处绿色植物',
    steps: [
      '闭上眼睛深呼吸3次',
      '慢慢睁开，向上看5秒',
      '缓缓向右转动眼球',
      '向下看5秒',
      '向左转动眼球完成一圈',
      '看向窗外最远的绿色物体',
    ],
    color: '#4CAF50',
  },
  {
    id: 'stretch',
    iconKey: 'stretch',
    title: '身体拉伸',
    desc: '颈部 + 肩部拉伸动作',
    steps: [
      '站起来，双脚与肩同宽',
      '将头缓缓向右倾斜',
      '保持5秒，感受左侧拉伸',
      '换向左侧倾斜，保持5秒',
      '双手向上举起伸展',
      '缓缓放下，转动肩膀',
    ],
    color: '#2196F3',
  },
  {
    id: 'breathe',
    iconKey: 'breathe',
    title: '深呼吸',
    desc: '4-7-8 呼吸法',
    steps: [
      '找一个舒适的坐姿',
      '用鼻子吸气 4 秒',
      '屏住呼吸 7 秒',
      '用嘴慢慢呼气 8 秒',
      '重复 3 次',
      '感受身体的放松',
    ],
    color: '#FF9800',
  },
];

export function getActivityByIndex(idx: number): RestActivity {
  const safe = ((idx % activities.length) + activities.length) % activities.length;
  return activities[safe];
}

export function findActivityById(id: RestActivityId): RestActivity | undefined {
  return activities.find(a => a.id === id);
}
