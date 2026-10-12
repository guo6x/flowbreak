/**
 * Prominent, separate notice before entering Android's Accessibility settings.
 * A click on a settings entry point MUST NOT directly request this privilege.
 * This component never grants Android permissions; the platform still asks.
 */
type Props = {
  open: boolean;
  busy: boolean;
  onDecline: () => void;
  onConfirm: () => void;
  error?: string;
};

export default function AccessibilityDisclosureDialog({
  open, busy, onDecline, onConfirm, error,
}: Props) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-5" role="presentation">
      <div role="dialog" aria-modal="true" aria-labelledby="accessibility-disclosure-title" className="w-full max-w-md rounded-2xl bg-white p-5 shadow-xl">
        <h2 id="accessibility-disclosure-title" className="text-[17px] font-bold text-gray-900 mb-3">
          开启无障碍强阻断前，请了解用途
        </h2>
        <p className="text-[13px] leading-relaxed text-gray-700">
          这是国内版的可选辅助阻断功能。启用后，FlowBreak 通过 Android 无障碍服务监听窗口切换事件中的应用包名，以及必要时用于识别特定页面的窗口类名，
          遇到微信窗口时，FlowBreak 会根据窗口类名在本机保存“是否为视频号页面”的判断值及最近检测时间，用于短时间内的目标识别；不保存页面文字。
          仅在命中你选定的受限应用且已达到阻断条件时，尝试返回桌面并显示阻断提示。
          不会读取、保存或上传页面文字、聊天内容或输入内容。你可以拒绝，基础保护设置仍可使用；
          也可以随时从 Android 系统设置关闭。具体效果受系统与设备后台限制影响。
        </p>
        {error && <p role="alert" className="mt-3 text-[12px] text-error">{error}</p>}
        <div className="mt-5 flex gap-3">
          <button type="button" onClick={onDecline} disabled={busy}
            className="flex-1 min-h-[44px] rounded-xl bg-gray-100 text-[13px] font-medium text-gray-700 disabled:opacity-50">
            暂不开启
          </button>
          <button type="button" onClick={onConfirm} disabled={busy}
            className="flex-1 min-h-[44px] rounded-xl bg-secondary text-[13px] font-medium text-white disabled:opacity-50">
            {busy ? '正在打开…' : '我理解，前往系统设置'}
          </button>
        </div>
      </div>
    </div>
  );
}
