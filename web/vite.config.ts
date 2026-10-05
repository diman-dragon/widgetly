/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Порт 8080 — dev-сервер и предпросмотр. host: true открывает доступ из локальной сети (для телефона).
export default defineConfig({
  plugins: [react()],
  server: { port: 8080, host: true, strictPort: true },
  preview: { port: 8080, host: true, strictPort: true },
  build: { outDir: 'dist', target: 'es2020', sourcemap: false },
  test: { environment: 'node', include: ['src/**/*.test.ts'] },
});
