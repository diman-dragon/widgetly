import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// base './' — чтобы сборка одинаково открывалась и из Capacitor (Android), и с любого хостинга
export default defineConfig({ base: './', plugins: [react()] })
