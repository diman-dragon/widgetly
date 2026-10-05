import { useMemo } from 'react';
import { TEMPLATES } from '../design/templates';
import { WidgetSVG } from '../design/render';
import { useStore } from '../store';

export function TemplatesPanel() {
  const useTemplate = useStore((s) => s.useTemplate);
  const newBlank = useStore((s) => s.newBlank);
  const preview = useStore((s) => s.preview);
  const items = useMemo(() => TEMPLATES.map((t) => ({ key: t.key, design: t.build() })), []);
  const now = new Date();

  return (
    <div className="panel-body presets-body">
      <div className="hero-copy">
        <div>
          <h2>Выберите готовый вид</h2>
          <p className="note">Ничего не нужно настраивать. Выберите вариант, а потом при желании поправьте детали.</p>
        </div>
        <button type="button" className="small" onClick={newBlank}>С нуля</button>
      </div>

      <ul className="preset-grid">
        {items.map(({ key, design }) => (
          <li key={key}>
            <button type="button" className={`preset-card ${key === 'glass-weather' ? 'featured' : ''}`} onClick={() => useTemplate(key)}>
              <span className="preset-thumb"><WidgetSVG design={design} now={now} weather={preview} /></span>
              <span className="preset-info">
                <span className="card-title">{design.name}</span>
                <span className="card-sub">{key === 'glass-weather' ? 'Похоже на современный погодный виджет' : 'Готовый вариант'}</span>
              </span>
              <span className="preset-choose">Выбрать</span>
            </button>
          </li>
        ))}
      </ul>

      <section className="quick-tip">
        <strong>Самый простой путь</strong>
        <span>Выберите пресет → сохраните → нажмите «На экран».</span>
      </section>
    </div>
  );
}
