import type { Design, Layer, WeatherSample } from './types';
import { baseHeight, baseWidth } from './types';
import { cityText, conditionText, formatDate, noDataText, rangeText, tempText, weatherIcon } from './format';

/** Текст слоя. Идентично layerText() в DesignRenderer.kt. */
export function layerText(l: Layer, locale: 'ru' | 'en', now: Date, w: WeatherSample | null): string {
  switch (l.type) {
    case 'text':
      return l.text;
    case 'clockDigital':
    case 'date':
      return formatDate(l.format, now, locale);
    case 'weatherIcon':
      return w ? weatherIcon(w.code, w.isDay) : '❓';
    case 'weatherTemp':
      return w ? tempText(w.tempC, l.unit, l.showUnit) : '--°';
    case 'weatherCondition':
      return w ? conditionText(w.code, locale) : noDataText(locale);
    case 'weatherCity':
      return cityText(w, locale);
    case 'weatherRange':
      return rangeText(w, l.unit);
    default:
      return '';
  }
}

export interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}

/** Приблизительная рамка слоя в координатах дизайна (для выделения и зоны касания). */
export function layerBox(l: Layer, d: Design, now: Date, wx: WeatherSample | null): Box {
  const W = baseWidth(d.cols);
  const H = baseHeight(d.rows);
  const cx = l.x * W;
  const cy = l.y * H;
  if (l.type === 'clockAnalog') return { x: cx - l.size / 2, y: cy - l.size / 2, w: l.size, h: l.size };
  if (l.type === 'shape') return { x: cx - l.w / 2, y: cy - l.h / 2, w: l.w, h: l.h };
  if (l.type === 'weatherIcon') {
    const s = l.size * 1.25;
    return { x: cx - s / 2, y: cy - s / 2, w: s, h: s };
  }
  const text = layerText(l, d.locale, now, wx);
  const factor = l.type === 'clockDigital' ? 0.56 : 0.54;
  const width = Math.max(l.size * 0.8, text.length * l.size * factor);
  const height = l.size * 1.2;
  const left = l.align === 'left' ? cx : l.align === 'right' ? cx - width : cx - width / 2;
  return { x: left, y: cy - height / 2, w: width, h: height };
}

/** Точки градиента: CSS-подобный угол (0 — вверх, 90 — вправо). Идентично Kotlin. */
export function gradientPoints(w: number, h: number, angleDeg: number) {
  const a = (angleDeg * Math.PI) / 180;
  const dx = Math.sin(a);
  const dy = -Math.cos(a);
  const half = (Math.abs(w * dx) + Math.abs(h * dy)) / 2;
  const cx = w / 2;
  const cy = h / 2;
  return { x1: cx - dx * half, y1: cy - dy * half, x2: cx + dx * half, y2: cy + dy * half };
}
