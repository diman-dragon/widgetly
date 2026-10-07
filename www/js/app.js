import { native } from './native.js';
import { t, LANG } from './i18n.js';
import { FONTS, CLOCK_FMT, DATE_FMT, BLOCK_TYPES, TYPE_ICON, PRESET_LIST, DEFAULT_SIZE, CELL_W, CELL_H,
  defaultSettings, newBlock, newDesign, rid } from './presets.js';

const PRIVACY_URL = 'https://example.com/widget-studio/privacy'; // TODO: заменить на свой URL перед публикацией
const $ = (s, r = document) => r.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const S = { designs: [], settings: defaultSettings(), screen: 'home', curId: null, sel: null, tab: 'block' };
const ed = { rn: 0, rt: 0, cities: [] };
const app = $('#app'), sheet = $('#sheet');
const find = (id) => S.designs.find((d) => d.id === id);
const cur = () => find(S.curId);

// ---------- тема, сохранение, навигация ----------
function applyTheme() {
  const th = S.settings.theme;
  const dark = th === 'dark' || (th === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
}
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);

let pt;
function persist(now) {
  clearTimeout(pt);
  const run = async () => { await native.saveDesigns(S.designs); await native.refresh(false); };
  if (now) return run();
  pt = setTimeout(run, 500);
}
const saveSettings = async () => { await native.saveSettings(S.settings); await native.refresh(false); };

function nav(screen) { history.pushState({ s: screen, id: S.curId }, ''); S.screen = screen; show(); }
addEventListener('popstate', (e) => { const st = e.state || { s: 'home' }; S.screen = st.s; if (st.id) S.curId = st.id; show(); });
document.addEventListener('visibilitychange', () => { if (!document.hidden && S.screen === 'settings') Settings(); });
function show() { ({ home: Home, editor: Editor, settings: Settings }[S.screen] || Home)(); }

function openSheet(html) { sheet.innerHTML = `<div class="in">${html}</div>`; sheet.hidden = false; sheet.onclick = (e) => { if (e.target === sheet) closeSheet(); }; }
function closeSheet() { sheet.hidden = true; sheet.innerHTML = ''; sheet.onclick = null; }

async function thumb(img, d) {
  const u = await native.render(d, d.cols * CELL_W, d.rows * CELL_H);
  if (u) img.src = u;
  else { const m = document.createElement('div'); m.className = 'mu'; m.style.padding = '24px 8px'; m.textContent = d.name + ' — ' + t('previewOnly'); img.replaceWith(m); }
}

// ---------- ГЛАВНАЯ ----------
function Home() {
  app.innerHTML = `
  <header class="bar"><h1>Widget Studio</h1><button class="icon" id="gear" aria-label="${t('settings')}">⚙️</button></header>
  <main>
    <h2>${t('mine')}</h2>
    <section class="grid" id="list">${S.designs.map((d) => `
      <article class="card" data-id="${d.id}"><img class="thumb" alt="">
        <div class="row"><b>${esc(d.name)}</b></div>
        <div class="row">
          <button class="primary sm" data-a="edit">${t('edit')}</button>
          <button class="sm" data-a="pin">${t('pin')}</button>
          <button class="sm" data-a="dup">${t('dup')}</button>
          <button class="sm danger" data-a="del">${t('del')}</button>
        </div></article>`).join('')}</section>
    <button class="primary wide" id="new">＋ ${t('new')}</button>
    <h2>${t('placed')}</h2><div id="placed"></div>
  </main>`;
  $('#gear').onclick = () => nav('settings');
  $('#new').onclick = newSheet;
  app.querySelectorAll('.card').forEach((el) => thumb($('img', el), find(el.dataset.id)));
  $('#list').onclick = (e) => {
    const b = e.target.closest('button[data-a]');
    if (!b) return;
    const id = b.closest('.card').dataset.id, d = find(id);
    if (b.dataset.a === 'edit') openEditor(id);
    else if (b.dataset.a === 'pin') pinSheet(id);
    else if (b.dataset.a === 'dup') { const c = JSON.parse(JSON.stringify(d)); c.id = rid(); c.name += ' 2'; c.blocks.forEach((x) => (x.id = rid())); S.designs.push(c); persist(true); Home(); }
    else if (b.dataset.a === 'del' && confirm(t('confirmDel'))) { S.designs = S.designs.filter((x) => x.id !== id); persist(true); Home(); }
  };
  placed();
}

async function placed() {
  const ws = await native.getWidgets(), el = $('#placed');
  if (!el) return;
  if (!ws.length) { el.innerHTML = `<p class="mu">${t('noPlaced')}</p>`; return; }
  el.innerHTML = ws.map((w) => `<div class="card row" style="margin-bottom:8px"><b>#${w.id} · ${w.size}</b>
    <select data-w="${w.id}" aria-label="${t('assign')}">${S.designs.map((d) => `<option value="${d.id}" ${d.id === w.design ? 'selected' : ''}>${esc(d.name)}</option>`).join('')}</select></div>`).join('');
  el.onchange = (e) => { const s = e.target.closest('select[data-w]'); if (s) native.assignWidget(+s.dataset.w, s.value); };
}

function newSheet() {
  const list = PRESET_LIST.map(([id]) => ({ id, d: newDesign(id, S.settings) }));
  openSheet(`<h2>${t('presets')}</h2><div class="grid">${list.map((p) => `
    <div class="card" data-p="${p.id}"><img class="thumb" alt=""><div class="row"><b>${p.id === 'empty' ? t('new') : esc(p.d.name)}</b></div></div>`).join('')}</div>`);
  sheet.querySelectorAll('[data-p]').forEach((el, i) => thumb($('img', el), list[i].d));
  sheet.onclick = (e) => {
    if (e.target === sheet) return closeSheet();
    const c = e.target.closest('[data-p]');
    if (!c) return;
    const d = list.find((p) => p.id === c.dataset.p).d;
    S.designs.push(d); persist(true); closeSheet(); openEditor(d.id);
  };
}

function pinSheet(id) {
  openSheet(`<h2>${t('sizeQ')}</h2>${['5x3', '4x2', '2x2'].map((s) => `<button class="wide" data-s="${s}">${s.replace('x', '×')}</button>`).join('')}`);
  sheet.onclick = async (e) => {
    if (e.target === sheet) return closeSheet();
    const b = e.target.closest('[data-s]');
    if (!b) return;
    closeSheet();
    const ok = await native.pinWidget(id, b.dataset.s);
    if (!ok) alert(t('pinFail'));
  };
}

// ---------- РЕДАКТОР ----------
function openEditor(id) { S.curId = id; S.sel = null; S.tab = 'block'; nav('editor'); }

function Editor() {
  const d = cur();
  if (!d) { S.screen = 'home'; return Home(); }
  app.innerHTML = `
  <header class="bar"><button class="icon" id="back" aria-label="${t('back')}">←</button><input class="name" id="nm" value="${esc(d.name)}" aria-label="${t('name')}"></header>
  <div class="pvwrap"><div id="pv"><img id="pvimg" alt=""><div id="ov"></div></div></div>
  ${native.isNative ? '' : `<p class="mu" style="padding:0 14px">${t('previewOnly')}</p>`}
  <div class="add">${BLOCK_TYPES.map((k) => `<button data-add="${k}">${TYPE_ICON[k]} ${t(k)}</button>`).join('')}</div>
  <div class="tabs"><button data-tab="block">${t('block')}</button><button data-tab="design">${t('widget')}</button></div>
  <div class="panel" id="panel"></div>`;
  $('#back').onclick = () => history.back();
  $('#nm').oninput = (e) => { d.name = e.target.value; persist(); };
  app.querySelectorAll('[data-add]').forEach((b) => (b.onclick = () => addBlock(b.dataset.add)));
  app.querySelectorAll('[data-tab]').forEach((b) => (b.onclick = () => { S.tab = b.dataset.tab; syncTabs(); drawPanel(); }));
  const p = $('#panel');
  p.oninput = onField; p.onchange = onField; p.onclick = onAct;
  bindDrag(); syncTabs(); drawOv(); drawPanel(); renderPv();
}

const syncTabs = () => app.querySelectorAll('[data-tab]').forEach((b) => b.classList.toggle('on', b.dataset.tab === S.tab));

function addBlock(type) {
  const d = cur();
  if (type === 'clock') { // нативный виджет использует один блок часов (системный TextClock)
    const ex = d.blocks.find((b) => b.type === 'clock');
    if (ex) { S.sel = ex.id; S.tab = 'block'; syncTabs(); drawOv(); drawPanel(); return; }
  }
  const w = Math.min(DEFAULT_SIZE[type][0], d.cols), h = Math.min(DEFAULT_SIZE[type][1], d.rows);
  let pos = [0, 0];
  outer: for (let y = 0; y <= d.rows - h; y++) for (let x = 0; x <= d.cols - w; x++) {
    if (!d.blocks.some((b) => x < b.x + b.w && x + w > b.x && y < b.y + b.h && y + h > b.y)) { pos = [x, y]; break outer; }
  }
  const b = newBlock(type, pos[0], pos[1], w, h, S.settings);
  d.blocks.push(b); S.sel = b.id; S.tab = 'block'; syncTabs(); drawPanel(); touch();
}

function drawOv() {
  const d = cur(), W = d.cols * CELL_W, H = d.rows * CELL_H, cw = (W - 2 * d.pad) / d.cols, ch = (H - 2 * d.pad) / d.rows;
  $('#pv').style.aspectRatio = `${W} / ${H}`;
  const dot = d.refresh ? `<div class="rfdot" style="${d.rc[0] === 't' ? 'top' : 'bottom'}:4px;${d.rc[1] === 'l' ? 'left' : 'right'}:4px">↻</div>` : '';
  $('#ov').innerHTML = d.blocks.map((b) => `<div class="blk ${b.id === S.sel ? 'sel' : ''}" data-id="${b.id}" style="left:${(d.pad + b.x * cw) / W * 100}%;top:${(d.pad + b.y * ch) / H * 100}%;width:${b.w * cw / W * 100}%;height:${b.h * ch / H * 100}%"><i>${TYPE_ICON[b.type]}</i><u class="rs"></u></div>`).join('') + dot;
}

function bindDrag() {
  const ov = $('#ov');
  let drag = null;
  ov.onpointerdown = (e) => {
    const el = e.target.closest('.blk'), d = cur();
    if (!el) { if (S.sel) { S.sel = null; drawOv(); drawPanel(); } return; }
    const b = d.blocks.find((x) => x.id === el.dataset.id), changed = S.sel !== b.id;
    S.sel = b.id;
    if (S.tab !== 'block') { S.tab = 'block'; syncTabs(); }
    const k = $('#pv').getBoundingClientRect().width / (d.cols * CELL_W);
    drag = { id: b.id, mode: e.target.closest('.rs') ? 'rs' : 'mv', sx: e.clientX, sy: e.clientY, o: { x: b.x, y: b.y, w: b.w, h: b.h },
      cw: (d.cols * CELL_W - 2 * d.pad) / d.cols * k, ch: (d.rows * CELL_H - 2 * d.pad) / d.rows * k, moved: false };
    ov.setPointerCapture(e.pointerId);
    drawOv(); if (changed) drawPanel();
    e.preventDefault();
  };
  ov.onpointermove = (e) => {
    if (!drag) return;
    const d = cur(), b = d.blocks.find((x) => x.id === drag.id);
    const dx = Math.round((e.clientX - drag.sx) / drag.cw), dy = Math.round((e.clientY - drag.sy) / drag.ch);
    if (drag.mode === 'mv') { b.x = clamp(drag.o.x + dx, 0, d.cols - b.w); b.y = clamp(drag.o.y + dy, 0, d.rows - b.h); }
    else { b.w = clamp(drag.o.w + dx, 1, d.cols - b.x); b.h = clamp(drag.o.h + dy, 1, d.rows - b.y); }
    if (dx || dy) drag.moved = true;
    drawOv();
  };
  ov.onpointerup = ov.onpointercancel = () => { if (drag && drag.moved) { touch(); drawPanel(); } drag = null; };
}

function touch() {
  drawOv();
  clearTimeout(ed.rt);
  ed.rt = setTimeout(renderPv, 90);
  persist();
}
async function renderPv() {
  const d = cur();
  if (!d) return;
  const n = ++ed.rn, u = await native.render(d, d.cols * CELL_W, d.rows * CELL_H), im = $('#pvimg');
  if (n === ed.rn && im && u) im.src = u;
}

// ---------- панели ----------
const f = (label, inner) => `<label class="f"><span>${label}</span>${inner}</label>`;
const num = (sc, k, v, min, max, step = 1) => `<input data-${sc}="${k}" type="number" inputmode="decimal" min="${min}" max="${max}" step="${step}" value="${v}">`;
const rng = (sc, k, v, min, max, step = 1) => `<input data-${sc}="${k}" type="range" min="${min}" max="${max}" step="${step}" value="${v}">`;
const clr = (sc, k, v) => `<input data-${sc}="${k}" type="color" value="${esc(v)}">`;
const chk = (sc, k, v, label) => `<label class="chk"><input type="checkbox" data-${sc}="${k}" ${v ? 'checked' : ''}> ${label}</label>`;
const sel = (sc, k, v, opts) => `<select data-${sc}="${k}">${opts.map(([val, l]) => `<option value="${esc(val)}" ${val === v ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select>`;
const seg = (act, cur_, opts) => `<div class="seg">${opts.map(([v, l]) => `<button data-act="${act}" data-v="${v}" class="${v === cur_ ? 'on' : ''}">${l}</button>`).join('')}</div>`;

function drawPanel() { const d = cur(); $('#panel').innerHTML = S.tab === 'design' ? designPanel(d) : blockPanel(d); }

function blockPanel(d) {
  const b = d.blocks.find((x) => x.id === S.sel);
  if (!b) return `<p class="mu">${t('selectBlock')}</p>`;
  const o = b.opt;
  let h = `<div class="row" style="margin-top:0"><b>${TYPE_ICON[b.type]} ${t(b.type)}</b>
    ${b.type === 'clock' ? '' : `<button class="sm" data-act="dup">${t('dup2')}</button>`}<button class="sm danger" data-act="rm">${t('remove')}</button></div>
    ${f(t('x'), num('k', 'x', b.x, 0, d.cols - 1))}${f(t('y'), num('k', 'y', b.y, 0, d.rows - 1))}
    ${f(t('w'), num('k', 'w', b.w, 1, d.cols))}${f(t('h'), num('k', 'h', b.h, 1, d.rows))}
    ${f(t('font'), sel('k', 'font', b.font, FONTS))}
    ${f(t('color'), `${clr('k', 'color', b.color || d.fg)}<button class="sm" data-act="clrc">${t('reset')}</button>`)}
    ${f(t('size'), rng('k', 'scale', b.scale, 0.3, 1, 0.05))}
    ${f(t('align'), seg('al', b.align, [['left', t('left')], ['center', t('center')], ['right', t('right')]]))}`;
  if (b.type === 'clock') h += f(t('fmt'), sel('o', 'fmt', o.fmt, CLOCK_FMT.map((x) => [x, x]))) + f(t('tz'), `<input data-o="tz" value="${esc(o.tz)}" placeholder="Europe/Berlin">`);
  if (b.type === 'date') h += f(t('fmt'), `<input data-o="fmt" list="dfl" value="${esc(o.fmt)}"><datalist id="dfl">${DATE_FMT.map((x) => `<option value="${x}">`).join('')}</datalist>`) + chk('o', 'upper', o.upper, t('upper'));
  if (b.type === 'weather') {
    h += f(t('city'), `<input id="cityq" value="${esc(o.city)}"><button class="sm" data-act="city">${t('search')}</button>`) + `<div id="cityres" class="res"></div>`
      + f(t('service'), sel('o', 'service', o.service, [['openmeteo', 'Open-Meteo'], ['metno', 'MET Norway']]))
      + f(t('units'), sel('o', 'units', o.units, [['c', '°C'], ['f', '°F']]))
      + chk('o', 'icon', o.icon, t('showIcon')) + chk('o', 'showCity', o.showCity, t('showCity'))
      + chk('o', 'showCond', o.showCond, t('showCond')) + chk('o', 'showHiLo', o.showHiLo, t('showHiLo'));
  }
  h += `<h2 style="margin-top:14px">${t('blockBg')}</h2>${chk('k', 'bgOn', !!b.bg, t('blockBg'))}`;
  if (b.bg) h += f(t('color'), clr('k', 'bg', b.bg)) + f(t('blockBgA'), rng('k', 'bgA', b.bgA, 0, 100)) + f(t('radius'), rng('k', 'radius', b.radius, 0, 40));
  h += `<h2 style="margin-top:14px">${t('dividers')}</h2>${chk('k', 'divR', b.divR, t('divR'))}${chk('k', 'divB', b.divB, t('divB'))}`;
  return h;
}

function designPanel(d) {
  const v = d.div;
  return `${f(t('cols'), num('d', 'cols', d.cols, 1, 8))}${f(t('rows'), num('d', 'rows', d.rows, 1, 8))}
    ${f(t('bg'), clr('d', 'bg', d.bg))}${chk('d', 'bg2On', !!d.bg2, t('bg2'))}${d.bg2 ? f(t('bg2'), clr('d', 'bg2', d.bg2)) : ''}
    ${f(t('bgA'), rng('d', 'bgA', d.bgA, 0, 100))}${f(t('radius'), rng('d', 'radius', d.radius, 0, 60))}
    ${f(t('pad'), rng('d', 'pad', d.pad, 0, 24))}${f(t('fg'), clr('d', 'fg', d.fg))}
    <h2 style="margin-top:14px">${t('dividers')}</h2>${chk('v', 'on', v.on, t('divOn'))}
    ${f(t('divColor'), clr('v', 'color', v.color))}${f(t('divA'), rng('v', 'a', v.a, 0, 100))}${f(t('divW'), rng('v', 'wd', v.wd, 1, 6, 0.5))}
    ${f(t('divStyle'), sel('v', 'style', v.style, [['solid', t('solid')], ['dashed', t('dashed')], ['dotted', t('dotted')]]))}
    ${f(t('divInset'), rng('v', 'inset', v.inset, 0, 40))}<p class="mu">${t('divHint')}</p>
    <h2 style="margin-top:14px">${t('refreshBtn')}</h2>${chk('d', 'refresh', d.refresh, t('refreshBtn'))}
    ${f(t('corner'), sel('d', 'rc', d.rc, [['tl', t('tl')], ['tr', t('tr')], ['bl', t('bl')], ['br', t('br')]]))}`;
}

const GEO = ['cols', 'rows', 'x', 'y', 'w', 'h'];
function onField(e) {
  const el = e.target, sc = ['k', 'o', 'd', 'v'].find((s) => el.dataset[s] !== undefined);
  if (!sc) return;
  const key = el.dataset[sc], d = cur(), b = d.blocks.find((x) => x.id === S.sel);
  if (GEO.includes(key) && sc !== 'o' && e.type !== 'change') return; // числа применяем по завершении ввода
  let val = el.type === 'checkbox' ? el.checked : (el.type === 'number' || el.type === 'range') ? Number(el.value) : el.value;
  if (typeof val === 'number' && isNaN(val)) return;
  const tgt = sc === 'k' ? b : sc === 'o' ? b && b.opt : sc === 'd' ? d : d.div;
  if (!tgt) return;
  let redraw = false;
  if (key === 'bgOn') { b.bg = val ? '#FFFFFF' : ''; redraw = true; }
  else if (key === 'bg2On') { d.bg2 = val ? '#1E3A8A' : ''; redraw = true; }
  else tgt[key] = val;
  if (sc === 'd' && (key === 'cols' || key === 'rows')) {
    d.cols = clamp(Math.round(d.cols), 1, 8); d.rows = clamp(Math.round(d.rows), 1, 8);
    d.blocks.forEach((x) => { x.w = clamp(x.w, 1, d.cols); x.h = clamp(x.h, 1, d.rows); x.x = clamp(x.x, 0, d.cols - x.w); x.y = clamp(x.y, 0, d.rows - x.h); });
    redraw = true;
  }
  if (sc === 'k' && GEO.includes(key)) {
    b.w = clamp(Math.round(b.w), 1, d.cols); b.h = clamp(Math.round(b.h), 1, d.rows);
    b.x = clamp(Math.round(b.x), 0, d.cols - b.w); b.y = clamp(Math.round(b.y), 0, d.rows - b.h);
    redraw = true;
  }
  if (redraw) drawPanel();
  touch();
}

async function onAct(e) {
  const bt = e.target.closest('[data-act]');
  if (!bt) return;
  const act = bt.dataset.act, d = cur(), b = d.blocks.find((x) => x.id === S.sel);
  if (act === 'rm' && b) { d.blocks = d.blocks.filter((x) => x !== b); S.sel = null; drawPanel(); touch(); }
  else if (act === 'dup' && b) {
    const c = JSON.parse(JSON.stringify(b)); c.id = rid(); c.x = clamp(c.x + 1, 0, d.cols - c.w); c.y = clamp(c.y + (c.x === b.x ? 1 : 0), 0, d.rows - c.h);
    d.blocks.push(c); S.sel = c.id; drawPanel(); touch();
  }
  else if (act === 'al' && b) { b.align = bt.dataset.v; drawPanel(); touch(); }
  else if (act === 'clrc' && b) { b.color = ''; drawPanel(); touch(); }
  else if (act === 'city' && b) {
    const q = $('#cityq').value.trim();
    if (!q) return;
    ed.cities = await native.searchCity(q, LANG);
    $('#cityres').innerHTML = ed.cities.length
      ? ed.cities.map((c, i) => `<button data-act="pick" data-i="${i}">${esc([c.name, c.admin, c.country].filter(Boolean).join(', '))}</button>`).join('')
      : `<p class="mu">${t('nothing')}</p>`;
  }
  else if (act === 'pick' && b) {
    const c = ed.cities[+bt.dataset.i];
    Object.assign(b.opt, { city: c.name, lat: c.lat, lon: c.lon });
    drawPanel(); touch();
  }
}

// ---------- НАСТРОЙКИ (шестерёнка) ----------
async function Settings() {
  const s = S.settings, info = await native.info();
  app.innerHTML = `
  <header class="bar"><button class="icon" id="back" aria-label="${t('back')}">←</button><h1>${t('settings')}</h1></header>
  <main>
    <div class="panel">${f(t('theme'), sel('s', 'theme', s.theme, [['system', t('sys')], ['light', t('light')], ['dark', t('dark')]]))}</div>
    <h2>${t('wDefaults')}</h2>
    <div class="panel">
      ${f(t('city'), `<input id="cityq" value="${esc(s.city.name)}"><button class="sm" data-act="city">${t('search')}</button>`)}<div id="cityres" class="res"></div>
      ${f(t('service'), sel('s', 'service', s.service, [['openmeteo', 'Open-Meteo'], ['metno', 'MET Norway']]))}
      ${f(t('units'), sel('s', 'units', s.units, [['c', '°C'], ['f', '°F']]))}
      ${f(t('interval'), sel('s', 'weatherHours', String(s.weatherHours), ['1', '3', '6', '12'].map((x) => [x, x])))}
      <p class="mu">${t('wNew')}</p>
    </div>
    <h2>${t('updates')}</h2><div class="panel"><p class="mu" style="margin:0">${t('updatesText')}</p></div>
    <h2>${t('about')}</h2>
    <div class="panel">
      <div class="f"><span>${t('version')}</span><b>${esc(info.version)} (${esc(info.build)})</b></div>
      <div class="row"><button class="sm" data-act="priv">${t('privacy')}</button><button class="sm danger" data-act="wipe">${t('wipe')}</button></div>
    </div>
  </main>`;
  $('#back').onclick = () => history.back();
  const root = $('main');
  root.onchange = async (e) => {
    const el = e.target, k = el.dataset.s;
    if (!k) return;
    s[k] = k === 'weatherHours' ? Number(el.value) : el.value;
    if (k === 'theme') applyTheme();
    await saveSettings();
  };
  root.onclick = async (e) => {
    const bt = e.target.closest('[data-act]');
    if (!bt) return;
    if (bt.dataset.act === 'priv') native.openUrl(PRIVACY_URL);
    else if (bt.dataset.act === 'city') {
      const q = $('#cityq').value.trim();
      if (!q) return;
      ed.cities = await native.searchCity(q, LANG);
      $('#cityres').innerHTML = ed.cities.length
        ? ed.cities.map((c, i) => `<button data-act="pick" data-i="${i}">${esc([c.name, c.admin, c.country].filter(Boolean).join(', '))}</button>`).join('')
        : `<p class="mu">${t('nothing')}</p>`;
    } else if (bt.dataset.act === 'pick') {
      const c = ed.cities[+bt.dataset.i];
      s.city = { name: c.name, lat: c.lat, lon: c.lon };
      await saveSettings(); Settings();
    } else if (bt.dataset.act === 'wipe' && confirm(t('confirmWipe'))) {
      S.settings = defaultSettings(); S.designs = [newDesign('samsung', S.settings)];
      applyTheme(); await native.saveSettings(S.settings); await persist(true); history.back();
    }
  };
}

// ---------- старт ----------
(async function init() {
  try {
    const r = await native.loadAll();
    S.designs = Array.isArray(r.designs) ? r.designs : [];
    S.designs = S.designs.filter((d) => d && Array.isArray(d.blocks)).map((d) => ({
      ...d,
      blocks: d.blocks.filter(Boolean),
    }));
    S.designs.forEach((d) => d.blocks.forEach((b) => { if (b.type === 'notif') { b.type = 'battery'; b.opt = {}; } }));
    S.settings = { ...defaultSettings(), ...(r.settings && typeof r.settings === 'object' ? r.settings : {}) };
    applyTheme();
    if (!S.designs.length) { S.designs.push(newDesign('samsung', S.settings)); await persist(true); }
    history.replaceState({ s: 'home' }, '');
    native.onOpenDesign((id) => find(id) && openEditor(id));
    const id = await native.getLaunchDesign();
    if (id && find(id)) openEditor(id); else Home();
  } catch (e) {
    console.error('Widget Studio init failed', e);
    app.innerHTML = `<div style="padding:24px;color:var(--danger,#d93b3b);background:var(--card,#1a1b20);border-radius:12px;margin:16px;"><h3>Startup Error</h3><pre style="white-space:pre-wrap;word-break:break-all;">${esc(e.stack || e.message || e)}</pre></div>`;
    S.designs = [newDesign('samsung', defaultSettings())];
    S.settings = defaultSettings();
    history.replaceState({ s: 'home' }, '');
    setTimeout(Home, 2000);
  }
})();
