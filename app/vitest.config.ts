/// <reference types="vitest" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    css: false,
    // Keep worker startup reliable on Windows hosts reporting many logical
    // CPUs, and leave room for route-level UI tests on slower machines.
    maxWorkers: 2,
    testTimeout: 15_000,
  },
});
