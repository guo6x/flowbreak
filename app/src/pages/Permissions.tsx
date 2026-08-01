import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { motion } from 'framer-motion';
import { Accessibility, ArrowLeft, Battery, Bell, Check, Clock, Eye, Power, RefreshCw, Shield } from 'lucide-react';
import { NativeFlow } from '../backend/nativeFlow';
import { useNativePermissions } from '../hooks/useNativePermissions';

const AUTO_START_BRANDS = [
  'xiaomi', 'redmi', 'blackshark',
  'huawei',
  'honor', 'hihonor',
  'oppo', 'realme', 'oneplus', 'oplus',
  'vivo', 'iqoo',
  'meizu',
  'samsung',
  'letv', 'leeco',
  'asus',
  'zte', 'nubia',
  'lenovo', 'motorola',
];

function brandLabel(manufacturer?: string): string {
  if (!manufacturer) return '系统';
  const m = manufacturer.toLowerCase();
  if (m.includes('xiaomi') || m.includes('redmi') || m.includes('blackshark')) return '小米 / 红米 / 黑鲨';
  if (m.includes('huawei')) return '华为';
  if (m.includes('honor') || m.includes('hihonor')) return '荣耀';
  if (m.includes('oppo') || m.includes('realme') || m.includes('oneplus') || m.includes('oplus')) {
    return 'OPPO / 一加 / realme';
  }
  if (m.includes('vivo') || m.includes('iqoo')) return 'vivo / iQOO';
  if (m.includes('meizu')) return '魅族';
  if (m.includes('samsung')) return '三星';
  if (m.includes('letv') || m.includes('leeco')) return '乐视';
  if (m.includes('asus')) return '华硕';
  if (m.includes('zte') || m.includes('nubia')) return '中兴 / 努比亚';
  if (m.includes('lenovo') || m.includes('motorola')) return '联想 / 摩托罗拉';
  return '系统';
}

export default function Permissions() {
  const navigate = useNavigate();
  const { isNative, permissions: state, checking, error: checkError, refresh: refreshPermissions } = useNativePermissions(true);
  const [notificationHandled, setNotificationHandled] = useState(false);
  const [requesting, setRequesting] = useState<string | null>(null);
  const [actionError, setActionError] = useState('');
  const [autoStartOpened, setAutoStartOpened] = useState(false);

  useEffect(() => {
    if (!isNative) {
      if (typeof Notification !== 'undefined') {
        setNotificationHandled(Notification.permission !== 'default');
      } else {
        setNotificationHandled(true);
      }
    }
  }, [isNative]);

  const manufacturer = state.manufacturer;
  const needsAutoStart = isNative
    && state.isDomestic
    && AUTO_START_BRANDS.some(b => (manufacturer || '').toLowerCase().includes(b));

  const items = [
    { key: 'usage', icon: Clock, title: '使用情况访问', desc: '识别所选应用并计算连续使用时长', granted: state.hasUsageStats, required: true },
    { key: 'overlay', icon: Eye, title: '悬浮窗权限', desc: '在达到阻断条件时显示全屏覆盖页', granted: state.hasOverlay, required: true },
    { key: 'notification', icon: Bell, title: '通知权限', desc: '显示前台服务状态与阶段提醒', granted: state.hasNotification, required: false },
    { key: 'battery', icon: Battery, title: '电池优化豁免', desc: '降低厂商后台限制导致的漏检', granted: state.isIgnoringBattery, required: false },
    ...(needsAutoStart ? [{
      key: 'autostart',
      icon: Power,
      title: `${brandLabel(manufacturer)} 自启动`,
      desc: autoStartOpened
        ? '已打开系统设置。此项无法由应用验证，请按系统页面完成设置后返回。'
        : '允许 FlowBreak 开机自启并加入后台白名单，避免被清理',
      // Android does not expose a reliable, cross-vendor readback for this.
      // Do not render an opened settings page as a granted permission.
      granted: false,
      required: false,
    }] : []),
    ...(state.isDomestic ? [{
      key: 'accessibility',
      icon: Accessibility,
      title: '无障碍强阻断',
      desc: '仅监听前台应用切换，命中阻断后返回桌面',
      granted: state.hasAccessibility,
      required: false,
    }] : []),
  ];

  const request = async (key: string) => {
    if (requesting) return;
    setActionError('');
    setRequesting(key);
    try {
      if (!isNative) {
        if (key === 'notification' && typeof Notification !== 'undefined') {
          const value = await Notification.requestPermission();
          setNotificationHandled(true);
          if (value === 'granted') await refreshPermissions();
        }
        return;
      }
      if (key === 'usage') await NativeFlow.requestUsageStatsPermission();
      else if (key === 'overlay') await NativeFlow.requestOverlayPermission();
      else if (key === 'notification') await NativeFlow.requestNotificationPermission();
      else if (key === 'battery') await NativeFlow.requestIgnoreBatteryOptimizations();
      else if (key === 'accessibility') await NativeFlow.requestAccessibilityPermission();
      else if (key === 'autostart') {
        await NativeFlow.openAutoStartSettings();
        setAutoStartOpened(true);
      }
      window.setTimeout(refreshPermissions, 500);
    } catch (error) {
      setActionError(error instanceof Error ? error.message : '权限请求失败，请稍后重试。');
    } finally {
      setRequesting(null);
    }
  };

  const canProceed = isNative
    ? state.hasUsageStats && state.hasOverlay
    : notificationHandled;

  return (
    <div className="flex flex-col min-h-dvh px-8 pt-14 pb-10">
      <button onClick={() => navigate('/login')} aria-label="返回登录页" className="w-10 h-10 rounded-full bg-white card flex items-center justify-center mb-4">
        <ArrowLeft size={20} />
      </button>
      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="flex flex-col flex-1">
        {!isNative && (
          <div className="mb-5 p-4 bg-secondary/5 border border-secondary/10 rounded-2xl flex gap-3">
            <Shield size={18} className="text-secondary shrink-0" />
            <p className="text-[12px] text-gray-600">Web 仅用于界面预览，正式阻断能力只支持 Android。</p>
          </div>
        )}
        <div className="w-14 h-14 rounded-2xl bg-secondary/10 flex items-center justify-center mb-5">
          <Shield size={30} className="text-secondary" />
        </div>
        <h1 className="text-[24px] font-bold mb-2">权限设置</h1>
        <p className="text-[14px] text-gray-500 mb-7">所有数据仅在本机处理。权限可随时在系统设置中撤销。</p>

        <button
          onClick={refreshPermissions}
          disabled={checking}
          className="mb-4 flex items-center justify-center gap-2 rounded-xl bg-gray-100 py-2.5 text-[12px] text-gray-600 disabled:opacity-60"
        >
          <RefreshCw size={14} className={checking ? 'animate-spin' : ''} />
          {checking ? '正在检测权限...' : '重新检测权限'}
        </button>

        <div className="flex flex-col gap-3 mb-8">
          {items.map(item => {
            const Icon = item.icon;
            const disabled = !isNative && item.key !== 'notification';
            return (
              <button
                key={item.key}
                onClick={() => request(item.key)}
                disabled={disabled || requesting === item.key}
                aria-label={`${item.title} 权限${item.granted ? '已开启' : '未开启'}`}
                className={`flex items-center gap-4 p-4 rounded-2xl border-2 text-left ${item.granted ? 'border-primary bg-primary/5' : 'border-gray-300/60 bg-white'} ${disabled ? 'opacity-50' : ''}`}
              >
                <div className={`w-11 h-11 rounded-xl flex items-center justify-center ${item.granted ? 'bg-primary/15' : 'bg-gray-100'}`}>
                  <Icon size={21} className={item.granted ? 'text-primary' : 'text-gray-400'} />
                </div>
                <div className="flex-1">
                  <p className="text-[14px] font-medium">{item.title}{item.required && isNative ? ' · 必需' : ''}</p>
                  <p className="text-[11px] text-gray-500 mt-1">{item.desc}</p>
                </div>
                <div className={`w-6 h-6 rounded-full border-2 flex items-center justify-center ${item.granted ? 'border-primary bg-primary' : 'border-gray-300'}`}>
                  {requesting === item.key ? (
                    <RefreshCw size={12} className="text-gray-500 animate-spin" />
                  ) : item.granted ? (
                    <Check size={14} className="text-white" />
                  ) : null}
                </div>
              </button>
            );
          })}
        </div>

        {(checkError || actionError) && <p className="text-[12px] text-error text-center mb-3">{actionError || checkError}</p>}

        <div className="mt-auto">
          <button onClick={() => navigate('/personalize')} disabled={!canProceed} className="btn-primary w-full disabled:opacity-50">
            继续
          </button>
          <p className="text-[11px] text-gray-400 text-center mt-3">
            使用情况访问和悬浮窗为必需；通知、电池优化、自启动白名单与强阻断可稍后设置
          </p>
          {needsAutoStart && (
            <p className="text-[11px] text-secondary/70 text-center mt-2">
              检测到 {brandLabel(manufacturer)} 机型，建议开启自启动白名单以保证后台保活生效
            </p>
          )}
        </div>
      </motion.div>
    </div>
  );
}
