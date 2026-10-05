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
      <div className="hero-copy">
        <div>
          <h2>Внешний вид</h2>
          <p className="note">Здесь только то, что влияет на общий вид. Тонкие настройки элементов — в «Настроить».</p>
        </div>
      </div>
      <TextField label="Название" value={name} onChange={(v) => updateMeta({ name: v })} />
      <SelectField label="Язык" value={locale} options={[{ value: 'ru', label: 'Русский' }, { value: 'en', label: 'English' }]} onChange={(v) => updateMeta({ locale: v }, false)} />
      <section className="appearance-card">
        <h3>Фон</h3>
        <SelectField label="Тип" value={bg.type} options={[{ value: 'gradient', label: 'Градиент' }, { value: 'solid', label: 'Однотонный' }, { value: 'none', label: 'Прозрачный' }]} onChange={(v) => updateBg({ type: v }, false)} />
        {bg.type !== 'none' && (
          <>
            <ColorField label={bg.type === 'gradient' ? 'Цвет 1' : 'Цвет'} value={bg.c1} onChange={(v) => updateBg({ c1: v })} />
            {bg.type === 'gradient' && <ColorField label="Цвет 2" value={bg.c2} onChange={(v) => updateBg({ c2: v })} />}
            {bg.type === 'gradient' && <NumberField label="Направление" value={bg.angle} min={0} max={360} step={5} onChange={(v) => updateBg({ angle: v })} unit="°" />}
            <NumberField label="Скругление" value={bg.radius} min={0} max={80} onChange={(v) => updateBg({ radius: v })} unit="dp" />
          </>
        )}
      </section>
    </div>
  );
}
