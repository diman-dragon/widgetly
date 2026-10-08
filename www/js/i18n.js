// [ru, en]
const T = {
  settings:['Настройки','Settings'], back:['Назад','Back'], addHome:['Добавить на рабочий стол','Add to home screen'],
  pinFail:['Лаунчер не поддерживает быстрое добавление. Долгий тап по рабочему столу → Виджеты → Widget Studio.','Your launcher can’t pin widgets. Long-press home → Widgets → Widget Studio.'],
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
const lang = (navigator.language || 'en').toLowerCase().startsWith('ru') ? 0 : 1;
export const LANG = lang === 0 ? 'ru' : 'en';
export const t = (k) => (T[k] ? T[k][lang] : k);
