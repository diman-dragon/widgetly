import type { Design } from '../design/types';

export interface WeatherData {
  tempC: number;
  code: number;
  isDay: boolean;
  hiC: number;
  loC: number;
  city: string;
  fetchedAt: number;
}

export interface CityChoice {
  name: string;
  lat: number;
  lon: number;
}

export interface WeatherSettings {
  mode: 'gps' | 'city';
  city: CityChoice | null;
  hasLocationPermission: boolean;
}

export interface ActiveWidget {
  widgetId: number;
  designId: string;
  widthDp: number;
  heightDp: number;
}

export interface Diagnostics {
  native: boolean;
  lastRenderAt: number;
  lastRenderMs: number;
  lastBitmap: string;
  widgetCount: number;
  lastError: string;
  weatherFetchedAt: number;
  weatherError: string;
  designCount: number;
}

export interface InfoResult {
  native: boolean;
  version: string;
  sdk: number;
  pinSupported: boolean;
}

/** Контракт нативного плагина WidgetBridge (WidgetBridgePlugin.kt) и веб-заглушки. */
export interface WidgetBridgePlugin {
  getInfo(): Promise<InfoResult>;
  listDesigns(): Promise<{ designs: Design[] }>;
  saveDesign(o: { design: Design }): Promise<void>;
  deleteDesign(o: { id: string }): Promise<void>;
  pinWidget(o: { designId: string }): Promise<{ requested: boolean }>;
  getActiveWidgets(): Promise<{ widgets: ActiveWidget[] }>;
  applyDesign(o: { widgetId: number; designId: string }): Promise<void>;
  refreshWidgets(): Promise<void>;
  getWeatherSettings(): Promise<WeatherSettings>;
  setWeatherSettings(o: { mode: 'gps' | 'city'; city?: CityChoice | null }): Promise<void>;
  getWeather(): Promise<{ weather: WeatherData | null }>;
  refreshWeather(): Promise<{ weather: WeatherData }>;
  requestLocationPermission(): Promise<{ granted: boolean }>;
  getDiagnostics(): Promise<Diagnostics>;
  openBatterySettings(): Promise<{ opened: boolean }>;
}
