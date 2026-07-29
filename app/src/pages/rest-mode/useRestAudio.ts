// React-layer hook that manages the AmbientPad lifecycle and exposes a
// `stopAmbient` imperative for the completion flow.
//
// The hook creates the AmbientPad inside an effect (not during render) to
// avoid AudioContext leaks under React 18 concurrent rendering, matching
// the original implementation.

import { useEffect, useRef } from 'react';
import { AmbientPad } from './ambientAudio';

export interface UseRestAudioOptions {
  isMuted: boolean;
  isPaused: boolean;
  /** When true (showReward), the ambient pad is fully stopped. */
  stopOnReward: boolean;
}

export interface UseRestAudioResult {
  /** Imperatively stop the ambient pad (used by handleComplete). */
  stopAmbient: () => void;
}

export function useRestAudio(options: UseRestAudioOptions): UseRestAudioResult {
  const { isMuted, isPaused, stopOnReward } = options;
  const ambientPadRef = useRef<AmbientPad | null>(null);

  // Create AmbientPad on mount, destroy on unmount.
  useEffect(() => {
    // AmbientPad 必须在 useEffect 中创建，不能在渲染阶段 new
    // React 18 并发渲染可能丢弃渲染树，导致 AudioContext 和 oscillator 节点泄漏
    ambientPadRef.current = new AmbientPad();
    return () => {
      if (ambientPadRef.current) {
        ambientPadRef.current.stop();
        ambientPadRef.current = null;
      }
    };
  }, []);

  // Audio lifecycle: start on mount, stop on cleanup.
  useEffect(() => {
    const pad = ambientPadRef.current;
    if (pad) {
      pad.start();
      pad.setMute(isMuted);
      pad.setPause(isPaused);
    }
    return () => {
      if (pad) {
        pad.stop();
      }
    };
    // Intentionally [] — start only once on mount; mute/pause handled by
    // dedicated effects below.
  }, []);

  // React to pause state changes.
  useEffect(() => {
    if (ambientPadRef.current) {
      ambientPadRef.current.setPause(isPaused);
    }
  }, [isPaused]);

  // React to mute state changes.
  useEffect(() => {
    if (ambientPadRef.current) {
      ambientPadRef.current.setMute(isMuted);
    }
  }, [isMuted]);

  // When entering reward page, fully fade out and terminate ambient music.
  useEffect(() => {
    if (stopOnReward && ambientPadRef.current) {
      ambientPadRef.current.stop();
    }
  }, [stopOnReward]);

  const stopAmbient = () => {
    if (ambientPadRef.current) {
      ambientPadRef.current.stop();
    }
  };

  return { stopAmbient };
}
