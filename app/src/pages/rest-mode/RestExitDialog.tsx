// Inline exit-confirmation block shown when the user taps "提前结束".
// Preserves the exact copy and button behaviour from the original page.

import { formatTime } from './colorUtils';

export interface RestExitDialogProps {
  timeLeft: number;
  closing: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export default function RestExitDialog({ timeLeft, closing, onCancel, onConfirm }: RestExitDialogProps) {
  return (
    <div className="w-full space-y-2">
      <div className="rounded-2xl bg-white/10 backdrop-blur-sm px-4 py-3 text-center">
        <p className="text-[14px] font-medium text-white">提前结束不会解锁</p>
        <p className="text-[12px] text-white/70 mt-1">剩余 {formatTime(timeLeft)}，目标应用仍将被阻断。坚持完成才能获得 10 分钟访问窗口。</p>
      </div>
      <div className="flex gap-3">
        <button
          onClick={onCancel}
          disabled={closing}
          className="flex-1 py-3 rounded-xl bg-white/10 text-white text-[14px] font-medium active:bg-white/20 transition-colors disabled:opacity-50"
        >
          继续休息
        </button>
        <button
          onClick={onConfirm}
          disabled={closing}
          className="flex-1 py-3 rounded-xl bg-white/20 text-white text-[14px] font-medium active:bg-white/30 transition-colors disabled:opacity-50"
        >
          {closing ? '正在退出...' : '确认提前结束'}
        </button>
      </div>
    </div>
  );
}
