import { create } from 'zustand';
import type { Design, Layer, LayerType, WeatherSample } from './design/types';
import { cloneDesign, defaultLayer, newDesign, normalizeDesign, uid } from './design/defaults';
import { fromTemplate } from './design/templates';
import { WidgetBridge } from './bridge';
import type { InfoResult } from './bridge';

export type Tab = 'layers' | 'bg' | 'templates' | 'mine' | 'weather';

const DRAFT_KEY = 'widgetly.draft';
const HISTORY_LIMIT = 60;

interface State {
  design: Design;
  selectedId: string | null;
  past: Design[];
  future: Design[];
  saved: Design[];
  info: InfoResult | null;
  tab: Tab;
  toast: string | null;
  dirty: boolean;
  preview: WeatherSample;

  init(): Promise<void>;
  setTab(t: Tab): void;
  select(id: string | null): void;
  say(msg: string): void;

  addLayer(type: LayerType): void;
  removeLayer(id: string): void;
  duplicateLayer(id: string): void;
  moveLayer(id: string, dir: -1 | 1): void;
  updateLayer(id: string, patch: Partial<Layer>, coalesce?: boolean): void;
  updateBg(patch: Partial<Design['bg']>, coalesce?: boolean): void;
  updateMeta(patch: Partial<Pick<Design, 'name' | 'cols' | 'rows' | 'locale'>>, coalesce?: boolean): void;
  setPreview(patch: Partial<WeatherSample>): void;

  undo(): void;
  redo(): void;

  loadDesign(d: Design): void;
  useTemplate(key: string): void;
  newBlank(): void;
  save(): Promise<void>;
  remove(id: string): Promise<void>;
  duplicateSaved(d: Design): Promise<void>;
  pin(): Promise<void>;
  importJson(text: string): void;
}

let lastCoalesceAt = 0;
let toastTimer: ReturnType<typeof setTimeout> | undefined;

function loadDraft(): Design | null {
  try {
    const raw = localStorage.getItem(DRAFT_KEY);
    return raw ? normalizeDesign(JSON.parse(raw)) : null;
  } catch {
    return null;
  }
}

export const useStore = create<State>()((set, get) => {
  /** Применяет изменение дизайна с записью в историю (с объединением быстрых правок). */
  const mutate = (fn: (d: Design) => Design, coalesce = false) => {
    const { design, past } = get();
    const next = { ...fn(design), updatedAt: Date.now() };
    const now = Date.now();
    const merge = coalesce && now - lastCoalesceAt < 700 && past.length > 0;
    lastCoalesceAt = coalesce ? now : 0;
    set({
      design: next,
      past: merge ? past : [...past, design].slice(-HISTORY_LIMIT),
      future: [],
      dirty: true,
    });
  };

  return {
    design: loadDraft() ?? fromTemplate('night'),
    selectedId: null,
    past: [],
    future: [],
    saved: [],
    info: null,
    tab: 'layers',
    toast: null,
    dirty: false,
    preview: { tempC: 21.4, code: 2, isDay: true, hiC: 24.2, loC: 14.8, city: 'Москва' },

    async init() {
      try {
        const info = await WidgetBridge.getInfo();
        const { designs } = await WidgetBridge.listDesigns();
        set({ info, saved: designs.map((d) => normalizeDesign(d)) });
      } catch (e) {
        get().say(`Не удалось связаться с модулем виджетов: ${String(e)}`);
      }
      useStore.subscribe((s, prev) => {
        if (s.design !== prev.design) {
          try {
            localStorage.setItem(DRAFT_KEY, JSON.stringify(s.design));
          } catch {
            /* ignore */
          }
        }
      });
    },

    setTab: (tab) => set({ tab }),
    select: (selectedId) => set({ selectedId }),
    say(msg) {
      set({ toast: msg });
      clearTimeout(toastTimer);
      toastTimer = setTimeout(() => set({ toast: null }), 3500);
    },

    addLayer(type) {
      const layer = defaultLayer(type);
      mutate((d) => ({ ...d, layers: [...d.layers, layer] }));
      set({ selectedId: layer.id });
    },
    removeLayer(id) {
      mutate((d) => ({ ...d, layers: d.layers.filter((l) => l.id !== id) }));
      if (get().selectedId === id) set({ selectedId: null });
    },
    duplicateLayer(id) {
      const src = get().design.layers.find((l) => l.id === id);
      if (!src) return;
      const copy: Layer = { ...src, id: uid('l'), x: Math.min(1, src.x + 0.04), y: Math.min(1, src.y + 0.06) };
      mutate((d) => ({ ...d, layers: [...d.layers, copy] }));
      set({ selectedId: copy.id });
    },
    moveLayer(id, dir) {
      mutate((d) => {
        const i = d.layers.findIndex((l) => l.id === id);
        const j = i + dir;
        if (i < 0 || j < 0 || j >= d.layers.length) return d;
        const layers = d.layers.slice();
        [layers[i], layers[j]] = [layers[j], layers[i]];
        return { ...d, layers };
      });
    },
    updateLayer(id, patch, coalesce = true) {
      mutate((d) => ({ ...d, layers: d.layers.map((l) => (l.id === id ? { ...l, ...patch } : l)) }), coalesce);
    },
    updateBg(patch, coalesce = true) {
      mutate((d) => ({ ...d, bg: { ...d.bg, ...patch } }), coalesce);
    },
    updateMeta(patch, coalesce = true) {
      mutate((d) => ({ ...d, ...patch }), coalesce);
    },
    setPreview(patch) {
      set((s) => ({ preview: { ...s.preview, ...patch } }));
    },

    undo() {
      const { past, design, future } = get();
      if (past.length === 0) return;
      set({ design: past[past.length - 1], past: past.slice(0, -1), future: [design, ...future], dirty: true });
    },
    redo() {
      const { past, design, future } = get();
      if (future.length === 0) return;
      set({ design: future[0], past: [...past, design], future: future.slice(1), dirty: true });
    },

    loadDesign(d) {
      set({ design: cloneKeepId(d), selectedId: null, past: [], future: [], dirty: false, tab: 'layers' });
    },
    useTemplate(key) {
      set({ design: fromTemplate(key), selectedId: null, past: [], future: [], dirty: true, tab: 'layers' });
      get().say('Шаблон применён. Сохраните дизайн, чтобы он появился в «Мои»');
    },
    newBlank() {
      set({ design: newDesign(), selectedId: null, past: [], future: [], dirty: true, tab: 'layers' });
    },

    async save() {
      const design = { ...get().design, updatedAt: Date.now() };
      try {
        await WidgetBridge.saveDesign({ design });
        const { designs } = await WidgetBridge.listDesigns();
        set({ design, saved: designs.map((d) => normalizeDesign(d)), dirty: false });
        get().say(get().info?.native ? 'Сохранено. Виджеты с этим дизайном обновлены' : 'Сохранено в браузере');
      } catch (e) {
        get().say(`Не удалось сохранить: ${String(e)}`);
      }
    },
    async remove(id) {
      try {
        await WidgetBridge.deleteDesign({ id });
        const { designs } = await WidgetBridge.listDesigns();
        set({ saved: designs.map((d) => normalizeDesign(d)) });
      } catch (e) {
        get().say(`Не удалось удалить: ${String(e)}`);
      }
    },
    async duplicateSaved(d) {
      const copy = cloneDesign(d, `${d.name} (копия)`);
      try {
        await WidgetBridge.saveDesign({ design: copy });
        const { designs } = await WidgetBridge.listDesigns();
        set({ saved: designs.map((x) => normalizeDesign(x)) });
      } catch (e) {
        get().say(`Не удалось дублировать: ${String(e)}`);
      }
    },
    async pin() {
      const info = get().info;
      if (!info?.native) {
        get().say('Закрепление работает только в приложении на Android');
        return;
      }
      await get().save();
      try {
        const r = await WidgetBridge.pinWidget({ designId: get().design.id });
        get().say(
          r.requested
            ? 'Подтвердите добавление виджета на экран'
            : 'Лаунчер не поддерживает закрепление. Добавьте виджет через меню виджетов вручную',
        );
      } catch (e) {
        get().say(`Не удалось закрепить: ${String(e)}`);
      }
    },
    importJson(text) {
      try {
        const d = normalizeDesign(JSON.parse(text));
        set({ design: cloneDesign(d), selectedId: null, past: [], future: [], dirty: true, tab: 'layers' });
        get().say('Дизайн импортирован');
      } catch (e) {
        get().say(`Не удалось импортировать: ${e instanceof Error ? e.message : String(e)}`);
      }
    },
  };
});

function cloneKeepId(d: Design): Design {
  return { ...d, bg: { ...d.bg }, layers: d.layers.map((l) => ({ ...l })) };
}
