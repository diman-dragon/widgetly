import { useCallback, useReducer } from 'react'
import type { Snapshot } from './model'

type State = { past: Snapshot[]; present: Snapshot; future: Snapshot[]; key: string | null; at: number }
type Action =
  | { type: 'commit'; fn: (s: Snapshot) => Snapshot; key?: string; at: number }
  | { type: 'undo' }
  | { type: 'redo' }

const LIMIT = 100
/** Правки с одним ключом в пределах окна склеиваются в один шаг истории (ползунки, перетаскивание, ввод текста). */
const COALESCE_MS = 900

export function reducer(h: State, a: Action): State {
  switch (a.type) {
    case 'commit': {
      const next = a.fn(h.present)
      if (next === h.present) return h
      if (a.key && a.key === h.key && a.at - h.at < COALESCE_MS) {
        return { ...h, present: next, at: a.at, future: [] }
      }
      return { past: [...h.past, h.present].slice(-LIMIT), present: next, future: [], key: a.key ?? null, at: a.at }
    }
    case 'undo': {
      const prev = h.past[h.past.length - 1]
      if (!prev) return h
      return { past: h.past.slice(0, -1), present: prev, future: [h.present, ...h.future], key: null, at: 0 }
    }
    case 'redo': {
      const next = h.future[0]
      if (!next) return h
      return { past: [...h.past, h.present], present: next, future: h.future.slice(1), key: null, at: 0 }
    }
  }
}

export function useHistory(initial: () => Snapshot) {
  const [h, dispatch] = useReducer(reducer, undefined, (): State => ({ past: [], present: initial(), future: [], key: null, at: 0 }))
  const commit = useCallback((fn: (s: Snapshot) => Snapshot, key?: string) => dispatch({ type: 'commit', fn, key, at: Date.now() }), [])
  const undo = useCallback(() => dispatch({ type: 'undo' }), [])
  const redo = useCallback(() => dispatch({ type: 'redo' }), [])
  return { snapshot: h.present, canUndo: h.past.length > 0, canRedo: h.future.length > 0, commit, undo, redo }
}
