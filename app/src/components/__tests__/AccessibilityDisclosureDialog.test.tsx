import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AccessibilityDisclosureDialog from "../AccessibilityDisclosureDialog";

describe("AccessibilityDisclosureDialog focus management", () => {
  it("moves focus into the disclosure, traps keyboard and programmatic focus, then restores focus and background state", () => {
    const onDecline = vi.fn();
    const onConfirm = vi.fn();
    const { rerender } = render(
      <>
        <button type="button">Open disclosure</button>
        <AccessibilityDisclosureDialog
          open={false}
          busy={false}
          onDecline={onDecline}
          onConfirm={onConfirm}
        />
      </>,
    );
    const trigger = screen.getByRole("button", { name: "Open disclosure" });
    trigger.focus();
    rerender(
      <>
        <button type="button">Open disclosure</button>
        <AccessibilityDisclosureDialog
          open
          busy={false}
          onDecline={onDecline}
          onConfirm={onConfirm}
        />
      </>,
    );

    const dialog = screen.getByRole("dialog", { name: "开启无障碍强阻断前，请了解用途" });
    const close = screen.getByRole("button", { name: "关闭告知" });
    const decline = screen.getByRole("button", { name: "暂不开启" });
    const confirm = screen.getByRole("button", { name: "我理解，前往系统设置" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(document.activeElement).toBe(screen.getByRole("heading", { name: "开启无障碍强阻断前，请了解用途" }));
    expect(trigger).toHaveAttribute("inert");

    confirm.focus();
    const tabForward = new KeyboardEvent("keydown", { key: "Tab", bubbles: true, cancelable: true });
    confirm.dispatchEvent(tabForward);
    expect(tabForward.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(close);

    const shiftTab = new KeyboardEvent("keydown", { key: "Tab", shiftKey: true, bubbles: true, cancelable: true });
    close.dispatchEvent(shiftTab);
    expect(shiftTab.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(confirm);

    trigger.focus();
    expect(document.activeElement).toBe(confirm);

    fireEvent.click(decline);
    rerender(
      <>
        <button type="button">Open disclosure</button>
        <AccessibilityDisclosureDialog
          open={false}
          busy={false}
          onDecline={onDecline}
          onConfirm={onConfirm}
        />
      </>,
    );
    expect(trigger).not.toHaveAttribute("inert");
    expect(document.activeElement).toBe(trigger);
    expect(onDecline).toHaveBeenCalledOnce();

  });

  it("keeps focus on the modal container when all controls are disabled", () => {
    const { rerender } = render(
      <AccessibilityDisclosureDialog
        open
        busy={false}
        onDecline={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );
    rerender(
      <AccessibilityDisclosureDialog
        open
        busy
        onDecline={vi.fn()}
        onConfirm={vi.fn()}
      />,
    );

    const dialog = screen.getByRole("dialog");
    const confirm = screen.getByRole("button", { name: "正在打开…" });
    expect(confirm).toBeDisabled();

    const tab = new KeyboardEvent("keydown", { key: "Tab", bubbles: true, cancelable: true });
    confirm.dispatchEvent(tab);
    expect(tab.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(dialog);
  });
});
