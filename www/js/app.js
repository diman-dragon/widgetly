import { native } from './native.js';
import { t, LANG } from './i18n.js';
import { FONTS, CLOCK_FMT, DATE_FMT, WEEKDAY_FMT, PREVIEW, SERVICES, KEY_URL, defaultCfg, defaultSettings, merge } from './defaults.js';

const PRIVACY_URL = 'https://example.com/widget-studio/privacy'; // TODO: свой URL перед публикацией
const $ = (s, r = document) => r.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const S = { cfg: defaultCfg(), settings: defaultSettings(), screen: 'home', open: new Set(['look']), cities: [], hasPhoto: false, count: 0, calGranted: false };
const app = $('#app');

// ---------- хранение / тема / навигация ----------
function applyTheme() {
  const th = S.settings.theme;
  const dark = th === 'dark' || (th === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
}
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);

let pt;
function persist() {
  clearTimeout(pt);
  pt = setTimeout(async () => { try { await native.saveCfg(S.cfg); await native.refresh(); } catch (e) { window.showErr && showErr('Сохранение: ' + (e.message || e)); } }, 500);
}
let rt, rn = 0;
function touch() {
  clearTimeout(rt);
  rt = setTimeout(renderPv, 90);
  persist();
}
async function renderPv() {
  const [W, H] = PREVIEW[S.settings.preview] || PREVIEW['5x3'];
  const n = ++rn, u = await native.render(S.cfg, W, H), im = $('#pvimg');
  if (n === rn && im && u) im.src = u;
}

function nav(screen) { history.pushState({ s: screen }, ''); S.screen = screen; show(); }
addEventListener('popstate', (e) => { S.screen = (e.state && e.state.s) || 'home'; show(); });
const show = () => (S.screen === 'settings' ? Settings() : Home());

// ---------- помощники для полей (путь вида "clock.fmt") ----------
const get = (p) => p.split('.').reduce((o, k) => (o == null ? o : o[k]), S.cfg);
const set = (p, v) => { const a = p.split('.'), l = a.pop(); a.reduce((o, k) => o[k], S.cfg)[l] = v; };
const f = (label, inner) => `<label class="f"><span>${label}</span>${inner}</label>`;
const rng = (p, min, max, step = 1) => `<input data-p="${p}" type="range" min="${min}" max="${max}" step="${step}" value="${get(p)}">`;
const clr = (p, fallback) => `<input data-p="${p}" type="color" value="${esc(get(p) || get(fallback) || '#ffffff')}">`;
const chk = (p, label) => `<label class="chk"><input type="checkbox" data-p="${p}" ${get(p) ? 'checked' : ''}> ${label}</label>`;
const txt = (p, ph = '') => `<input data-p="${p}" value="${esc(get(p))}" placeholder="${esc(ph)}">`;
const sel = (p, opts) => `<select data-p="${p}">${opts.map(([v, l]) => `<option value="${esc(v)}" ${String(v) === String(get(p)) ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select>`;
const sec = (id, title, body) => `<details class="sec" data-sec="${id}" ${S.open.has(id) ? 'open' : ''}><summary>${title}</summary><div>${body}</div></details>`;
const colorRow = (p, fb) => f(t('color'), `${clr(p, fb)}<button class="sm" data-act="clr" data-p="${p}">${t('reset')}</button>`);
const fontOpts = FONTS.map(([v, l]) => [v, l]);

// ---------- главный экран ----------
function Home() {
  app.innerHTML = `
  <header class="bar"><div class="brand"><img class="logo" src="img/datalaw-logo.png" alt=""><h1>DataLaw</h1></div><button class="icon" id="gear" aria-label="${t('settings')}">⚙️</button></header>
  <div class="pvwrap"><div id="pv"><img id="pvimg" alt=""></div></div>
  <div class="chips">${Object.keys(PREVIEW).map((k) => `<button data-prev="${k}" class="${k === S.settings.preview ? 'on' : ''}">${k.replace('x', '×')}</button>`).join('')}</div>
  ${native.isNative ? '' : `<p class="mu" style="padding:0 14px">${t('previewOnly')}</p>`}
  <main>
    <button class="primary wide" id="pin" style="margin-top:8px"></button>
    <p class="msg" id="msg"></p>
    <div id="secs"></div>
  </main>
  <input type="file" id="file" accept="image/*" hidden>`;
  $('#gear').onclick = () => nav('settings');
  $('#pin').onclick = async () => {
    const r = await native.pinWidget();
    if (r.exists) msg(t('updated'));
    else if (!r.supported) alert(t('pinFail'));
    await refreshState();
  };
  app.querySelectorAll('[data-prev]').forEach((b) => (b.onclick = () => {
    S.settings.preview = b.dataset.prev; native.saveSettings(S.settings);
    app.querySelectorAll('[data-prev]').forEach((x) => x.classList.toggle('on', x === b));
    setPvRatio(); renderPv();
  }));
  const secs = $('#secs');
  secs.oninput = secs.onchange = onField;
  secs.onclick = onAct;
  secs.addEventListener('toggle', (e) => { const d = e.target; if (d.dataset && d.dataset.sec) d.open ? S.open.add(d.dataset.sec) : S.open.delete(d.dataset.sec); }, true);
  $('#file').onchange = (e) => e.target.files[0] && pickPhoto(e.target.files[0]);
  drawSecs(); setPvRatio(); renderPv(); pinLabel(); refreshState();
}

function pinLabel() { const b = $('#pin'); if (b) b.textContent = S.count > 0 ? `↻ ${t('updateWidget')}` : `＋ ${t('addHome')}`; }
function msg(text) { const m = $('#msg'); if (!m) return; m.textContent = text; setTimeout(() => { if (m.textContent === text) m.textContent = ''; }, 3500); }
// Число виджетов на рабочем столе и состояние доступа к календарю (после возврата из системных настроек — тоже)
async function refreshState() {
  const [n, g] = await Promise.all([native.widgetCount(), native.calendarState()]);
  const changed = g !== S.calGranted;
  S.count = n; S.calGranted = g; pinLabel();
  if (changed && S.screen === 'home' && $('#secs')) { drawSecs(); renderPv(); }
}
document.addEventListener('visibilitychange', () => { if (!document.hidden && S.screen === 'home') refreshState(); });

function setPvRatio() { const [W, H] = PREVIEW[S.settings.preview] || PREVIEW['5x3']; $('#pv').style.aspectRatio = `${W} / ${H}`; }

function drawSecs() {
  const w = S.cfg.weather, keyed = w.service === 'owm' || w.service === 'weatherapi';
  $('#secs').innerHTML =
    sec('look', t('look'),
      f(t('opacity'), rng('opacity', 0, 100)) + f(t('radius'), rng('radius', 0, 60)) + chk('border', t('border')) +
      f(t('bgMode'), sel('bgMode', [['sunset', t('sunset')], ['night', t('night')], ...(S.hasPhoto ? [['photo', t('photo')]] : []), ['none', t('none')]])) +
      `<div class="row"><button class="sm" data-act="photo">${t('pickPhoto')}</button>${S.cfg.bgMode === 'photo' ? `<button class="sm danger" data-act="rmphoto">${t('rmPhoto')}</button>` : ''}</div>` +
      f(t('dim'), rng('dim', 0, 70)) + f(t('split'), rng('split', 35, 60)) +
      f(t('panel'), clr('panel')) + f(t('panel2'), clr('panel2')) + f(t('fg'), clr('fg')) + f(t('fg2'), clr('fg2')) +
      chk('refresh', t('refreshBtn'))) +
    sec('clock', t('clock'),
      chk('clock.show', t('show')) + f(t('fmt'), sel('clock.fmt', CLOCK_FMT.map((x) => [x, x]))) + f(t('font'), sel('clock.font', fontOpts)) +
      f(t('size'), rng('clock.scale', 0.4, 1, 0.05)) + colorRow('clock.color', 'fg') + f(t('tz'), txt('clock.tz', 'Europe/Berlin'))) +
    sec('weekday', t('weekday'),
      chk('weekday.show', t('show')) + f(t('fmt'), sel('weekday.pattern', WEEKDAY_FMT.map((x) => [x, x]))) + f(t('font'), sel('weekday.font', fontOpts)) +
      f(t('size'), rng('weekday.scale', 0.4, 1, 0.05)) + colorRow('weekday.color', 'fg') + chk('weekday.upper', t('upper'))) +
    sec('date', t('date'),
      chk('date.show', t('show')) +
      f(t('fmt'), `<input data-p="date.pattern" list="dfl" value="${esc(S.cfg.date.pattern)}"><datalist id="dfl">${DATE_FMT.map((x) => `<option value="${x}">`).join('')}</datalist>`) +
      f(t('font'), sel('date.font', fontOpts)) + f(t('size'), rng('date.scale', 0.4, 1, 0.05)) + colorRow('date.color', 'fg')) +
    sec('calendar', t('calendar'),
      chk('calendar.show', t('show')) +
      (native.isNative && !S.calGranted ? `<p class="mu">${t('calNeed')}</p><div class="row" style="margin-top:0"><button class="sm primary" data-act="calgrant">${t('calGrant')}</button></div>` : '') +
      f(t('calDays'), sel('calendar.days', [[1, t('calToday')], [2, '2 ' + t('dayShort')], [3, '3 ' + t('dayShort')], [7, '7 ' + t('dayShort')]])) +
      f(t('font'), sel('calendar.font', fontOpts)) + f(t('size'), rng('calendar.scale', 0.4, 1, 0.05)) + colorRow('calendar.color', 'fg') +
      `<p class="mu">${t('calHint')}</p>`) +
    sec('weather', t('weather'),
      chk('weather.show', t('show')) +
      f(t('service'), sel('weather.service', SERVICES)) +
      (keyed ? f(t('apiKey'), txt('weather.apiKey', 'API key')) +
        `<p class="mu">${t('keyHint')}${KEY_URL[w.service]}</p>` : '') +
      `<div class="row" style="margin-top:0"><button class="sm" data-act="check">${t('check')}</button><span class="mu" id="chk"></span></div>` +
      f(t('city'), `<input id="cityq" value="${esc(w.city)}"><button class="sm" data-act="city">${t('search')}</button>`) + `<div id="cityres" class="res"></div>` +
      f(t('units'), sel('weather.units', [['c', '°C'], ['f', '°F']])) +
      f(t('days'), sel('weather.days', [[0, '0'], [1, '1'], [2, '2'], [3, '3']])) +
      f(t('hours'), sel('weather.hours', [[6, '6'], [12, '12'], [24, '24']])) +
      chk('weather.showCountry', t('showCountry')) + chk('weather.showCond', t('showCond')) + chk('weather.showHiLo', t('showHiLo')) +
      f(t('font'), sel('weather.font', fontOpts)) + f(t('size'), rng('weather.scale', 0.4, 1, 0.05)) +
      `<p class="mu">${t('weatherNote')}</p>`);
}

const NUM = new Set(['opacity', 'radius', 'dim', 'split', 'clock.scale', 'weekday.scale', 'date.scale', 'weather.scale', 'weather.days', 'weather.hours', 'calendar.scale', 'calendar.days']);
function onField(e) {
  const el = e.target, p = el.dataset.p;
  if (!p) return;
  let v = el.type === 'checkbox' ? el.checked : NUM.has(p) ? Number(el.value) : el.value;
  set(p, v);
  if (p === 'bgMode' || p === 'weather.service') drawSecs();
  touch();
}

async function citySearch(q) {
  S.cities = await native.searchCity(q, LANG);
  return S.cities.length
    ? S.cities.map((c, i) => `<button data-act="pick" data-i="${i}">${esc([c.name, c.admin, c.country].filter(Boolean).join(', '))}</button>`).join('')
    : `<p class="mu">${t('nothing')}</p>`;
}

async function onAct(e) {
  const bt = e.target.closest('[data-act]');
  if (!bt) return;
  const act = bt.dataset.act;
  if (act === 'clr') { set(bt.dataset.p, ''); drawSecs(); touch(); }
  else if (act === 'calgrant') {
    const ok = await native.calendarRequest();
    S.calGranted = ok;
    if (!ok && confirm(t('calDenied'))) native.openAppSettings();
    drawSecs(); renderPv(); native.refresh(true);
  }
  else if (act === 'photo') $('#file').click();
  else if (act === 'rmphoto') { await native.removeBackground(); S.hasPhoto = false; S.cfg.bgMode = 'sunset'; S.cfg.photoRev = Date.now(); drawSecs(); touch(); }
  else if (act === 'check') { $('#chk').textContent = '…'; $('#chk').textContent = await native.checkWeather(S.cfg.weather); renderPv(); }
  else if (act === 'city') { const q = $('#cityq').value.trim(); if (q) $('#cityres').innerHTML = await citySearch(q); }
  else if (act === 'pick') {
    const c = S.cities[+bt.dataset.i];
    Object.assign(S.cfg.weather, { city: c.name, country: c.country || '', lat: c.lat, lon: c.lon });
    drawSecs(); touch();
  }
}

// своё фото для левой части: уменьшаем до 1200 px и отправляем в нативное хранилище
async function pickPhoto(file) {
  const url = URL.createObjectURL(file), img = new Image();
  await new Promise((r) => { img.onload = r; img.src = url; });
  const k = Math.min(1, 1200 / Math.max(img.width, img.height)), c = document.createElement('canvas');
  c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
  c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
  URL.revokeObjectURL(url);
  await native.saveBackground(c.toDataURL('image/jpeg', 0.85).split(',')[1]);
  S.hasPhoto = true; S.cfg.bgMode = 'photo'; S.cfg.photoRev = Date.now();
  $('#file').value = '';
  drawSecs(); touch();
}

// ---------- настройки (шестерёнка) ----------
async function Settings() {
  const info = await native.info();
  app.innerHTML = `
  <header class="bar"><button class="icon" id="back" aria-label="${t('back')}">←</button><h1>${t('settings')}</h1></header>
  <main>
    <div class="panel">${f(t('theme'), `<select data-s="theme">${[['system', t('sys')], ['light', t('light')], ['dark', t('dark')]].map(([v, l]) => `<option value="${v}" ${v === S.settings.theme ? 'selected' : ''}>${l}</option>`).join('')}</select>`)}</div>
    <h2>${t('updates')}</h2><div class="panel"><p class="mu" style="margin:0">${t('updatesText')}</p></div>
    <h2>${t('about')}</h2>
    <div class="panel">
      <div class="aboutlogo"><img class="logo" src="img/datalaw-logo.png" alt=""><b>DataLaw</b></div>
      <div class="f"><span>${t('version')}</span><b>${esc(info.version)} (${esc(info.build)})</b></div>
      <div class="row"><button class="sm" data-act="priv">${t('privacy')}</button><button class="sm danger" data-act="wipe">${t('wipe')}</button></div>
      <p class="mu">© ${new Date().getFullYear()} DataLaw. ${t('rights')}</p>
    </div>
  </main>`;
  $('#back').onclick = () => history.back();
  const root = $('main');
  root.onchange = async (e) => {
    if (e.target.dataset.s !== 'theme') return;
    S.settings.theme = e.target.value; applyTheme(); await native.saveSettings(S.settings);
  };
  root.onclick = async (e) => {
    const bt = e.target.closest('[data-act]');
    if (!bt) return;
    if (bt.dataset.act === 'priv') native.openUrl(PRIVACY_URL);
    else if (bt.dataset.act === 'wipe' && confirm(t('confirmWipe'))) {
      await native.removeBackground();
      S.cfg = defaultCfg(); S.settings = defaultSettings(); S.hasPhoto = false;
      applyTheme(); await native.saveSettings(S.settings); await native.saveCfg(S.cfg); await native.refresh(); history.back();
    }
  };
}

// ---------- старт ----------
// Интерфейс показываем сразу (с настройками по умолчанию), сохранённые данные подгружаем следом.
(async function init() {
  applyTheme();
  history.replaceState({ s: 'home' }, '');
  Home();
  window.__wsReady = true;
  try {
    const r = await native.loadAll();
    S.cfg = merge(defaultCfg(), r.cfg);
    S.settings = { ...defaultSettings(), ...r.settings };
    S.hasPhoto = S.cfg.bgMode === 'photo';
    applyTheme();
    if (S.screen === 'home') Home();
    persist();
  } catch (e) {
    window.showErr && showErr('Загрузка настроек: ' + (e.message || e));
  }
})();
