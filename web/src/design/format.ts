import type { WeatherSample } from './types';

// ВАЖНО: логика и таблицы идентичны TextFormat.kt. Менять только синхронно.

const TOKEN = /'[^']*'|EEEE|EEE|MMMM|MMM|MM|M|yyyy|yy|dd|d|HH|H|hh|h|mm|A|a/g;

const DAYS = {
  ru: ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'],
  en: ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'],
};
const DAYS_SHORT = {
  ru: ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'],
  en: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'],
};
const MONTHS = {
  ru: ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'],
  en: ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'],
};
const MONTHS_SHORT = {
  ru: ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'],
  en: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
};

const pad2 = (n: number) => (n < 10 ? `0${n}` : `${n}`);

export function formatDate(pattern: string, d: Date, locale: 'ru' | 'en'): string {
  const hour = d.getHours();
  const h12 = hour % 12 === 0 ? 12 : hour % 12;
  return pattern.replace(TOKEN, (t) => {
    switch (t) {
      case 'EEEE': return DAYS[locale][d.getDay()];
      case 'EEE': return DAYS_SHORT[locale][d.getDay()];
      case 'MMMM': return MONTHS[locale][d.getMonth()];
      case 'MMM': return MONTHS_SHORT[locale][d.getMonth()];
      case 'MM': return pad2(d.getMonth() + 1);
      case 'M': return `${d.getMonth() + 1}`;
      case 'yyyy': return `${d.getFullYear()}`;
      case 'yy': return pad2(d.getFullYear() % 100);
      case 'dd': return pad2(d.getDate());
      case 'd': return `${d.getDate()}`;
      case 'HH': return pad2(hour);
      case 'H': return `${hour}`;
      case 'hh': return pad2(h12);
      case 'h': return `${h12}`;
      case 'mm': return pad2(d.getMinutes());
      case 'A': return hour < 12 ? 'AM' : 'PM';
      case 'a': return hour < 12 ? 'am' : 'pm';
      default:
        // литерал в одинарных кавычках; '' даёт одну кавычку
        return t.length === 2 ? "'" : t.slice(1, -1);
    }
  });
}

/** Идентично Math.floor(x + 0.5) в Kotlin. */
export function roundHalfUp(x: number): number {
  return Math.floor(x + 0.5);
}

export function tempText(tempC: number, unit: 'C' | 'F', showUnit: boolean): string {
  const v = unit === 'F' ? (tempC * 9) / 5 + 32 : tempC;
  return `${roundHalfUp(v)}°${showUnit ? unit : ''}`;
}

export function weatherIcon(code: number, isDay: boolean): string {
  switch (code) {
    case 0: return isDay ? '☀️' : '🌙';
    case 1: return isDay ? '🌤️' : '🌙';
    case 2: return isDay ? '⛅' : '☁️';
    case 3: return '☁️';
    case 45: case 48: return '🌫️';
    case 51: case 53: case 55: case 56: case 57: return '🌦️';
    case 61: case 63: case 65: case 66: case 67: return '🌧️';
    case 71: case 73: case 75: case 77: return '🌨️';
    case 80: case 81: case 82: return '🌧️';
    case 85: case 86: return '🌨️';
    case 95: case 96: case 99: return '⛈️';
    default: return '❓';
  }
}

const COND_RU: Record<number, string> = {
  0: 'Ясно', 1: 'Малооблачно', 2: 'Переменная облачность', 3: 'Пасмурно',
  45: 'Туман', 48: 'Туман',
  51: 'Морось', 53: 'Морось', 55: 'Морось', 56: 'Ледяная морось', 57: 'Ледяная морось',
  61: 'Небольшой дождь', 63: 'Дождь', 65: 'Сильный дождь', 66: 'Ледяной дождь', 67: 'Ледяной дождь',
  71: 'Небольшой снег', 73: 'Снег', 75: 'Сильный снег', 77: 'Снежная крупа',
  80: 'Ливень', 81: 'Ливень', 82: 'Сильный ливень', 85: 'Снегопад', 86: 'Снегопад',
  95: 'Гроза', 96: 'Гроза с градом', 99: 'Гроза с градом',
};
const COND_EN: Record<number, string> = {
  0: 'Clear', 1: 'Mostly clear', 2: 'Partly cloudy', 3: 'Overcast',
  45: 'Fog', 48: 'Fog',
  51: 'Drizzle', 53: 'Drizzle', 55: 'Drizzle', 56: 'Freezing drizzle', 57: 'Freezing drizzle',
  61: 'Light rain', 63: 'Rain', 65: 'Heavy rain', 66: 'Freezing rain', 67: 'Freezing rain',
  71: 'Light snow', 73: 'Snow', 75: 'Heavy snow', 77: 'Snow grains',
  80: 'Showers', 81: 'Showers', 82: 'Heavy showers', 85: 'Snow showers', 86: 'Snow showers',
  95: 'Thunderstorm', 96: 'Thunderstorm with hail', 99: 'Thunderstorm with hail',
};

export function conditionText(code: number, locale: 'ru' | 'en'): string {
  const table = locale === 'ru' ? COND_RU : COND_EN;
  return table[code] ?? '—';
}

export function noDataText(locale: 'ru' | 'en'): string {
  return locale === 'ru' ? 'Нет данных' : 'No data';
}

export function cityText(w: WeatherSample | null, locale: 'ru' | 'en'): string {
  if (!w) return '';
  if (w.city.trim() !== '') return w.city;
  return locale === 'ru' ? 'Моё место' : 'My location';
}

export function rangeText(w: WeatherSample | null, unit: 'C' | 'F'): string {
  if (!w) return '--';
  return `↑${tempText(w.hiC, unit, false)} ↓${tempText(w.loC, unit, false)}`;
}

/** Коды WMO для выпадающего списка в редакторе. */
export const SAMPLE_CODES: number[] = [0, 1, 2, 3, 45, 51, 61, 65, 71, 80, 95];
