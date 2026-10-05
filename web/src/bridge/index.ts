import { registerPlugin } from '@capacitor/core';
import type { WidgetBridgePlugin } from './definitions';

/** Нативная реализация — WidgetBridgePlugin.kt; в браузере подключается web.ts. */
export const WidgetBridge = registerPlugin<WidgetBridgePlugin>('WidgetBridge', {
  web: () => import('./web').then((m) => new m.WidgetBridgeWeb()),
});

export * from './definitions';
