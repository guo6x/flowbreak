// Web Audio API implementation for ambient pad sound and chime effects.
// No React — only browser audio APIs.

interface WebkitAudioWindow {
  webkitAudioContext?: typeof AudioContext;
}

// Lazy singleton AudioContext to prevent leaks
let globalAudioCtx: AudioContext | null = null;

function getAudioCtx(): AudioContext {
  if (!globalAudioCtx) {
    // `AudioContext` is a global var declaration in lib.dom.d.ts (accessible
    // directly, but not via `window.AudioContext`). The webkit-prefixed fallback
    // is needed for very old iOS Safari and is not in the standard types, so we
    // narrow through a small interface instead of using `any`.
    const w = window as unknown as WebkitAudioWindow;
    const Ctor = AudioContext || w.webkitAudioContext;
    if (!Ctor) {
      throw new Error('AudioContext not supported in this environment');
    }
    globalAudioCtx = new Ctor();
  }
  return globalAudioCtx;
}

/**
 * Ambient pad synthesiser — layered oscillators through a low-pass filter
 * with a slow LFO modulating the cutoff frequency.
 *
 * Lifecycle:
 *  - `start()` creates nodes and ramps up volume.
 *  - `setMute()` / `setPause()` ramp volume to 0 without stopping nodes.
 *  - `stop()` ramps down then disconnects all nodes after 350ms.
 *
 * The class is designed to be instantiated inside a React effect so that
 * AudioContext creation only happens after user interaction.
 */
export class AmbientPad {
  private ctx: AudioContext | null = null;
  private oscs: OscillatorNode[] = [];
  private oscGains: GainNode[] = [];
  private filter: BiquadFilterNode | null = null;
  private masterGain: GainNode | null = null;
  private lfo: OscillatorNode | null = null;
  private lfoGain: GainNode | null = null;
  private isPlaying: boolean = false;
  private isMuted: boolean = false;
  private isPaused: boolean = false;
  private targetVolume: number = 0.08;

  start() {
    if (this.isPlaying) return;
    try {
      this.ctx = getAudioCtx();
      if (this.ctx.state === 'suspended') {
        this.ctx.resume().catch(() => {});
      }

      this.masterGain = this.ctx.createGain();
      this.masterGain.gain.setValueAtTime(0, this.ctx.currentTime);

      this.filter = this.ctx.createBiquadFilter();
      this.filter.type = 'lowpass';
      this.filter.frequency.setValueAtTime(240, this.ctx.currentTime);
      this.filter.Q.setValueAtTime(1, this.ctx.currentTime);

      const freqs = [110, 164.81, 220, 277.18];
      const types: OscillatorType[] = ['triangle', 'sine', 'sine', 'sine'];

      freqs.forEach((freq, idx) => {
        if (!this.ctx || !this.filter) return;
        const osc = this.ctx.createOscillator();
        const oscGain = this.ctx.createGain();

        osc.type = types[idx] || 'sine';
        osc.frequency.setValueAtTime(freq, this.ctx.currentTime);

        if (idx > 0) {
          osc.detune.setValueAtTime((Math.random() - 0.5) * 8, this.ctx.currentTime);
        }

        const baseGain = idx === 0 ? 0.35 : idx === 1 ? 0.25 : idx === 2 ? 0.2 : 0.15;
        oscGain.gain.setValueAtTime(baseGain, this.ctx.currentTime);

        osc.connect(oscGain);
        oscGain.connect(this.filter);
        osc.start(this.ctx.currentTime);

        this.oscs.push(osc);
        this.oscGains.push(oscGain);
      });

      this.lfo = this.ctx.createOscillator();
      this.lfo.frequency.setValueAtTime(0.08, this.ctx.currentTime);

      this.lfoGain = this.ctx.createGain();
      this.lfoGain.gain.setValueAtTime(35, this.ctx.currentTime);

      this.lfo.connect(this.lfoGain);
      this.lfoGain.connect(this.filter.frequency);
      this.lfo.start(this.ctx.currentTime);

      this.filter.connect(this.masterGain);
      this.masterGain.connect(this.ctx.destination);

      const currentVol = (this.isMuted || this.isPaused) ? 0 : this.targetVolume;
      this.masterGain.gain.linearRampToValueAtTime(currentVol, this.ctx.currentTime + 1.5);

      this.isPlaying = true;
    } catch (e) {
      console.error('Failed to start AmbientPad:', e);
    }
  }

  setMute(mute: boolean) {
    this.isMuted = mute;
    this.updateGain();
  }

  setPause(paused: boolean) {
    this.isPaused = paused;
    this.updateGain();
  }

  private updateGain() {
    if (!this.masterGain || !this.ctx) return;
    const target = (this.isMuted || this.isPaused) ? 0 : this.targetVolume;
    try {
      this.masterGain.gain.cancelScheduledValues(this.ctx.currentTime);
      this.masterGain.gain.setValueAtTime(this.masterGain.gain.value, this.ctx.currentTime);
      this.masterGain.gain.linearRampToValueAtTime(target, this.ctx.currentTime + 0.8);
    } catch (e) {
      console.error('Failed to update Gain:', e);
    }
  }

  stop() {
    if (!this.isPlaying) return;
    try {
      const ctx = this.ctx;
      const masterGain = this.masterGain;
      const filter = this.filter;
      const lfo = this.lfo;
      const lfoGain = this.lfoGain;
      const oscs = this.oscs;
      const oscGains = this.oscGains;

      this.masterGain = null;
      this.filter = null;
      this.lfo = null;
      this.lfoGain = null;
      this.oscs = [];
      this.oscGains = [];
      this.isPlaying = false;

      if (masterGain && ctx) {
        masterGain.gain.cancelScheduledValues(ctx.currentTime);
        masterGain.gain.setValueAtTime(masterGain.gain.value, ctx.currentTime);
        masterGain.gain.linearRampToValueAtTime(0, ctx.currentTime + 0.3);
      }

      const scheduledStop = () => {
        oscs.forEach(osc => {
          try { osc.stop(); } catch { /* ignore */ }
          try { osc.disconnect(); } catch { /* ignore */ }
        });

        oscGains.forEach(g => {
          try { g.disconnect(); } catch { /* ignore */ }
        });

        if (lfo) {
          try { lfo.stop(); } catch { /* ignore */ }
          try { lfo.disconnect(); } catch { /* ignore */ }
        }
        if (lfoGain) {
          try { lfoGain.disconnect(); } catch { /* ignore */ }
        }
        if (filter) {
          try { filter.disconnect(); } catch { /* ignore */ }
        }
        if (masterGain) {
          try { masterGain.disconnect(); } catch { /* ignore */ }
        }
      };

      setTimeout(scheduledStop, 350);
    } catch (e) {
      console.error('Failed to stop AmbientPad:', e);
    }
  }
}

/**
 * Play a short sine-wave chime with a low-pass filter and exponential decay.
 * Safe to call when audio is unavailable — failures are silently ignored.
 */
export function playChime(freq: number, duration: number): void {
  try {
    const ctx = getAudioCtx();
    if (ctx.state === 'suspended') ctx.resume().catch(() => {});
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    const chimeFilter = ctx.createBiquadFilter();
    chimeFilter.type = 'lowpass';
    chimeFilter.frequency.setValueAtTime(1200, ctx.currentTime);

    osc.connect(gain);
    gain.connect(chimeFilter);
    chimeFilter.connect(ctx.destination);

    osc.type = 'sine';
    osc.frequency.setValueAtTime(freq, ctx.currentTime);

    // 20ms 线性淡入，消去原版中因音频瞬间开启产生的"咔哒"瞬间音
    gain.gain.setValueAtTime(0, ctx.currentTime);
    gain.gain.linearRampToValueAtTime(0.12, ctx.currentTime + 0.02);

    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration);

    osc.start(ctx.currentTime);
    osc.stop(ctx.currentTime + duration);
    osc.onended = () => {
      try {
        osc.disconnect();
        gain.disconnect();
        chimeFilter.disconnect();
      } catch { /* ignore */ }
    };
  } catch { /* audio not available */ }
}
