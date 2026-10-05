import { useId } from 'react';
import type { CSSProperties, PointerEvent, ReactElement } from 'react';
import type { Design, FontKey, Layer, WeatherSample } from './types';
import { baseHeight, baseWidth } from './types';
import { gradientPoints, layerBox, layerText } from './content';

/** Соответствие шрифтов: Android (Typeface.create) ↔ CSS. */
const FONT_CSS: Record<FontKey, { family: string; weight: number; stretch?: string }> = {
  sans: { family: 'Roboto, "Helvetica Neue", Arial, sans-serif', weight: 400 },
  'sans-light': { family: 'Roboto, "Helvetica Neue", Arial, sans-serif', weight: 300 },
  'sans-thin': { family: 'Roboto, "Helvetica Neue", Arial, sans-serif', weight: 100 },
  condensed: { family: '"Roboto Condensed", "Arial Narrow", Roboto, sans-serif', weight: 400, stretch: 'condensed' },
  serif: { family: '"Noto Serif", Georgia, "Times New Roman", serif', weight: 400 },
  mono: { family: '"Roboto Mono", Menlo, Consolas, "Courier New", monospace', weight: 400 },
};

const ANCHOR = { left: 'start', center: 'middle', right: 'end' } as const;

function Analog({ l, cx, cy, now }: { l: Layer; cx: number; cy: number; now: Date }) {
  const r = l.size / 2;
  const hour = now.getHours() % 12;
  const min = now.getMinutes();
  const hourAngle = ((hour + min / 60) * 30 * Math.PI) / 180;
  const minAngle = (min * 6 * Math.PI) / 180;
  const pt = (angle: number, len: number) => ({ x: cx + Math.sin(angle) * len, y: cy - Math.cos(angle) * len });

  const ticks: ReactElement[] = [];
  if (l.ticks !== 'none') {
    const count = l.ticks === 'quarters' ? 4 : 12;
    for (let i = 0; i < count; i++) {
      const ang = ((i * 360) / count) * (Math.PI / 180);
      const main = l.ticks === 'quarters' || i % 3 === 0;
      const a = pt(ang, r * (main ? 0.82 : 0.88));
      const b = pt(ang, r * 0.95);
      ticks.push(
        <line key={i} x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke={l.tickColor} strokeWidth={Math.max(1, r * (main ? 0.05 : 0.03))} strokeLinecap="round" />,
      );
    }
  }
  const hh = pt(hourAngle, r * 0.5);
  const mm = pt(minAngle, r * 0.78);
  return (
    <g opacity={l.opacity}>
      {l.face !== '' && <circle cx={cx} cy={cy} r={r} fill={l.face} />}
      {l.ring !== '' && l.ringWidth > 0 && <circle cx={cx} cy={cy} r={r - l.ringWidth / 2} fill="none" stroke={l.ring} strokeWidth={l.ringWidth} />}
      {ticks}
      <line x1={cx} y1={cy} x2={hh.x} y2={hh.y} stroke={l.hourColor} strokeWidth={l.handWidth} strokeLinecap="round" />
      <line x1={cx} y1={cy} x2={mm.x} y2={mm.y} stroke={l.minuteColor} strokeWidth={Math.max(1, l.handWidth * 0.75)} strokeLinecap="round" />
      <circle cx={cx} cy={cy} r={Math.max(2, l.handWidth * 0.8)} fill={l.minuteColor} />
    </g>
  );
}

export interface WidgetSVGProps {
  design: Design;
  now: Date;
  weather: WeatherSample | null;
  selectedId?: string | null;
  interactive?: boolean;
  onSelect?: (id: string | null) => void;
  onDragStart?: (id: string, e: PointerEvent<SVGRectElement>) => void;
  style?: CSSProperties;
  svgRef?: React.Ref<SVGSVGElement>;
}

export function WidgetSVG({ design, now, weather, selectedId, interactive, onSelect, onDragStart, style, svgRef }: WidgetSVGProps) {
  const uidx = useId().replace(/:/g, '');
  const W = baseWidth(design.cols);
  const H = baseHeight(design.rows);
  const bg = design.bg;
  const gp = gradientPoints(W, H, bg.angle);

  return (
    <svg
      ref={svgRef}
      viewBox={`0 0 ${W} ${H}`}
      width="100%"
      style={{ display: 'block', touchAction: interactive ? 'none' : undefined, ...style }}
      onPointerDown={interactive ? (e) => { if (e.target === e.currentTarget) onSelect?.(null); } : undefined}
    >
      <defs>
        <linearGradient id={`g${uidx}`} gradientUnits="userSpaceOnUse" x1={gp.x1} y1={gp.y1} x2={gp.x2} y2={gp.y2}>
          <stop offset="0" stopColor={bg.c1} />
          <stop offset="1" stopColor={bg.c2} />
        </linearGradient>
      </defs>
      {bg.type !== 'none' && (
        <rect
          x={0}
          y={0}
          width={W}
          height={H}
          rx={bg.radius}
          ry={bg.radius}
          fill={bg.type === 'gradient' ? `url(#g${uidx})` : bg.c1}
          opacity={bg.opacity}
        />
      )}
      {design.layers.map((l) => {
        const cx = l.x * W;
        const cy = l.y * H;
        let node: ReactElement | null = null;
        if (l.type === 'clockAnalog') {
          node = <Analog l={l} cx={cx} cy={cy} now={now} />;
        } else if (l.type === 'shape') {
          node =
            l.shape === 'ellipse' ? (
              <ellipse cx={cx} cy={cy} rx={l.w / 2} ry={l.h / 2} fill={l.color} opacity={l.opacity} />
            ) : (
              <rect x={cx - l.w / 2} y={cy - l.h / 2} width={l.w} height={l.h} rx={l.radius} ry={l.radius} fill={l.color} opacity={l.opacity} />
            );
        } else {
          const f = FONT_CSS[l.font];
          const bold = l.weight === 'bold';
          const text = layerText(l, design.locale, now, weather);
          node = (
            <text
              x={cx}
              y={cy + 0.35 * l.size}
              fontSize={l.size}
              fill={l.color}
              opacity={l.opacity}
              textAnchor={ANCHOR[l.align]}
              fontFamily={f.family}
              fontWeight={bold ? 700 : f.weight}
              letterSpacing={l.letterSpacing * l.size}
              style={{ fontStretch: f.stretch, whiteSpace: 'pre', userSelect: 'none' }}
            >
              {text}
            </text>
          );
        }
        return <g key={l.id}>{node}</g>;
      })}
      {interactive &&
        design.layers.map((l) => {
          const b = layerBox(l, design, now, weather);
          const sel = l.id === selectedId;
          return (
            <g key={`h_${l.id}`}>
              {sel && <rect x={b.x} y={b.y} width={b.w} height={b.h} fill="none" stroke="#ffb86b" strokeWidth={1} strokeDasharray="3 2" pointerEvents="none" />}
              <rect
                x={b.x - 4}
                y={b.y - 4}
                width={b.w + 8}
                height={b.h + 8}
                fill="transparent"
                style={{ cursor: 'grab' }}
                onPointerDown={(e) => {
                  e.stopPropagation();
                  onSelect?.(l.id);
                  onDragStart?.(l.id, e);
                }}
              />
            </g>
          );
        })}
    </svg>
  );
}
