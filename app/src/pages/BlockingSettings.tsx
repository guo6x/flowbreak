import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, RefreshCw, ShieldCheck } from 'lucide-react';
import { useNavigate } from 'react-router';
import { NativeFlow } from '../backend/nativeFlow';
import AccessibilityDisclosureDialog from '../components/AccessibilityDisclosureDialog';
import { useStore } from '../hooks/useStore';
import { useNativePermissions } from '../hooks/useNativePermissions';
import { translateLimit } from '../utils/protectionStatus';
import { UNSUPPORTED_DEVICE_MESSAGE } from '../utils/backgroundStability';
import { getNativeErrorMessage } from '../utils/nativeError';

export default function BlockingSettings() {
  const navigate = useNavigate();
  const profile = useStore(s => s.profile);
  const updateProfile = useStore(s => s.updateProfile);
  const [limit, setLimit] = useState(profile.sessionLimit);
  const [restDuration, setRestDuration] = useState(profile.restDuration);
  const [emergency, setEmergency] = useState(profile.allowEmergencyUnlock);
  const [strong, setStrong] = useState(profile.strongBlockingEnabled);
  const { isNative, permissions, checking, error: permissionError, refresh } = useNativePermissions();
  const [loading, setLoading] = useState(isNative);
  const [loadFailed, setLoadFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [accessibilityDisclosureOpen, setAccessibilityDisclosureOpen] = useState(false);
  const [accessibilityBusy, setAccessibilityBusy] = useState(false);
  const unsupportedDevice = isNative && permissions.unsupportedDevice === true;

  const times = translateLimit(limit);

  const applyNativeSettings = useCallback((settings: Awaited<ReturnType<typeof NativeFlow.loadSettings>>) => {
    setLimit(settings.limitMinutes);
    setRestDuration(settings.restDuration);
    setEmergency(settings.allowEmergencyUnlock);
    setStrong(settings.strongBlockingEnabled);
    updateProfile({
      sessionLimit: settings.limitMinutes,
      restDuration: settings.restDuration,
      allowEmergencyUnlock: settings.allowEmergencyUnlock,
      strongBlockingEnabled: settings.strongBlockingEnabled,
      ...(Array.isArray(settings.targetApps) ? { targetApps: settings.targetApps } : {}),
    });
  }, [updateProfile]);

  const loadSettings = useCallback(async () => {
    if (!isNative) return;
    setLoading(true);
    setLoadFailed(false);
    setError('');
    try {
      const settings = await NativeFlow.loadSettings();
      applyNativeSettings(settings);
    } catch {
      setLoadFailed(true);
      setError('阻断设置读取失败。为避免覆盖未读取的设置，请先重新读取。');
    } finally {
      setLoading(false);
    }
  }, [applyNativeSettings, isNative]);

  const reconcileAfterSaveFailure = async (message: string) => {
    try {
      const settings = await NativeFlow.loadSettings();
      applyNativeSettings(settings);
      setLoadFailed(false);
      setError(`${message} 已同步设备中的最新设置。`);
    } catch {
      setLoadFailed(true);
      setError(`${message} 暂时无法读取设备中的最新设置，请重新读取后再试。`);
    }
  };

  useEffect(() => {
    void loadSettings();
  }, [loadSettings]);

  const save = async () => {
    if (loading || loadFailed || saving) return;
    if (permissions.isDomestic && !unsupportedDevice && strong && !permissions.hasAccessibility) {
      setError('开启强阻断前，请先授权无障碍服务。');
      return;
    }
    setSaving(true);
    setError('');
    try {
      if (isNative) {
        await NativeFlow.saveSettings({
          limitMinutes: limit,
          restDuration,
          allowEmergencyUnlock: emergency,
          strongBlockingEnabled: strong,
        });
      }
      updateProfile({
        sessionLimit: limit,
        restDuration,
        allowEmergencyUnlock: emergency,
        strongBlockingEnabled: strong,
      });
      navigate('/profile');
    } catch (cause) {
      const message = getNativeErrorMessage(cause, '保存设置失败，请重试。');
      if (isNative) await reconcileAfterSaveFailure(message);
      else setError(message);
    } finally {
      setSaving(false);
    }
  };

  const openAccessibility = () => {
    if (!isNative || !permissions.isDomestic || unsupportedDevice) return;
    setError('');
    setAccessibilityDisclosureOpen(true);
  };

  const confirmAccessibilityDisclosure = async () => {
    if (accessibilityBusy || !isNative || !permissions.isDomestic || unsupportedDevice) return;
    setError('');
    setAccessibilityBusy(true);
    try {
      await NativeFlow.requestAccessibilityPermission();
      setAccessibilityDisclosureOpen(false);
      window.setTimeout(refresh, 500);
    } catch {
      setError('无法打开无障碍设置，请重试或稍后从系统设置中手动进入。');
    } finally {
      setAccessibilityBusy(false);
    }
  };

  return (
    <div className="min-h-dvh px-5 pt-7 pb-10 sm:px-8">
      <div className="flex items-center gap-3 mb-7">
        <button onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/profile'))} aria-label="返回" className="w-[44px] h-[44px] shrink-0 rounded-full card flex items-center justify-center"><ArrowLeft size={20} /></button>
        <div>
          <h1 className="text-[24px] font-bold tracking-tight">阻断设置</h1>
          <p className="text-[13px] text-gray-500 mt-1">把提醒调到适合你的节奏</p>
        </div>
      </div>

      {unsupportedDevice && <div role="status" className="mb-5 p-4 rounded-2xl bg-warning/10 border border-warning/20 text-[13px] text-gray-700 leading-relaxed"><p className="font-semibold mb-1">{UNSUPPORTED_DEVICE_MESSAGE}</p>你仍可保存规则，已有配置与历史记录不会被删除。这里不会引导你继续开启权限。</div>}

      <fieldset disabled={loading || loadFailed || saving} aria-busy={loading} className={`space-y-4 border-0 p-0 min-w-0 ${loading || loadFailed ? 'opacity-60' : ''}`}>
        <section className="card p-5" aria-labelledby="blocking-limit-label">
          <div className="flex items-center justify-between gap-3">
            <label id="blocking-limit-label" htmlFor="blocking-limit" className="text-[15px] font-semibold">共享连续限额</label>
            <span className="text-primary text-[18px] font-bold">{limit} 分钟</span>
          </div>
          <p className="text-[13px] text-gray-500 mt-2">所有受限应用共享计时，切换应用不重置。</p>
          <input id="blocking-limit" aria-describedby="blocking-limit-help" aria-valuetext={`${limit} 分钟`} type="range" min="5" max="90" step="5" value={limit} onChange={e => setLimit(Number(e.target.value))} className="w-full h-[44px] mt-2 accent-primary cursor-pointer" />
          <div className="flex justify-between text-[13px] text-gray-500 mb-4"><span>5 分钟</span><span>90 分钟</span></div>
          <p id="blocking-limit-help" className="text-[13px] text-gray-600 mb-3">80% 感知提醒，100% 认知提醒，120% 阻断。</p>
          <div className="grid grid-cols-3 gap-2">
            <div role="group" aria-label="轻提醒时间" className="rounded-xl bg-primary/5 px-2 py-3 text-primary text-center">
              <p className="text-[18px] font-semibold whitespace-nowrap">{times.perceptionMinutes}<span className="text-[12px] font-normal ml-0.5">分钟</span></p>
              <p className="text-[13px] mt-1 whitespace-nowrap">轻提醒</p>
            </div>
            <div role="group" aria-label="强提醒时间" className="rounded-xl bg-secondary/5 px-2 py-3 text-secondary text-center">
              <p className="text-[18px] font-semibold whitespace-nowrap">{times.cognitionMinutes}<span className="text-[12px] font-normal ml-0.5">分钟</span></p>
              <p className="text-[13px] mt-1 whitespace-nowrap">强提醒</p>
            </div>
            <div role="group" aria-label="休息引导时间" className="rounded-xl bg-accent/10 px-2 py-3 text-gray-700 text-center">
              <p className="text-[18px] font-semibold whitespace-nowrap">{times.blockedMinutes}<span className="text-[12px] font-normal ml-0.5">分钟</span></p>
              <p className="text-[13px] mt-1 whitespace-nowrap">休息引导</p>
            </div>
          </div>
        </section>

        <section className="card p-5" aria-labelledby="blocking-rest-label">
          <label id="blocking-rest-label" htmlFor="blocking-rest" className="text-[15px] font-semibold">解锁所需休息</label>
          <select id="blocking-rest" value={restDuration} onChange={e => setRestDuration(Number(e.target.value))} aria-describedby="blocking-rest-help" className="w-full min-h-[48px] mt-3 border border-gray-200 bg-gray-50 rounded-xl px-3 py-3 focus:ring-2 focus:ring-primary/30">
            {[120, 180, 300].map(value => <option key={value} value={value}>{value / 60} 分钟</option>)}
          </select>
          <p id="blocking-rest-help" className="text-[13px] text-gray-500 mt-3 leading-relaxed">从 2 分钟开始，选一段愿意留给自己的时间。提前结束休息不会解锁。</p>
        </section>

        <section className="card p-5">
        <Toggle label="允许每日一次紧急使用" description="长按 10 秒后开放 5 分钟，并记录本地事件。" value={emergency} onChange={setEmergency} />
        </section>

        {permissions.isDomestic && !unsupportedDevice && (
          <section className="card p-5">
            <Toggle label="无障碍强阻断" description="命中已阻断应用时返回桌面并显示阻断页，不读取页面内容。" value={strong} onChange={setStrong} />
            <button onClick={openAccessibility} disabled={checking} className="mt-4 w-full min-h-[48px] py-3 rounded-xl bg-secondary/10 text-secondary text-[13px] font-medium disabled:opacity-60">
              {checking ? '正在检测...' : permissions.hasAccessibility ? '无障碍服务已授权' : '前往授权无障碍服务'}
            </button>
            <div className="mt-4 p-4 rounded-xl bg-gray-50 text-[13px] text-gray-600 leading-relaxed space-y-2">
              <p>· 仅监听「窗口切换」事件，用于判断前台应用是否为已阻断的目标。</p>
              <p>· 不读取页面文字、聊天或输入内容；窗口包名、必要类名及视频号判断值和最近检测时间只在设备内用于识别，不自动上传。</p>
              <p>· 仅在「连续使用达到限额并进入阻断状态」时执行返回桌面动作。</p>
              <p>· 可随时在系统设置中关闭，关闭后仅回退到悬浮窗阻断。</p>
            </div>
          </section>
        )}
      </fieldset>

      <div className="mt-5 p-4 rounded-2xl bg-primary/5 flex gap-3">
        <ShieldCheck className="text-primary shrink-0" size={20} />
        <p className="text-[13px] text-gray-600 leading-relaxed">完整休息后连续计时归零，并固定开放 10 分钟访问窗口。</p>
      </div>
      <AccessibilityDisclosureDialog
        open={accessibilityDisclosureOpen && isNative && permissions.isDomestic && !unsupportedDevice}
        busy={accessibilityBusy}
        onDecline={() => setAccessibilityDisclosureOpen(false)}
        onConfirm={() => void confirmAccessibilityDisclosure()}
        error={error}
      />
      {(permissionError || (error && !accessibilityDisclosureOpen)) && <p role="alert" className="text-[13px] text-error text-center mt-4">{permissionError || error}</p>}
      {loadFailed && <button onClick={loadSettings} className="flex items-center justify-center gap-2 w-full min-h-[48px] mt-4 rounded-2xl bg-secondary/10 text-secondary text-[14px] font-medium"><RefreshCw size={17} aria-hidden="true" />重新读取设置</button>}
      <button onClick={save} disabled={loading || loadFailed || saving} className="btn-primary w-full mt-7 disabled:opacity-50">
        {saving ? '正在保存...' : '保存设置'}
      </button>
    </div>
  );
}

function Toggle({ label, description, value, onChange }: {
  label: string;
  description: string;
  value: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <button onClick={() => onChange(!value)} className="w-full min-h-[52px] flex items-center gap-4 text-left" role="switch" aria-checked={value} aria-label={label}>
      <div className="flex-1">
        <p className="text-[15px] font-semibold">{label}</p>
        <p className="text-[13px] text-gray-500 mt-1 leading-relaxed">{description}</p>
      </div>
      <div aria-hidden="true" className={`w-12 h-7 shrink-0 rounded-full p-1 transition-colors ${value ? 'bg-primary' : 'bg-gray-300'}`}>
        <div className={`w-5 h-5 rounded-full bg-white transition-transform ${value ? 'translate-x-5' : ''}`} />
      </div>
    </button>
  );
}
