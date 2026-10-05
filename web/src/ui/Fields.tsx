import type { ReactNode } from 'react';

export function Row({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="row">
      <span className="row-label">{label}</span>
      <span className="row-ctl">{children}</span>
      {hint && <span className="row-hint">{hint}</span>}
    </label>
  );
}

export function NumberField(p: {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange: (v: number) => void;
  unit?: string;
}) {
  const step = p.step ?? 1;
  return (
    <Row label={p.label}>
      <input
        className="range"
        type="range"
        min={p.min}
        max={p.max}
        step={step}
        value={p.value}
        onChange={(e) => p.onChange(Number(e.target.value))}
      />
      <input
        className="num"
        type="number"
        min={p.min}
        max={p.max}
        step={step}
        value={Number.isFinite(p.value) ? p.value : 0}
        onChange={(e) => {
          const n = Number(e.target.value);
          if (Number.isFinite(n)) p.onChange(Math.min(p.max, Math.max(p.min, n)));
        }}
      />
      {p.unit && <span className="unit">{p.unit}</span>}
    </Row>
  );
}

export function ColorField(p: { label: string; value: string; onChange: (v: string) => void; allowEmpty?: boolean }) {
  const empty = p.value === '';
  return (
    <Row label={p.label}>
      <input
        className="color"
        type="color"
        value={empty ? '#ffffff' : p.value}
        onChange={(e) => p.onChange(e.target.value.toLowerCase())}
      />
      <code className="hex">{empty ? 'нет' : p.value}</code>
      {p.allowEmpty && (
        <button type="button" className="mini" onClick={() => p.onChange(empty ? '#ffffff' : '')}>
          {empty ? 'включить' : 'убрать'}
        </button>
      )}
    </Row>
  );
}

export function TextField(p: { label: string; value: string; onChange: (v: string) => void; hint?: string; placeholder?: string }) {
  return (
    <Row label={p.label} hint={p.hint}>
      <input className="text" type="text" value={p.value} placeholder={p.placeholder} onChange={(e) => p.onChange(e.target.value)} />
    </Row>
  );
}

export function SelectField<T extends string>(p: {
  label: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (v: T) => void;
}) {
  return (
    <Row label={p.label}>
      <select className="select" value={p.value} onChange={(e) => p.onChange(e.target.value as T)}>
        {p.options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </Row>
  );
}

export function ToggleField(p: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <Row label={p.label}>
      <input className="check" type="checkbox" checked={p.value} onChange={(e) => p.onChange(e.target.checked)} />
    </Row>
  );
}

export function Segmented<T extends string>(p: { value: T; options: { value: T; label: string }[]; onChange: (v: T) => void; label?: string }) {
  return (
    <div className="seg" role="group" aria-label={p.label}>
      {p.options.map((o) => (
        <button key={o.value} type="button" className={o.value === p.value ? 'on' : ''} onClick={() => p.onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  );
}
