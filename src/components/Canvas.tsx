import { useRef, useState, type DragEvent, type KeyboardEvent, type PointerEvent } from 'react'
import { Columns2, Copy, GripVertical, Plus, Rows2, Trash2 } from 'lucide-react'
import type { AreaNode, Direction, Leaf, Snapshot, Split, WidgetType } from '../model'
import { WidgetView } from '../widgets'

export const DND_TYPE = 'application/x-widgetly-type'
export const DND_LEAF = 'application/x-widgetly-leaf'

export type CanvasCtx = {
  snapshot: Snapshot
  selected: string | null
  mode: 'edit' | 'preview'
  placing: WidgetType | null
  onSelect: (id: string | null) => void
  onPlace: (leafId: string, type: WidgetType) => void
  onMove: (from: string, to: string) => void
  onSplit: (leafId: string, direction: Direction) => void
  onDuplicate: (leafId: string) => void
  onDelete: (leafId: string) => void
  onRatio: (splitId: string, ratio: number) => void
}

export function Canvas({ node, ctx }: { node: AreaNode; ctx: CanvasCtx }) {
  return node.kind === 'split' ? <SplitView node={node} ctx={ctx} /> : <LeafView node={node} ctx={ctx} />
}

const hasDnd = (e: DragEvent) => {
  const types = Array.from(e.dataTransfer.types)
  return types.includes(DND_TYPE) || types.includes(DND_LEAF)
}

function LeafView({ node, ctx }: { node: Leaf; ctx: CanvasCtx }) {
  const [over, setOver] = useState(false)
  const edit = ctx.mode === 'edit'
  const widget = node.widgetId ? ctx.snapshot.widgets[node.widgetId] : undefined
  const selected = edit && ctx.selected === node.id

  const activate = () => {
    if (!edit) return
    if (ctx.placing) ctx.onPlace(node.id, ctx.placing)
    else ctx.onSelect(node.id)
  }
  const stop = (fn: () => void) => (e: { stopPropagation: () => void }) => { e.stopPropagation(); fn() }

  const cls = ['leaf', selected && 'selected', over && 'over', edit && ctx.placing && 'placing-target'].filter(Boolean).join(' ')
  return (
    <div
      className={cls}
      onClick={e => { e.stopPropagation(); activate() }}
      onKeyDown={(e: KeyboardEvent) => { if (edit && e.target === e.currentTarget && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); activate() } }}
      tabIndex={edit ? 0 : undefined}
      onDragOver={e => { if (edit && hasDnd(e)) { e.preventDefault(); setOver(true) } }}
      onDragLeave={e => { if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setOver(false) }}
      onDrop={e => {
        setOver(false)
        if (!edit || !hasDnd(e)) return
        e.preventDefault()
        const type = e.dataTransfer.getData(DND_TYPE)
        const from = e.dataTransfer.getData(DND_LEAF)
        if (type) ctx.onPlace(node.id, type as WidgetType)
        else if (from) ctx.onMove(from, node.id)
      }}
    >
      {widget ? <WidgetView widget={widget} /> : edit ? (
        <div className="empty"><Plus /><span>{ctx.placing ? 'Нажмите, чтобы добавить сюда' : 'Добавить виджет'}</span></div>
      ) : null}

      {selected && (
        <div className="leaf-actions" onClick={e => e.stopPropagation()}>
          <button aria-label="Разделить: сверху и снизу" title="Разделить: сверху и снизу" onClick={stop(() => ctx.onSplit(node.id, 'horizontal'))}><Rows2 /></button>
          <button aria-label="Разделить: слева и справа" title="Разделить: слева и справа" onClick={stop(() => ctx.onSplit(node.id, 'vertical'))}><Columns2 /></button>
          <button aria-label="Дублировать" title="Дублировать" onClick={stop(() => ctx.onDuplicate(node.id))}><Copy /></button>
          <button aria-label="Удалить область" title="Удалить область" onClick={stop(() => ctx.onDelete(node.id))}><Trash2 /></button>
          <span
            className="drag-handle"
            title="Перетащить на другую область"
            aria-hidden="true"
            draggable
            onDragStart={e => { e.stopPropagation(); e.dataTransfer.setData(DND_LEAF, node.id); e.dataTransfer.effectAllowed = 'move' }}
          ><GripVertical /></span>
        </div>
      )}
    </div>
  )
}

function SplitView({ node, ctx }: { node: Split; ctx: CanvasCtx }) {
  const box = useRef<HTMLDivElement>(null)
  const dragging = useRef(false)
  const edit = ctx.mode === 'edit'
  const sideBySide = node.direction === 'vertical'

  const ratioAt = (e: PointerEvent<HTMLDivElement>): number | null => {
    const el = box.current
    if (!el) return null
    const r = el.getBoundingClientRect()
    const g = e.currentTarget.getBoundingClientRect()
    const size = sideBySide ? r.width - g.width : r.height - g.height
    if (size <= 0) return null
    return ((sideBySide ? e.clientX - r.left : e.clientY - r.top) - (sideBySide ? g.width : g.height) / 2) / size
  }
  const end = (e: PointerEvent<HTMLDivElement>) => {
    dragging.current = false
    if (e.currentTarget.hasPointerCapture(e.pointerId)) e.currentTarget.releasePointerCapture(e.pointerId)
  }
  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    const dec = sideBySide ? 'ArrowLeft' : 'ArrowUp'
    const inc = sideBySide ? 'ArrowRight' : 'ArrowDown'
    if (e.key !== dec && e.key !== inc) return
    e.preventDefault()
    ctx.onRatio(node.id, node.ratio + (e.key === inc ? 0.02 : -0.02))
  }

  return (
    <div ref={box} className={'split ' + node.direction}>
      <div className="split-child" style={{ flex: `${node.ratio} 1 0%` }}><Canvas node={node.first} ctx={ctx} /></div>
      {edit && (
        <div
          className="gutter"
          role="separator"
          aria-orientation={sideBySide ? 'vertical' : 'horizontal'}
          aria-label="Изменить размер областей"
          aria-valuemin={12} aria-valuemax={88} aria-valuenow={Math.round(node.ratio * 100)}
          tabIndex={0}
          onKeyDown={onKey}
          onPointerDown={e => { e.preventDefault(); dragging.current = true; e.currentTarget.setPointerCapture(e.pointerId) }}
          onPointerMove={e => { if (!dragging.current) return; const r = ratioAt(e); if (r !== null) ctx.onRatio(node.id, r) }}
          onPointerUp={end}
          onPointerCancel={end}
        />
      )}
      <div className="split-child" style={{ flex: `${1 - node.ratio} 1 0%` }}><Canvas node={node.second} ctx={ctx} /></div>
    </div>
  )
}
