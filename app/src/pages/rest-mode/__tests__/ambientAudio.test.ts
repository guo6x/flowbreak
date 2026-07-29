// Tests for the AudioContext constructor selection in ambientAudio.ts.
//
// ambientAudio keeps a module-level singleton `globalAudioCtx`, so each
// test resets the module registry and re-imports the module to start
// from a clean state. The tests verify:
//   1. Standard `globalThis.AudioContext` is preferred when present.
//   2. `webkitAudioContext` fallback is used when only it exists.
//   3. When neither constructor exists, `playChime()` degrades silently
//      (no uncaught ReferenceError) and the AmbientPad constructor/start
//      path also degrades silently.
//
// We do not expose `getAudioCtx` from the module; instead we observe the
// constructor that was selected by spying on the constructor function
// itself and by calling the public `playChime` / `AmbientPad.start` APIs.

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

// Minimal AudioContext stub that satisfies the surface area touched by
// `playChime` and `AmbientPad.start`. Both wrap audio calls in try/catch
// and degrade silently on error, so the stub only needs to be callable
// and return an object with the methods used.
function makeAudioContextStub(label: string) {
  const calls: string[] = [];
  class AudioContextStub {
    state: AudioContextState = 'running';
    currentTime = 0;
    destination = {} as AudioDestinationNode;
    // Track which constructor was used so tests can assert selection.
    __label = label;
    __calls = calls;
    createGain(): GainNode {
      return {
        gain: {
          value: 0,
          setValueAtTime: () => {},
          linearRampToValueAtTime: () => {},
          exponentialRampToValueAtTime: () => {},
          cancelScheduledValues: () => {},
        },
        connect: () => {},
        disconnect: () => {},
      } as unknown as GainNode;
    }
    createOscillator(): OscillatorNode {
      return {
        type: 'sine',
        frequency: { setValueAtTime: () => {}, value: 0 },
        detune: { setValueAtTime: () => {} },
        connect: () => {},
        disconnect: () => {},
        start: () => {},
        stop: () => {},
        onended: null,
      } as unknown as OscillatorNode;
    }
    createBiquadFilter(): BiquadFilterNode {
      return {
        type: 'lowpass',
        frequency: { setValueAtTime: () => {}, value: 0 },
        Q: { setValueAtTime: () => {} },
        connect: () => {},
        disconnect: () => {},
      } as unknown as BiquadFilterNode;
    }
    resume(): Promise<void> {
      return Promise.resolve();
    }
  }
  return { AudioContextStub, calls };
}

type GlobalWithAudio = {
  AudioContext?: unknown;
  webkitAudioContext?: unknown;
};

function deleteAudioGlobals() {
  const g = globalThis as unknown as GlobalWithAudio;
  delete g.AudioContext;
  delete g.webkitAudioContext;
}

beforeEach(() => {
  // jsdom's test setup assigns a generic AudioContextStub to globalThis;
  // remove both symbols so each test can reinstall exactly the constructor
  // configuration it needs.
  deleteAudioGlobals();
});

afterEach(() => {
  vi.resetModules();
  vi.useRealTimers();
  deleteAudioGlobals();
});

describe('ambientAudio AudioContext fallback', () => {
  it('prefers the standard AudioContext constructor when present', async () => {
    const { AudioContextStub } = makeAudioContextStub('standard');
    (globalThis as unknown as GlobalWithAudio).AudioContext = AudioContextStub;
    // Also install a webkit constructor to prove the standard one wins.
    const webkit = makeAudioContextStub('webkit').AudioContextStub;
    (globalThis as unknown as GlobalWithAudio).webkitAudioContext = webkit;

    vi.resetModules();
    const { playChime } = await import('../ambientAudio');

    // playChime swallows errors; if the wrong constructor were selected
    // we'd still not throw, so observe selection indirectly by spying on
    // the standard constructor's instantiation.
    const standardSpy = vi.spyOn(
      globalThis as unknown as GlobalWithAudio & { AudioContext: typeof AudioContextStub },
      'AudioContext',
    );

    playChime(440, 0.1);

    expect(standardSpy).toHaveBeenCalledTimes(1);
  });

  it('uses the webkitAudioContext fallback when only it exists', async () => {
    const { AudioContextStub: WebkitStub } = makeAudioContextStub('webkit');
    (globalThis as unknown as GlobalWithAudio).webkitAudioContext = WebkitStub;
    // Standard AudioContext is deliberately absent.

    vi.resetModules();
    const { playChime } = await import('../ambientAudio');

    const webkitSpy = vi.spyOn(
      globalThis as unknown as GlobalWithAudio & {
        webkitAudioContext: typeof WebkitStub;
      },
      'webkitAudioContext',
    );

    playChime(440, 0.1);

    expect(webkitSpy).toHaveBeenCalledTimes(1);
  });

  it('degrades silently when no AudioContext constructor exists (playChime does not throw)', async () => {
    // Neither AudioContext nor webkitAudioContext is installed.
    vi.resetModules();
    const { playChime } = await import('../ambientAudio');

    expect(() => playChime(440, 0.1)).not.toThrow();
  });

  it('does not raise a ReferenceError for AudioContext when the symbol is absent', async () => {
    // This is a regression guard: the previous implementation referenced
    // the bare `AudioContext` identifier, which throws ReferenceError in
    // environments where it is not declared. The new implementation reads
    // via `globalThis`, so no ReferenceError should escape.
    vi.resetModules();
    const { playChime, AmbientPad } = await import('../ambientAudio');

    expect(() => playChime(440, 0.1)).not.toThrow();
    // AmbientPad.start() also calls getAudioCtx internally and must
    // swallow the "not supported" error.
    const pad = new AmbientPad();
    expect(() => pad.start()).not.toThrow();
  });
});
