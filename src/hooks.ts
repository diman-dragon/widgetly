import { useEffect, useState } from 'react'

/** Текущее время; обновляется по таймеру и при возврате на вкладку. */
export function useNow(intervalMs = 1000): Date {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const tick = () => setNow(new Date())
    const t = setInterval(tick, intervalMs)
    document.addEventListener('visibilitychange', tick)
    return () => { clearInterval(t); document.removeEventListener('visibilitychange', tick) }
  }, [intervalMs])
  return now
}

/* ───────── погода (Open-Meteo, без ключа и без собственного сервера) ───────── */

export type WeatherOk = { status: 'ok'; place: string; temp: number; code: number; wind: number }
export type WeatherState = { status: 'empty' | 'loading' | 'error' } | WeatherOk

const TTL = 15 * 60_000
const cache = new Map<string, { at: number; data: WeatherOk }>()

async function fetchWeather(city: string, signal: AbortSignal): Promise<WeatherOk> {
  const g = await fetch(
    `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(city)}&count=1&language=ru&format=json`,
    { signal }
  )
  if (!g.ok) throw new Error('geocoding')
  const place = (await g.json())?.results?.[0]
  if (!place || typeof place.latitude !== 'number' || typeof place.longitude !== 'number') throw new Error('not found')
  const r = await fetch(
    `https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}` +
      '&current=temperature_2m,weather_code,wind_speed_10m&timezone=auto',
    { signal }
  )
  if (!r.ok) throw new Error('forecast')
  const c = (await r.json())?.current
  if (!c || typeof c.temperature_2m !== 'number') throw new Error('bad forecast')
  return {
    status: 'ok',
    place: String(place.name ?? city),
    temp: Math.round(c.temperature_2m),
    code: Number(c.weather_code),
    wind: Math.round(Number(c.wind_speed_10m) || 0)
  }
}

export function useWeather(city: string): WeatherState {
  const key = city.trim().toLowerCase()
  const [state, setState] = useState<WeatherState>(() => {
    const hit = cache.get(key)
    return hit ? hit.data : { status: key ? 'loading' : 'empty' }
  })
  const [tick, setTick] = useState(0)

  useEffect(() => {
    const t = setInterval(() => setTick(x => x + 1), TTL)
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    if (!key) { setState({ status: 'empty' }); return }
    const hit = cache.get(key)
    if (hit) setState(hit.data)
    else setState({ status: 'loading' })
    if (hit && Date.now() - hit.at < TTL) return
    const ctrl = new AbortController()
    // небольшая задержка, чтобы не слать запрос на каждую набранную букву
    const timer = setTimeout(() => {
      fetchWeather(key, ctrl.signal)
        .then(data => { cache.set(key, { at: Date.now(), data }); setState(data) })
        .catch(() => { if (!ctrl.signal.aborted && !hit) setState({ status: 'error' }) })
    }, hit ? 0 : 600)
    return () => { clearTimeout(timer); ctrl.abort() }
  }, [key, tick])

  return state
}

export function weatherText(code: number): string {
  if (code === 0) return 'Ясно'
  if (code === 1) return 'Преимущественно ясно'
  if (code === 2) return 'Переменная облачность'
  if (code === 3) return 'Пасмурно'
  if (code === 45 || code === 48) return 'Туман'
  if (code >= 51 && code <= 57) return 'Морось'
  if (code >= 61 && code <= 67) return 'Дождь'
  if (code >= 71 && code <= 77) return 'Снег'
  if (code >= 80 && code <= 82) return 'Ливень'
  if (code === 85 || code === 86) return 'Снегопад'
  if (code >= 95) return 'Гроза'
  return 'Погода'
}
