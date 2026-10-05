import { WebPlugin } from '@capacitor/core';
import type { Design } from '../design/types';
import type {
  ActiveWidget,
  CityChoice,
  Diagnostics,
  InfoResult,
  WeatherData,
  WeatherSettings,
  WidgetBridgePlugin,
} from './definitions';
import { fetchWeather } from './openMeteo';

const K_DESIGNS = 'widgetly.designs';
const K_WEATHER = 'widgetly.weather';
const K_WSET = 'widgetly.weatherSettings';

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}
function write(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* хранилище недоступно — игнорируем */
  }
}

/**
 * Реализация для браузера (npm run dev на :8080): весь редактор работает без Android.
 * Закрепление виджета и активные виджеты недоступны — это возможно только в нативной сборке.
 */
export class WidgetBridgeWeb extends WebPlugin implements WidgetBridgePlugin {
  async getInfo(): Promise<InfoResult> {
    return { native: false, version: 'web', sdk: 0, pinSupported: false };
  }
  async listDesigns(): Promise<{ designs: Design[] }> {
    return { designs: read<Design[]>(K_DESIGNS, []) };
  }
  async saveDesign(o: { design: Design }): Promise<void> {
    const all = read<Design[]>(K_DESIGNS, []).filter((d) => d.id !== o.design.id);
    all.unshift(o.design);
    write(K_DESIGNS, all);
  }
  async deleteDesign(o: { id: string }): Promise<void> {
    write(K_DESIGNS, read<Design[]>(K_DESIGNS, []).filter((d) => d.id !== o.id));
  }
  async pinWidget(): Promise<{ requested: boolean }> {
    return { requested: false };
  }
  async getActiveWidgets(): Promise<{ widgets: ActiveWidget[] }> {
    return { widgets: [] };
  }
  async applyDesign(): Promise<void> {}
  async refreshWidgets(): Promise<void> {}
  async getWeatherSettings(): Promise<WeatherSettings> {
    const s = read<{ mode: 'gps' | 'city'; city: CityChoice | null }>(K_WSET, { mode: 'city', city: null });
    return { ...s, hasLocationPermission: false };
  }
  async setWeatherSettings(o: { mode: 'gps' | 'city'; city?: CityChoice | null }): Promise<void> {
    write(K_WSET, { mode: o.mode, city: o.city ?? null });
  }
  async getWeather(): Promise<{ weather: WeatherData | null }> {
    return { weather: read<WeatherData | null>(K_WEATHER, null) };
  }
  async refreshWeather(): Promise<{ weather: WeatherData }> {
    const s = read<{ mode: 'gps' | 'city'; city: CityChoice | null }>(K_WSET, { mode: 'city', city: null });
    if (s.mode !== 'city' || !s.city) {
      throw new Error('В браузере доступен только выбор города вручную');
    }
    const weather = await fetchWeather(s.city.lat, s.city.lon, s.city.name);
    write(K_WEATHER, weather);
    return { weather };
  }
  async requestLocationPermission(): Promise<{ granted: boolean }> {
    return { granted: false };
  }
  async getDiagnostics(): Promise<Diagnostics> {
    const w = read<WeatherData | null>(K_WEATHER, null);
    return {
      native: false,
      lastRenderAt: 0,
      lastRenderMs: 0,
      lastBitmap: '—',
      widgetCount: 0,
      lastError: '',
      weatherFetchedAt: w?.fetchedAt ?? 0,
      weatherError: '',
      designCount: read<Design[]>(K_DESIGNS, []).length,
    };
  }
  async openBatterySettings(): Promise<{ opened: boolean }> {
    return { opened: false };
  }
}
