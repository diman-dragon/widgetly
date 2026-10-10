// Мост к нативной части. В обычном браузере (npm run web) работает заглушка на localStorage.
const cap = window.Capacitor;
const isNative = !!(cap && cap.isNativePlatform && cap.isNativePlatform());
// В Capacitor 7/8 нативный мост не содержит registerPlugin (он есть только в бандле @capacitor/core,
// а сборщика у нас нет), поэтому вызываем плагин напрямую через мост: nativePromise(плагин, метод, параметры).
function makePlugin(name) {
  if (typeof cap.registerPlugin === 'function') return cap.registerPlugin(name);
  if (typeof cap.nativePromise !== 'function') throw new Error('Capacitor bridge: нет nativePromise');
  return new Proxy({}, {
    get: (_, method) => (method === 'then' ? undefined : (opts) => cap.nativePromise(name, String(method), opts || {})),
  });
}
const P = isNative ? makePlugin('WidgetBridge') : null;
const ls = {
  get: (k, d) => { try { return JSON.parse(localStorage.getItem(k)) ?? d; } catch { return d; } },
  set: (k, v) => localStorage.setItem(k, JSON.stringify(v)),
};
const timeout = (p, ms, name) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('Нативный вызов ' + name + ' не ответил за ' + ms / 1000 + ' с')), ms))]);
const DPR = () => Math.min(window.devicePixelRatio || 2, 3);

export const native = {
  isNative: !!P,
  async loadAll() {
    if (P) { const r = await timeout(P.loadAll(), 4000, 'loadAll'); return { cfg: JSON.parse(r.cfg || '{}'), settings: JSON.parse(r.settings || '{}') }; }
    return { cfg: ls.get('cfg', {}), settings: ls.get('settings', {}) };
  },
  async saveCfg(c) { if (P) await P.saveCfg({ json: JSON.stringify(c) }); else ls.set('cfg', c); },
  async saveSettings(s) { if (P) await P.saveSettings({ json: JSON.stringify(s) }); else ls.set('settings', s); },
  /** PNG data-URL того же рендера, что и на рабочем столе. W,H — размер в dp. */
  async render(cfg, W, H) {
    if (!P) return null;
    const dpr = DPR();
    try {
      const r = await P.render({ cfg: JSON.stringify(cfg), width: Math.round(W * dpr), height: Math.round(H * dpr), density: dpr });
      return 'data:image/png;base64,' + r.png;
    } catch { return null; }
  },
  async refresh(force = false) { if (P) try { await P.refresh({ force }); } catch {} },
  async checkWeather(w) { if (!P) return '—'; try { return (await P.checkWeather({ weather: JSON.stringify(w) })).message; } catch (e) { return String(e); } },
  async saveBackground(b64) { if (P) await P.saveBackground({ data: b64 }); },
  async removeBackground() { if (P) await P.removeBackground(); },
  /** {supported, exists}: exists=true — виджет уже стоит, новый не создаётся, существующий обновляется. */
  async pinWidget() { return P ? await P.pinWidget() : { supported: false, exists: false }; },
  async widgetCount() { return P ? (await P.widgetCount()).count : 0; },
  async calendarState() { return P ? (await P.calendarState()).granted : false; },
  async calendarRequest() { return P ? (await P.calendarRequest()).granted : false; },
  async openAppSettings() { if (P) await P.openAppSettings(); },
  async openUrl(url) { if (P) await P.openUrl({ url }); else window.open(url, '_blank'); },
  async info() { return P ? await P.getInfo() : { version: 'web', build: '0' }; },
  async searchCity(query, lang) {
    if (P) { try { return JSON.parse((await P.searchCity({ query, lang })).results); } catch { return []; } }
    try {
      const j = await (await fetch(`https://geocoding-api.open-meteo.com/v1/search?count=6&language=${lang}&name=${encodeURIComponent(query)}`)).json();
      return (j.results || []).map((r) => ({ name: r.name, lat: r.latitude, lon: r.longitude, country: r.country, admin: r.admin1 }));
    } catch { return []; }
  },
};
