// Static visual theme data for the rest mode page.
import type { ThemeGlow } from './types';

export const themeGlows: Record<number, ThemeGlow> = {
  0: {
    baseBg: 'linear-gradient(145deg, #102C26 0%, #163F34 55%, #234E3B 100%)',
    colors: ['rgba(108, 165, 131, 0.2)', 'rgba(96, 164, 147, 0.18)', 'rgba(36, 115, 83, 0.2)'],
  },
  1: {
    baseBg: 'linear-gradient(145deg, #142C38 0%, #1C4050 55%, #285364 100%)',
    colors: ['rgba(103, 169, 186, 0.2)', 'rgba(79, 135, 160, 0.18)', 'rgba(128, 189, 194, 0.2)'],
  },
  2: {
    baseBg: 'linear-gradient(145deg, #242D31 0%, #354448 55%, #455655 100%)',
    colors: ['rgba(169, 187, 180, 0.16)', 'rgba(154, 174, 193, 0.18)', 'rgba(96, 119, 119, 0.2)'],
  },
  3: {
    baseBg: 'linear-gradient(145deg, #352B35 0%, #49384A 55%, #5C4654 100%)',
    colors: ['rgba(190, 148, 174, 0.2)', 'rgba(164, 147, 185, 0.18)', 'rgba(193, 175, 154, 0.16)'],
  },
  4: {
    baseBg: 'linear-gradient(145deg, #3C2D28 0%, #514035 55%, #6C5040 100%)',
    colors: ['rgba(202, 164, 119, 0.2)', 'rgba(190, 138, 111, 0.18)', 'rgba(178, 151, 150, 0.16)'],
  },
};

export const themedParticles: Record<number, string[]> = {
  0: ['🍃', '🌿', '🪴', '🍂', '🌱', '🪵', '🌳'],
  1: ['🫧', '🐚', '🪸', '💧', '🫧', '🌊', '🐠'],
  2: ['❄️', '🏔️', '⛰️', '❄️', '🌨️', '❄️', '🗻'],
  3: ['🌸', '🌺', '🪷', '🌷', '💮', '🌼', '🏵️'],
  4: ['✨', '🔥', '💫', '🌟', '🕯️', '✨', '🌅'],
};

export const calmColor = '#B9D8C3';
