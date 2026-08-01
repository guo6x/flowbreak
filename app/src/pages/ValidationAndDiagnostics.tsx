import { useCallback, useEffect, useState } from 'react';
import { Capacitor } from '@capacitor/core';
import { ArrowLeft, CheckCircle2, Download, RefreshCw, ShieldCheck, TriangleAlert } from 'lucide-react';
import { useNavigate } from 'react-router';
import { NativeFlow, PermissionState } from '../backend/nativeFlow';
import { getReflectionCounts, getWeekStats } from '../backend/storage';

type ValidationSummary = {
  days: number;
  restCount: number;
  outcomeCount: number;
  successfulPullbackCount: number;
  postRestReturnCount: number;
  postRestTargetSeconds: number;
  helpedDays: number;
  neutralDays: number;
  notHelpedDays: number;
};

type Diagnostics = {
  versionName: string;
  versionCode: number;
  channel: string;
  packageName: string;
  databaseVersion: number;
  serviceAlive: boolean;
  serviceHeartbeatAt: number;
  lastUsageEventAt: number;
  state: string;
  sessionSeconds: number;
  graceUntil: number;
  monitoringEnabled: boolean;
  targetCount: number;
  eventCount: number;
  usageRowCount: number;
  latestEventAt: number;
  permissions: PermissionState;
};

const emptyPermissions: PermissionState = {
  hasUsageStats: false,
  hasOverlay: false,
  isIgnoringBattery: false,
  hasNotification: false,
  hasAccessibility: false,
  isDomestic: false,
  channel: 'base',
};

function formatSeconds(seconds: number) {
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  return `${minutes} 分钟`;
}

function formatTime(timestamp: number) {
  return timestamp > 0 ? new Date(timestamp).toLocaleString('zh-CN') : '暂无记录';
}

export default function ValidationAndDiagnostics() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState<ValidationSummary | null>(null);
  const [diagnostics, setDiagnostics] = useState<Diagnostics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [exporting, setExporting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      if (Capacitor.isNativePlatform()) {
        const [validation, nativeDiagnostics] = await Promise.all([
          NativeFlow.getValidationSummary({ days: 7 }),
          NativeFlow.getDiagnostics(),
        ]);
        setSummary(validation);
        setDiagnostics(nativeDiagnostics);
      } else {
        const week = getWeekStats();
        const reflections = getReflectionCounts(7);
        setSummary({
          days: 7,
          restCount: week.reduce((sum, day) => sum + day.restCount, 0),
          outcomeCount: 0,
          successfulPullbackCount: 0,
          postRestReturnCount: 0,
          postRestTargetSeconds: 0,
          ...reflections,
        });
        setDiagnostics({
          versionName: 'Web 预览', versionCode: 0, channel: 'web', packageName: '',
          databaseVersion: 0, serviceAlive: false, serviceHeartbeatAt: 0,
          lastUsageEventAt: 0, state: 'IDLE', sessionSeconds: 0, graceUntil: 0,
          monitoringEnabled: true, targetCount: 0, eventCount: 0, usageRowCount: 0,
          latestEventAt: 0, permissions: emptyPermissions,
        });
      }
    } catch {
      setError('暂时无法读取效果或诊断数据，请刷新重试。');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const exportDiagnostics = async () => {
    setExporting(true);
    setError('');
    try {
      if (Capacitor.isNativePlatform()) {
        await NativeFlow.shareDiagnostics();
      } else {
        const content = JSON.stringify({ formatVersion: 1, webPreview: true, diagnostics }, null, 2);
        const url = URL.createObjectURL(new Blob([content], { type: 'application/json' }));
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = 'flowbreak-diagnostics-web.json';
        anchor.click();
        URL.revokeObjectURL(url);
      }
    } catch {
      setError('诊断导出失败，请稍后重试。');
    } finally {
      setExporting(false);
    }
  };

  const successRate = summary && summary.outcomeCount > 0
    ? Math.round(summary.successfulPullbackCount * 100 / summary.outcomeCount)
    : null;
  const permissionReady = !!diagnostics
    && diagnostics.permissions.hasUsageStats
    && diagnostics.permissions.hasOverlay;

  return (
    <div className="flex flex-col pb-24 px-5 pt-6 no-scrollbar overflow-y-auto min-h-dvh" data-testid="validation-page">
      <div className="flex items-center gap-3 mb-6">
        <button onClick={() => navigate('/profile')} className="w-9 h-9 rounded-full bg-white shadow-sm flex items-center justify-center">
          <ArrowLeft size={19} />
        </button>
        <div className="flex-1">
          <h1 className="text-[22px] font-bold text-gray-900">效果验证与运行诊断</h1>
          <p className="text-[11px] text-gray-500">只看它有没有真的把你拉回来</p>
        </div>
        <button data-testid="validation-refresh" onClick={() => void load()} className="w-9 h-9 rounded-full bg-white shadow-sm flex items-center justify-center">
          <RefreshCw size={17} className={loading ? 'animate-spin' : ''} />
        </button>
      </div>

      {error && <div className="mb-4 rounded-xl border border-error/20 bg-error/5 p-3 text-[12px] text-error">{error}</div>}

      <section className="card-lg p-5 mb-5 bg-gradient-to-br from-primary to-primary-dark text-white">
        <p className="text-[12px] text-white/70">近 7 天成功拉回率</p>
        <p className="text-[38px] font-light mt-1" data-testid="pullback-rate">{loading ? '--' : successRate === null ? '待积累' : `${successRate}%`}</p>
        <p className="text-[11px] text-white/75 mt-2 leading-relaxed">
          成功拉回：休息后 10 分钟内没再打开目标应用；或回刷后主动离开全部目标应用满 30 秒。
        </p>
      </section>

      <div className="grid grid-cols-2 gap-3 mb-5">
        {[
          ['完成休息', summary?.restCount ?? 0, '次'],
          ['已判定窗口', summary?.outcomeCount ?? 0, '次'],
          ['休息后回刷', summary?.postRestReturnCount ?? 0, '次'],
          ['回刷时长', formatSeconds(summary?.postRestTargetSeconds ?? 0), ''],
        ].map(([label, value, suffix]) => (
          <div key={String(label)} className="card p-4">
            <p className="text-[11px] text-gray-500">{label}</p>
            <p className="text-[22px] font-bold mt-1">{loading ? '--' : value}<span className="text-[11px] font-normal text-gray-400 ml-1">{suffix}</span></p>
          </div>
        ))}
      </div>

      <div className="card p-4 mb-5">
        <h2 className="text-[14px] font-bold text-gray-900">你的主观感受</h2>
        <p className="text-[11px] text-gray-500 mt-1">近 7 天已反馈的天数，不把“没反馈”算成没帮助。</p>
        <div className="flex gap-2 mt-3 text-[12px]">
          <span className="rounded-full bg-primary/10 text-primary px-3 py-1.5">有帮助 {summary?.helpedDays ?? 0} 天</span>
          <span className="rounded-full bg-gray-100 text-gray-600 px-3 py-1.5">一般 {summary?.neutralDays ?? 0} 天</span>
          <span className="rounded-full bg-error/10 text-error px-3 py-1.5">没帮助 {summary?.notHelpedDays ?? 0} 天</span>
        </div>
      </div>

      <div className="flex items-center justify-between mb-2 px-1">
        <h2 className="text-[15px] font-bold text-gray-900">运行诊断</h2>
        <span className="text-[11px] text-gray-400">不包含所选 App 名称</span>
      </div>
      <div className="card overflow-hidden mb-4">
        {[
          ['保护服务', diagnostics?.serviceAlive, diagnostics?.serviceAlive ? '运行正常' : '未检测到心跳'],
          ['关键权限', permissionReady, permissionReady ? '已具备' : '需要检查'],
          ['本地数据库', diagnostics?.databaseVersion === 3, `Room v${diagnostics?.databaseVersion ?? '--'}`],
          ['受限应用', (diagnostics?.targetCount ?? 0) > 0, `${diagnostics?.targetCount ?? 0} 个`],
        ].map(([label, good, detail]) => (
          <div key={String(label)} className="flex items-center gap-3 px-4 py-3 border-b border-gray-200/50 last:border-0">
            {good ? <CheckCircle2 size={17} className="text-primary" /> : <TriangleAlert size={17} className="text-accent" />}
            <span className="text-[13px] text-gray-900 flex-1">{label}</span>
            <span className="text-[11px] text-gray-500">{detail}</span>
          </div>
        ))}
      </div>

      <div className="card p-4 mb-4 text-[11px] text-gray-500 leading-relaxed">
        <div className="flex items-center gap-2 text-gray-800 font-medium mb-2"><ShieldCheck size={16} className="text-primary" />脱敏诊断内容</div>
        版本、渠道、状态机状态、服务心跳、权限结果和本地记录数量。不会包含姓名、受限 App 包名、具体使用明细或活动内容。
        <p className="mt-2">最近心跳：{formatTime(diagnostics?.serviceHeartbeatAt ?? 0)}</p>
      </div>

      <button
        data-testid="export-diagnostics"
        disabled={exporting || loading}
        onClick={() => void exportDiagnostics()}
        className="w-full rounded-2xl bg-primary text-white py-3.5 text-[14px] font-medium flex items-center justify-center gap-2 disabled:opacity-50"
      >
        <Download size={17} />{exporting ? '正在导出...' : '导出脱敏诊断'}
      </button>
    </div>
  );
}
