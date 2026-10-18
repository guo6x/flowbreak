import { useCallback, useEffect, useRef, useState } from 'react';
import { Capacitor } from '@capacitor/core';
import { ArrowLeft, CheckCircle2, ChevronDown, Download, Heart, Info, RefreshCw, ShieldCheck, TriangleAlert } from 'lucide-react';
import { useNavigate } from 'react-router';
import { NativeFlow } from '../backend/nativeFlow';
import { getReflectionCounts, getWeekStats } from '../backend/storage';
import { UNSUPPORTED_DEVICE_DETAIL, UNSUPPORTED_DEVICE_MESSAGE } from '../utils/backgroundStability';

type ValidationSummary = Awaited<ReturnType<typeof NativeFlow.getValidationSummary>>;
type Diagnostics = Awaited<ReturnType<typeof NativeFlow.getDiagnostics>>;

function formatSeconds(seconds: number) {
  return seconds < 60 ? `${seconds} 秒` : `${Math.floor(seconds / 60)} 分钟`;
}

function formatTime(timestamp: number) {
  return timestamp > 0 ? new Date(timestamp).toLocaleString('zh-CN') : '暂无记录';
}

export default function ValidationAndDiagnostics() {
  const navigate = useNavigate();
  const isNative = Capacitor.isNativePlatform();
  const [summary, setSummary] = useState<ValidationSummary | null>(null);
  const [diagnostics, setDiagnostics] = useState<Diagnostics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [exporting, setExporting] = useState(false);
  const loadingRef = useRef(false);
  const exportingRef = useRef(false);
  const loadVersion = useRef(0);

  const load = useCallback(async () => {
    if (loadingRef.current) return;
    loadingRef.current = true;
    const version = ++loadVersion.current;
    setLoading(true);
    setError('');
    setSummary(null);
    setDiagnostics(null);
    try {
      if (isNative) {
        // These are independent: one failed read must not hide the other result.
        const [validation, runtime] = await Promise.allSettled([
          NativeFlow.getValidationSummary({ days: 7 }),
          NativeFlow.getDiagnostics(),
        ]);
        if (version !== loadVersion.current) return;
        if (validation.status === 'fulfilled') setSummary(validation.value);
        if (runtime.status === 'fulfilled') setDiagnostics(runtime.value);
        if (validation.status === 'rejected' || runtime.status === 'rejected') {
          setError('部分数据暂时无法读取，未读取的项目不会显示为正常或零记录。请刷新重试。');
        }
      } else {
        const week = getWeekStats();
        setSummary({
          days: 7,
          restCount: week.reduce((sum, day) => sum + day.restCount, 0),
          outcomeCount: 0,
          successfulPullbackCount: 0,
          postRestReturnCount: 0,
          postRestTargetSeconds: 0,
          ...getReflectionCounts(7),
        });
      }
    } catch {
      if (version === loadVersion.current) setError('暂时无法读取本地数据，请刷新重试。');
    } finally {
      if (version === loadVersion.current) {
        loadingRef.current = false;
        setLoading(false);
      }
    }
  }, [isNative]);

  useEffect(() => {
    void load();
    return () => { loadVersion.current += 1; loadingRef.current = false; };
  }, [load]);

  const exportDiagnostics = async () => {
    if (exportingRef.current || !diagnostics) return;
    exportingRef.current = true;
    setExporting(true);
    setError('');
    try {
      await NativeFlow.shareDiagnostics();
    } catch {
      setError('诊断导出失败，请稍后重试。');
    } finally {
      exportingRef.current = false;
      setExporting(false);
    }
  };

  const successRate = summary && summary.outcomeCount > 0
    ? Math.round(summary.successfulPullbackCount * 100 / summary.outcomeCount)
    : null;
  const permissionReady = !!diagnostics && diagnostics.permissions.hasUsageStats && diagnostics.permissions.hasOverlay;
  const monitorThreadHealthy = diagnostics?.runtimeTracking.monitorThreadAlive === true && diagnostics.runtimeTracking.monitorLooperIsMain === false;
  const unavailable = loading ? '读取中' : '暂不可用';
  const metricValue = (value: number | undefined, nativeOnly = false) => loading ? '—' : !summary || (nativeOnly && !isNative) ? '—' : value;
  const diagnosticRows = diagnostics ? [
    { label: '保护服务', good: diagnostics.serviceAlive, detail: diagnostics.serviceAlive ? '检测到服务心跳' : '未检测到心跳' },
    { label: '监控线程', good: monitorThreadHealthy, detail: monitorThreadHealthy ? '独立线程运行中' : '线程状态需检查' },
    { label: '保护能力', good: diagnostics.protectionRuntimeAvailable, detail: diagnostics.protectionRuntimeAvailable ? '设备支持运行' : '设备暂不支持' },
    { label: '监控配置', good: diagnostics.monitoringConfigured, detail: diagnostics.monitoringConfigured ? '已配置' : '未配置' },
    { label: '关键权限', good: permissionReady, detail: permissionReady ? '已具备' : '需要检查' },
    { label: '本地数据库', good: diagnostics.databaseVersion === 3, detail: `Room v${diagnostics.databaseVersion}` },
    { label: '受限应用', good: diagnostics.targetCount > 0, detail: `${diagnostics.targetCount} 个` },
  ] : [];

  return (
    <div className="flex flex-col pb-12 px-5 pt-6 min-h-dvh sm:px-8" data-testid="validation-page" aria-busy={loading}>
      <header className="flex items-center gap-3 mb-7">
        <button onClick={() => navigate('/profile')} aria-label="返回个人中心" className="w-11 h-11 shrink-0 rounded-full card flex items-center justify-center text-gray-700"><ArrowLeft size={20} /></button>
        <div className="flex-1 min-w-0"><p className="text-[11px] tracking-[0.14em] text-primary font-semibold mb-1">SMALL STEPS, REAL CHANGE</p><h1 className="text-[22px] font-bold tracking-tight">效果与运行诊断</h1></div>
        <button data-testid="validation-refresh" aria-label={loading ? '正在刷新诊断' : '刷新诊断'} disabled={loading || exporting} onClick={() => void load()} className="w-11 h-11 shrink-0 rounded-full card flex items-center justify-center text-gray-700 disabled:opacity-50"><RefreshCw size={18} className={loading ? 'animate-spin' : ''} /></button>
      </header>

      {error ? <div role="alert" className="mb-5 rounded-2xl border border-error/20 bg-error/5 p-4 text-[13px] text-error leading-relaxed">{error}</div> : null}
      {!isNative ? <div className="mb-5 rounded-2xl border border-secondary/15 bg-secondary/5 p-4 flex gap-3"><Info size={19} className="text-secondary shrink-0 mt-0.5" /><p className="text-[13px] text-gray-700 leading-relaxed">当前是 Web 界面预览。可查看本地休息与反馈；成功拉回、保护服务和权限诊断需要在 Android 设备上验证。</p></div> : null}

      <section className="rounded-[26px] p-6 mb-5 bg-primary-dark text-white relative overflow-hidden" aria-labelledby="pullback-heading">
        <div aria-hidden="true" className="absolute -right-12 -top-12 w-48 h-48 rounded-full border-[28px] border-white/5" />
        <p id="pullback-heading" className="text-[13px] text-white/85">近 7 天 · 成功拉回率</p>
        <p className="text-[46px] font-semibold tracking-tight mt-2 leading-tight" data-testid="pullback-rate">{loading ? '—' : !summary ? '暂不可用' : !isNative ? '设备端查看' : successRate === null ? '待积累' : `${successRate}%`}</p>
        <p className="text-[13px] text-white/85 mt-3 leading-relaxed">{!isNative ? '连接真实的休息记录，才能看见改变。' : summary && summary.outcomeCount === 0 ? '完成一次休息后，等待 10 分钟观察窗口结束，就会开始积累。' : '用休息后的实际选择，看见一点点改变。'}</p>
        <details className="mt-5 border-t border-white/20 pt-3">
          <summary className="min-h-9 text-[12px] text-white/85 cursor-pointer flex items-center justify-between gap-3">怎样算一次「成功拉回」<ChevronDown size={15} aria-hidden="true" /></summary>
          <p className="text-[12px] text-white/85 leading-relaxed mt-2">休息后 10 分钟内没再打开目标应用；或回刷后主动离开全部目标应用满 30 秒。这是短期行为记录，不代表治疗效果或长期健康改善。</p>
        </details>
      </section>

      <div className="grid grid-cols-2 gap-3 mb-5">
        {[
          { label: '完成休息', value: metricValue(summary?.restCount), suffix: '次' },
          { label: '已判定窗口', value: metricValue(summary?.outcomeCount, true), suffix: '次' },
          { label: '休息后回刷', value: metricValue(summary?.postRestReturnCount, true), suffix: '次' },
          { label: '回刷时长', value: !loading && summary && isNative ? formatSeconds(summary.postRestTargetSeconds) : '—', suffix: '' },
        ].map(({ label, value, suffix }) => <div key={label} className="card p-4"><p className="text-[12px] text-gray-600">{label}</p><p className="text-[25px] font-semibold tracking-tight mt-2">{value}<span className="text-[12px] font-normal text-gray-600 ml-1">{value === '—' ? '' : suffix}</span></p></div>)}
      </div>

      <section className="card p-5 mb-7" aria-labelledby="reflection-heading">
        <div className="flex items-center gap-2.5"><Heart size={18} className="text-primary" /><h2 id="reflection-heading" className="text-[15px] font-bold">你的感受，同样重要</h2></div>
        <p className="text-[13px] text-gray-600 mt-2 leading-relaxed">近 7 天已反馈的天数，未反馈不计为“没帮助”。</p>
        <div className="flex flex-wrap gap-2 mt-4 text-[12px]">
          <span className="rounded-full bg-primary/8 text-primary px-3 py-2">有帮助 {metricValue(summary?.helpedDays)} 天</span>
          <span className="rounded-full bg-gray-100 text-gray-700 px-3 py-2">一般 {metricValue(summary?.neutralDays)} 天</span>
          <span className="rounded-full bg-accent/10 text-gray-700 px-3 py-2">没帮助 {metricValue(summary?.notHelpedDays)} 天</span>
        </div>
      </section>

      <div className="mb-3 px-1"><h2 className="text-[17px] font-bold">运行诊断</h2><p className="text-[12px] text-gray-600 mt-1">仅展示本次读取的运行状态，不包含所选 App 名称</p></div>
      {diagnostics ? (
        <div className="card overflow-hidden mb-4">{diagnosticRows.map(({ label, good, detail }) => (
          <div key={label} className="flex items-center gap-3 px-4 py-3.5 border-b border-gray-200/60 last:border-0">
            {good ? <CheckCircle2 size={18} className="text-primary shrink-0" aria-hidden="true" /> : <TriangleAlert size={18} className="text-accent shrink-0" aria-hidden="true" />}
            <span className="text-[13px] font-medium flex-1">{label}</span><span className="text-[12px] text-gray-600 text-right">{detail}</span>
          </div>
        ))}</div>
      ) : <div className="card p-5 mb-4"><div className="w-11 h-11 rounded-2xl bg-secondary/8 text-secondary flex items-center justify-center mb-3"><ShieldCheck size={22} /></div><h3 className="text-[14px] font-semibold">{isNative ? `运行诊断${unavailable}` : '原生诊断仅在 Android 提供'}</h3><p className="text-[13px] text-gray-600 leading-relaxed mt-2">{isNative ? loading ? '正在读取保护服务、关键权限和本地数据库状态。' : '未取得设备状态。请刷新重试，不会将读取失败标记为保护正常。' : '浏览器不能检查 Android 服务、悬浮窗或使用情况权限，也不能验证保护能力。'}</p></div>}

      {diagnostics && !diagnostics.protectionRuntimeAvailable ? <div role="status" className="mb-4 rounded-2xl border border-error/20 bg-error/5 p-4 text-[13px] text-error"><p className="font-semibold">{UNSUPPORTED_DEVICE_MESSAGE}</p><p className="mt-2 leading-relaxed">{UNSUPPORTED_DEVICE_DETAIL}</p></div> : null}

      <section className="rounded-2xl bg-gray-100/80 p-4 mb-5" aria-labelledby="export-privacy">
        <h3 id="export-privacy" className="flex items-center gap-2 text-[13px] font-semibold mb-2"><ShieldCheck size={17} className="text-primary" />只分享排查所需的信息</h3>
        <p className="text-[12px] text-gray-600 leading-relaxed">脱敏诊断包含版本、渠道、状态机、服务心跳、权限结果和记录数量，不含姓名、受限 App 包名、具体使用明细或活动内容。</p>
        {diagnostics ? <p className="text-[12px] text-gray-600 mt-3 pt-3 border-t border-gray-300/60">最近心跳：{formatTime(diagnostics.serviceHeartbeatAt)}</p> : null}
      </section>
      <button data-testid="export-diagnostics" disabled={exporting || loading || !diagnostics} onClick={() => void exportDiagnostics()} className="btn-primary w-full flex items-center justify-center gap-2 disabled:opacity-50"><Download size={18} />{exporting ? '正在导出...' : '导出脱敏诊断'}</button>
      {!isNative ? <p className="text-[12px] text-gray-600 text-center mt-3">请在 Android 设备上读取并导出真实诊断。</p> : null}
    </div>
  );
}
