export const FONT_GROUPS = [['sans', 'Без засечек', 'Sans-serif'], ['serif', 'С засечками', 'Serif'], ['mono', 'Моноширинные', 'Monospace'], ['display', 'Декоративные', 'Display']];
export const FONTS = [
  ['sans-serif', 'Sans', 'sans'],
  ['sans-serif-light', 'Light', 'sans'],
  ['sans-serif-thin', 'Thin', 'sans'],
  ['sans-serif-medium', 'Medium', 'sans'],
  ['sans-serif-black', 'Black', 'sans'],
  ['sans-serif-condensed', 'Condensed', 'sans'],
  ['serif', 'Serif', 'serif'],
  ['monospace', 'Mono', 'mono'],
  ['casual', 'Casual', 'display'],
  ['cursive', 'Cursive', 'display'],
  ['ws_lora', 'Lora', 'serif'],
  ['ws_lora_bold', 'Lora Bold', 'serif'],
  ['ws_plex_serif', 'IBM Plex Serif', 'serif'],
  ['ws_plex_serif_bold', 'IBM Plex Serif Bold', 'serif'],
  ['ws_poiret', 'Poiret One', 'display'],
  ['ws_jura_light', 'Jura Light', 'sans'],
  ['ws_jura', 'Jura', 'sans'],
  ['ws_tektur', 'Tektur', 'sans'],
  ['ws_jetbrains', 'JetBrains Mono', 'mono'],
  ['ws_geist_mono', 'Geist Mono', 'mono'],
];
export const CLOCK_FMT = ['HH:mm', 'H:mm', 'hh:mm', 'h:mm', 'h:mm a', 'hh:mm a'];
export const DATE_FMT = ['d MMMM yyyy', 'd MMMM', 'dd.MM.yyyy', 'EEE, d MMM', 'd MMM yyyy', 'MMMM d, yyyy'];
export const WEEKDAY_FMT = ['EEEE', 'EEE'];
// Размеры предпросмотра в dp (как у реальных виджетов на сетке лаунчера: 70·n − 30)
export const PREVIEW = { '5x3': [320, 180], '4x3': [250, 180], '5x2': [320, 110], '4x2': [250, 110] };
export const SERVICES = [
  ['openmeteo', 'Open-Meteo'], ['metno', 'MET Norway'], ['owm', 'OpenWeatherMap (API)'], ['weatherapi', 'WeatherAPI.com (API)'],
];
export const KEY_URL = { owm: 'https://openweathermap.org/api', weatherapi: 'https://www.weatherapi.com/' };

export function defaultSettings() { return { theme: 'system', preview: '5x3' }; }

export function defaultCfg() {
  return {
    opacity: 92, radius: 28, border: true,
    bgMode: 'sunset', photoRev: 0, dim: 10, split: 48,
    panel: '#2A3363', panel2: '#171C3E', fg: '#FFFFFF', fg2: '#B7BEE8',
    refresh: true,
    clock: { show: true, fmt: 'HH:mm', font: 'sans-serif-light', scale: 1, color: '', tz: '' },
    weekday: { show: true, pattern: 'EEEE', font: 'sans-serif', scale: 1, color: '', upper: false },
    date: { show: true, pattern: 'd MMMM yyyy', font: 'sans-serif-medium', scale: 1, color: '' },
    calendar: { show: true, days: 2, font: 'sans-serif', scale: 1, color: '' },
    weather: {
      show: true, service: 'openmeteo', apiKey: '', city: 'London', country: 'United Kingdom', lat: 51.5085, lon: -0.1257,
      units: 'c', days: 3, hours: 6, showCountry: true, showCond: true, showHiLo: true, font: 'sans-serif', scale: 1,
    },
  };
}

export function merge(base, over) {
  if (!over || typeof over !== 'object') return base;
  for (const k of Object.keys(over)) {
    if (base[k] && typeof base[k] === 'object' && !Array.isArray(base[k])) merge(base[k], over[k]);
    else base[k] = over[k];
  }
  return base;
}
