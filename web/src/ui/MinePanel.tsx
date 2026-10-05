import { useCallback, useEffect, useState } from 'react';
import { WidgetBridge } from '../bridge';
import type { ActiveWidget } from '../bridge';
import { WidgetSVG } from '../design/render';
import { useStore } from '../store';

function ActiveWidgets() {
  const saved = useStore((s) => s.saved);
  const info = useStore((s) => s.info);
  const say = useStore((s) => s.say);
  const [widgets, setWidgets] = useState<ActiveWidget[]>([]);

  const load = useCallback(async () => {
    try {
      const r = await WidgetBridge.getActiveWidgets();
      setWidgets(r.widgets);
    } catch (e) {
      say(`Не удалось получить список виджетов: ${String(e)}`);
    }
  }, [say]);

  useEffect(() => {
    if (info?.native) void load();
  }, [info?.native, load]);

  if (!info?.native) return null;

  return (
    <section className="block">
      <div className="panel-head">
        <h3>Виджеты на рабочем столе</h3>
        <button type="button" className="small" onClick={() => void load()}>Обновить список</button>
      </div>
      {widgets.length === 0 && <p className="empty">Пока нет ни одного. Нажмите «На экран» вверху или добавьте виджет Widgetly через меню виджетов лаунчера.</p>}
      <ul className="rows">
        {widgets.map((w) => (
          <li key={w.widgetId}>
            <span className="row-title">Виджет #{w.widgetId}<small>{Math.round(w.widthDp)}×{Math.round(w.heightDp)} dp</small></span>
            <select
              value={saved.some((d) => d.id === w.designId) ? w.designId : ''}
              onChange={async (e) => {
                try {
                  await WidgetBridge.applyDesign({ widgetId: w.widgetId, designId: e.target.value });
                  await load();
                } catch (err) {
                  say(`Не удалось применить: ${String(err)}`);
                }
              }}
              aria-label={`Дизайн для виджета ${w.widgetId}`}
            >
              <option value="" disabled>Стандартный</option>
              {saved.map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ImportExport() {
  const design = useStore((s) => s.design);
  const importJson = useStore((s) => s.importJson);
  const say = useStore((s) => s.say);
  const [text, setText] = useState('');
  const [open, setOpen] = useState(false);

  const copy = async () => {
    const json = JSON.stringify(design, null, 2);
    try {
      await navigator.clipboard.writeText(json);
      say('JSON скопирован в буфер обмена');
    } catch {
      setText(json);
      setOpen(true);
      say('Буфер недоступен — скопируйте текст вручную');
    }
  };

  return (
    <section className="block">
      <div className="panel-head"><h3>Обмен дизайном</h3></div>
      <div className="btn-row">
        <button type="button" onClick={() => void copy()}>Скопировать JSON</button>
        <button type="button" onClick={() => setOpen((v) => !v)} aria-expanded={open}>Вставить JSON</button>
      </div>
      {open && (
        <div className="import">
          <textarea value={text} onChange={(e) => setText(e.target.value)} rows={7} spellCheck={false} placeholder="Вставьте JSON дизайна" aria-label="JSON дизайна" />
          <button type="button" className="primary small" onClick={() => { importJson(text); setOpen(false); setText(''); }} disabled={text.trim() === ''}>Загрузить в редактор</button>
        </div>
      )}
    </section>
  );
}

export function MinePanel() {
  const saved = useStore((s) => s.saved);
  const current = useStore((s) => s.design.id);
  const loadDesign = useStore((s) => s.loadDesign);
  const remove = useStore((s) => s.remove);
  const duplicateSaved = useStore((s) => s.duplicateSaved);
  const preview = useStore((s) => s.preview);
  const now = new Date();

  return (
    <div className="panel-body">
      <div className="panel-head"><h2>Мои дизайны</h2></div>
      {saved.length === 0 && <p className="empty">Здесь появятся сохранённые дизайны. Нажмите «Сохранить» вверху.</p>}
      <ul className="cards">
        {saved.map((d) => (
          <li key={d.id}>
            <div className={`card static ${d.id === current ? 'on' : ''}`}>
              <button type="button" className="thumb" onClick={() => loadDesign(d)} aria-label={`Открыть «${d.name}»`}>
                <WidgetSVG design={d} now={now} weather={preview} />
              </button>
              <span className="card-title">{d.name}</span>
              <span className="card-actions">
                <button type="button" onClick={() => loadDesign(d)}>Открыть</button>
                <button type="button" onClick={() => void duplicateSaved(d)}>Копия</button>
                <button type="button" className="danger" onClick={() => void remove(d.id)}>Удалить</button>
              </span>
            </div>
          </li>
        ))}
      </ul>
      <ActiveWidgets />
      <ImportExport />
    </div>
  );
}
