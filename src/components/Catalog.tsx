import { catalog } from '../widgets'
import { widgetName, type WidgetType } from '../model'
import { DND_TYPE } from './Canvas'

export function CatalogList({ onPick }: { onPick: (t: WidgetType) => void }) {
  return <>{catalog.map(({ type, hint, icon: Icon }) => (
    <button
      key={type}
      className="catalog-item"
      onClick={() => onPick(type)}
      draggable
      onDragStart={e => { e.dataTransfer.setData(DND_TYPE, type); e.dataTransfer.effectAllowed = 'copy' }}
    >
      <span className="catalog-icon"><Icon /></span>
      <span><b>{widgetName[type]}</b><small>{hint}</small></span>
    </button>
  ))}</>
}
