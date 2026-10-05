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
    <div className="panel-body">
      <div className="panel-head">
        <h2>Шаблоны</h2>
        <button type="button" className="small" onClick={newBlank}>Пустой дизайн</button>
      </div>
      <p className="note">Шаблон заменяет дизайн в редакторе. Сохранённые дизайны из раздела «Мои» не затрагиваются.</p>
      <ul className="cards">
        {items.map(({ key, design }) => (
          <li key={key}>
            <button type="button" className="card" onClick={() => useTemplate(key)}>
              <span className="thumb"><WidgetSVG design={design} now={now} weather={preview} /></span>
              <span className="card-title">{design.name}</span>
              <span className="card-sub">{design.cols} × {design.rows}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
