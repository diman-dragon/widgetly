import { describe, expect, it } from 'vitest';
import { normalizeDesign, newDesign } from './defaults';
import { TEMPLATES } from './templates';

describe('normalizeDesign', () => {
  it('отклоняет не-объекты', () => {
    expect(() => normalizeDesign(null)).toThrow();
    expect(() => normalizeDesign([])).toThrow();
    expect(() => normalizeDesign('x')).toThrow();
  });
  it('подставляет значения по умолчанию и ограничивает диапазоны', () => {
    const d = normalizeDesign({ cols: 99, rows: -5, bg: { c1: 'red', opacity: 7 }, layers: [{ type: 'date', x: 5, color: 'zzz' }, { type: 'нет' }, 42] });
    expect(d.cols).toBe(8);
    expect(d.rows).toBe(1);
    expect(d.bg.c1).toBe('#1b1b2f');
    expect(d.bg.opacity).toBe(1);
    expect(d.layers).toHaveLength(1);
    expect(d.layers[0].x).toBe(1);
    expect(d.layers[0].color).toBe('#c8c8d0');
  });
  it('делает id слоёв уникальными', () => {
    const d = normalizeDesign({ layers: [{ id: 'a', type: 'text' }, { id: 'a', type: 'text' }] });
    expect(new Set(d.layers.map((l) => l.id)).size).toBe(2);
  });
  it('круговой обход: нормализованный дизайн не меняется', () => {
    const d = newDesign('X');
    expect(normalizeDesign(JSON.parse(JSON.stringify(d)))).toEqual(d);
  });
  it('все шаблоны валидны и стабильны при нормализации', () => {
    for (const t of TEMPLATES) {
      const d = t.build();
      expect(d.layers.length).toBeGreaterThan(0);
      expect(normalizeDesign(JSON.parse(JSON.stringify(d)))).toEqual(d);
    }
  });
});
