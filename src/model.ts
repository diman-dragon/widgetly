/**
 * Чистая модель редактора: дерево областей + набор виджетов.
 * Здесь нет React и DOM — всё покрыто unit-тестами (tests/model.test.mjs).
 *
 * Direction:
 *  - 'horizontal' — разделитель горизонтальный, дочерние области друг над другом;
 *  - 'vertical'   — разделитель вертикальный, дочерние области рядом.
 */
export type Direction = 'horizontal' | 'vertical'
export type WidgetType =
  | 'clock' | 'weather' | 'calendar' | 'events'
  | 'notifications' | 'text' | 'metric' | 'world'
export type Align = 'left' | 'center' | 'right'

export type Leaf = { id: string; kind: 'leaf'; widgetId: string | null }
export type Split = { id: string; kind: 'split'; direction: Direction; ratio: number; first: AreaNode; second: AreaNode }
export type AreaNode = Leaf | Split

export type Widget = {
  id: string
  type: WidgetType
  /** Множитель размера содержимого: 0.5 … 2.5 */
  scale: number
  opacity: number
  align: Align
  accent: boolean
  city?: string
  text?: string
  value?: string
  label?: string
  /** Строки списка (события / уведомления) */
  items?: string
  /** Часовые пояса для «Мирового времени» */
  zones?: string[]
}
export type Snapshot = { root: AreaNode; widgets: Record<string, Widget> }

export const MIN_RATIO = 0.12
export const MAX_RATIO = 0.88
export const SCALE_MIN = 0.5
export const SCALE_MAX = 2.5
const MAX_DEPTH = 32
const MAX_NODES = 200

let seq = 0
export const uid = (prefix: string) =>
  `${prefix}-${Math.random().toString(36).slice(2, 8)}${(seq++).toString(36)}`

export const widgetName: Record<WidgetType, string> = {
  clock: 'Часы', weather: 'Погода', calendar: 'Календарь', events: 'События',
  notifications: 'Уведомления', text: 'Текст', metric: 'Показатель', world: 'Мировое время'
}

export const WORLD_ZONES: { id: string; name: string }[] = [
  { id: 'Europe/Belgrade', name: 'Белград' },
  { id: 'Europe/London', name: 'Лондон' },
  { id: 'Europe/Moscow', name: 'Москва' },
  { id: 'Asia/Dubai', name: 'Дубай' },
  { id: 'Asia/Tokyo', name: 'Токио' },
  { id: 'Australia/Sydney', name: 'Сидней' },
  { id: 'America/New_York', name: 'Нью-Йорк' },
  { id: 'America/Los_Angeles', name: 'Лос-Анджелес' }
]
export const DEFAULT_ZONES = ['Europe/Belgrade', 'Asia/Tokyo', 'America/New_York']

/* ───────── дерево областей ───────── */

export const leaf = (widgetId: string | null = null): Leaf => ({ id: uid('leaf'), kind: 'leaf', widgetId })
export const isLeaf = (n: AreaNode): n is Leaf => n.kind === 'leaf'

export const find = (n: AreaNode, target: string): AreaNode | null =>
  n.id === target ? n : n.kind === 'split' ? find(n.first, target) ?? find(n.second, target) : null

export const update = (n: AreaNode, target: string, fn: (n: AreaNode) => AreaNode): AreaNode =>
  n.id === target ? fn(n)
    : n.kind === 'split' ? { ...n, first: update(n.first, target, fn), second: update(n.second, target, fn) }
    : n

/** Делит лист на два. Исходный лист остаётся первым, `newLeaf` становится вторым. */
export const split = (root: AreaNode, target: string, direction: Direction, newLeaf: Leaf = leaf()): AreaNode =>
  update(root, target, n => isLeaf(n) ? { id: uid('split'), kind: 'split', direction, ratio: 0.5, first: n, second: newLeaf } : n)

export const clampRatio = (r: number) => Math.max(MIN_RATIO, Math.min(MAX_RATIO, Number.isFinite(r) ? r : 0.5))

export const setRatio = (root: AreaNode, target: string, ratio: number): AreaNode =>
  update(root, target, n => n.kind === 'split' ? { ...n, ratio: clampRatio(ratio) } : n)

/** Удаляет лист; его сосед занимает место родителя. Единственный корневой лист не удаляется. */
export const remove = (n: AreaNode, target: string): AreaNode => {
  if (n.kind === 'leaf' || n.id === target) return n
  if (n.first.id === target) return n.second
  if (n.second.id === target) return n.first
  return { ...n, first: remove(n.first, target), second: remove(n.second, target) }
}

export const leafIds = (n: AreaNode, out: string[] = []): string[] => {
  if (isLeaf(n)) { out.push(n.id); return out }
  leafIds(n.first, out)
  return leafIds(n.second, out)
}
export const countLeaves = (n: AreaNode): number => leafIds(n).length

/** id виджетов, которые реально размещены в областях */
export const collect = (n: AreaNode, out: string[] = []): string[] => {
  if (isLeaf(n)) { if (n.widgetId) out.push(n.widgetId); return out }
  collect(n.first, out)
  return collect(n.second, out)
}

/** Убирает виджеты, не привязанные ни к одной области */
export const prune = (s: Snapshot): Snapshot => {
  const keep = new Set(collect(s.root))
  const widgets: Record<string, Widget> = {}
  for (const [k, w] of Object.entries(s.widgets)) if (keep.has(k)) widgets[k] = w
  return { root: s.root, widgets }
}

/* ───────── виджеты ───────── */

export const createWidget = (type: WidgetType): Widget => {
  const w: Widget = { id: uid('widget'), type, scale: 1, opacity: 1, align: 'center', accent: false }
  switch (type) {
    case 'weather': w.city = 'Белград'; break
    case 'text': w.text = 'Ваш текст'; break
    case 'metric': w.value = '128'; w.label = 'Показатель'; break
    case 'events': w.items = '14:30 Встреча\n18:00 Позвонить'; break
    case 'notifications': w.items = 'Новое сообщение\nНапоминание'; break
    case 'world': w.zones = [...DEFAULT_ZONES]; break
  }
  return w
}

export const starter = (): Snapshot => {
  const a = createWidget('clock'), b = createWidget('weather'), c = createWidget('calendar'), d = createWidget('events')
  const root: AreaNode = {
    id: uid('root'), kind: 'split', direction: 'horizontal', ratio: 0.31,
    first: leaf(a.id),
    second: {
      id: uid('split'), kind: 'split', direction: 'horizontal', ratio: 0.58,
      first: { id: uid('split'), kind: 'split', direction: 'vertical', ratio: 0.5, first: leaf(b.id), second: leaf(c.id) },
      second: leaf(d.id)
    }
  }
  return { root, widgets: Object.fromEntries([a, b, c, d].map(w => [w.id, w])) }
}

/** «14:30 Встреча» → { lead: '14:30', text: 'Встреча' } */
export const parseItems = (raw: string | undefined, max = 12): { lead: string; text: string }[] =>
  (raw ?? '').split(/\r?\n/).map(l => l.trim()).filter(Boolean).slice(0, max).map(line => {
    const m = /^(\d{1,2})[:.](\d{2})\s+(.+)$/.exec(line)
    return m ? { lead: `${m[1].padStart(2, '0')}:${m[2]}`, text: m[3] } : { lead: '', text: line }
  })

/* ───────── проверка данных из хранилища ───────── */

type Obj = Record<string, unknown>
const isObj = (v: unknown): v is Obj => typeof v === 'object' && v !== null && !Array.isArray(v)
const str = (v: unknown, max: number): string | undefined => typeof v === 'string' ? v.slice(0, max) : undefined
const num = (v: unknown, min: number, max: number, def: number) =>
  typeof v === 'number' && Number.isFinite(v) ? Math.min(max, Math.max(min, v)) : def
const TYPES = new Set<string>(Object.keys(widgetName))
const ZONE_IDS = new Set(WORLD_ZONES.map(z => z.id))

const sanitizeWidget = (raw: unknown, key: string): Widget | null => {
  if (!isObj(raw) || typeof raw.type !== 'string' || !TYPES.has(raw.type)) return null
  const type = raw.type as WidgetType
  const base = createWidget(type)
  const w: Widget = {
    ...base,
    id: key,
    scale: num(raw.scale, SCALE_MIN, SCALE_MAX, 1),
    opacity: num(raw.opacity, 0.2, 1, 1),
    align: raw.align === 'left' || raw.align === 'right' ? raw.align : 'center',
    accent: raw.accent === true
  }
  if (type === 'weather') w.city = str(raw.city, 80) ?? base.city
  if (type === 'text') w.text = str(raw.text, 2000) ?? base.text
  if (type === 'metric') { w.value = str(raw.value, 40) ?? base.value; w.label = str(raw.label, 40) ?? base.label }
  if (type === 'events' || type === 'notifications') w.items = str(raw.items, 2000) ?? base.items
  if (type === 'world') {
    w.zones = Array.isArray(raw.zones)
      ? [...new Set(raw.zones.filter((z): z is string => typeof z === 'string' && ZONE_IDS.has(z)))]
      : base.zones
  }
  return w
}

/** Возвращает исправный Snapshot либо null, если данные непригодны. */
export function sanitize(raw: unknown): Snapshot | null {
  if (!isObj(raw) || !isObj(raw.widgets)) return null
  const widgets: Record<string, Widget> = {}
  for (const [k, v] of Object.entries(raw.widgets)) {
    if (k === '__proto__') continue
    const w = sanitizeWidget(v, k)
    if (w) widgets[k] = w
  }
  const ids = new Set<string>()
  const used = new Set<string>()
  let count = 0
  const walk = (n: unknown, depth: number): AreaNode | null => {
    if (!isObj(n) || depth > MAX_DEPTH || ++count > MAX_NODES) return null
    if (typeof n.id !== 'string' || !n.id || ids.has(n.id)) return null
    ids.add(n.id)
    if (n.kind === 'leaf') {
      let wid = typeof n.widgetId === 'string' ? n.widgetId : null
      if (wid && (!widgets[wid] || used.has(wid))) wid = null
      if (wid) used.add(wid)
      return { id: n.id, kind: 'leaf', widgetId: wid }
    }
    if (n.kind === 'split' && (n.direction === 'horizontal' || n.direction === 'vertical')) {
      const first = walk(n.first, depth + 1)
      const second = first && walk(n.second, depth + 1)
      if (!first || !second) return null
      return { id: n.id, kind: 'split', direction: n.direction, ratio: clampRatio(typeof n.ratio === 'number' ? n.ratio : 0.5), first, second }
    }
    return null
  }
  const root = walk(raw.root, 0)
  return root ? prune({ root, widgets }) : null
}
