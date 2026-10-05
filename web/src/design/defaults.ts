import type { Background, Design, Layer, LayerType } from './types';
import { MAX_GRID } from './types';

const HEX = /^#[0-9a-fA-F]{6}$/;

let counter = 0;
export function uid(prefix = 'id'): string {
  counter += 1;
  const rnd =
    typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID().slice(0, 8)
      : Math.random().toString(36).slice(2, 10);
  return `${prefix}_${rnd}${counter.toString(36)}`;
}

export const LAYER_TYPES: { type: LayerType; label: string }[] = [
  { type: 'clockDigital', label: 'Цифровые часы' },
  { type: 'clockAnalog', label: 'Аналоговые часы' },
  { type: 'date', label: 'Дата' },
  { type: 'text', label: 'Текст' },
  { type: 'weatherIcon', label: 'Иконка погоды' },
  { type: 'weatherTemp', label: 'Температура' },
  { type: 'weatherCondition', label: 'Состояние погоды' },
  { type: 'weatherRange', label: 'Макс / мин' },
  { type: 'weatherCity', label: 'Город' },
  { type: 'shape', label: 'Фигура' },
];

export function layerLabel(type: LayerType): string {
  return LAYER_TYPES.find((t) => t.type === type)?.label ?? type;
}

const BASE: Omit<Layer, 'id' | 'type'> = {
  x: 0.5,
  y: 0.5,
  opacity: 1,
  size: 16,
  color: '#ffffff',
  align: 'center',
  font: 'sans',
  weight: 'normal',
  letterSpacing: 0,
  text: 'Текст',
  format: 'HH:mm',
  unit: 'C',
  showUnit: false,
  face: '',
  ring: '#ffffff',
  ringWidth: 2,
  ticks: 'hours',
  tickColor: '#ffffff',
  hourColor: '#ffffff',
  minuteColor: '#ffffff',
  handWidth: 4,
  shape: 'rect',
  w: 60,
  h: 30,
  radius: 8,
};

export function defaultLayer(type: LayerType, over: Partial<Layer> = {}): Layer {
  const l: Layer = { ...BASE, id: uid('l'), type };
  switch (type) {
    case 'clockDigital':
      Object.assign(l, { size: 48, weight: 'bold', format: 'HH:mm', align: 'left', x: 0.08 });
      break;
    case 'clockAnalog':
      Object.assign(l, { size: 80 });
      break;
    case 'date':
      Object.assign(l, { size: 16, format: 'EEE, d MMM', align: 'left', x: 0.08, color: '#c8c8d0' });
      break;
    case 'text':
      Object.assign(l, { size: 18, text: 'Привет' });
      break;
    case 'weatherIcon':
      Object.assign(l, { size: 40 });
      break;
    case 'weatherTemp':
      Object.assign(l, { size: 32, weight: 'bold' });
      break;
    case 'weatherCondition':
      Object.assign(l, { size: 14, color: '#c8c8d0' });
      break;
    case 'weatherRange':
      Object.assign(l, { size: 14, color: '#c8c8d0' });
      break;
    case 'weatherCity':
      Object.assign(l, { size: 14, color: '#c8c8d0' });
      break;
    case 'shape':
      Object.assign(l, { color: '#ffffff', opacity: 0.15 });
      break;
  }
  return Object.assign(l, over);
}

export function defaultBackground(): Background {
  return { type: 'gradient', c1: '#1b1b2f', c2: '#162447', angle: 135, radius: 24, opacity: 1 };
}

export function newDesign(name = 'Новый дизайн'): Design {
  return {
    v: 1,
    id: uid('d'),
    name,
    cols: 4,
    rows: 2,
    locale: 'ru',
    bg: defaultBackground(),
    layers: [defaultLayer('clockDigital', { y: 0.4 }), defaultLayer('date', { y: 0.78 })],
    updatedAt: Date.now(),
  };
}

// ---------- нормализация (импорт/загрузка из хранилища) ----------

const isObj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);
const num = (v: unknown, d: number, min: number, max: number): number => {
  const n = typeof v === 'number' && Number.isFinite(v) ? v : d;
  return Math.min(max, Math.max(min, n));
};
const str = (v: unknown, d: string): string => (typeof v === 'string' ? v : d);
const color = (v: unknown, d: string): string => (typeof v === 'string' && HEX.test(v) ? v.toLowerCase() : d);
const colorOrEmpty = (v: unknown, d: string): string => (v === '' ? '' : color(v, d));
const oneOf = <T extends string>(v: unknown, opts: readonly T[], d: T): T =>
  typeof v === 'string' && (opts as readonly string[]).includes(v) ? (v as T) : d;

const TYPES: readonly LayerType[] = LAYER_TYPES.map((t) => t.type);

export function normalizeLayer(raw: unknown): Layer | null {
  if (!isObj(raw)) return null;
  const type = oneOf(raw.type, TYPES, 'text' as LayerType);
  if (!TYPES.includes(raw.type as LayerType)) return null;
  const d = defaultLayer(type);
  return {
    id: str(raw.id, d.id) || d.id,
    type,
    x: num(raw.x, d.x, 0, 1),
    y: num(raw.y, d.y, 0, 1),
    opacity: num(raw.opacity, d.opacity, 0, 1),
    size: num(raw.size, d.size, 4, 400),
    color: color(raw.color, d.color),
    align: oneOf(raw.align, ['left', 'center', 'right'] as const, d.align),
    font: oneOf(raw.font, ['sans', 'sans-light', 'sans-thin', 'condensed', 'serif', 'mono'] as const, d.font),
    weight: oneOf(raw.weight, ['normal', 'bold'] as const, d.weight),
    letterSpacing: num(raw.letterSpacing, d.letterSpacing, -0.2, 1),
    text: str(raw.text, d.text).slice(0, 200),
    format: str(raw.format, d.format).slice(0, 80),
    unit: oneOf(raw.unit, ['C', 'F'] as const, d.unit),
    showUnit: typeof raw.showUnit === 'boolean' ? raw.showUnit : d.showUnit,
    face: colorOrEmpty(raw.face, d.face),
    ring: colorOrEmpty(raw.ring, d.ring),
    ringWidth: num(raw.ringWidth, d.ringWidth, 0, 30),
    ticks: oneOf(raw.ticks, ['none', 'quarters', 'hours'] as const, d.ticks),
    tickColor: color(raw.tickColor, d.tickColor),
    hourColor: color(raw.hourColor, d.hourColor),
    minuteColor: color(raw.minuteColor, d.minuteColor),
    handWidth: num(raw.handWidth, d.handWidth, 1, 30),
    shape: oneOf(raw.shape, ['rect', 'ellipse'] as const, d.shape),
    w: num(raw.w, d.w, 1, 800),
    h: num(raw.h, d.h, 1, 800),
    radius: num(raw.radius, d.radius, 0, 400),
  };
}

export function normalizeDesign(raw: unknown): Design {
  if (!isObj(raw)) throw new Error('Это не дизайн: ожидался JSON-объект');
  const base = newDesign();
  const bgRaw = isObj(raw.bg) ? raw.bg : {};
  const layersRaw = Array.isArray(raw.layers) ? raw.layers : [];
  const layers = layersRaw.map(normalizeLayer).filter((l): l is Layer => l !== null).slice(0, 40);
  const seen = new Set<string>();
  for (const l of layers) {
    if (seen.has(l.id)) l.id = uid('l');
    seen.add(l.id);
  }
  return {
    v: 1,
    id: str(raw.id, base.id) || base.id,
    name: str(raw.name, 'Без названия').slice(0, 60) || 'Без названия',
    cols: Math.round(num(raw.cols, 4, 1, MAX_GRID)),
    rows: Math.round(num(raw.rows, 2, 1, MAX_GRID)),
    locale: oneOf(raw.locale, ['ru', 'en'] as const, 'ru'),
    bg: {
      type: oneOf(bgRaw.type, ['none', 'solid', 'gradient'] as const, 'gradient'),
      c1: color(bgRaw.c1, '#1b1b2f'),
      c2: color(bgRaw.c2, '#162447'),
      angle: num(bgRaw.angle, 135, 0, 360),
      radius: num(bgRaw.radius, 24, 0, 200),
      opacity: num(bgRaw.opacity, 1, 0, 1),
    },
    layers,
    updatedAt: num(raw.updatedAt, Date.now(), 0, Number.MAX_SAFE_INTEGER),
  };
}

/** Копия с новыми id — для шаблонов и дублирования. */
export function cloneDesign(d: Design, name?: string): Design {
  return {
    ...d,
    id: uid('d'),
    name: name ?? d.name,
    layers: d.layers.map((l) => ({ ...l, id: uid('l') })),
    bg: { ...d.bg },
    updatedAt: Date.now(),
  };
}
