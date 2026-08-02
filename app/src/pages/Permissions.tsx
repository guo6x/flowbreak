import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { motion } from 'framer-motion';
import { Accessibility, ArrowLeft, Battery, Bell, ChevronDown, ChevronUp, Clock, Eye, Power, RefreshCw, Shield } from 'lucide-react';
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
  const [requesting, setRequesting] = useState<string | null>(null);
  const [actionError, setActionError] = useState('');
  const [autoStartOpened, setAutoStartOpened] = useState(false);
  const [optionalExpanded, setOptionalExpanded] = useState(false);

  useEffect(() => {
    refreshPermissions();
  }, [isNative, refreshPermissions]);

  const manufacturer = state.manufacturer;
  const needsAutoStart = isNative
    && state.isDomestic
    && AUTO_START_BRANDS.some(b => (manufacturer || '').toLowerCase().includes(b));

  const requiredItems = [
    { key: 'usage', icon: Clock, title: '使用情况访问', desc: '识别所选应用并计算连续使用时长', granted: state.hasUsageStats },
    { key: 'overlay', icon: Eye, title: '悬浮窗权限', desc: '在达到阻断条件时显示全屏覆盖页', granted: state.hasOverlay },
  ];

  const optionalItems = [
    { key: 'notification', icon: Bell, title: '通知权限', desc: '显示前台服务状态与阶段提醒', granted: state.hasNotification },
    { key: 'battery', icon: Battery, title: '电池优化豁免', desc: '降低厂商后台限制导致的漏检', granted: state.isIgnoringBattery },
    ...(needsAutoStart ? [{
      key: 'autostart',
      icon: Power,
      title: `${brandLabel(manufacturer)} 自启动`,
      desc: autoStartOpened
        ? '已打开系统设置。此项无法由应用验证，请按系统页面完成设置后返回。'
        : '允许 FlowBreak 开机自启并加入后台白名单，避免被清理',
      granted: false,
    }] : []),
    ...(state.isDomestic ? [{
      key: 'accessibility',
      icon: Accessibility,
      title: '无障碍强阻断',
      desc: '仅监听前台应用切换，命中阻断后返回桌面',
      granted: state.hasAccessibility,
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

  const usageGranted = state.hasUsageStats;
  const overlayGranted = state.hasOverlay;

  const canProceed = isNative
    ? usageGranted && overlayGranted
    : true;

  let btnText = '继续设置保护';
  if (isNative && !canProceed) {
    if (!usageGranted) {
      btnText = '还需开启：使用情况访问';
    } else if (!overlayGranted) {
      btnText = '还需开启：悬浮窗权限';
    }
  }

  const getStatusText = (granted: boolean): string => {
    if (checking) return '检测中…';
    if (checkError) return '检测失败';
    return granted ? '已开启' : '未开启';
  };

  const getStatusClass = (granted: boolean): string => {
    if (checking) return 'text-gray-400';
    if (checkError) return 'text-error';
    return granted ? 'text-primary' : 'text-gray-400';
  };

  return (
    <div className="flex flex-col min-h-dvh px-8 pt-14 pb-10">
      <button onClick={() => navigate('/onboarding')} aria-label="返回引导页" className="w-10 h-10 rounded-full bg-white card flex items-center justify-center mb-4">
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
        <h1 className="text-[24px] font-bold mb-2">开始保护前需要两项权限</h1>
        <p className="text-[14px] text-gray-500 mb-7">所有数据仅在本机处理。权限可随时在系统设置中撤销。</p>

        {/* Required permissions */}
        <p className="text-[12px] font-medium text-gray-400 mb-3 uppercase tracking-wide">必需权限</p>
        <div className="flex flex-col gap-3 mb-6">
          {requiredItems.map(item => {
            const Icon = item.icon;
            const isGranted = item.granted;
            const statusText = getStatusText(isGranted);
            const statusClass = getStatusClass(isGranted);
            const isReq = requesting === item.key;
            const disabled = (!isNative && item.key !== 'notification');
            return (
              <div
                key={item.key}
                className={`flex items-center gap-4 p-4 rounded-2xl border-2 ${isGranted ? 'border-primary bg-primary/5' : 'border-gray-200 bg-white'}`}
              >
                <div className={`w-11 h-11 rounded-xl flex items-center justify-center ${isGranted ? 'bg-primary/15' : 'bg-gray-100'}`}>
                  <Icon size={21} className={isGranted ? 'text-primary' : 'text-gray-400'} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-[14px] font-medium">{item.title}</p>
                  <p className="text-[11px] text-gray-500 mt-1">{item.desc}</p>
                </div>
                <span className={`text-[12px] font-medium shrink-0 ${statusClass}`}>{statusText}</span>
                <button
                  onClick={() => request(item.key)}
                  disabled={isReq || disabled}
                  className={`shrink-0 px-3 py-1.5 rounded-lg text-[12px] font-medium transition-colors ${item.granted ? 'bg-primary/10 text-primary' : 'bg-secondary/10 text-secondary'}`}
                >
                  {isReq ? '请求中…' : isGranted ? '已授权' : '去开启'}
                </button>
              </div>
            );
          })}
        </div>

        {/* Optional permissions - collapsible */}
        <div className="mb-6">
          <button
            onClick={() => setOptionalExpanded(!optionalExpanded)}
            className="w-full flex items-center justify-between p-3 rounded-xl bg-gray-50 hover:bg-gray-100 transition-colors"
          >
            <span className="text-[13px] font-medium text-gray-600">提升后台稳定性（可稍后设置）</span>
            {optionalExpanded ? <ChevronUp size={18} className="text-gray-400" /> : <ChevronDown size={18} className="text-gray-400" />}
          </button>
          {optionalExpanded && (
            <div className="flex flex-col gap-2 mt-3">
              {optionalItems.map(item => {
                const Icon = item.icon;
                const isGranted = item.granted;
                const statusText = getStatusText(isGranted);
                const statusClass = getStatusClass(isGranted);
                const isReq = requesting === item.key;
                const disabled = (!isNative && item.key !== 'notification');
                return (
                  <div
                    key={item.key}
                    className={`flex items-center gap-3 p-3 rounded-xl border ${isGranted ? 'border-primary/30 bg-primary/[0.03]' : 'border-gray-100 bg-white'}`}
                  >
                    <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${isGranted ? 'bg-primary/10' : 'bg-gray-100'}`}>
                      <Icon size={16} className={isGranted ? 'text-primary' : 'text-gray-400'} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-[13px] font-medium">{item.title}</p>
                      <p className="text-[11px] text-gray-400 mt-0.5">{item.desc}</p>
                    </div>
                    <span className={`text-[11px] font-medium shrink-0 ${statusClass}`}>{statusText}</span>
                    <button
                      onClick={() => request(item.key)}
                      disabled={isReq || disabled}
                      className={`shrink-0 px-2.5 py-1 rounded-lg text-[11px] font-medium transition-colors ${isGranted ? 'bg-primary/10 text-primary' : 'bg-gray-100 text-gray-600'}`}
                    >
                      {isReq ? '…' : isGranted ? '已授权' : '设置'}
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {(checkError || actionError) && <p className="text-[12px] text-error text-center mb-3">{actionError || checkError}</p>}

        {/* Bottom buttons */}
        <div className="mt-auto flex items-center gap-3">
          <button
            onClick={refreshPermissions}
            disabled={checking}
            className="flex items-center justify-center gap-2 rounded-2xl bg-gray-100 py-3.5 px-5 text-[13px] font-medium text-gray-600 shrink-0 disabled:opacity-50 transition-colors hover:bg-gray-200"
          >
            <RefreshCw size={16} className={checking ? 'animate-spin' : ''} />
            重新检测权限
          </button>
          <button onClick={() => navigate('/personalize')} disabled={!canProceed} className="btn-primary flex-1 disabled:opacity-50">
            {btnText}
          </button>
        </div>
      </motion.div>
    </div>
  );
}
