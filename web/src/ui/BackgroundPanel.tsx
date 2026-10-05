import { useStore } from '../store';
import { ColorField, NumberField, SelectField, TextField } from './Fields';

export function BackgroundPanel() {
  const bg = useStore((s) => s.design.bg);
  const name = useStore((s) => s.design.name);
  const locale = useStore((s) => s.design.locale);
  const updateBg = useStore((s) => s.updateBg);
  const updateMeta = useStore((s) => s.updateMeta);

  return (
    <div className="panel-body">
      <div className="panel-head"><h2>Фон и язык</h2></div>
      <TextField label="Название" value={name} onChange={(v) => updateMeta({ name: v })} />
      <SelectField
        label="Язык дат и погоды"
        value={locale}
        options={[{ value: 'ru', label: 'Русский' }, { value: 'en', label: 'English' }]}
        onChange={(v) => updateMeta({ locale: v }, false)}
      />
      <SelectField
        label="Тип фона"
        value={bg.type}
        options={[{ value: 'gradient', label: 'Градиент' }, { value: 'solid', label: 'Однотонный' }, { value: 'none', label: 'Прозрачный' }]}
        onChange={(v) => updateBg({ type: v }, false)}
      />
      {bg.type !== 'none' && (
        <>
          <ColorField label={bg.type === 'gradient' ? 'Цвет 1' : 'Цвет'} value={bg.c1} onChange={(v) => updateBg({ c1: v })} />
          {bg.type === 'gradient' && (
            <>
              <ColorField label="Цвет 2" value={bg.c2} onChange={(v) => updateBg({ c2: v })} />
              <NumberField label="Угол" value={bg.angle} min={0} max={360} step={5} onChange={(v) => updateBg({ angle: v })} unit="°" />
            </>
          )}
          <NumberField label="Скругление" value={bg.radius} min={0} max={80} onChange={(v) => updateBg({ radius: v })} unit="dp" />
          <NumberField label="Непрозрачность" value={bg.opacity} min={0} max={1} step={0.05} onChange={(v) => updateBg({ opacity: v })} />
        </>
      )}
    </div>
  );
}
