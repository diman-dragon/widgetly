import type { CityChoice, WeatherData } from './definitions';

interface ForecastResponse {
  current?: { temperature_2m?: number; weather_code?: number; is_day?: number };
  daily?: { temperature_2m_max?: number[]; temperature_2m_min?: number[] };
}

export async function fetchWeather(lat: number, lon: number, city: string): Promise<WeatherData> {
  const url =
    `https://api.open-meteo.com/v1/forecast?latitude=${lat.toFixed(4)}&longitude=${lon.toFixed(4)}` +
    `&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Open-Meteo: HTTP ${res.status}`);
  const j = (await res.json()) as ForecastResponse;
  const cur = j.current;
  if (!cur || typeof cur.temperature_2m !== 'number') throw new Error('Open-Meteo: нет текущих данных');
  return {
    tempC: cur.temperature_2m,
    code: cur.weather_code ?? -1,
    isDay: (cur.is_day ?? 1) === 1,
    hiC: j.daily?.temperature_2m_max?.[0] ?? cur.temperature_2m,
    loC: j.daily?.temperature_2m_min?.[0] ?? cur.temperature_2m,
    city,
    fetchedAt: Date.now(),
  };
}

interface GeoResponse {
  results?: { name: string; latitude: number; longitude: number; country?: string; admin1?: string }[];
}

export interface CityResult extends CityChoice {
  subtitle: string;
}

export async function searchCities(query: string, lang: 'ru' | 'en'): Promise<CityResult[]> {
  const q = query.trim();
  if (q.length < 2) return [];
  const url = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(q)}&count=6&language=${lang}&format=json`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Геокодинг: HTTP ${res.status}`);
  const j = (await res.json()) as GeoResponse;
  return (j.results ?? []).map((r) => ({
    name: r.name,
    lat: r.latitude,
    lon: r.longitude,
    subtitle: [r.admin1, r.country].filter(Boolean).join(', '),
  }));
}
