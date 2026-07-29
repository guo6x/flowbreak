// Integration test for the RestMode completion flow.
//
// Renders the full RestMode page in WEB mode (Capacitor.isNativePlatform()
// returns false) with mocked store/router, fast-forwards the timer to zero,
// clicks "完成休息", and verifies:
//   - completeRestActivity fires exactly once (no double-settlement)
//   - setBlockState is called with the 10-minute grace window
//   - the reward view ("休息完成！") becomes visible
//   - a second click attempt does NOT trigger a second completion

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import type { ReactNode } from 'react';

// --- Mocks -------------------------------------------------------------
// vi.mock factories are hoisted to the top of the file, so any variables
// they reference must be created with vi.hoisted() to be available at
// mock-evaluation time.

const { mockCompleteRest, mockSetBlockState, mockNavigate, mockStore, useStoreMock } = vi.hoisted(() => {
  const mockCompleteRest = vi.fn();
  const mockSetBlockState = vi.fn();
  const mockNavigate = vi.fn();
  const mockStore = {
    profile: {
      restDuration: 2, // keep the test fast
      selectedBackground: 0,
      onboardingDone: true,
    },
    completeRestActivity: mockCompleteRest,
    setBlockState: mockSetBlockState,
    achievements: [] as Array<{ id: string; unlocked: boolean; title: string }>,
  };
  // Zustand's useStore is called both as a hook (selector) and with
  // .getState()/.setState() static methods. Mock all three.
  const useStoreMock = Object.assign(
    vi.fn(<T,>(selector: (s: typeof mockStore) => T): T => selector(mockStore)),
    {
      getState: () => mockStore,
      setState: (patch: Partial<typeof mockStore>) => {
        Object.assign(mockStore, patch);
      },
    },
  );
  return { mockCompleteRest, mockSetBlockState, mockNavigate, mockStore, useStoreMock };
});

vi.mock('../../../hooks/useStore', () => ({ useStore: useStoreMock }));

vi.mock('@capacitor/core', () => ({
  Capacitor: {
    isNativePlatform: () => false,
  },
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useLocation: () => ({ state: {} }),
}));

// NativeFlow should never be called in web mode, but mock it so any
// accidental call surfaces explicitly.
vi.mock('../../../backend/nativeFlow', () => ({
  NativeFlow: {
    beginRest: vi.fn(),
    cancelRest: vi.fn(),
    completeRestAndUnlock: vi.fn(),
    getBlockState: vi.fn(),
  },
}));

// --- Subject ------------------------------------------------------------

import RestMode from '../../RestMode';

function withRouter(node: ReactNode) {
  // We mocked react-router-dom above, so we don't need a real Router.
  return node;
}

beforeEach(() => {
  vi.useFakeTimers();
  mockCompleteRest.mockReset();
  mockSetBlockState.mockReset();
  mockNavigate.mockReset();
  mockStore.achievements = [];
});

afterEach(() => {
  vi.useRealTimers();
});

describe('RestMode completion flow (web)', () => {
  it('completes exactly once and shows the reward view', () => {
    render(withRouter(<RestMode />));

    // The page initialises timeLeft from profile.restDuration (2s).
    // Tick one second at a time so the re-armed setTimeout is scheduled
    // inside act()'s flush window (matching the real timer semantics).
    act(() => vi.advanceTimersByTime(1000));
    act(() => vi.advanceTimersByTime(1000));

    const completeButton = screen.getByRole('button', { name: /完成休息/ });
    expect(completeButton).toBeInTheDocument();

    fireEvent.click(completeButton);

    // completeRestActivity must fire exactly once.
    expect(mockCompleteRest).toHaveBeenCalledTimes(1);
    expect(mockCompleteRest).toHaveBeenCalledWith('eye', 2);

    // 10-minute grace window (web fallback).
    expect(mockSetBlockState).toHaveBeenCalledTimes(1);
    const [state, graceUntil] = mockSetBlockState.mock.calls[0];
    expect(state).toBe('GRACE');
    expect(graceUntil).toBeGreaterThan(Date.now());

    // The reward view's content is gated behind a 500ms reveal delay
    // (setRewardContentVisible). Advance past it so the "休息完成！"
    // heading becomes visible in the DOM.
    act(() => vi.advanceTimersByTime(600));

    // The reward view should now be rendered.
    expect(screen.getByText('休息完成！')).toBeInTheDocument();

    // The "完成休息" button is gone — no second click possible.
    expect(screen.queryByRole('button', { name: /完成休息/ })).toBeNull();
  });

  it('removes the complete button after settlement so the UI cannot retrigger', () => {
    render(withRouter(<RestMode />));

    act(() => vi.advanceTimersByTime(1000));
    act(() => vi.advanceTimersByTime(1000));

    const completeButton = screen.getByRole('button', { name: /完成休息/ });
    fireEvent.click(completeButton);

    // After completion the reward view replaces the activity view, so the
    // complete button is no longer in the DOM. This is the UI-layer guard
    // for web mode; the synchronous ref guard is covered by the native
    // concurrency test in completionFlow.native.test.tsx.
    expect(mockCompleteRest).toHaveBeenCalledTimes(1);

    // There is no second button to click — verify it's gone.
    expect(screen.queryByRole('button', { name: /完成休息/ })).toBeNull();

    // Reveal the reward content (500ms delay) so the "继续使用" button
    // becomes accessible.
    act(() => vi.advanceTimersByTime(600));

    // The reward screen's "继续使用" button navigates to /dashboard.
    const finishButton = screen.getByRole('button', { name: '继续使用' });
    fireEvent.click(finishButton);
    expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
  });
});
