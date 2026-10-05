import { useCallback, useEffect, useRef, useState } from 'react';
import { WidgetBridge } from '../bridge';
import type { Diagnostics, WeatherData, WeatherSettings } from '../bridge';
import { searchCities } from '../bridge/openMeteo';
import type { CityResult } from '../bridge/openMeteo';
import { conditionText, tempText, weatherIcon } from '../design/format';
import { useStore } from '../store';
import { Segmented } from './Fields';

function ago(ts: number): string {
  if (!ts) return 'ещё не обновлялось';
  const min = Math.max(0, Math.round((Date.now() - ts) / 60000));
  if (min < 1) return 'только что';
  if (min < 60) return `${min} мин назад`;
  return `${Math.round(min / 60)} ч назад`;
}

export function WeatherPanel() {
  const info = useStore((s) => s.info);
  const say = useStore((s) => s.say);
  const [settings, setSettings] = useState<WeatherSettings | null>(null);
  const [weather, setWeather] = useState<WeatherData | null>(null);
  const [diag, setDiag] = useState<Diagnostics | null>(null);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<CityResult[]>([]);
  const [busy, setBusy] = useState(false);
  const seq = useRef(0);

  const reload = useCallback(async () => {
    try {
      const [s, w, d] = await Promise.all([WidgetBridge.getWeatherSettings(), WidgetBridge.getWeather(), WidgetBridge.getDiagnostics()]);
      setSettings(s);
      setWeather(w.weather);
      setDiag(d);
    } catch (e) {
      say(`Ошибка чтения настроек погоды: ${String(e)}`);
    }
  }, [say]);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    const my = ++seq.current;
    if (query.trim().length < 2) {
      setResults([]);
      return;
    }
    const t = setTimeout(async () => {
      try {
        const r = await searchCities(query, 'ru');
        if (my === seq.current) setResults(r);
      } catch (e) {
        if (my === seq.current) say(`Поиск города не удался: ${e instanceof Error ? e.message : String(e)}`);
      }
    }, 350);
    return () => clearTimeout(t);
  }, [query, say]);

  const refresh = async () => {
    setBusy(true);
    try {
      const r = await WidgetBridge.refreshWeather();
      setWeather(r.weather);
      say('Погода обновлена');
    } catch (e) {
      say(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
      void reload();
    }
  };

  const setMode = async (mode: 'gps' | 'city') => {
    try {
      await WidgetBridge.setWeatherSettings({ mode, city: settings?.city ?? null });
      if (mode === 'gps') {
        const r = await WidgetBridge.requestLocationPermission();
        if (!r.granted) say('Разрешение не выдано. Выберите город вручную или разрешите геолокацию в настройках');
      }
      await reload();
    } catch (e) {
      say(String(e));
    }
  };

  const pickCity = async (c: CityResult) => {
    try {
      await WidgetBridge.setWeatherSettings({ mode: 'city', city: { name: c.name, lat: c.lat, lon: c.lon } });
      setQuery('');
      setResults([]);
      await reload();
      await refresh();
    } catch (e) {
      say(String(e));
    }
  };

  return (
    <div className="panel-body">
      <div className="panel-head"><h2>Погода</h2></div>

      {weather ? (
        <div className="now-card">
          <span className="now-icon" aria-hidden>{weatherIcon(weather.code, weather.isDay)}</span>
          <span className="now-temp">{tempText(weather.tempC, 'C', false)}</span>
          <span className="now-text">{conditionText(weather.code, 'ru')}{weather.city ? `, ${weather.city}` : ''}</span>
          <span className="now-sub">обновлено {ago(weather.fetchedAt)}</span>
        </div>
      ) : (
        <p className="empty">Данных о погоде ещё нет. Выберите место и нажмите «Обновить».</p>
      )}

      <section className="block">
        <h3>Откуда брать место</h3>
        <Segmented
          value={settings?.mode ?? 'city'}
          options={[{ value: 'city', label: 'Город' }, { value: 'gps', label: 'Геолокация' }]}
          onChange={(m) => void setMode(m)}
          label="Источник места"
        />
        {settings?.mode === 'gps' ? (
          <p className="note">
            Используется примерное местоположение (coarse), без доступа в фоне: в фоне берётся последняя известная точка.
            {info?.native && !settings.hasLocationPermission && ' Разрешение пока не выдано.'}
          </p>
        ) : (
          <>
            <p className="note">Сейчас: <strong>{settings?.city?.name ?? 'город не выбран'}</strong></p>
            <input className="text wide" type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Начните вводить название города" aria-label="Поиск города" />
            <ul className="rows">
              {results.map((c) => (
                <li key={`${c.lat},${c.lon}`}>
                  <button type="button" className="row-btn" onClick={() => void pickCity(c)}>
                    <span>{c.name}</span>
                    <small>{c.subtitle}</small>
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}
        <div className="btn-row">
          <button type="button" className="primary" disabled={busy} onClick={() => void refresh()}>{busy ? 'Обновляю…' : 'Обновить сейчас'}</button>
        </div>
      </section>

      <section className="block">
        <h3>Состояние</h3>
        <dl className="diag">
          <dt>Режим</dt><dd>{diag?.native ? 'приложение Android' : 'браузер'}</dd>
          <dt>Виджетов на экране</dt><dd>{diag?.widgetCount ?? 0}</dd>
          <dt>Сохранённых дизайнов</dt><dd>{diag?.designCount ?? 0}</dd>
          <dt>Последняя отрисовка</dt><dd>{diag?.lastRenderAt ? `${ago(diag.lastRenderAt)}, ${diag.lastRenderMs} мс, ${diag.lastBitmap}` : '—'}</dd>
          <dt>Погода обновлена</dt><dd>{ago(diag?.weatherFetchedAt ?? 0)}</dd>
          {diag?.lastError && (<><dt>Ошибка виджета</dt><dd className="err">{diag.lastError}</dd></>)}
          {diag?.weatherError && (<><dt>Ошибка погоды</dt><dd className="err">{diag.weatherError}</dd></>)}
        </dl>
        {info?.native && (
          <>
            <p className="note">Если виджет перестаёт обновляться на Xiaomi, Huawei, Samsung и других оболочках, отключите для приложения ограничения батареи и разрешите автозапуск.</p>
            <div className="btn-row">
              <button type="button" onClick={() => void WidgetBridge.openBatterySettings().then((r) => !r.opened && say('Не удалось открыть настройки батареи'))}>Настройки батареи</button>
              <button type="button" onClick={() => void WidgetBridge.refreshWidgets().then(() => reload())}>Перерисовать виджеты</button>
            </div>
          </>
        )}
      </section>
    </div>
  );
}
