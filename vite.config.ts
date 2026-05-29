import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  plugins: [svelte()],

  // Prevent Vite from obscuring Tauri logs
  clearScreen: false,

  // Tauri expects a fixed port, and fails if that port is already in use
  server: {
    port: 1420,
    strictPort: true,
    host: 'localhost'
  },

  // to make use of `TAURI_DEBUG` and other env variables
  // https://v2.tauri.app/reference/config/
  envPrefix: ['VITE_', 'TAURI_'],

  build: {
    // Tauri supports es2021
    target: process.env.TAURI_PLATFORM == 'windows' ? 'chrome105' : 'safari13',
    // don't minify for debug builds
    minify: !process.env.TAURI_DEBUG ? 'esbuild' : false,
    // produce sourcemaps for debug builds
    sourcemap: !!process.env.TAURI_DEBUG
  }
});
