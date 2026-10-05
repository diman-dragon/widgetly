import { describe, expect, it } from 'vitest';
import { conditionText, formatDate, rangeText, tempText, weatherIcon } from './format';
import { gradientPoints } from './content';

// Понедельник, 5 октября 2026, 14:07 (локальное время). Те же ожидания — в TextFormatTest.kt.
const d = new Date(2026, 9, 5, 14, 7, 0);

describe('formatDate', () => {
  it('24-часовой формат', () => expect(formatDate('HH:mm', d, 'ru')).toBe('14:07'));
  it('12-часовой формат', () => {
    expect(formatDate('hh:mm A', d, 'en')).toBe('02:07 PM');
    expect(formatDate('h:mm a', d, 'en')).toBe('2:07 pm');
  });
  it('полночь в 12-часовом формате', () => expect(formatDate('h A', new Date(2026, 0, 1, 0, 5), 'en')).toBe('12 AM'));
  it('дата по-русски', () => {
    expect(formatDate('EEE, d MMM', d, 'ru')).toBe('Пн, 5 окт');
    expect(formatDate('EEEE, d MMMM yyyy', d, 'ru')).toBe('Понедельник, 5 октября 2026');
  });
  it('дата по-английски', () => expect(formatDate('EEE, d MMM', d, 'en')).toBe('Mon, 5 Oct'));
  it('числовые токены', () => expect(formatDate('dd.MM.yy', d, 'ru')).toBe('05.10.26'));
  it('литералы в кавычках', () => {
    expect(formatDate("HH 'ч' mm 'мин'", d, 'ru')).toBe('14 ч 07 мин');
    expect(formatDate("''HH''", d, 'ru')).toBe("'14'");
  });
});

describe('погода', () => {
  it('температура', () => {
    expect(tempText(21.4, 'C', false)).toBe('21°');
    expect(tempText(21.5, 'C', true)).toBe('22°C');
    expect(tempText(-0.4, 'C', false)).toBe('0°');
    expect(tempText(-3.5, 'C', false)).toBe('-3°');
    expect(tempText(20, 'F', true)).toBe('68°F');
  });
  it('диапазон', () => {
    const w = { tempC: 10, code: 3, isDay: true, hiC: 12.4, loC: 3.6, city: '' };
    expect(rangeText(w, 'C')).toBe('↑12° ↓4°');
    expect(rangeText(null, 'C')).toBe('--');
  });
  it('иконки и описания', () => {
    expect(weatherIcon(0, true)).toBe('☀️');
    expect(weatherIcon(0, false)).toBe('🌙');
    expect(weatherIcon(999, true)).toBe('❓');
    expect(conditionText(63, 'ru')).toBe('Дождь');
    expect(conditionText(95, 'en')).toBe('Thunderstorm');
    expect(conditionText(1234, 'en')).toBe('—');
  });
});

describe('градиент', () => {
  it('угол 90 идёт слева направо', () => {
    const g = gradientPoints(200, 100, 90);
    expect(g.x1).toBeCloseTo(0);
    expect(g.x2).toBeCloseTo(200);
    expect(g.y1).toBeCloseTo(50);
    expect(g.y2).toBeCloseTo(50);
  });
  it('угол 180 идёт сверху вниз', () => {
    const g = gradientPoints(200, 100, 180);
    expect(g.y1).toBeCloseTo(0);
    expect(g.y2).toBeCloseTo(100);
  });
});
