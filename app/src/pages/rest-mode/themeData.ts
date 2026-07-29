// Static visual theme data for the rest mode page.
import type { ThemeGlow } from './types';

export const themeGlows: Record<number, ThemeGlow> = {
  0: {
    baseBg: 'linear-gradient(135deg, #0f3014 0%, #1b4d22 50%, #2d6a4f 100%)',
    colors: ['rgba(76, 175, 80, 0.45)', 'rgba(129, 199, 132, 0.35)', 'rgba(0, 77, 64, 0.4)'],
  },
  1: {
    baseBg: 'linear-gradient(135deg, #0a1f44 0%, #0d3263 50%, #154c79 100%)',
    colors: ['rgba(0, 172, 193, 0.45)', 'rgba(63, 81, 181, 0.35)', 'rgba(128, 222, 234, 0.3)'],
  },
  2: {
    baseBg: 'linear-gradient(135deg, #212529 0%, #343a40 50%, #495057 100%)',
    colors: ['rgba(224, 224, 224, 0.25)', 'rgba(159, 168, 218, 0.35)', 'rgba(55, 71, 79, 0.45)'],
  },
  3: {
    baseBg: 'linear-gradient(135deg, #2b0f1a 0%, #4a1228 50%, #5c1c38 100%)',
    colors: ['rgba(244, 143, 177, 0.45)', 'rgba(206, 147, 216, 0.35)', 'rgba(255, 224, 130, 0.3)'],
  },
  4: {
    baseBg: 'linear-gradient(135deg, #371200 0%, #571e04 50%, #7d2d0b 100%)',
    colors: ['rgba(255, 183, 77, 0.45)', 'rgba(255, 87, 34, 0.35)', 'rgba(103, 58, 183, 0.3)'],
  },
};

export const themedParticles: Record<number, string[]> = {
  0: ['🍃', '🌿', '🪴', '🍂', '🌱', '🪵', '🌳'],
  1: ['🫧', '🐚', '🪸', '💧', '🫧', '🌊', '🐠'],
  2: ['❄️', '🏔️', '⛰️', '❄️', '🌨️', '❄️', '🗻'],
  3: ['🌸', '🌺', '🪷', '🌷', '💮', '🌼', '🏵️'],
  4: ['✨', '🔥', '💫', '🌟', '🕯️', '✨', '🌅'],
};

export const calmColor = '#A5D6A7';
