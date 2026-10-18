import { useState } from 'react';
import { useNavigate } from 'react-router';
import { motion, useReducedMotion } from 'framer-motion';
import { Accessibility, ArrowLeft, Battery, Bell, ChevronDown, ChevronUp, Clock, Eye, Power, RefreshCw, Shield } from 'lucide-react';
import { NativeFlow } from '../backend/nativeFlow';
import { useNativePermissions } from '../hooks/useNativePermissions';
import {
  UNSUPPORTED_DEVICE_DETAIL,
  UNSUPPORTED_DEVICE_MESSAGE,
} from '../utils/backgroundStability';

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
  const reduceMotion = useReducedMotion();


  const manufacturer = state.manufacturer;
  const unsupportedDevice = isNative && state.unsupportedDevice === true;
  const needsAutoStart = isNative
    && !unsupportedDevice
    && state.isDomestic
    && AUTO_START_BRANDS.some(b => (manufacturer || '').toLowerCase().includes(b));

  const requiredItems = unsupportedDevice ? [] : [
      { key: 'usage', icon: Clock, title: '使用情况访问', desc: '统计所选应用的连续使用时长', granted: state.hasUsageStats },
      { key: 'overlay', icon: Eye, title: '悬浮窗权限', desc: '达到阻断条件时显示覆盖页', granted: state.hasOverlay },
    ];

  const optionalItems = unsupportedDevice ? [] : [
    { key: 'notification', icon: Bell, title: '通知权限', desc: '显示前台服务状态与阶段提醒', granted: state.hasNotification },
    {
      key: 'battery',
      icon: Battery,
      title: '电池优化豁免',
      desc: '降低厂商后台限制导致的漏检',
      granted: state.isIgnoringBattery,
    },
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
    ? !unsupportedDevice && usageGranted && overlayGranted
    : true;

  let btnText = unsupportedDevice ? UNSUPPORTED_DEVICE_MESSAGE : '继续设置保护';
  if (isNative && !unsupportedDevice && !canProceed) {
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
    return granted ? 'text-primary' : 'text-gray-500';
  };

  return (
    <div className="flex flex-col min-h-dvh px-5 pt-7 pb-8 sm:px-8">
      <div className="flex items-center justify-between mb-7">
        <button onClick={() => navigate('/onboarding')} aria-label="返回引导页" className="w-[44px] h-[44px] rounded-full bg-white card flex items-center justify-center">
          <ArrowLeft size={20} />
        </button>
        <span className="text-[13px] font-medium text-gray-500">保护设置 · 第 1 步</span>
      </div>
      <motion.div initial={reduceMotion ? false : { opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} className="flex flex-col flex-1">
        {!isNative && (
          <div className="mb-5 p-4 bg-secondary/5 border border-secondary/10 rounded-2xl flex gap-3">
            <Shield size={18} className="text-secondary shrink-0" />
            <p className="text-[13px] text-gray-600 leading-relaxed">Web 仅用于界面预览，正式阻断能力只支持 Android。</p>
          </div>
        )}
        <div className="w-14 h-14 rounded-2xl bg-secondary/10 flex items-center justify-center mb-5">
          <Shield size={30} className="text-secondary" />
        </div>
        <h1 className="text-[26px] font-bold tracking-tight mb-3">
          {unsupportedDevice ? UNSUPPORTED_DEVICE_MESSAGE : '开始保护前需要两项权限'}
        </h1>
        <p className="text-[14px] text-gray-600 leading-6 mb-7">
          {unsupportedDevice ? UNSUPPORTED_DEVICE_DETAIL : '所有数据仅在本机处理。权限可随时在系统设置中撤销。'}
        </p>

        {unsupportedDevice && (
          <div className="mb-6 rounded-2xl border border-error/20 bg-error/5 p-4 text-[13px] text-error" role="alert">
            为避免显示已保护但实际失效，FlowBreak 不会在此设备上启动保护。你的目标应用、限额和历史数据不会被删除。
          </div>
        )}

        {/* Required permissions */}
        {!unsupportedDevice && <div className="flex items-center justify-between mb-3">
          <p className="text-[13px] font-semibold text-gray-700">必需权限</p>
          {isNative && <p className="text-[13px] text-primary font-medium" role="status">已开启 {Number(usageGranted) + Number(overlayGranted)} / 2</p>}
        </div>}
        {!unsupportedDevice && <div className="flex flex-col gap-3 mb-6">
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
                className={`p-4 rounded-[22px] border ${isGranted ? 'border-primary/35 bg-primary/5' : 'border-gray-200 bg-white'}`}
              >
                <div className="flex items-start gap-3">
                  <div className={`w-11 h-11 shrink-0 rounded-2xl flex items-center justify-center ${isGranted ? 'bg-primary/10' : 'bg-secondary/10'}`}>
                    <Icon size={21} className={isGranted ? 'text-primary' : 'text-secondary'} aria-hidden="true" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-[15px] font-semibold">{item.title}</p>
                    <p className="text-[13px] text-gray-600 leading-relaxed mt-1">{item.desc}</p>
                  </div>
                </div>
                <div className="mt-3 pl-14 flex items-center justify-between gap-3">
                  <span className={`text-[13px] font-medium ${statusClass}`}>{isNative ? statusText : '仅 Android 可设置'}</span>
                  <button
                    onClick={() => request(item.key)}
                    disabled={isReq || disabled}
                    aria-label={`${isGranted ? '查看' : '开启'}${item.title}`}
                    className={`min-h-[44px] px-4 rounded-xl text-[13px] font-semibold transition-colors disabled:opacity-50 ${item.granted ? 'bg-primary/10 text-primary' : 'bg-secondary/10 text-secondary'}`}
                  >
                    {isReq ? '请求中…' : isGranted ? '已授权' : '去开启'}
                  </button>
                </div>
              </div>
            );
          })}
        </div>}

        {/* Optional permissions - collapsible */}
        {!unsupportedDevice && <div className="mb-6">
          <button
            onClick={() => setOptionalExpanded(!optionalExpanded)}
            aria-expanded={optionalExpanded}
            aria-controls="optional-permissions"
            className="w-full min-h-[52px] flex items-center justify-between gap-3 p-4 rounded-2xl bg-white border border-gray-200 hover:bg-gray-100 transition-colors text-left"
          >
            <span className="text-[13px] font-medium text-gray-600">提升后台稳定性（可稍后设置）</span>
            {optionalExpanded ? <ChevronUp size={18} className="text-gray-400" /> : <ChevronDown size={18} className="text-gray-400" />}
          </button>
          {optionalExpanded && (
            <div id="optional-permissions" className="flex flex-col gap-3 mt-3">
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
                    className={`p-4 rounded-2xl border ${isGranted ? 'border-primary/30 bg-primary/[0.03]' : 'border-gray-200 bg-white'}`}
                  >
                    <div className="flex items-start gap-3">
                      <div className={`w-9 h-9 shrink-0 rounded-xl flex items-center justify-center ${isGranted ? 'bg-primary/10' : 'bg-gray-100'}`}>
                        <Icon size={18} className={isGranted ? 'text-primary' : 'text-gray-600'} aria-hidden="true" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-[14px] font-medium">{item.title}</p>
                        <p className="text-[13px] leading-relaxed text-gray-600 mt-1">{item.desc}</p>
                        {item.key === 'accessibility' && <p className="text-[12px] text-secondary mt-1">可选实验性能力，不读取页面内容。</p>}
                      </div>
                    </div>
                    <div className="mt-2 pl-12 flex items-center justify-between gap-3">
                      <span className={`text-[13px] font-medium ${statusClass}`}>{item.key === 'autostart' ? (autoStartOpened ? '请在系统中确认' : '需手动设置') : statusText}</span>
                      <button
                        onClick={() => request(item.key)}
                        disabled={isReq || disabled}
                        aria-label={`设置${item.title}`}
                        className={`min-h-[44px] px-4 rounded-xl text-[13px] font-medium transition-colors disabled:opacity-50 ${isGranted ? 'bg-primary/10 text-primary' : 'bg-gray-100 text-gray-700'}`}
                      >
                        {isReq ? '请求中…' : isGranted ? '已授权' : '设置'}
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>}

        {(checkError || actionError) && <p role="alert" className="text-[13px] text-error text-center mb-3">{actionError || checkError}</p>}

        {/* Bottom buttons */}
        <div className="mt-auto flex flex-col gap-3 pt-3">
          <button onClick={() => navigate('/personalize')} disabled={!canProceed} className="btn-primary w-full disabled:opacity-50">
            {btnText}
          </button>
          <button
            onClick={() => void refreshPermissions()}
            disabled={checking}
            className="flex items-center justify-center gap-2 rounded-2xl min-h-[44px] px-5 text-[13px] font-medium text-gray-600 disabled:opacity-50 transition-colors hover:bg-white"
          >
            <RefreshCw size={16} className={checking ? 'animate-spin' : ''} />
            重新检测权限
          </button>
        </div>
      </motion.div>
    </div>
  );
}
