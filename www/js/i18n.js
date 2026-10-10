// [ru, en]
const T = {
  settings:['Настройки','Settings'], back:['Назад','Back'], addHome:['Добавить на рабочий стол','Add to home screen'],
  pinFail:['Лаунчер не поддерживает быстрое добавление. Долгий тап по рабочему столу → Виджеты → DataLaw.','Your launcher can’t pin widgets. Long-press home → Widgets → DataLaw.'],
  previewOnly:['Предпросмотр доступен только в приложении на Android','Preview is available in the Android app only'],
  look:['Внешний вид','Appearance'], opacity:['Прозрачность виджета','Widget opacity'], radius:['Скругление углов','Corner radius'],
  border:['Тонкая рамка','Thin border'], bgMode:['Фон слева','Left background'], sunset:['Закат','Sunset'], night:['Ночь','Night'],
  photo:['Своё фото','Own photo'], none:['Без картинки','No image'], pickPhoto:['Выбрать фото','Choose photo'], rmPhoto:['Убрать фото','Remove photo'],
  dim:['Затемнение картинки','Image dimming'], split:['Ширина левой части, %','Left part width, %'],
  panel:['Правая панель: цвет 1','Right panel: color 1'], panel2:['Правая панель: цвет 2','Right panel: color 2'],
  fg:['Цвет текста','Text color'], fg2:['Цвет второстепенного текста','Secondary text color'],
  refreshBtn:['Кнопка обновления ↻','Refresh button ↻'],
  clock:['Часы','Clock'], weekday:['День недели','Weekday'], date:['Дата','Date'], weather:['Погода','Weather'],
  show:['Показывать','Show'], fmt:['Формат','Format'], font:['Шрифт','Font'], size:['Размер','Size'], color:['Цвет (пусто = общий)','Color (empty = default)'],
  reset:['Сброс','Reset'], tz:['Часовой пояс (пусто = местный)','Time zone (empty = local)'], upper:['ЗАГЛАВНЫМИ','UPPERCASE'],
  service:['Источник погоды','Weather source'], apiKey:['API-ключ','API key'], check:['Проверить','Check'],
  keyHint:['Ключ хранится только на устройстве. Получить: ','The key is stored on-device only. Get one at: '],
  city:['Город','City'], search:['Найти','Search'], nothing:['Ничего не найдено','Nothing found'],
  units:['Единицы','Units'], days:['Прогноз, дней вперёд','Forecast days ahead'], hours:['Обновлять погоду, не чаще, ч','Refresh weather no more than every, h'],
  showCountry:['Показывать страну','Show country'], showCond:['Описание погоды','Condition text'], showHiLo:['Мин/макс за день','Day high/low'],
  weatherNote:['Погода запрашивается не чаще одного раза в выбранный интервал (минимум 6 часов); прогноз приходит тем же запросом.','Weather is requested at most once per chosen interval (6 h minimum); the forecast comes in the same request.'],
  theme:['Тема приложения','App theme'], sys:['Системная','System'], light:['Светлая','Light'], dark:['Тёмная','Dark'],
  updates:['Как обновляется виджет','How the widget updates'],
  updatesText:['Часы — системные, каждую минуту. Дата и день недели — раз в сутки при первом включении экрана. Погода — не чаще раза в 6 часов. При выключенном экране ничего не обновляется.','Clock — system-driven, every minute. Date and weekday — once a day on first screen-on. Weather — at most every 6 hours. Nothing updates while the screen is off.'],
  about:['О приложении','About'], version:['Версия','Version'], privacy:['Политика конфиденциальности','Privacy policy'],
  wipe:['Сбросить все настройки','Reset everything'], confirmWipe:['Сбросить виджет и настройки к исходным?','Reset widget and settings to defaults?'],
};
Object.assign(T, {
  calendar:['Календарь','Calendar'], calDays:['Показывать события','Show events for'], calToday:['только сегодня','today only'], dayShort:['дн.','d'],
  calNeed:['Чтобы показывать события под датой, нужен доступ к календарю. Данные остаются на устройстве.','To show events under the date, calendar access is needed. Data stays on your device.'],
  calGrant:['Разрешить доступ к календарю','Allow calendar access'],
  calDenied:['Доступ не выдан. Открыть настройки приложения, чтобы разрешить?','Access not granted. Open app settings to allow it?'],
  calHint:['Две строки; если событий больше двух — листаются по кругу. Нет событий — блок скрыт. Тап по событиям открывает календарь, тап по часам — часы.','Two lines; with more than two events they rotate. No events — the block is hidden. Tap events to open Calendar, tap the clock to open Clock.'],
  updateWidget:['Обновить виджет на рабочем столе','Update widget on home screen'], updated:['Виджет обновлён — новый не создавался','Widget updated — no new one was created'],
  rights:['Все права защищены.','All rights reserved.'],
  calGranted:['Доступ к календарю выдан','Calendar access granted'], calList:['Какие календари показывать','Which calendars to show'],
  calNone:['Календари не найдены','No calendars found'], by:['от DataLaw','by DataLaw'],
  fontsCredit:['Шрифты: Lora, IBM Plex Serif, Poiret One, Jura, Tektur, JetBrains Mono, Geist Mono — лицензия SIL OFL 1.1.','Fonts: Lora, IBM Plex Serif, Poiret One, Jura, Tektur, JetBrains Mono, Geist Mono — SIL OFL 1.1.'],
  calOneLeft:['Должен остаться хотя бы один календарь','At least one calendar must stay selected'],
});
const lang = (navigator.language || 'en').toLowerCase().startsWith('ru') ? 0 : 1;
export const LANG = lang === 0 ? 'ru' : 'en';
export const t = (k) => (T[k] ? T[k][lang] : k);
