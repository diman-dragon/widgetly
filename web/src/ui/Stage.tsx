import { useEffect, useRef, useState } from 'react';
import type { PointerEvent } from 'react';
import { WidgetSVG } from '../design/render';
import { baseHeight, baseWidth, MAX_GRID } from '../design/types';
import { SAMPLE_CODES, conditionText } from '../design/format';
import { useStore } from '../store';

function useNow(intervalMs: number): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(t);
  }, [intervalMs]);
  return now;
}

export function Stage() {
  const design = useStore((s) => s.design);
  const selectedId = useStore((s) => s.selectedId);
  const preview = useStore((s) => s.preview);
  const select = useStore((s) => s.select);
  const updateLayer = useStore((s) => s.updateLayer);
  const updateMeta = useStore((s) => s.updateMeta);
  const setPreview = useStore((s) => s.setPreview);
  const now = useNow(5000);
  const svgRef = useRef<SVGSVGElement | null>(null);
  const drag = useRef<{ id: string; pointerId: number; dx: number; dy: number } | null>(null);

  const W = baseWidth(design.cols);
  const H = baseHeight(design.rows);

  const toDesign = (e: { clientX: number; clientY: number }) => {
    const svg = svgRef.current;
    if (!svg) return { x: 0, y: 0 };
    const r = svg.getBoundingClientRect();
    return { x: ((e.clientX - r.left) / r.width) * W, y: ((e.clientY - r.top) / r.height) * H };
  };

  const onDragStart = (id: string, e: PointerEvent<SVGRectElement>) => {
    const layer = design.layers.find((l) => l.id === id);
    if (!layer) return;
    const p = toDesign(e);
    drag.current = { id, pointerId: e.pointerId, dx: layer.x * W - p.x, dy: layer.y * H - p.y };
    svgRef.current?.setPointerCapture(e.pointerId);
  };

  const onMove = (e: PointerEvent<HTMLDivElement>) => {
    const d = drag.current;
    if (!d || d.pointerId !== e.pointerId) return;
    const p = toDesign(e);
    let x = Math.min(1, Math.max(0, (p.x + d.dx) / W));
    let y = Math.min(1, Math.max(0, (p.y + d.dy) / H));
    if (Math.abs(x - 0.5) < 0.015) x = 0.5;
    if (Math.abs(y - 0.5) < 0.015) y = 0.5;
    updateLayer(d.id, { x: Math.round(x * 1000) / 1000, y: Math.round(y * 1000) / 1000 });
  };

  const onUp = (e: PointerEvent<HTMLDivElement>) => {
    if (drag.current?.pointerId === e.pointerId) {
      svgRef.current?.releasePointerCapture(e.pointerId);
      drag.current = null;
    }
  };

  const step = (key: 'cols' | 'rows', delta: number) => {
    const v = Math.min(MAX_GRID, Math.max(1, design[key] + delta));
    updateMeta({ [key]: v }, false);
  };

  return (
    <section className="stage" aria-label="Предпросмотр виджета">
      <div className="wallpaper" onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onUp}>
        <div className="widget-frame" style={{ maxWidth: `min(${Math.round(W * 1.7)}px, 520px, calc(34vh * ${W} / ${H}))` }}>
          <WidgetSVG
            design={design}
            now={now}
            weather={preview}
            selectedId={selectedId}
            interactive
            onSelect={select}
            onDragStart={onDragStart}
            svgRef={svgRef}
          />
        </div>
      </div>

      <div className="stage-bar">
        <div className="stepper" aria-label="Ширина в ячейках">
          <button type="button" onClick={() => step('cols', -1)} aria-label="Уже">−</button>
          <span>{design.cols} × {design.rows}</span>
          <button type="button" onClick={() => step('cols', 1)} aria-label="Шире">+</button>
        </div>
        <div className="stepper" aria-label="Высота в ячейках">
          <button type="button" onClick={() => step('rows', -1)} aria-label="Ниже">−</button>
          <span>высота</span>
          <button type="button" onClick={() => step('rows', 1)} aria-label="Выше">+</button>
        </div>
        <label className="sample">
          <span>Погода в превью</span>
          <select value={preview.code} onChange={(e) => setPreview({ code: Number(e.target.value) })}>
            {SAMPLE_CODES.map((c) => (
              <option key={c} value={c}>{conditionText(c, 'ru')}</option>
            ))}
          </select>
        </label>
        <label className="sample">
          <span>{Math.round(preview.tempC)}°</span>
          <input type="range" min={-30} max={45} value={Math.round(preview.tempC)} onChange={(e) => setPreview({ tempC: Number(e.target.value), hiC: Number(e.target.value) + 3, loC: Number(e.target.value) - 6 })} aria-label="Температура в превью" />
        </label>
      </div>
    </section>
  );
}
