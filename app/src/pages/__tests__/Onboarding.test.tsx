import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BrowserRouter } from "react-router";
import Onboarding from "../Onboarding";

const mockNavigate = vi.fn();
vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return { ...actual, useNavigate: () => mockNavigate };
});

function renderOnboarding() {
  return render(
    <BrowserRouter>
      <Onboarding />
    </BrowserRouter>
  );
}

describe("Onboarding", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  it("第一页点击跳过直接进入permissions", () => {
    renderOnboarding();
    fireEvent.click(screen.getByText("跳过"));
    expect(mockNavigate).toHaveBeenCalledWith("/permissions", { replace: true });
  });

  it("下一步切换到下一页", async () => {
    renderOnboarding();
    expect(screen.getByText("自然唤醒")).toBeDefined();
    fireEvent.click(screen.getByText("下一步"));
    await waitFor(() => {
      expect(screen.getByText("三层渐进式干预")).toBeDefined();
    });
  });

  it("第二页点击跳过直接进入permissions", () => {
    renderOnboarding();
    fireEvent.click(screen.getByText("下一步"));
    fireEvent.click(screen.getByText("跳过"));
    expect(mockNavigate).toHaveBeenCalledWith("/permissions", { replace: true });
  });

  it("最后一页点击开始使用进入permissions", () => {
    renderOnboarding();
    fireEvent.click(screen.getByText("下一步"));
    fireEvent.click(screen.getByText("下一步"));
    fireEvent.click(screen.getByText("开始使用"));
    expect(mockNavigate).toHaveBeenCalledWith("/permissions");
  });
});
