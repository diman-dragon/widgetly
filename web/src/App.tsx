import { useEffect } from 'react';
import { useStore } from './store';
import type { Tab } from './store';
import { Stage } from './ui/Stage';
import { LayersPanel } from './ui/LayersPanel';
import { BackgroundPanel } from './ui/BackgroundPanel';
import { TemplatesPanel } from './ui/TemplatesPanel';
import { MinePanel } from './ui/MinePanel';
import { WeatherPanel } from './ui/WeatherPanel';

const TABS: { id: Tab; label: string }[] = [
  { id: 'templates', label: 'Пресеты' },
  { id: 'layers', label: 'Настроить' },
  { id: 'bg', label: 'Внешний вид' },
  { id: 'mine', label: 'Мои' },
  { id: 'weather', label: 'Погода' },
];

export default function App() {
  const init = useStore((s) => s.init);
  const tab = useStore((s) => s.tab);
  const setTab = useStore((s) => s.setTab);
  const toast = useStore((s) => s.toast);
  const info = useStore((s) => s.info);
  const dirty = useStore((s) => s.dirty);
  const canUndo = useStore((s) => s.past.length > 0);
  const canRedo = useStore((s) => s.future.length > 0);
  const undo = useStore((s) => s.undo);
  const redo = useStore((s) => s.redo);
  const save = useStore((s) => s.save);
  const pin = useStore((s) => s.pin);

  useEffect(() => {
    void init();
  }, [init]);

  return (
    <div className="app">
      <header className="top">
        <div className="brand">
          <span className="brand-mark" aria-hidden />
          <span>
            <span className="brand-name">Widgetly</span>
            <span className="brand-subtitle">Виджет за минуту</span>
          </span>
          {info && !info.native && <span className="chip">браузер</span>}
        </div>
        <div className="top-actions">
          <button type="button" onClick={undo} disabled={!canUndo} aria-label="Отменить">↶</button>
          <button type="button" onClick={redo} disabled={!canRedo} aria-label="Повторить">↷</button>
          <button type="button" onClick={() => void save()} className={dirty ? 'primary' : ''}>{dirty ? 'Сохранить' : 'Сохранено'}</button>
          <button type="button" onClick={() => void pin()} className="accent">На экран</button>
        </div>
      </header>

      <main className="workspace">
        <Stage />
        <section className="panel" aria-live="polite">
          <nav className="tabs" role="tablist" aria-label="Разделы редактора">
            {TABS.map((t) => (
              <button key={t.id} role="tab" aria-selected={tab === t.id} className={tab === t.id ? 'on' : ''} onClick={() => setTab(t.id)} type="button">
                {t.label}
              </button>
            ))}
          </nav>
          {tab === 'templates' && <TemplatesPanel />}
          {tab === 'layers' && <LayersPanel />}
          {tab === 'bg' && <BackgroundPanel />}
          {tab === 'mine' && <MinePanel />}
          {tab === 'weather' && <WeatherPanel />}
        </section>
      </main>

      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  );
}
