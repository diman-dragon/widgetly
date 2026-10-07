// Мост к нативной части. В обычном браузере (npm run web) работает заглушка на localStorage.
const cap = window.Capacitor;
const P = cap && cap.isNativePlatform && cap.isNativePlatform() ? cap.registerPlugin('WidgetBridge') : null;
const ls = {
  get: (k, d) => { try { return JSON.parse(localStorage.getItem(k)) ?? d; } catch { return d; } },
  set: (k, v) => localStorage.setItem(k, JSON.stringify(v)),
};
const DPR = () => Math.min(window.devicePixelRatio || 2, 3);

export const native = {
  isNative: !!P,
  async loadAll() {
    if (P) { const r = await P.loadAll(); return { designs: JSON.parse(r.designs || '[]'), settings: JSON.parse(r.settings || '{}') }; }
    return { designs: ls.get('designs', []), settings: ls.get('settings', {}) };
  },
  async saveDesigns(d) { if (P) await P.saveDesigns({ json: JSON.stringify(d) }); else ls.set('designs', d); },
  async saveSettings(s) { if (P) await P.saveSettings({ json: JSON.stringify(s) }); else ls.set('settings', s); },
  /** PNG data-URL того же рендера, что и на рабочем столе. W,H — размер в dp. */
  async render(design, W, H) {
    if (!P) return null;
    const dpr = DPR();
    try {
      const r = await P.render({ design: JSON.stringify(design), width: Math.round(W * dpr), height: Math.round(H * dpr), density: dpr });
      return 'data:image/png;base64,' + r.png;
    } catch { return null; }
  },
  async refresh(force = false) { if (P) try { await P.refresh({ force }); } catch {} },
  async getWidgets() { if (!P) return []; try { return JSON.parse((await P.getWidgets()).widgets); } catch { return []; } },
  async assignWidget(widgetId, designId) { if (P) await P.assignWidget({ widgetId, designId }); },
  async pinWidget(designId, size) { return P ? (await P.pinWidget({ designId, size })).supported : false; },
  async notifAccess() { return P ? (await P.notifAccess()).granted : false; },
  async openNotifAccess() { if (P) await P.openNotifAccess(); },
  async openUrl(url) { if (P) await P.openUrl({ url }); else window.open(url, '_blank'); },
  async info() { return P ? await P.getInfo() : { version: 'web', build: '0', appId: 'web' }; },
  async getLaunchDesign() { if (!P) return ''; try { return (await P.getLaunchDesign()).id; } catch { return ''; } },
  onOpenDesign(cb) { if (P) P.addListener('openDesign', (e) => cb(e.id)); },
  async searchCity(query, lang) {
    if (P) { try { return JSON.parse((await P.searchCity({ query, lang })).results); } catch { return []; } }
    try {
      const j = await (await fetch(`https://geocoding-api.open-meteo.com/v1/search?count=6&language=${lang}&name=${encodeURIComponent(query)}`)).json();
      return (j.results || []).map((r) => ({ name: r.name, lat: r.latitude, lon: r.longitude, country: r.country, admin: r.admin1 }));
    } catch { return []; }
  },
};
