import { useState } from 'react';
import type { Layer, LayerType } from '../design/types';
import { LAYER_TYPES, layerLabel } from '../design/defaults';
import { useStore } from '../store';
import { ColorField, NumberField, SelectField, TextField, ToggleField } from './Fields';

const FONTS = [
  { value: 'sans', label: 'Обычный' },
  { value: 'sans-light', label: 'Тонкий' },
  { value: 'sans-thin', label: 'Сверхтонкий' },
  { value: 'condensed', label: 'Узкий' },
  { value: 'serif', label: 'С засечками' },
  { value: 'mono', label: 'Моноширинный' },
] as const;

const TEXTUAL: LayerType[] = ['text', 'clockDigital', 'date', 'weatherTemp', 'weatherCondition', 'weatherCity', 'weatherRange', 'weatherIcon'];

function Inspector({ layer }: { layer: Layer }) {
  const update = useStore((s) => s.updateLayer);
  const set = (patch: Partial<Layer>) => update(layer.id, patch);
  const isText = TEXTUAL.includes(layer.type);
  const noFont = layer.type === 'weatherIcon';

  return (
    <div className="inspector">
      <div className="insp-title">{layerLabel(layer.type)}</div>

      {layer.type === 'text' && <TextField label="Текст" value={layer.text} onChange={(v) => set({ text: v })} />}
      {(layer.type === 'clockDigital' || layer.type === 'date') && (
        <TextField
          label="Формат"
          value={layer.format}
          onChange={(v) => set({ format: v })}
          hint="HH часы 24ч, hh/h — 12ч, mm минуты, A — AM/PM, EEEE/EEE день недели, d/dd число, MMMM/MMM/MM месяц, yyyy год. Буквы в 'кавычках' остаются как есть."
        />
      )}
      {(layer.type === 'weatherTemp' || layer.type === 'weatherRange') && (
        <SelectField label="Единицы" value={layer.unit} options={[{ value: 'C', label: '°C' }, { value: 'F', label: '°F' }]} onChange={(v) => set({ unit: v })} />
      )}
      {layer.type === 'weatherTemp' && <ToggleField label="Буква единиц" value={layer.showUnit} onChange={(v) => set({ showUnit: v })} />}

      {isText && <NumberField label="Размер" value={layer.size} min={8} max={200} onChange={(v) => set({ size: v })} unit="dp" />}
      {isText && !noFont && (
        <>
          <ColorField label="Цвет" value={layer.color} onChange={(v) => set({ color: v })} />
          <SelectField label="Шрифт" value={layer.font} options={FONTS.map((f) => ({ ...f }))} onChange={(v) => set({ font: v })} />
          <SelectField label="Начертание" value={layer.weight} options={[{ value: 'normal', label: 'Обычное' }, { value: 'bold', label: 'Жирное' }]} onChange={(v) => set({ weight: v })} />
          <SelectField label="Выравнивание" value={layer.align} options={[{ value: 'left', label: 'По левому краю точки' }, { value: 'center', label: 'По центру точки' }, { value: 'right', label: 'По правому краю точки' }]} onChange={(v) => set({ align: v })} />
          <NumberField label="Межбуквенный" value={layer.letterSpacing} min={-0.1} max={0.5} step={0.01} onChange={(v) => set({ letterSpacing: v })} unit="em" />
        </>
      )}

      {layer.type === 'clockAnalog' && (
        <>
          <NumberField label="Диаметр" value={layer.size} min={30} max={400} onChange={(v) => set({ size: v })} unit="dp" />
          <ColorField label="Циферблат" value={layer.face} allowEmpty onChange={(v) => set({ face: v })} />
          <ColorField label="Обод" value={layer.ring} allowEmpty onChange={(v) => set({ ring: v })} />
          <NumberField label="Толщина обода" value={layer.ringWidth} min={0} max={20} step={0.5} onChange={(v) => set({ ringWidth: v })} unit="dp" />
          <SelectField label="Метки" value={layer.ticks} options={[{ value: 'none', label: 'Нет' }, { value: 'quarters', label: 'Четверти' }, { value: 'hours', label: 'Часовые' }]} onChange={(v) => set({ ticks: v })} />
          <ColorField label="Цвет меток" value={layer.tickColor} onChange={(v) => set({ tickColor: v })} />
          <ColorField label="Часовая стрелка" value={layer.hourColor} onChange={(v) => set({ hourColor: v })} />
          <ColorField label="Минутная стрелка" value={layer.minuteColor} onChange={(v) => set({ minuteColor: v })} />
          <NumberField label="Толщина стрелок" value={layer.handWidth} min={1} max={16} step={0.5} onChange={(v) => set({ handWidth: v })} unit="dp" />
        </>
      )}

      {layer.type === 'weatherIcon' && <NumberField label="Размер" value={layer.size} min={12} max={200} onChange={(v) => set({ size: v })} unit="dp" />}

      {layer.type === 'shape' && (
        <>
          <SelectField label="Форма" value={layer.shape} options={[{ value: 'rect', label: 'Прямоугольник' }, { value: 'ellipse', label: 'Эллипс' }]} onChange={(v) => set({ shape: v })} />
          <ColorField label="Цвет" value={layer.color} onChange={(v) => set({ color: v })} />
          <NumberField label="Ширина" value={layer.w} min={2} max={600} onChange={(v) => set({ w: v })} unit="dp" />
          <NumberField label="Высота" value={layer.h} min={2} max={600} onChange={(v) => set({ h: v })} unit="dp" />
          {layer.shape === 'rect' && <NumberField label="Скругление" value={layer.radius} min={0} max={200} onChange={(v) => set({ radius: v })} unit="dp" />}
        </>
      )}

      <NumberField label="Прозрачность" value={layer.opacity} min={0} max={1} step={0.05} onChange={(v) => set({ opacity: v })} />
      <NumberField label="Позиция X" value={layer.x} min={0} max={1} step={0.005} onChange={(v) => set({ x: v })} />
      <NumberField label="Позиция Y" value={layer.y} min={0} max={1} step={0.005} onChange={(v) => set({ y: v })} />
    </div>
  );
}

export function LayersPanel() {
  const layers = useStore((s) => s.design.layers);
  const selectedId = useStore((s) => s.selectedId);
  const select = useStore((s) => s.select);
  const addLayer = useStore((s) => s.addLayer);
  const removeLayer = useStore((s) => s.removeLayer);
  const duplicateLayer = useStore((s) => s.duplicateLayer);
  const moveLayer = useStore((s) => s.moveLayer);
  const [adding, setAdding] = useState(false);
  const selected = layers.find((l) => l.id === selectedId) ?? null;

  // Список показываем сверху вниз как стопку: верхний элемент рисуется последним.
  const ordered = layers.slice().reverse();

  return (
    <div className="panel-body">
      <div className="panel-head">
        <h2>Слои</h2>
        <button type="button" className="primary small" onClick={() => setAdding((v) => !v)} aria-expanded={adding}>
          Добавить слой
        </button>
      </div>

      {adding && (
        <div className="add-grid">
          {LAYER_TYPES.map((t) => (
            <button
              key={t.type}
              type="button"
              onClick={() => {
                addLayer(t.type);
                setAdding(false);
              }}
            >
              {t.label}
            </button>
          ))}
        </div>
      )}

      {layers.length === 0 && <p className="empty">Слоёв нет. Добавьте часы, дату или погоду.</p>}

      <ul className="layer-list">
        {ordered.map((l) => (
          <li key={l.id} className={l.id === selectedId ? 'on' : ''}>
            <button type="button" className="layer-main" onClick={() => select(l.id === selectedId ? null : l.id)}>
              <span className="layer-name">{layerLabel(l.type)}</span>
              <span className="layer-sub">{l.type === 'text' ? l.text : l.type === 'clockDigital' || l.type === 'date' ? l.format : ''}</span>
            </button>
            <span className="layer-actions">
              <button type="button" onClick={() => moveLayer(l.id, 1)} aria-label="Поднять выше">↑</button>
              <button type="button" onClick={() => moveLayer(l.id, -1)} aria-label="Опустить ниже">↓</button>
              <button type="button" onClick={() => duplicateLayer(l.id)} aria-label="Дублировать">⧉</button>
              <button type="button" onClick={() => removeLayer(l.id)} aria-label="Удалить" className="danger">✕</button>
            </span>
          </li>
        ))}
      </ul>

      {selected ? <Inspector layer={selected} /> : layers.length > 0 && <p className="empty">Выберите слой в списке или коснитесь его в превью. Перетаскивайте слои пальцем.</p>}
    </div>
  );
}
