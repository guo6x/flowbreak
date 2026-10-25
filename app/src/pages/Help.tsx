import { ArrowLeft, ArrowRight, Leaf, LockKeyhole, ShieldCheck, Smartphone } from 'lucide-react';
import { useNavigate } from 'react-router';
import { Capacitor } from '@capacitor/core';
import Brand from '../components/Brand';
import SupportContact from '../components/SupportContact';

const questions = [
  ['为什么切换到另一个应用，时间还在累计？', '所有受限应用共用一段连续使用时间。尚未进入阻断时，离开所有受限应用或熄屏超过 30 秒会重置连续会话；已经进入阻断后，单纯离开或锁屏不会解除阻断。'],
  ['到达限额就会马上阻断吗？', '不会。达到限额的 80% 先轻提醒，100% 再提醒，120% 才进入阻断。例如设置 25 分钟，对应 20、25、30 分钟。它衡量的是连续使用时间，不是生理疲劳。'],
  ['休息和紧急使用有什么区别？', '完整休息后会有 10 分钟访问窗口。若开启紧急使用，每天可长按 10 秒获得一次 5 分钟窗口。提前退出休息不会解锁；窗口结束后重新计算连续使用。'],
  ['为什么有时提醒会延迟？', 'Android 或厂商系统可能暂停后台执行。可检查必需权限、电池优化及自启动设置，但不能保证系统始终调度服务。诊断页可检查最近心跳；诊断正常也不是全天候保护保证。'],
  ['我的设备支持吗？', '当前已验证参考设备为 Xiaomi / Redmi。vivo / iQOO 在当前版本中不支持开启保护；其他厂商仍属未验证范围。国内版可选的无障碍强阻断及微信视频号识别均为实验性能力。'],
  ['数据会上传吗？可以换手机吗？', '使用记录与配置只保存在本机，不需要账号，没有广告或第三方统计。可以从“隐私与数据”导出备份；当前没有导入或云同步功能，卸载或清除数据可能导致记录丢失。'],
] as const;
const quickSteps = [{ icon: Smartphone, label: '选应用' }, { icon: ShieldCheck, label: '定限额' }, { icon: Leaf, label: '留白休息' }];

export default function Help() {
  const navigate = useNavigate();
  return (
    <div className="min-h-dvh px-5 pt-6 pb-10">
      <header className="flex items-center gap-3 mb-7">
        <button type="button" aria-label="返回我的" onClick={() => navigate('/profile')} className="w-11 h-11 rounded-2xl bg-white flex items-center justify-center border border-gray-200">
          <ArrowLeft size={20} aria-hidden="true" />
        </button>
        <div><h1 className="text-[23px] font-bold">使用指南</h1><p className="text-[12px] text-gray-500 mt-1">少一点刷屏，多一点自己的时间</p></div>
      </header>
      <section className="card-lg p-6 mb-6">
        <Brand />
        <h2 className="text-[23px] font-bold mt-7 mb-3">从一个小小的停顿开始</h2>
        <p className="text-[14px] text-gray-500 leading-7">不必一次改变所有习惯。先选几个最容易让你忘记时间的应用，给自己一个合理的连续使用限额。</p>
        <div className="grid grid-cols-3 gap-3 mt-6">
          {quickSteps.map(({ icon: Icon, label }) => <div key={label} className="flex flex-col items-center gap-2 rounded-2xl bg-primary/5 py-4"><Icon size={22} aria-hidden="true" className="text-primary" /><span className="text-[12px] text-gray-700">{label}</span></div>)}
        </div>
      </section>
      {!Capacitor.isNativePlatform() && <p role="note" className="rounded-2xl bg-secondary/8 border border-secondary/15 p-4 text-[13px] text-secondary leading-6 mb-6">你正在浏览网页预览。这里可以了解界面、调整本地演示设置；不能识别或阻断手机上其他应用。实际保护需 Android 应用及系统权限。</p>}
      <h2 className="text-[18px] font-bold mb-3">你可能想知道</h2>
      <div className="space-y-3 mb-6">
        {questions.map(([question, answer]) => (
          <details key={question} className="card group p-4">
            <summary className="cursor-pointer text-[14px] font-semibold leading-6 min-h-11 content-center pr-2">{question}</summary>
            <p className="text-[13px] text-gray-500 leading-7 pt-3 border-t border-gray-100 mt-2">{answer}</p>
          </details>
        ))}
      </div>
      <div className="card p-5 mb-4">
        <div className="flex items-center gap-2 mb-2"><LockKeyhole size={18} className="text-primary" aria-hidden="true" /><h2 className="text-[15px] font-semibold">从了解开始，而不是从自责开始</h2></div>
        <p className="text-[13px] text-gray-500 leading-6">FlowBreak 是数字习惯辅助工具，不做医疗诊断，也不承诺健康疗效。今天少刷一会儿、完成一次休息，都可以是自己的节奏。</p>
      </div>
      <SupportContact />
      <button type="button" onClick={() => navigate('/validation')} className="btn-secondary w-full flex items-center justify-between gap-3 text-[14px]">检查权限与运行状态<ArrowRight size={18} aria-hidden="true" /></button>
    </div>
  );
}
