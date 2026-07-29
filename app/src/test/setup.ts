// Test setup — runs before every test file.
// Adds jest-dom matchers and stubs the small set of browser APIs our
// pure modules touch so jsdom (which lacks them) doesn't blow up.

import '@testing-library/jest-dom';

// jsdom does not implement Web Audio. We only need the call sites to be
// reachable; the modules under test already swallow errors thrown by
// `new AudioContext()`. Provide a minimal stub so `AudioContext` is at
// least constructable.
class AudioContextStub {
  state: AudioContextState = 'running';
  currentTime = 0;
  destination = {} as AudioDestinationNode;
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

// `AudioContext` is a global var in lib.dom.d.ts but jsdom doesn't ship
// an implementation. Assigning to `globalThis` makes the symbol resolve
// to our stub at runtime.
(globalThis as unknown as { AudioContext: typeof AudioContextStub }).AudioContext = AudioContextStub;

// jsdom also lacks `navigator.vibrate`. Tests that exercise vibration
// branches need the function to exist; we make it a no-op.
if (!('vibrate' in navigator)) {
  (navigator as unknown as { vibrate: () => boolean }).vibrate = () => true;
}
