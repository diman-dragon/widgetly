export const rid = () => Math.random().toString(36).slice(2, 9);
export const CELL_W = 72, CELL_H = 84; // dp на ячейку: 5×3 ≈ 360×252 dp

export const FONTS = [
  ['sans-serif', 'Sans'], ['sans-serif-light', 'Light'], ['sans-serif-thin', 'Thin'], ['sans-serif-medium', 'Medium'],
  ['sans-serif-black', 'Black'], ['sans-serif-condensed', 'Condensed'], ['serif', 'Serif'], ['monospace', 'Mono'],
  ['casual', 'Casual'], ['cursive', 'Cursive'],
];
export const CLOCK_FMT = ['HH:mm', 'H:mm', 'hh:mm', 'h:mm', 'h:mm a', 'hh:mm a'];
export const DATE_FMT = ['EEEE, d MMMM', 'EEE, d MMM', 'EEEE d MMM', 'd MMMM', 'dd.MM.yyyy', 'EEEE', 'd MMM yyyy'];
export const BLOCK_TYPES = ['clock', 'date', 'weather', 'battery', 'alarm'];
export const TYPE_ICON = { clock: '🕒', date: '📅', weather: '⛅', battery: '🔋', alarm: '⏰' };
export const PRESET_LIST = [
  ['samsung', 'Samsung One UI'], ['apple', 'Apple'], ['htc', 'HTC Sense'], ['empty', '—'],
];

export function defaultSettings() {
  return { theme: 'system', city: { name: 'London', lat: 51.5085, lon: -0.1257 }, service: 'openmeteo', units: 'c', weatherHours: 6 };
}

function optFor(type, s) {
  switch (type) {
    case 'clock': return { fmt: 'HH:mm', tz: '' };
    case 'date': return { fmt: 'EEEE, d MMMM', upper: false };
    case 'weather': return { city: s.city.name, lat: s.city.lat, lon: s.city.lon, service: s.service, units: s.units, icon: true, showCity: true, showCond: true, showHiLo: false };
    case 'battery': return {};
    default: return {};
  }
}

export function newBlock(type, x, y, w, h, s, extra = {}) {
  const { opt, ...rest } = extra;
  return {
    id: rid(), type, x, y, w, h, color: '', scale: 1, align: 'left', font: 'sans-serif',
    bg: '', bgA: 30, radius: 12, divR: false, divB: false,
    ...rest, opt: { ...optFor(type, s), ...(opt || {}) },
  };
}

export const DEFAULT_SIZE = { clock: [3, 1], date: [3, 1], weather: [2, 2], battery: [2, 1], alarm: [1, 1] };

function base(name, o) {
  return {
    id: rid(), name, cols: 5, rows: 3, bg: '#15161A', bg2: '', bgA: 85, radius: 24, pad: 8, fg: '#FFFFFF',
    refresh: true, rc: 'tr',
    div: { on: false, color: '#FFFFFF', a: 30, wd: 1, style: 'solid', inset: 10 },
    blocks: [], ...o,
  };
}

export function newDesign(preset, s) {
  const B = (...a) => newBlock(...a.slice(0, 5), s, a[5] || {});
  switch (preset) {
    case 'samsung': // крупные часы слева, погода справа, дата и будильник снизу
      return base('Samsung One UI', {
        bg: '#15161A', bgA: 82, radius: 28,
        blocks: [
          B('clock', 0, 0, 3, 2, { font: 'sans-serif-light', opt: { fmt: 'h:mm' } }),
          B('date', 0, 2, 2, 1, { scale: 0.7, opt: { fmt: 'EEE, d MMM' } }),
          B('alarm', 2, 2, 1, 1, { scale: 0.7, align: 'right' }),
          B('weather', 3, 0, 2, 2, { align: 'center' }),
          B('battery', 3, 2, 2, 1, { align: 'center', opt: { list: false } }),
        ],
      });
    case 'apple': // синий градиент, погода крупно слева, дата и часы справа
      return base('Apple', {
        bg: '#3B82F6', bg2: '#1E3A8A', bgA: 100, radius: 30, pad: 12,
        blocks: [
          B('weather', 0, 0, 2, 3, { opt: { showHiLo: true } }),
          B('date', 2, 0, 3, 1, { align: 'right', scale: 0.7, font: 'sans-serif-medium', opt: { fmt: 'EEEE d MMM', upper: true } }),
          B('clock', 2, 1, 3, 2, { align: 'right', font: 'sans-serif-medium', opt: { fmt: 'H:mm' } }),
        ],
      });
    case 'htc': // классика Sense: часы по центру, снизу погода и дата, тонкие линии
      return base('HTC Sense', {
        bg: '#000000', bgA: 60, radius: 14,
        div: { on: true, color: '#FFFFFF', a: 35, wd: 1, style: 'solid', inset: 8 },
        blocks: [
          B('clock', 0, 0, 5, 2, { align: 'center', font: 'sans-serif-thin', divB: true, opt: { fmt: 'h:mm' } }),
          B('weather', 0, 2, 3, 1, { divR: true, opt: { showCond: false } }),
          B('date', 3, 2, 2, 1, { align: 'center', scale: 0.8, opt: { fmt: 'EEEE d MMM' } }),
        ],
      });
    default:
      return base('Widget', {});
  }
}
