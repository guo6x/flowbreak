import { useEffect, useState } from 'react';
import { Capacitor } from '@capacitor/core';
import { ArrowLeft, Download, Trash2, FileWarning, ChevronDown, ChevronUp } from 'lucide-react';
import { useNavigate } from 'react-router';
import { NativeFlow } from '../backend/nativeFlow';
import * as storage from '../backend/storage';

type CrashLog = { filename: string; timestamp: number; content: string };

export default function PrivacyAndData() {
  const navigate = useNavigate();
  const [build, setBuild] = useState({ versionName: '1.1.0', channel: 'Web', packageName: 'com.flowbreak.app' });
  const [confirming, setConfirming] = useState(false);
  const [busyAction, setBusyAction] = useState<'export' | 'clear' | ''>('');
  const [error, setError] = useState('');
  const [crashLogs, setCrashLogs] = useState<CrashLog[]>([]);
  const [expandedCrash, setExpandedCrash] = useState<string | null>(null);
  const [crashBusy, setCrashBusy] = useState(false);

  useEffect(() => {
    if (Capacitor.isNativePlatform()) {
      NativeFlow.getBuildInfo().then(info => setBuild(info)).catch(() => {});
      refreshCrashLogs();
    }
  }, []);

  const refreshCrashLogs = () => {
    if (!Capacitor.isNativePlatform()) return;
    NativeFlow.getCrashLogs().then(result => setCrashLogs(result.logs)).catch(() => {});
  };

  const clearCrashLogs = async () => {
    if (crashBusy) return;
    setCrashBusy(true);
    try {
      await NativeFlow.clearCrashLogs();
      setCrashLogs([]);
      setExpandedCrash(null);
    } catch {
      setError('清除崩溃日志失败，请重试。');
    } finally {
      setCrashBusy(false);
    }
  };

  const exportData = async () => {
    setBusyAction('export');
    setError('');
    try {
      if (Capacitor.isNativePlatform()) {
        await NativeFlow.shareLocalData({ uiData: storage.exportLegacyPayload() });
        return;
      }
      const json = JSON.stringify(storage.exportLegacyPayload(), null, 2);
      const blob = new Blob([json], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `flowbreak-export-${new Date().toISOString().slice(0, 10)}.json`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch {
      setError('数据导出失败，请重试。');
    } finally {
      setBusyAction('');
    }
  };

  const clearData = async () => {
    if (!confirming) {
      setConfirming(true);
      return;
    }
    setBusyAction('clear');
    setError('');
    try {
      if (Capacitor.isNativePlatform()) await NativeFlow.clearLocalData();
      storage.clearAllLocalData();
      window.location.replace('/onboarding');
    } catch {
      setError('本地数据清除失败，数据未从界面移除，请重试。');
      setConfirming(false);
      setBusyAction('');
    }
  };

  return (
    <div className="min-h-dvh px-5 pt-6 pb-10">
      <div className="flex items-center gap-3 mb-7">
        <button onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/profile'))} aria-label="返回" className="w-10 h-10 rounded-full card flex items-center justify-center"><ArrowLeft size={20} /></button>
        <h1 className="text-[22px] font-bold">隐私、数据与关于</h1>
      </div>

      <section className="card p-5 mb-4">
        <h2 className="text-[16px] font-bold mb-3">隐私承诺</h2>
        <div className="space-y-2 text-[13px] text-gray-600 leading-relaxed">
          <p>FlowBreak 无账号、无广告、无云同步，不集成第三方统计或广告 SDK。</p>
          <p>应用列表、使用时间、阻断、休息、积分和成就仅保存在本机。使用情况访问只用于识别你选择的应用及计算使用时长。</p>
          <p>国内版无障碍服务只监听前台窗口所属应用，在达到阻断条件时返回桌面；不会读取、记录或上传页面内容。</p>
          <p>卸载应用或使用下方“清除本地数据”会删除 FlowBreak 保存的数据。</p>
        </div>
      </section>

      {Capacitor.isNativePlatform() && (
        <section className="card p-5 mb-4">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-[16px] font-bold">崩溃日志</h2>
            {crashLogs.length > 0 && (
              <button
                onClick={clearCrashLogs}
                disabled={crashBusy}
                className="text-[12px] text-error disabled:opacity-50"
              >
                {crashBusy ? '清除中...' : '清除全部'}
              </button>
            )}
          </div>
          {crashLogs.length === 0 ? (
            <div className="flex items-center gap-2 text-[13px] text-gray-500">
              <FileWarning size={16} className="text-primary" />
              <span>暂无崩溃记录，应用运行正常。</span>
            </div>
          ) : (
            <div className="space-y-2">
              {crashLogs.map(log => (
                <div key={log.filename} className="border border-gray-200 rounded-xl overflow-hidden">
                  <button
                    onClick={() => setExpandedCrash(expandedCrash === log.filename ? null : log.filename)}
                    className="w-full flex items-center gap-2 px-3 py-2.5 text-left bg-gray-50"
                  >
                    <FileWarning size={14} className="text-error shrink-0" />
                    <span className="text-[12px] font-medium flex-1 truncate">{log.filename}</span>
                    {expandedCrash === log.filename ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                  </button>
                  {expandedCrash === log.filename && (
                    <pre className="text-[10px] text-gray-700 p-3 overflow-x-auto max-h-48 bg-gray-900/5">
                      {log.content}
                    </pre>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      <section className="card p-5 mb-4">
        <h2 className="text-[16px] font-bold mb-3">本地数据</h2>
        <button onClick={exportData} disabled={busyAction !== ''} className="w-full flex items-center gap-3 py-3 text-left border-b border-gray-300/30 disabled:opacity-50">
          <Download size={18} className="text-secondary" />
          <span className="text-[14px]">{busyAction === 'export' ? '正在准备导出...' : '导出 JSON 数据'}</span>
        </button>
        <button onClick={clearData} disabled={busyAction !== ''} className="w-full flex items-center gap-3 py-3 text-left text-error disabled:opacity-50">
          <Trash2 size={18} />
          <span className="text-[14px]">{busyAction === 'clear' ? '正在清除...' : confirming ? '再次点击确认永久清除' : '清除全部本地数据'}</span>
        </button>
      </section>
      {error && <p className="text-[12px] text-error text-center mb-4">{error}</p>}

      <section className="card p-5">
        <h2 className="text-[16px] font-bold mb-3">关于 FlowBreak</h2>
        <p className="text-[13px] text-gray-600">版本 {build.versionName} · 渠道 {build.channel}</p>
        <p className="text-[11px] text-gray-400 mt-1">{build.packageName}</p>
        <h3 className="text-[14px] font-bold mt-5 mb-2">开源许可</h3>
        <p className="text-[12px] text-gray-600 leading-relaxed">
          Capacitor、React、Room、AndroidX、Framer Motion、Lucide、Recharts 和 Zustand
          按各自许可使用。完整清单随项目 THIRD_PARTY_NOTICES.md 提供。
        </p>
      </section>
    </div>
  );
}
