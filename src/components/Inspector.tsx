import { SCALE_MAX, SCALE_MIN, WORLD_ZONES, widgetName, type Widget } from '../model'

type Props = {
  /** Выбрана ли область вообще */
  hasArea: boolean
  widget: Widget | null
  onPatch: (widgetId: string, patch: Partial<Widget>, key: string) => void
  onClear: () => void
}

export function Inspector({ hasArea, widget: w, onPatch, onClear }: Props) {
  if (!hasArea) return <div className="inspector-empty">Выберите область, чтобы настроить виджет.</div>
  if (!w) return <div className="inspector-empty">Область пуста. Выберите виджет в каталоге или перетащите его сюда.</div>

  const patch = (p: Partial<Widget>, field: string) => onPatch(w.id, p, `${w.id}:${field}`)
  const zones = w.zones ?? []

  return (
    <div className="inspector">
      <div className="eyebrow">Виджет</div>
      <h3>{widgetName[w.type]}</h3>

      <label>
        <span className="row"><span>Размер</span><span>{Math.round(w.scale * 100)}%</span></span>
        <input type="range" min={SCALE_MIN} max={SCALE_MAX} step={0.05} value={w.scale} onChange={e => patch({ scale: +e.target.value }, 'scale')} />
      </label>
      <label>
        <span className="row"><span>Прозрачность</span><span>{Math.round(w.opacity * 100)}%</span></span>
        <input type="range" min={0.2} max={1} step={0.05} value={w.opacity} onChange={e => patch({ opacity: +e.target.value }, 'opacity')} />
      </label>

      <div className="seg" role="group" aria-label="Выравнивание">
        {(['left', 'center', 'right'] as const).map(a => (
          <button key={a} className={w.align === a ? 'on' : ''} aria-pressed={w.align === a} onClick={() => patch({ align: a }, 'align')}>
            {a === 'left' ? 'Слева' : a === 'center' ? 'Центр' : 'Справа'}
          </button>
        ))}
      </div>

      <label className="check">
        <input type="checkbox" checked={w.accent} onChange={e => patch({ accent: e.target.checked }, 'accent')} />
        <span>Акцентный цвет</span>
      </label>

      {w.type === 'weather' && <label>Город<input value={w.city ?? ''} maxLength={80} onChange={e => patch({ city: e.target.value }, 'city')} /></label>}
      {w.type === 'text' && <label>Текст<textarea value={w.text ?? ''} maxLength={2000} onChange={e => patch({ text: e.target.value }, 'text')} /></label>}
      {w.type === 'metric' && <>
        <label>Подпись<input value={w.label ?? ''} maxLength={40} onChange={e => patch({ label: e.target.value }, 'label')} /></label>
        <label>Значение<input value={w.value ?? ''} maxLength={40} onChange={e => patch({ value: e.target.value }, 'value')} /></label>
      </>}
      {(w.type === 'events' || w.type === 'notifications') && (
        <label>
          Строки списка
          <textarea value={w.items ?? ''} maxLength={2000} placeholder={w.type === 'events' ? '14:30 Встреча' : 'Новое сообщение'} onChange={e => patch({ items: e.target.value }, 'items')} />
          {w.type === 'events' && <small>По одной записи на строку. Время в начале («14:30 …») выделяется.</small>}
        </label>
      )}
      {w.type === 'world' && (
        <fieldset>
          <legend>Города</legend>
          {WORLD_ZONES.map(z => (
            <label key={z.id} className="check">
              <input type="checkbox" checked={zones.includes(z.id)} onChange={e => patch({ zones: e.target.checked ? [...zones, z.id] : zones.filter(x => x !== z.id) }, 'zones')} />
              <span>{z.name}</span>
            </label>
          ))}
        </fieldset>
      )}

      <button className="danger" onClick={onClear}>Убрать виджет из области</button>
    </div>
  )
}
