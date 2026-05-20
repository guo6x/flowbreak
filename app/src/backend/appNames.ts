export const APP_NAMES: Record<string, string> = {
  // 视频/短视频
  'com.ss.android.ugc.aweme': '抖音',
  'tv.danmaku.bili': 'B站',
  'com.smile.gifmaker': '快手',
  'com.google.android.youtube': 'YouTube',
  'com.tencent.qqlive': '腾讯视频',
  'com.qiyi.video': '爱奇艺',
  'com.youku.phone': '优酷',
  'com.ss.android.ugc.live': '抖音极速版',
  'com.kuaishou.nebula': '快手极速版',
  // 社交
  'com.tencent.mm': '微信',
  'com.tencent.mobileqq': 'QQ',
  'com.xingin.xhs': '小红书',
  'com.sina.weibo': '微博',
  'com.zhiliaoapp.musically': 'TikTok',
  'com.tencent.wework': '企业微信',
  'com.alibaba.android.rimet': '钉钉',
  // 浏览器
  'com.android.chrome': 'Chrome',
  'com.UCMobile': 'UC浏览器',
  'com.tencent.mtt': 'QQ浏览器',
  'com.baidu.browser.apps': '百度浏览器',
  'org.mozilla.firefox': 'Firefox',
  'com.microsoft.emmx': 'Edge',
  // 购物
  'com.taobao.taobao': '淘宝',
  'com.jingdong.app.mall': '京东',
  'com.xunmeng.pinduoduo': '拼多多',
  'com.meituan.android': '美团',
  // 游戏
  'com.tencent.tmgp.sgame': '王者荣耀',
  'com.tencent.tmgp.pubgmhd': '和平精英',
  'com.miHoYo.ys': '原神',
  'com.netease.l10': '蛋仔派对',
  // 新闻/阅读
  'com.ss.android.article.news': '今日头条',
  'com.tencent.news': '腾讯新闻',
  'com.netease.newsreader.activity': '网易新闻',
  'com.zhihu.android': '知乎',
  'com.dragon.read': '番茄小说',
  // 系统/工具
  'com.android.settings': '系统设置',
  'com.android.launcher': '桌面',
  'com.android.systemui': '系统界面',
  'com.google.android.apps.nexuslauncher': '桌面',
  'com.android.vending': 'Google Play',
  // 音乐
  'com.tencent.qqmusic': 'QQ音乐',
  'com.netease.cloudmusic': '网易云音乐',
  'com.kugou.android': '酷狗音乐',
};

export const DEFAULT_TARGET_APPS = [
  'com.ss.android.ugc.aweme',
  'tv.danmaku.bili',
  'com.smile.gifmaker',
  'com.tencent.mm',
  'com.xingin.xhs',
];

export function getAppName(packageName: string): string {
  if (!packageName) return '未知应用';
  const known = APP_NAMES[packageName];
  if (known) return known;
  // For known patterns but unmapped specific variants, try to match
  const parts = packageName.split('.');
  const lastPart = parts[parts.length - 1];
  // Common known package endings that are recognizable
  const recognizable: Record<string, string> = {
    'android': '系统应用',
    'google': 'Google',
    'samsung': 'Samsung',
    'huawei': '华为',
    'xiaomi': '小米',
    'oppo': 'OPPO',
    'vivo': 'vivo',
    'launcher': '桌面',
    'settings': '系统设置',
    'camera': '相机',
    'gallery': '相册',
    'calendar': '日历',
    'clock': '时钟',
    'calculator': '计算器',
    'contacts': '联系人',
    'phone': '电话',
    'messages': '短信',
    'email': '邮件',
    'music': '音乐',
    'video': '视频',
    'photo': '照片',
    'file': '文件管理',
    'browser': '浏览器',
    'maps': '地图',
    'weather': '天气',
    'notes': '笔记',
    'health': '健康',
    'fitness': '运动',
    'store': '应用商店',
    'pay': '支付',
    'bank': '银行',
    'wallet': '钱包',
    'reader': '阅读器',
    'news': '新闻',
    'game': '游戏',
    'chat': '聊天',
    'social': '社交',
    'shop': '购物',
    'food': '美食',
    'travel': '出行',
    'car': '汽车',
    'home': '智能家居',
    'tv': '电视',
    'wear': '穿戴设备',
    'input': '输入法',
    'keyboard': '输入法',
    'vpn': 'VPN',
    'security': '安全',
    'clean': '清理工具',
    'backup': '备份',
    'sync': '同步',
    'assistant': '语音助手',
    'ai': 'AI助手',
  };
  const fallback = recognizable[lastPart];
  if (fallback) return fallback;
  // If the last part looks like a readable word, capitalize first letter
  if (/^[a-zA-Z]{3,}$/.test(lastPart)) {
    return lastPart.charAt(0).toUpperCase() + lastPart.slice(1);
  }
  // Completely unknown - return a generic label
  return '其他应用';
}
