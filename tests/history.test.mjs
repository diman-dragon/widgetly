import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import ts from 'typescript'
import { writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { pathToFileURL } from 'node:url'
import { root } from './helpers.mjs'

// history.ts импортирует react только ради хука; для теста редьюсера подставляем заглушку.
const src = readFileSync(join(root, 'src/history.ts'), 'utf8')
  .replace(/import \{[^}]*\} from 'react'/, 'const useCallback=f=>f, useReducer=()=>{}')
  .replace(/import type \{[^}]*\} from '\.\/model'/, '')
const js = ts.transpileModule(src, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
const dir = mkdtempSync(join(tmpdir(), 'widgetly-h-'))
writeFileSync(join(dir, 'history.mjs'), js)
const { reducer } = await import(pathToFileURL(join(dir, 'history.mjs')).href)

const init = v => ({ past: [], present: v, future: [], key: null, at: 0 })
const commit = (h, v, key, at) => reducer(h, { type: 'commit', fn: () => v, key, at })

test('commit pushes history and clears redo', () => {
  let h = commit(init(1), 2, undefined, 0)
  h = commit(h, 3, undefined, 10)
  assert.deepEqual(h.past, [1, 2]); assert.equal(h.present, 3)
  h = reducer(h, { type: 'undo' })
  assert.equal(h.present, 2); assert.deepEqual(h.future, [3])
  h = commit(h, 9, undefined, 20)
  assert.deepEqual(h.future, [])
})

test('same-key commits inside the window collapse into one history step', () => {
  let h = commit(init(0), 1, 'ratio', 1000)
  h = commit(h, 2, 'ratio', 1100)
  h = commit(h, 3, 'ratio', 1200)
  assert.deepEqual(h.past, [0]); assert.equal(h.present, 3)
  h = commit(h, 4, 'ratio', 5000)   // окно истекло
  assert.deepEqual(h.past, [0, 3])
  h = commit(h, 5, 'other', 5050)   // другой ключ
  assert.deepEqual(h.past, [0, 3, 4])
})

test('undo/redo are no-ops at the ends; identical state is not recorded', () => {
  const h = init(1)
  assert.equal(reducer(h, { type: 'undo' }), h)
  assert.equal(reducer(h, { type: 'redo' }), h)
  assert.equal(commit(h, 1, undefined, 0), h)
})

test('history is capped', () => {
  let h = init(0)
  for (let i = 1; i <= 250; i++) h = commit(h, i, undefined, i * 10_000)
  assert.equal(h.past.length, 100)
})
