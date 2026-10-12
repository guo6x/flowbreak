import { useEffect, useState } from 'react';
import { ArrowLeft, ShieldCheck } from 'lucide-react';
import { useNavigate } from 'react-router';
import { NativeFlow } from '../backend/nativeFlow';
import AccessibilityDisclosureDialog from '../components/AccessibilityDisclosureDialog';
import { useStore } from '../hooks/useStore';
import { useNativePermissions } from '../hooks/useNativePermissions';
import { translateLimit } from '../utils/protectionStatus';

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
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [accessibilityDisclosureOpen, setAccessibilityDisclosureOpen] = useState(false);
  const [accessibilityBusy, setAccessibilityBusy] = useState(false);

  const times = translateLimit(limit);

  useEffect(() => {
    if (!isNative) return;
    setLoading(true);
    NativeFlow.loadSettings().then(settings => {
      setLimit(settings.limitMinutes);
      setRestDuration(settings.restDuration);
      setEmergency(settings.allowEmergencyUnlock);
      setStrong(settings.strongBlockingEnabled);
      setError('');
    }).catch(() => {
      setError('阻断设置读取失败，请重新进入或重试。');
    }).finally(() => setLoading(false));
  }, [isNative]);

  const save = async () => {
    if (permissions.isDomestic && strong && !permissions.hasAccessibility) {
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
    } catch {
      setError('保存失败，原设置未更改，请重试。');
    } finally {
      setSaving(false);
    }
  };

  const openAccessibility = () => {
    if (!isNative || !permissions.isDomestic || permissions.unsupportedDevice === true) return;
    setError('');
    // All Accessibility entry points require an independent affirmative choice.
    setAccessibilityDisclosureOpen(true);
  };

  const confirmAccessibilityDisclosure = async () => {
    if (accessibilityBusy || !isNative || !permissions.isDomestic
        || permissions.unsupportedDevice === true) return;
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
    <div className="min-h-dvh px-5 pt-6 pb-10">
      <div className="flex items-center gap-3 mb-7">
        <button onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/profile'))} aria-label="返回" className="w-10 h-10 rounded-full card flex items-center justify-center"><ArrowLeft size={20} /></button>
        <div>
          <h1 className="text-[22px] font-bold">阻断设置</h1>
          <p className="text-[12px] text-gray-500">所有受限应用共享连续限额</p>
        </div>
      </div>

      <div className={`card p-5 space-y-6 ${loading ? 'opacity-60 pointer-events-none' : ''}`}>
        <label className="block">
          <span className="text-[14px] font-medium">共享连续限额</span>
          <span className="float-right text-primary font-bold">{limit} 分钟</span>
          <input type="range" min="5" max="90" step="5" value={limit} onChange={e => setLimit(Number(e.target.value))} className="w-full mt-3 accent-green-600" />
          <p className="text-[11px] text-gray-500 mt-1">80% 感知提醒，100% 认知提醒，120% 阻断。</p>
          <div className="text-[13px] text-gray-400 mt-1">
            {times.perceptionMinutes}分钟轻提醒 · {times.cognitionMinutes}分钟强提醒 · {times.blockedMinutes}分钟进入休息引导
          </div>
        </label>

        <label className="block">
          <span className="text-[14px] font-medium">解锁所需休息</span>
          <select value={restDuration} onChange={e => setRestDuration(Number(e.target.value))} className="w-full mt-2 bg-gray-100 rounded-xl px-3 py-3">
            {[120, 180, 300].map(value => <option key={value} value={value}>{value / 60} 分钟</option>)}
          </select>
          <p className="text-[11px] text-gray-500 mt-1">2 分钟起，太短难以真正从刷视频状态切换回来。</p>
        </label>

        <Toggle label="允许每日一次紧急使用" description="长按 10 秒后开放 5 分钟，并记录本地事件。" value={emergency} onChange={setEmergency} />

        {permissions.isDomestic && (
          <div className="pt-2 border-t border-gray-300/40">
            <Toggle label="无障碍强阻断" description={permissions.unsupportedDevice === true ? "当前设备不支持可靠的保护运行时，保留原有配置但不启用强阻断。" : "命中已阻断应用时返回桌面并显示阻断页，不读取页面内容。"} value={strong} onChange={setStrong} disabled={!isNative || permissions.unsupportedDevice === true} />
            <button onClick={openAccessibility} disabled={checking || !isNative || permissions.unsupportedDevice === true} className="mt-3 w-full py-3 rounded-xl bg-secondary/10 text-secondary text-[13px] font-medium disabled:opacity-60">
              {permissions.unsupportedDevice === true ? '当前设备不支持无障碍强阻断' : checking ? '正在检测...' : permissions.hasAccessibility ? '无障碍服务已授权' : '前往授权无障碍服务'}
            </button>
            <div className="mt-3 p-3 rounded-xl bg-gray-50 text-[11px] text-gray-500 leading-relaxed space-y-1">
              <p>· 仅监听「窗口切换」事件，用于判断前台应用是否为已阻断的目标。</p>
              <p>· 此服务不读取页面文字、不记录输入，也不通过无障碍服务上传页面内容。</p>
              <p>· 仅在「连续使用达到限额并进入阻断状态」时执行返回桌面动作。</p>
              <p>· 可随时在系统设置中关闭，关闭后仅回退到悬浮窗阻断。</p>
            </div>
          </div>
        )}
      </div>

      <div className="mt-5 p-4 rounded-2xl bg-primary/5 flex gap-3">
        <ShieldCheck className="text-primary shrink-0" size={20} />
        <p className="text-[12px] text-gray-600">完整休息后连续计时归零，并固定开放 10 分钟访问窗口。</p>
      </div>
      <AccessibilityDisclosureDialog
        open={accessibilityDisclosureOpen && isNative && permissions.isDomestic
          && permissions.unsupportedDevice !== true}
        busy={accessibilityBusy}
        onDecline={() => setAccessibilityDisclosureOpen(false)}
        onConfirm={() => void confirmAccessibilityDisclosure()}
        error={error}
      />
      {(error || permissionError) && <p className="text-[12px] text-error text-center mt-4">{error || permissionError}</p>}
      <button onClick={save} disabled={loading || saving} className="btn-primary w-full mt-7 disabled:opacity-50">
        {saving ? '正在保存...' : '保存设置'}
      </button>
    </div>
  );
}

function Toggle({ label, description, value, onChange, disabled = false }: {
  label: string;
  description: string;
  value: boolean;
  onChange: (value: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <button onClick={() => onChange(!value)} disabled={disabled} className="w-full flex items-center gap-4 text-left disabled:opacity-50" role="switch" aria-checked={value} aria-label={label}>
      <div className="flex-1">
        <p className="text-[14px] font-medium">{label}</p>
        <p className="text-[12px] text-gray-500 mt-1">{description}</p>
      </div>
      <div className={`w-12 h-7 rounded-full p-1 transition-colors ${value ? 'bg-primary' : 'bg-gray-300'}`}>
        <div className={`w-5 h-5 rounded-full bg-white transition-transform ${value ? 'translate-x-5' : ''}`} />
      </div>
    </button>
  );
}
