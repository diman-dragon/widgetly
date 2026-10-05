/**
 * Единый формат дизайна виджета (версия 1).
 * Тот же JSON читает нативный рендерер на Kotlin (DesignRenderer.kt).
 * Любое изменение здесь — одновременно менять и Kotlin.
 *
 * Геометрия: все размеры в "dp дизайна". Базовый размер холста:
 *   W0 = 70 * cols - 30,  H0 = 70 * rows - 30  (формула размера ячеек Android).
 * Позиции x, y — доли (0..1) от реального размера виджета.
 * Масштаб k = min(wPx / W0, hPx / H0).
 */
export type LayerType =
  | 'text'
  | 'clockDigital'
  | 'clockAnalog'
  | 'date'
  | 'weatherIcon'
  | 'weatherTemp'
  | 'weatherCondition'
  | 'weatherCity'
  | 'weatherRange'
  | 'shape';

export type FontKey = 'sans' | 'sans-light' | 'sans-thin' | 'condensed' | 'serif' | 'mono';
export type Align = 'left' | 'center' | 'right';

export interface Layer {
  id: string;
  type: LayerType;
  /** Якорь по горизонтали, доля ширины 0..1 */
  x: number;
  /** Центр по вертикали, доля высоты 0..1 */
  y: number;
  opacity: number;

  // Текстовые слои
  size: number;
  color: string;
  align: Align;
  font: FontKey;
  weight: 'normal' | 'bold';
  /** В долях em */
  letterSpacing: number;
  text: string;
  format: string;
  unit: 'C' | 'F';
  showUnit: boolean;

  // Аналоговые часы (size — диаметр)
  face: string; // '' = без заливки
  ring: string; // '' = без обода
  ringWidth: number;
  ticks: 'none' | 'quarters' | 'hours';
  tickColor: string;
  hourColor: string;
  minuteColor: string;
  handWidth: number;

  // Фигура
  shape: 'rect' | 'ellipse';
  w: number;
  h: number;
  radius: number;
}

export interface Background {
  type: 'none' | 'solid' | 'gradient';
  c1: string;
  c2: string;
  angle: number;
  radius: number;
  opacity: number;
}

export interface Design {
  v: 1;
  id: string;
  name: string;
  cols: number;
  rows: number;
  locale: 'ru' | 'en';
  bg: Background;
  layers: Layer[];
  updatedAt: number;
}

export interface WeatherSample {
  tempC: number;
  code: number;
  isDay: boolean;
  hiC: number;
  loC: number;
  /** Пустая строка = "текущее место" */
  city: string;
}

export const MAX_GRID = 8;

export function baseWidth(cols: number): number {
  return 70 * cols - 30;
}
export function baseHeight(rows: number): number {
  return 70 * rows - 30;
}
