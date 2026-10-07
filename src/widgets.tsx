import type { CSSProperties, ReactNode } from 'react'
import {
  Bell, CalendarDays, Clock3, Cloud, CloudFog, CloudLightning, CloudRain, CloudSnow, CloudSun,
  Gauge, Globe2, List, Sun, Type
} from 'lucide-react'
import { DEFAULT_ZONES, WORLD_ZONES, parseItems, type Align, type Widget, type WidgetType } from './model'
import { useNow, useWeather, weatherText } from './hooks'

export const catalog: { type: WidgetType; hint: string; icon: typeof Clock3 }[] = [
  { type: 'clock', hint: 'Текущее время', icon: Clock3 },
  { type: 'weather', hint: 'Температура и погода', icon: CloudSun },
  { type: 'calendar', hint: 'Дата и календарь', icon: CalendarDays },
  { type: 'events', hint: 'Список событий', icon: List },
  { type: 'notifications', hint: 'Уведомления', icon: Bell },
  { type: 'text', hint: 'Произвольный текст', icon: Type },
  { type: 'metric', hint: 'Число или показатель', icon: Gauge },
  { type: 'world', hint: 'Время в городах', icon: Globe2 }
]

const ALIGN: Record<Align, CSSProperties['alignItems']> = { left: 'flex-start', center: 'center', right: 'flex-end' }

const fmtTime = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
const fmtDate = new Intl.DateTimeFormat('ru-RU', { weekday: 'long', day: 'numeric', month: 'long' })
const fmtMonth = new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric' })
const zoneFormats = new Map<string, Intl.DateTimeFormat>()
const zoneFormat = (zone: string) => {
  let f = zoneFormats.get(zone)
  if (!f) { f = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: zone }); zoneFormats.set(zone, f) }
  return f
}
const DOW = ['пн', 'вт', 'ср', 'чт', 'пт', 'сб', 'вс']

export function WidgetView({ widget: w }: { widget: Widget }) {
  const style = { '--scale': w.scale, opacity: w.opacity, textAlign: w.align, alignItems: ALIGN[w.align] } as CSSProperties
  return (
    <div className="widget">
      <div className={'wc' + (w.accent ? ' accent' : '')} style={style}>
        <Body widget={w} />
      </div>
    </div>
  )
}

function Body({ widget: w }: { widget: Widget }): ReactNode {
  switch (w.type) {
    case 'clock': return <ClockBody />
    case 'weather': return <WeatherBody city={w.city ?? ''} />
    case 'calendar': return <CalendarBody />
    case 'events': return <ListBody title="События" items={w.items} />
    case 'notifications': return <ListBody title="Уведомления" items={w.items} />
    case 'text': return <><div className="eyebrow">Текст</div><div className="text-value">{w.text || 'Ваш текст'}</div></>
    case 'metric': return <><div className="eyebrow">{w.label || 'Показатель'}</div><div className="metric-value">{w.value || '0'}</div></>
    case 'world': return <WorldBody zones={w.zones ?? DEFAULT_ZONES} />
  }
}

function ClockBody() {
  const now = useNow(1000)
  return <>
    <div className="eyebrow">Текущее время</div>
    <div className="clock-value">{fmtTime.format(now)}</div>
    <div className="muted">{fmtDate.format(now)}</div>
  </>
}

function WeatherIcon({ code }: { code: number }) {
  if (code === 0) return <Sun />
  if (code === 1 || code === 2) return <CloudSun />
  if (code === 3) return <Cloud />
  if (code === 45 || code === 48) return <CloudFog />
  if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return <CloudRain />
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return <CloudSnow />
  if (code >= 95) return <CloudLightning />
  return <Cloud />
}

function WeatherBody({ city }: { city: string }) {
  const s = useWeather(city)
  if (s.status === 'ok') return <>
    <div className="eyebrow">{s.place}</div>
    <div className="weather-row"><span className="weather-icon"><WeatherIcon code={s.code} /></span><span className="weather-value">{s.temp}°</span></div>
    <div className="muted">{weatherText(s.code)} · ветер {s.wind} км/ч</div>
  </>
  return <>
    <div className="eyebrow">{city || 'Город'}</div>
    <div className="weather-value">—</div>
    <div className="muted">{s.status === 'empty' ? 'Укажите город' : s.status === 'loading' ? 'Загрузка…' : 'Нет данных'}</div>
  </>
}

function CalendarBody() {
  const now = useNow(30_000)
  const y = now.getFullYear(), m = now.getMonth(), today = now.getDate()
  const offset = (new Date(y, m, 1).getDay() + 6) % 7
  const days = new Date(y, m + 1, 0).getDate()
  const cells: (number | null)[] = [...Array<null>(offset).fill(null), ...Array.from({ length: days }, (_, i) => i + 1)]
  return <>
    <div className="cal-compact">
      <div className="eyebrow">Календарь</div>
      <div className="calendar-day">{today}</div>
      <div className="muted">{fmtDate.format(now)}</div>
    </div>
    <div className="cal-full">
      <div className="eyebrow">{fmtMonth.format(now)}</div>
      <div className="cal-grid">
        {DOW.map(d => <span key={d} className="dow">{d}</span>)}
        {cells.map((d, i) => <span key={i} className={d === null ? 'out' : d === today ? 'today' : ''}>{d ?? ''}</span>)}
      </div>
    </div>
  </>
}

function ListBody({ title, items }: { title: string; items?: string }) {
  const rows = parseItems(items)
  return <>
    <div className="eyebrow">{title}</div>
    <div className="list">
      {rows.length === 0 && <div><span>Список пуст</span></div>}
      {rows.map((r, i) => <div key={i}>{r.lead ? <><b>{r.lead}</b><span>{r.text}</span></> : <span className="solo">{r.text}</span>}</div>)}
    </div>
  </>
}

function WorldBody({ zones }: { zones: string[] }) {
  const now = useNow(1000)
  const rows = WORLD_ZONES.filter(z => zones.includes(z.id))
  return <>
    <div className="eyebrow">Мировое время</div>
    <div className="list">
      {rows.length === 0 && <div><span>Выберите города</span></div>}
      {rows.map(z => <div key={z.id}><b>{z.name}</b><span className="tabular">{zoneFormat(z.id).format(now)}</span></div>)}
    </div>
  </>
}
