import { useCallback, useEffect, useRef, useState } from 'react';
import { Capacitor } from '@capacitor/core';
import { ArrowLeft, ArrowUpRight, Check, ChevronDown, Download, FileWarning, HardDrive, LockKeyhole, RefreshCw, ShieldCheck, Trash2 } from 'lucide-react';
import { useNavigate } from 'react-router';
import { NativeFlow } from '../backend/nativeFlow';
import * as storage from '../backend/storage';

type CrashLog = { filename: string; timestamp: number; content: string };
type BuildInfo = Awaited<ReturnType<typeof NativeFlow.getBuildInfo>>;

const privacyPromises = [
  { icon: HardDrive, title: '只保存在这台设备', detail: '应用选择、使用时长、休息和进度均保存在本机，不上传使用明细。' },
  { icon: LockKeyhole, title: '不用账号，也没有广告', detail: '无需注册，不提供云同步，不集成第三方统计或广告 SDK。' },
  { icon: ShieldCheck, title: '权限有明确的边界', detail: '使用情况访问用于识别所选应用与计时。国内版无障碍服务不读取、记录或上传页面内容。' },
];

export default function PrivacyAndData() {
  const navigate = useNavigate();
  const isNative = Capacitor.isNativePlatform();
  const [build, setBuild] = useState<BuildInfo | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [busyAction, setBusyAction] = useState<'export' | 'clear' | ''>('');
  const actionInFlight = useRef(false);
  const clearTriggerRef = useRef<HTMLButtonElement>(null);
  const keepDataRef = useRef<HTMLButtonElement>(null);
  const confirmationWasOpen = useRef(false);
  const [error, setError] = useState('');
  const [crashLogs, setCrashLogs] = useState<CrashLog[]>([]);
  const [crashStatus, setCrashStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [expandedCrash, setExpandedCrash] = useState<string | null>(null);
  const [crashBusy, setCrashBusy] = useState(false);

  const refreshCrashLogs = useCallback(async () => {
    if (!isNative) return;
    setCrashStatus('loading');
    try {
      const result = await NativeFlow.getCrashLogs();
      setCrashLogs(result.logs);
      setCrashStatus('ready');
    } catch {
      setCrashStatus('error');
    }
  }, [isNative]);

  useEffect(() => {
    if (!isNative) return;
    let active = true;
    NativeFlow.getBuildInfo().then(info => { if (active) setBuild(info); }).catch(() => {});
    void refreshCrashLogs();
    return () => { active = false; };
  }, [isNative, refreshCrashLogs]);

  useEffect(() => {
    // The inline confirmation replaces its trigger. Move focus to the safe
    // choice after that render, and restore it when cancellation or a failed
    // clear brings the trigger back. Do not steal focus on initial page load.
    if (confirming) keepDataRef.current?.focus();
    else if (confirmationWasOpen.current) clearTriggerRef.current?.focus();
    confirmationWasOpen.current = confirming;
  }, [confirming]);

  const clearCrashLogs = async () => {
    if (crashBusy) return;
    setCrashBusy(true);
    setError('');
    try {
      await NativeFlow.clearCrashLogs();
      setCrashLogs([]);
      setExpandedCrash(null);
      setCrashStatus('ready');
    } catch {
      setError('清除崩溃日志失败，请重试。');
    } finally {
      setCrashBusy(false);
    }
  };

  const exportData = async () => {
    if (actionInFlight.current) return;
    actionInFlight.current = true;
    setBusyAction('export');
    setError('');
    try {
      if (isNative) {
        await NativeFlow.shareLocalData({ uiData: storage.exportLegacyPayload() });
      } else {
        const json = JSON.stringify(storage.exportLegacyPayload(), null, 2);
        const url = URL.createObjectURL(new Blob([json], { type: 'application/json' }));
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `flowbreak-export-${new Date().toISOString().slice(0, 10)}.json`;
        anchor.click();
        URL.revokeObjectURL(url);
      }
    } catch {
      setError('数据导出失败，请重试。');
    } finally {
      actionInFlight.current = false;
      setBusyAction('');
    }
  };

  const clearData = async () => {
    if (actionInFlight.current) return;
    if (!confirming) {
      setConfirming(true);
      setError('');
      return;
    }
    actionInFlight.current = true;
    setBusyAction('clear');
    setError('');
    try {
      if (isNative) await NativeFlow.clearLocalData();
      storage.clearAllLocalData();
      window.location.replace('/onboarding');
    } catch {
      setError('本地数据清除失败，界面缓存未清除。请稍后重试。');
      setConfirming(false);
      actionInFlight.current = false;
      setBusyAction('');
    }
  };

  return (
    <div className="min-h-dvh px-5 pt-6 pb-12 sm:px-8" data-testid="privacy-page">
      <header className="flex items-center gap-3 mb-7">
        <button onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/profile'))} aria-label="返回" className="w-11 h-11 shrink-0 rounded-full card flex items-center justify-center text-gray-700"><ArrowLeft size={20} /></button>
        <div>
          <p className="text-[11px] tracking-[0.16em] text-primary font-semibold mb-1">YOUR DATA, YOUR CHOICE</p>
          <h1 className="text-[23px] font-bold tracking-tight">隐私与本地数据</h1>
        </div>
      </header>

      <section className="rounded-[26px] bg-primary-dark p-6 mb-6 text-white relative overflow-hidden">
        <div className="absolute -right-8 -top-8 w-40 h-40 rounded-full border-[24px] border-white/5 pointer-events-none" aria-hidden="true" />
        <div className="w-11 h-11 rounded-2xl bg-white/15 flex items-center justify-center mb-5"><ShieldCheck size={23} aria-hidden="true" /></div>
        <h2 className="text-[24px] font-semibold tracking-tight mb-2">生活是你的，数据也是。</h2>
        <p className="text-[14px] text-white/85 leading-relaxed max-w-md">FlowBreak 帮你照顾使用节奏，不追踪你的生活。你可以随时导出或清除自己的数据。</p>
      </section>

      <section aria-labelledby="privacy-promises" className="card p-5 mb-6">
        <h2 id="privacy-promises" className="text-[16px] font-bold mb-5">我们如何保护你的隐私</h2>
        <div className="space-y-5">
          {privacyPromises.map(({ icon: Icon, title, detail }) => (
            <div key={title} className="flex gap-3.5">
              <span className="w-10 h-10 rounded-xl bg-primary/8 text-primary flex items-center justify-center shrink-0"><Icon size={19} aria-hidden="true" /></span>
              <div className="min-w-0"><h3 className="text-[14px] font-semibold mb-1">{title}</h3><p className="text-[13px] text-gray-600 leading-relaxed">{detail}</p></div>
            </div>
          ))}
        </div>
      </section>

      <section aria-labelledby="local-data" className="card p-5 mb-6">
        <h2 id="local-data" className="text-[16px] font-bold">数据由你掌握</h2>
        <p className="text-[13px] text-gray-600 mt-1 mb-3">导出文件可能包含昵称、应用选择与使用明细，请妥善保管。</p>
        <button onClick={() => void exportData()} disabled={busyAction !== ''} className="w-full flex items-center gap-3 py-4 text-left border-b border-gray-200 disabled:opacity-50">
          <span className="w-10 h-10 rounded-xl bg-secondary/8 text-secondary flex items-center justify-center shrink-0"><Download size={19} /></span>
          <span className="flex-1"><span className="block text-[14px] font-semibold">{busyAction === 'export' ? '正在准备导出...' : '导出 JSON 数据'}</span><span className="block text-[12px] text-gray-600 mt-0.5">将本机记录保存为可读文件</span></span>
          <ArrowUpRight size={17} className="text-gray-500" aria-hidden="true" />
        </button>
        {!confirming ? (
          <button ref={clearTriggerRef} onClick={() => void clearData()} disabled={busyAction !== ''} className="w-full flex items-center gap-3 pt-4 pb-1 text-left disabled:opacity-50">
            <span className="w-10 h-10 rounded-xl bg-error/8 text-error flex items-center justify-center shrink-0"><Trash2 size={19} /></span>
            <span><span className="block text-[14px] font-semibold text-error">清除全部本地数据</span><span className="block text-[12px] text-gray-600 mt-0.5">停止保护并重新开始设置</span></span>
          </button>
        ) : (
          <div className="mt-4 rounded-2xl border border-error/20 bg-error/5 p-4" role="group" aria-labelledby="clear-data-title" aria-describedby="clear-data-description">
            <h3 id="clear-data-title" className="text-[14px] font-semibold text-error">确定要清除全部数据吗？</h3>
            <p id="clear-data-description" className="mt-2 text-[13px] text-gray-700 leading-relaxed">保护会停止，设置、使用记录、休息与进度将被永久删除，无法撤销。建议先导出需要保留的数据。</p>
            <div className="flex flex-wrap gap-2 mt-4">
              <button ref={keepDataRef} onClick={() => setConfirming(false)} disabled={busyAction !== ''} className="flex-1 min-h-11 rounded-xl border border-gray-300 px-3 text-[13px] font-semibold bg-white disabled:opacity-50">保留数据</button>
              <button onClick={() => void clearData()} disabled={busyAction !== ''} className="flex-1 min-h-11 rounded-xl bg-error text-white px-3 text-[13px] font-semibold disabled:opacity-50">{busyAction === 'clear' ? '正在清除...' : '永久清除并重新设置'}</button>
            </div>
          </div>
        )}
        <p className="text-[12px] text-gray-600 mt-4 leading-relaxed">卸载应用也会删除 FlowBreak 在本机保存的数据。</p>
      </section>
      {error ? <p role="alert" className="rounded-xl border border-error/20 bg-error/5 p-4 text-[13px] text-error mb-6">{error}</p> : null}

      {isNative ? (
        <section className="card p-5 mb-6" aria-labelledby="crash-log-title">
          <div className="flex items-center justify-between gap-3 mb-3">
            <h2 id="crash-log-title" className="text-[16px] font-bold">本地崩溃日志</h2>
            {crashStatus === 'ready' && crashLogs.length > 0 ? <button onClick={() => void clearCrashLogs()} disabled={crashBusy} className="min-h-11 px-2 text-[13px] text-error disabled:opacity-50">{crashBusy ? '清除中...' : '清除全部'}</button> : null}
          </div>
          {crashStatus === 'loading' ? <p role="status" className="text-[13px] text-gray-600">正在读取本机日志...</p> : crashStatus === 'error' ? (
            <div className="rounded-xl bg-gray-50 p-4"><p role="status" className="text-[13px] text-gray-700">暂时无法读取崩溃日志，不能据此判断运行状态。</p><button onClick={() => void refreshCrashLogs()} className="mt-2 min-h-11 text-primary font-semibold text-[13px] flex items-center gap-2"><RefreshCw size={15} />重新读取</button></div>
          ) : crashLogs.length === 0 ? (
            <div className="flex items-center gap-2 text-[13px] text-gray-600"><Check size={17} className="text-primary shrink-0" /><span>本机暂无已保存的崩溃记录。</span></div>
          ) : (
            <div className="space-y-2">{crashLogs.map((log, index) => (
              <div key={log.filename} className="border border-gray-200 rounded-xl overflow-hidden">
                <button onClick={() => setExpandedCrash(expandedCrash === log.filename ? null : log.filename)} aria-expanded={expandedCrash === log.filename} aria-controls={`crash-log-${index}`} className="w-full min-h-12 flex items-center gap-2 px-3 py-3 text-left bg-gray-50">
                  <FileWarning size={16} className="text-error shrink-0" /><span className="text-[12px] font-medium flex-1 truncate">{log.filename}</span><ChevronDown size={16} className={`shrink-0 transition-transform ${expandedCrash === log.filename ? 'rotate-180' : ''}`} />
                </button>
                {expandedCrash === log.filename ? <pre id={`crash-log-${index}`} tabIndex={0} className="text-[12px] text-gray-700 p-3 overflow-auto max-h-56 bg-gray-50 leading-relaxed">{log.content}</pre> : null}
              </div>
            ))}</div>
          )}
        </section>
      ) : null}

      <section className="card p-5" aria-labelledby="about-flowbreak">
        <h2 id="about-flowbreak" className="text-[16px] font-bold mb-3">关于 FlowBreak</h2>
        <div className="rounded-xl bg-gray-50 p-3">
          <p className="text-[13px] text-gray-700">{isNative ? build ? `版本 ${build.versionName} · 渠道 ${build.channel}` : '版本信息暂不可用' : 'Web 界面预览 · Android 原生保护仅在设备上提供'}</p>
          {build ? <p className="text-[12px] text-gray-600 mt-1 break-all">{build.packageName}</p> : null}
        </div>
        <h3 className="text-[14px] font-semibold mt-5 mb-2">感谢开源社区</h3>
        <p className="text-[13px] text-gray-600 leading-relaxed">Capacitor、React、Room、AndroidX、Framer Motion、Lucide、Recharts 和 Zustand 按各自许可使用。完整清单随项目 THIRD_PARTY_NOTICES.md 提供。</p>
      </section>
    </div>
  );
}
