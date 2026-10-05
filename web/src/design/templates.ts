import type { Design } from './types';
import { cloneDesign, defaultLayer, newDesign } from './defaults';

export interface Template {
  key: string;
  build: () => Design;
}

function make(name: string, patch: Partial<Design>, layers: Design['layers']): Design {
  const d = newDesign(name);
  return { ...d, ...patch, name, layers };
}

export const TEMPLATES: Template[] = [
  {
    key: 'night',
    build: () =>
      make('Ночной', {}, [
        defaultLayer('clockDigital', { x: 0.07, y: 0.4, size: 46, weight: 'bold' }),
        defaultLayer('date', { x: 0.07, y: 0.78, size: 15, format: 'EEE, d MMM' }),
        defaultLayer('weatherIcon', { x: 0.85, y: 0.3, size: 34 }),
        defaultLayer('weatherTemp', { x: 0.85, y: 0.7, size: 26 }),
      ]),
  },
  {
    key: 'paper',
    build: () =>
      make(
        'Бумага',
        { bg: { type: 'solid', c1: '#f1ede4', c2: '#f1ede4', angle: 0, radius: 20, opacity: 1 } },
        [
          defaultLayer('clockDigital', { x: 0.07, y: 0.4, size: 46, font: 'sans-thin', color: '#23211c', weight: 'normal' }),
          defaultLayer('date', { x: 0.07, y: 0.8, size: 14, color: '#6b665a', format: 'EEE, d MMM' }),
          defaultLayer('weatherTemp', { x: 0.93, y: 0.4, size: 28, color: '#23211c', align: 'right', font: 'sans-light' }),
          defaultLayer('weatherRange', { x: 0.93, y: 0.8, size: 13, color: '#6b665a', align: 'right' }),
        ],
      ),
  },
  {
    key: 'analog',
    build: () =>
      make(
        'Циферблат',
        { bg: { type: 'gradient', c1: '#0f2027', c2: '#2c5364', angle: 160, radius: 28, opacity: 1 } },
        [
          defaultLayer('clockAnalog', { x: 0.2, y: 0.5, size: 92, face: '#ffffff', ring: '#e8e8e8', ringWidth: 3, tickColor: '#222222', hourColor: '#111111', minuteColor: '#111111', ticks: 'hours', opacity: 1 }),
          defaultLayer('date', { x: 0.42, y: 0.34, size: 17, format: 'EEEE', align: 'left', color: '#ffffff', weight: 'bold' }),
          defaultLayer('date', { x: 0.42, y: 0.56, size: 14, format: 'd MMMM', align: 'left', color: '#bcd3dc' }),
          defaultLayer('weatherTemp', { x: 0.42, y: 0.8, size: 24, align: 'left', unit: 'C' }),
        ],
      ),
  },
  {
    key: 'sunset',
    build: () =>
      make(
        'Закат',
        { bg: { type: 'gradient', c1: '#ff7e5f', c2: '#6a3093', angle: 120, radius: 30, opacity: 1 } },
        [
          defaultLayer('clockDigital', { x: 0.5, y: 0.42, size: 58, align: 'center', weight: 'bold', letterSpacing: 0.02 }),
          defaultLayer('date', { x: 0.5, y: 0.78, size: 16, align: 'center', color: '#ffe9e0', format: 'EEE, d MMMM' }),
        ],
      ),
  },
  {
    key: 'big',
    build: () =>
      make(
        'Крупное время',
        { cols: 4, rows: 2, bg: { type: 'solid', c1: '#000000', c2: '#000000', angle: 0, radius: 18, opacity: 0.85 } },
        [
          defaultLayer('clockDigital', { x: 0.5, y: 0.5, size: 72, align: 'center', font: 'condensed', weight: 'bold', color: '#ffffff', format: 'H:mm' }),
        ],
      ),
  },
  {
    key: 'weather',
    build: () =>
      make(
        'Погода',
        { cols: 4, rows: 2, bg: { type: 'gradient', c1: '#3a7bd5', c2: '#00d2ff', angle: 150, radius: 26, opacity: 1 } },
        [
          defaultLayer('weatherIcon', { x: 0.14, y: 0.42, size: 46 }),
          defaultLayer('weatherTemp', { x: 0.3, y: 0.4, size: 36, weight: 'bold', align: 'left' }),
          defaultLayer('weatherCondition', { x: 0.3, y: 0.8, size: 11, align: 'left', color: '#eaf6ff' }),
          defaultLayer('weatherCity', { x: 0.94, y: 0.2, size: 13, align: 'right', color: '#eaf6ff' }),
          defaultLayer('weatherRange', { x: 0.94, y: 0.48, size: 13, align: 'right', color: '#eaf6ff' }),
        ],
      ),
  },
];

export function fromTemplate(key: string): Design {
  const t = TEMPLATES.find((x) => x.key === key) ?? TEMPLATES[0];
  return cloneDesign(t.build());
}
