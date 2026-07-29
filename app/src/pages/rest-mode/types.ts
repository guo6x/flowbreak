// Shared types for the RestMode module.

export type RestActivityId = 'eye' | 'stretch' | 'breathe';

export interface RestActivity {
  id: RestActivityId;
  iconKey: string;
  title: string;
  desc: string;
  steps: string[];
  color: string;
}

export interface ThemeGlow {
  baseBg: string;
  colors: string[];
}

export interface AmbientParticle {
  emoji: string;
  x: number;
  rotate: number;
  duration: number;
  delay: number;
  left: number;
}

export interface ConfettiPiece {
  rotate: number;
  duration: number;
  delay: number;
  left: number;
  color: string;
}
