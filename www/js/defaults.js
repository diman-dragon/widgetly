export const FONTS = [
  ['sans-serif', 'Sans'], ['sans-serif-light', 'Light'], ['sans-serif-thin', 'Thin'], ['sans-serif-medium', 'Medium'],
  ['sans-serif-black', 'Black'], ['sans-serif-condensed', 'Condensed'], ['serif', 'Serif'], ['monospace', 'Mono'],
  ['casual', 'Casual'], ['cursive', 'Cursive'],
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
