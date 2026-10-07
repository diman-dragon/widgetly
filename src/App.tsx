import { useEffect, useState } from 'react'
import { Eye, Maximize, MoreHorizontal, Pencil, Redo2, RotateCcw, SlidersHorizontal, Undo2 } from 'lucide-react'
import {
  countLeaves, createWidget, find, isLeaf, leaf, prune, remove, setRatio, split, starter, uid, update, widgetName,
  type Direction, type Widget, type WidgetType
} from './model'
import { useHistory } from './history'
import { loadSnapshot, saveSnapshot } from './storage'
import { Canvas, type CanvasCtx } from './components/Canvas'
import { CatalogList } from './components/Catalog'
import { Inspector } from './components/Inspector'
import { Sheet } from './components/Sheet'

type SheetKind = 'catalog' | 'settings' | 'menu' | null

const isTyping = (t: EventTarget | null) =>
  t instanceof HTMLElement && (t.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(t.tagName))

/** В режиме просмотра панель управления прячется и появляется при любом движении. */
function useAwake(active: boolean, ms = 2800) {
  const [awake, setAwake] = useState(true)
  useEffect(() => {
    if (!active) return
    let timer = 0
    const wake = () => { setAwake(true); window.clearTimeout(timer); timer = window.setTimeout(() => setAwake(false), ms) }
    wake()
    const events = ['pointermove', 'pointerdown', 'keydown', 'touchstart'] as const
    events.forEach(ev => window.addEventListener(ev, wake, { passive: true }))
    return () => { window.clearTimeout(timer); events.forEach(ev => window.removeEventListener(ev, wake)) }
  }, [active, ms])
  return awake
}

export function App() {
  const { snapshot, canUndo, canRedo, commit, undo, redo } = useHistory(() => loadSnapshot() ?? starter())
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [mode, setMode] = useState<'edit' | 'preview'>('edit')
  const [placing, setPlacing] = useState<WidgetType | null>(null)
  const [sheet, setSheet] = useState<SheetKind>(null)
  const [confirmReset, setConfirmReset] = useState(false)
  const [fullscreen, setFullscreen] = useState(false)
  const awake = useAwake(mode === 'preview')

  useEffect(() => saveSnapshot(snapshot), [snapshot])
  useEffect(() => {
    const onChange = () => setFullscreen(!!document.fullscreenElement)
    document.addEventListener('fullscreenchange', onChange)
    return () => document.removeEventListener('fullscreenchange', onChange)
  }, [])

  // Выбранная область могла исчезнуть после отмены/повтора — тогда выбор снимается.
  const selNode = selectedId ? find(snapshot.root, selectedId) : null
  const sel = selNode && isLeaf(selNode) ? selNode : null
  const selWidget: Widget | null = sel?.widgetId ? snapshot.widgets[sel.widgetId] ?? null : null

  /* ───── действия ───── */
  const place = (leafId: string, type: WidgetType) => {
    const w = createWidget(type)
    commit(s => {
      const n = find(s.root, leafId)
      if (!n || !isLeaf(n)) return s
      const root = update(s.root, leafId, x => (isLeaf(x) ? { ...x, widgetId: w.id } : x))
      return prune({ root, widgets: { ...s.widgets, [w.id]: w } })
    })
    setSelectedId(leafId); setPlacing(null); setSheet(null)
  }
  const pick = (type: WidgetType) => {
    if (sel) place(sel.id, type)
    else { setPlacing(type); setSheet(null) }
  }
  const move = (from: string, to: string) => {
    if (from === to) return
    commit(s => {
      const a = find(s.root, from), b = find(s.root, to)
      if (!a || !b || !isLeaf(a) || !isLeaf(b)) return s
      let root = update(s.root, from, n => (isLeaf(n) ? { ...n, widgetId: b.widgetId } : n))
      root = update(root, to, n => (isLeaf(n) ? { ...n, widgetId: a.widgetId } : n))
      return { root, widgets: s.widgets }
    })
    setSelectedId(to)
  }
  const splitArea = (leafId: string, direction: Direction) =>
    commit(s => ({ root: split(s.root, leafId, direction), widgets: s.widgets }))
  const duplicate = (leafId: string) => {
    const n = find(snapshot.root, leafId)
    if (!n || !isLeaf(n)) return
    const src = n.widgetId ? snapshot.widgets[n.widgetId] : undefined
    const copy: Widget | null = src ? { ...structuredClone(src), id: uid('widget') } : null
    const created = leaf(copy ? copy.id : null)
    commit(s => ({
      root: split(s.root, leafId, 'vertical', created),
      widgets: copy ? { ...s.widgets, [copy.id]: copy } : s.widgets
    }))
    setSelectedId(created.id)
  }
  const deleteArea = (leafId: string) => {
    commit(s => {
      const root = countLeaves(s.root) <= 1
        ? update(s.root, leafId, n => (isLeaf(n) ? { ...n, widgetId: null } : n))
        : remove(s.root, leafId)
      return prune({ root, widgets: s.widgets })
    })
    setSelectedId(null)
  }
  const clearWidget = (leafId: string) =>
    commit(s => prune({ root: update(s.root, leafId, n => (isLeaf(n) ? { ...n, widgetId: null } : n)), widgets: s.widgets }))
  const patchWidget = (id: string, patch: Partial<Widget>, key: string) =>
    commit(s => {
      const w = s.widgets[id]
      return w ? { root: s.root, widgets: { ...s.widgets, [id]: { ...w, ...patch } } } : s
    }, key)
  const changeRatio = (splitId: string, ratio: number) =>
    commit(s => ({ root: setRatio(s.root, splitId, ratio), widgets: s.widgets }), `ratio:${splitId}`)
  const resetAll = () => { commit(() => starter()); setSelectedId(null); setPlacing(null); setSheet(null); setConfirmReset(false) }

  const toggleFullscreen = async () => {
    try {
      if (document.fullscreenElement) await document.exitFullscreen()
      else await document.documentElement.requestFullscreen()
    } catch { /* не поддерживается WebView — просмотр работает и без этого */ }
  }
  const exitPreview = () => {
    setMode('edit')
    if (document.fullscreenElement) void document.exitFullscreen().catch(() => undefined)
  }
  const openSheet = (k: SheetKind) => { setSheet(k); setConfirmReset(false) }

  /* ───── клавиатура ───── */
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const meta = e.ctrlKey || e.metaKey
      const typing = isTyping(e.target)
      if (e.key === 'Escape') {
        if (sheet) setSheet(null)
        else if (mode === 'preview') exitPreview()
        else if (placing) setPlacing(null)
        else setSelectedId(null)
      } else if (typing) {
        return
      } else if (meta && e.key.toLowerCase() === 'z') { e.preventDefault(); if (e.shiftKey) redo(); else undo() }
      else if (meta && e.key.toLowerCase() === 'y') { e.preventDefault(); redo() }
      else if ((e.key === 'Delete' || e.key === 'Backspace') && mode === 'edit' && sel) { e.preventDefault(); deleteArea(sel.id) }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  const ctx: CanvasCtx = {
    snapshot, selected: sel?.id ?? null, mode, placing,
    onSelect: setSelectedId, onPlace: place, onMove: move, onSplit: splitArea,
    onDuplicate: duplicate, onDelete: deleteArea, onRatio: changeRatio
  }
  const inspector = (
    <Inspector hasArea={!!sel} widget={selWidget} onPatch={patchWidget} onClear={() => sel && clearWidget(sel.id)} />
  )

  return (
    <div className={'app ' + mode}>
      {mode === 'edit' && (
        <header className="topbar">
          <div className="brand"><strong>Widgetly</strong><span>конструктор</span></div>
          <div className="toolbar">
            <button disabled={!canUndo} onClick={undo} aria-label="Отменить" title="Отменить (Ctrl+Z)"><Undo2 /></button>
            <button disabled={!canRedo} onClick={redo} aria-label="Вернуть" title="Вернуть (Ctrl+Shift+Z)"><Redo2 /></button>
          </div>
          <div className="top-actions">
            <button className="mobile-only" onClick={() => openSheet('catalog')}>Виджеты</button>
            <button className="mobile-only" onClick={() => openSheet('settings')} aria-label="Настройки виджета" title="Настройки виджета"><SlidersHorizontal /></button>
            <button className="primary" onClick={() => { setPlacing(null); setSelectedId(null); setMode('preview') }}><Eye /><span>Просмотр</span></button>
            <button className="icon" onClick={() => openSheet('menu')} aria-label="Меню" title="Меню"><MoreHorizontal /></button>
          </div>
        </header>
      )}

      {mode === 'edit' && placing && (
        <div className="placing">Нажмите на область для «{widgetName[placing]}» · <button onClick={() => setPlacing(null)}>Отмена</button></div>
      )}

      <div className="workspace">
        {mode === 'edit' && (
          <aside className="catalog">
            <div className="panel-title">Виджеты</div>
            <div className="catalog-list"><CatalogList onPick={pick} /></div>
          </aside>
        )}
        <main className={'canvas-area ' + (mode === 'edit' ? 'edit-canvas' : 'preview-canvas')} onClick={() => mode === 'edit' && setSelectedId(null)}>
          <Canvas node={snapshot.root} ctx={ctx} />
        </main>
        {mode === 'edit' && (
          <aside className="settings">
            <div className="panel-title">Настройки</div>
            {inspector}
          </aside>
        )}
      </div>

      {mode === 'preview' && (
        <div className={'preview-bar' + (awake ? ' awake' : '')}>
          <button onClick={toggleFullscreen} aria-label={fullscreen ? 'Выйти из полного экрана' : 'На весь экран'} title="На весь экран"><Maximize /></button>
          <button onClick={exitPreview}><Pencil /><span>Редактировать</span></button>
        </div>
      )}

      {sheet === 'catalog' && <Sheet title="Виджеты" onClose={() => setSheet(null)}><CatalogList onPick={pick} /></Sheet>}
      {sheet === 'settings' && <Sheet title="Настройки виджета" onClose={() => setSheet(null)}>{inspector}</Sheet>}
      {sheet === 'menu' && (
        <Sheet title="Меню" onClose={() => setSheet(null)}>
          <div className="menu">
            {confirmReset ? (
              <>
                <p>Экран вернётся к исходному шаблону. Это действие можно отменить кнопкой «Отменить».</p>
                <button className="danger" onClick={resetAll}><RotateCcw />Да, сбросить</button>
                <button onClick={() => setConfirmReset(false)}>Не сбрасывать</button>
              </>
            ) : (
              <button onClick={() => setConfirmReset(true)}><RotateCcw />Сбросить к шаблону</button>
            )}
          </div>
        </Sheet>
      )}
    </div>
  )
}
