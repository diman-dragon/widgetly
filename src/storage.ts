import { sanitize, type Snapshot } from './model'

const KEY = 'widgetly:v2'

export function loadSnapshot(): Snapshot | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as { snapshot?: unknown } | null
    return sanitize(parsed?.snapshot)
  } catch {
    return null
  }
}

export function saveSnapshot(snapshot: Snapshot): void {
  try {
    localStorage.setItem(KEY, JSON.stringify({ v: 2, snapshot }))
  } catch {
    /* приватный режим или переполненное хранилище — редактор продолжает работать */
  }
}
