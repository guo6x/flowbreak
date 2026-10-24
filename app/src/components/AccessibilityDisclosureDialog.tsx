import { useEffect, useRef } from "react";
import { X } from "lucide-react";

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
  const overlayRef = useRef<HTMLDivElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const onDeclineRef = useRef(onDecline);
  const busyRef = useRef(busy);
  onDeclineRef.current = onDecline;
  busyRef.current = busy;

  useEffect(() => {
    if (!open) return;

    const previousFocus = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    const dialog = dialogRef.current;
    if (!dialog) return;

    // Keep every branch outside the overlay inert while the disclosure is open.
    // aria-modal remains the semantic fallback for assistive technologies.
    const inertedElements: Array<{ element: HTMLElement; wasInert: boolean }> = [];
    let branch: HTMLElement | null = overlayRef.current;
    while (branch?.parentElement) {
      const ancestor = branch.parentElement;
      for (const sibling of Array.from(ancestor.children)) {
        if (sibling !== branch && sibling instanceof HTMLElement) {
          inertedElements.push({ element: sibling, wasInert: sibling.hasAttribute("inert") });
          sibling.setAttribute("inert", "");
        }
      }
      if (ancestor === document.body) break;
      branch = ancestor;
    }

    const focusableSelector = [
      "a[href]",
      "area[href]",
      "button:not([disabled])",
      "input:not([disabled]):not([type='hidden'])",
      "select:not([disabled])",
      "textarea:not([disabled])",
      "[tabindex]:not([tabindex='-1'])",
    ].join(",");
    let lastFocusedElement: HTMLElement = titleRef.current ?? dialog;

    const getFocusableElements = () => Array.from(
      dialog.querySelectorAll<HTMLElement>(focusableSelector),
    ).filter((element) => (
      element.tabIndex >= 0
      && !element.matches(":disabled")
      && !element.closest("[hidden], [inert], [aria-hidden='true']")
    ));
    const isAvailableFocusTarget = (element: HTMLElement) => (
      dialog.contains(element)
      && !element.matches(":disabled")
      && !element.closest("[hidden], [inert], [aria-hidden='true']")
    );

    const handleFocusIn = (event: FocusEvent) => {
      const target = event.target;
      if (target instanceof HTMLElement && dialog.contains(target)) {
        lastFocusedElement = target;
        return;
      }

      const fallback = isAvailableFocusTarget(lastFocusedElement)
        ? lastFocusedElement
        : titleRef.current ?? dialog;
      fallback.focus();
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        if (!busyRef.current) onDeclineRef.current();
        return;
      }
      if (event.key !== "Tab") return;

      const focusableElements = getFocusableElements();
      if (focusableElements.length === 0) {
        event.preventDefault();
        dialog.focus();
        return;
      }

      const activeIndex = focusableElements.indexOf(document.activeElement as HTMLElement);
      if (event.shiftKey && activeIndex <= 0) {
        event.preventDefault();
        focusableElements[focusableElements.length - 1].focus();
      } else if (!event.shiftKey && activeIndex === -1) {
        event.preventDefault();
        focusableElements[0].focus();
      } else if (!event.shiftKey && activeIndex === focusableElements.length - 1) {
        event.preventDefault();
        focusableElements[0].focus();
      }
    };

    const declineIfIdle = () => {
      if (!busyRef.current) onDeclineRef.current();
    };

    document.addEventListener("focusin", handleFocusIn);
    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener("popstate", declineIfIdle);
    (titleRef.current ?? dialog).focus();
    return () => {
      document.removeEventListener("focusin", handleFocusIn);
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("popstate", declineIfIdle);
      for (const { element, wasInert } of inertedElements) {
        if (wasInert) element.setAttribute("inert", "");
        else element.removeAttribute("inert");
      }
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [open]);

  if (!open) return null;
  return (
    <div
      ref={overlayRef}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-5"
      role="presentation"
      onClick={(event) => {
        if (event.target === event.currentTarget && !busy) onDecline();
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="accessibility-disclosure-title"
        tabIndex={-1}
        className="w-full max-w-md rounded-2xl bg-white p-5 shadow-xl"
      >
        <div className="flex items-start justify-between gap-3 mb-3">
          <h2 ref={titleRef} id="accessibility-disclosure-title" tabIndex={-1} className="text-[17px] font-bold text-gray-900">
            开启无障碍强阻断前，请了解用途
          </h2>
          <button
            type="button"
            aria-label="关闭告知"
            onClick={onDecline}
            disabled={busy}
            className="w-9 h-9 shrink-0 rounded-full bg-gray-100 text-gray-700 flex items-center justify-center disabled:opacity-50"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>
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
