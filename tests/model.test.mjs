import test from 'node:test'
import assert from 'node:assert/strict'
import { loadTs } from './helpers.mjs'

const m = await loadTs('src/model.ts')

const widgetsOf = s => Object.keys(s.widgets).sort()

test('starter() is a valid snapshot and survives sanitize()', () => {
  const s = m.starter()
  assert.equal(m.countLeaves(s.root), 4)
  assert.deepEqual(m.collect(s.root).sort(), widgetsOf(s))
  assert.deepEqual(m.sanitize(JSON.parse(JSON.stringify(s))), s)
})

test('split() divides only a leaf, keeps the original first, and passes the target id', () => {
  const l = m.leaf('w1')
  const other = m.leaf('w2')
  const root = { id: 'r', kind: 'split', direction: 'vertical', ratio: 0.5, first: l, second: other }
  const created = m.leaf()
  const next = m.split(root, l.id, 'horizontal', created)
  assert.equal(next.first.kind, 'split')
  assert.equal(next.first.direction, 'horizontal')
  assert.equal(next.first.first.id, l.id)
  assert.equal(next.first.second.id, created.id)
  assert.equal(next.second.id, other.id)
  // деление «split»-узла ничего не меняет
  assert.deepEqual(m.split(root, 'r', 'vertical'), root)
  // неизвестный id ничего не меняет
  assert.deepEqual(m.split(root, 'nope', 'vertical'), root)
})

test('remove() lifts the sibling and never deletes the only leaf', () => {
  const a = m.leaf('a'), b = m.leaf('b'), c = m.leaf('c')
  const inner = { id: 'i', kind: 'split', direction: 'vertical', ratio: 0.5, first: b, second: c }
  const root = { id: 'r', kind: 'split', direction: 'horizontal', ratio: 0.5, first: a, second: inner }
  assert.equal(m.remove(root, a.id).id, 'i')
  const r2 = m.remove(root, b.id)
  assert.equal(r2.second.id, c.id)
  assert.equal(m.countLeaves(r2), 2)
  assert.equal(m.remove(a, a.id), a)
})

test('setRatio() clamps to the allowed range and ignores leaves', () => {
  const l = m.leaf()
  const root = { id: 'r', kind: 'split', direction: 'vertical', ratio: 0.5, first: l, second: m.leaf() }
  assert.equal(m.setRatio(root, 'r', -5).ratio, m.MIN_RATIO)
  assert.equal(m.setRatio(root, 'r', 9).ratio, m.MAX_RATIO)
  assert.equal(m.setRatio(root, 'r', NaN).ratio, 0.5)
  assert.equal(m.setRatio(root, l.id, 0.3).first.kind, 'leaf')
})

test('prune() drops widgets that are not placed in any area', () => {
  const s = m.starter()
  const extra = m.createWidget('text')
  const pruned = m.prune({ root: s.root, widgets: { ...s.widgets, [extra.id]: extra } })
  assert.equal(pruned.widgets[extra.id], undefined)
  assert.equal(Object.keys(pruned.widgets).length, 4)
})

test('createWidget() gives every type sane defaults', () => {
  for (const type of Object.keys(m.widgetName)) {
    const w = m.createWidget(type)
    assert.equal(w.type, type)
    assert.ok(w.scale >= m.SCALE_MIN && w.scale <= m.SCALE_MAX)
  }
  assert.ok(m.createWidget('world').zones.length > 0)
})

test('parseItems() recognises times and plain lines', () => {
  assert.deepEqual(m.parseItems('9:05 Завтрак\n\n  Позвонить  \n14.30 Встреча'), [
    { lead: '09:05', text: 'Завтрак' },
    { lead: '', text: 'Позвонить' },
    { lead: '14:30', text: 'Встреча' }
  ])
  assert.deepEqual(m.parseItems(undefined), [])
  assert.equal(m.parseItems('a\n'.repeat(50)).length, 12)
})

test('sanitize() rejects garbage', () => {
  for (const bad of [null, undefined, 1, 'x', [], {}, { widgets: {} }, { widgets: {}, root: null }, { widgets: {}, root: { id: 'x', kind: 'weird' } }]) {
    assert.equal(m.sanitize(bad), null)
  }
})

test('sanitize() repairs broken widget references and values', () => {
  const raw = {
    widgets: {
      a: { id: 'a', type: 'metric', value: 'x'.repeat(500), scale: 99, opacity: -4, align: 'nope', accent: 'yes' },
      b: { id: 'b', type: 'unknown' },
      c: { id: 'c', type: 'world', zones: ['Asia/Tokyo', 'Mars/Base', 42, 'Asia/Tokyo'] },
      orphan: { id: 'orphan', type: 'clock' },
      __proto__: { type: 'clock' }
    },
    root: {
      id: 'r', kind: 'split', direction: 'vertical', ratio: 7,
      first: { id: 'l1', kind: 'leaf', widgetId: 'a' },
      second: { id: 's', kind: 'split', direction: 'horizontal', ratio: 'x',
        first: { id: 'l2', kind: 'leaf', widgetId: 'a' },          // дубль ссылки на виджет
        second: { id: 'l3', kind: 'leaf', widgetId: 'b' } }         // виджет невалиден
    }
  }
  const s = m.sanitize(JSON.parse(JSON.stringify(raw)))
  assert.ok(s)
  assert.equal(s.root.ratio, m.MAX_RATIO)
  assert.equal(s.root.second.ratio, 0.5)
  assert.equal(s.root.first.widgetId, 'a')
  assert.equal(s.root.second.first.widgetId, null)
  assert.equal(s.root.second.second.widgetId, null)
  assert.deepEqual(widgetsOf(s), ['a'])
  const a = s.widgets.a
  assert.equal(a.value.length, 40)
  assert.equal(a.scale, m.SCALE_MAX)
  assert.equal(a.opacity, 0.2)
  assert.equal(a.align, 'center')
  assert.equal(a.accent, false)
})

test('sanitize() rejects duplicate ids and over-deep trees', () => {
  const dup = { widgets: {}, root: { id: 'r', kind: 'split', direction: 'vertical', ratio: 0.5, first: { id: 'x', kind: 'leaf', widgetId: null }, second: { id: 'x', kind: 'leaf', widgetId: null } } }
  assert.equal(m.sanitize(dup), null)
  let deep = { id: 'leaf-end', kind: 'leaf', widgetId: null }
  for (let i = 0; i < 60; i++) deep = { id: 's' + i, kind: 'split', direction: 'vertical', ratio: 0.5, first: deep, second: { id: 'l' + i, kind: 'leaf', widgetId: null } }
  assert.equal(m.sanitize({ widgets: {}, root: deep }), null)
})

test('every world-clock zone is a real IANA time zone', () => {
  for (const z of m.WORLD_ZONES) assert.doesNotThrow(() => new Intl.DateTimeFormat('ru-RU', { timeZone: z.id }))
  for (const z of m.DEFAULT_ZONES) assert.ok(m.WORLD_ZONES.some(x => x.id === z))
})
