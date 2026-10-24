// Inline exit-confirmation block shown when the user taps "提前结束".
// Preserves the exact copy and button behaviour from the original page.

import { formatTime } from './colorUtils';

export interface RestExitDialogProps {
  timeLeft: number;
  closing: boolean;
  exitContext?: 'active' | 'unconfirmed' | 'recovering';
  onCancel: () => void;
  onConfirm: () => void;
}

export default function RestExitDialog({ timeLeft, closing, exitContext = 'active', onCancel, onConfirm }: RestExitDialogProps) {
  const activeRest = exitContext === 'active';
  const unconfirmedStart = exitContext === 'unconfirmed';
  const title = activeRest
    ? '提前结束不会解锁'
    : unconfirmedStart ? '设备尚未确认休息' : '计时正在恢复核验';
  const description = activeRest
    ? `剩余 ${formatTime(timeLeft)}，目标应用仍将被阻断。坚持完成才能获得 10 分钟访问窗口。`
    : unconfirmedStart
      ? '离开后会安全取消本次启动请求；确认前不会开始计时，也不会开放解锁窗口。'
      : '设备状态暂时无法确认。完成与解锁入口会保持关闭；你可以继续等待，或结束本次休息返回面板。';
  return (
    <div className="w-full space-y-3" role="group" aria-labelledby="rest-exit-title">
      <div className="rounded-2xl border border-white/20 bg-white/10 px-4 py-3 text-center">
        <p id="rest-exit-title" className="text-[14px] font-semibold text-white">{title}</p>
        <p className="text-[12px] text-white/85 mt-2 leading-relaxed">{description}</p>
      </div>
      <div className="flex gap-3">
        <button
          onClick={onCancel}
          disabled={closing}
          className="flex-1 min-h-12 py-3 rounded-xl bg-white text-primary-dark text-[14px] font-semibold disabled:opacity-50"
        >
          {activeRest ? '继续休息' : '继续等待'}
        </button>
        <button
          onClick={onConfirm}
          disabled={closing}
          className="flex-1 min-h-12 py-3 rounded-xl border border-white/20 bg-white/5 text-white text-[14px] font-medium disabled:opacity-50"
        >
          {closing ? '正在退出...' : activeRest ? '确认提前结束' : unconfirmedStart ? '取消启动并返回' : '结束休息并返回'}
        </button>
      </div>
    </div>
  );
}
