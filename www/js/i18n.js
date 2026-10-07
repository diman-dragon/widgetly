// [ru, en]
const T = {
  settings:['Настройки','Settings'], new:['Новый виджет','New widget'], presets:['Выберите пресет','Choose a preset'],
  mine:['Мои виджеты','My widgets'], placed:['На рабочем столе','On home screen'], edit:['Изменить','Edit'],
  pin:['На рабочий стол','Add to home'], dup:['Копия','Copy'], del:['Удалить','Delete'], confirmDel:['Удалить этот виджет?','Delete this widget?'],
  noPlaced:['Пока нет добавленных виджетов. Нажмите «На рабочий стол».','Nothing placed yet. Tap “Add to home”.'],
  pinFail:['Ваш лаунчер не поддерживает быстрое добавление. Добавьте виджет вручную: долгий тап по рабочему столу → Виджеты → Widget Studio.','Your launcher can’t pin widgets. Add it manually: long-press home → Widgets → Widget Studio.'],
  block:['Блок','Block'], widget:['Виджет','Widget'], addBlock:['Добавить','Add'],
  clock:['Часы','Clock'], date:['Дата','Date'], weather:['Погода','Weather'], notif:['Уведомления','Notifications'], alarm:['Будильник','Alarm'],
  selectBlock:['Выберите блок на превью или добавьте новый','Select a block in the preview or add one'],
  x:['Колонка','Column'], y:['Строка','Row'], w:['Ширина (яч.)','Width (cells)'], h:['Высота (яч.)','Height (cells)'],
  font:['Шрифт','Font'], color:['Цвет','Color'], size:['Размер','Size'], align:['Выравнивание','Align'],
  left:['Слева','Left'], center:['Центр','Center'], right:['Справа','Right'], reset:['Сбросить','Reset'],
  blockBg:['Фон блока','Block bg'], blockBgA:['Прозрачность фона','Bg opacity'], radius:['Скругление','Radius'],
  divR:['Разделитель справа','Divider right'], divB:['Разделитель снизу','Divider bottom'],
  dup2:['Дублировать','Duplicate'], remove:['Удалить блок','Remove block'],
  fmt:['Формат','Format'], tz:['Часовой пояс (пусто = местный)','Time zone (empty = local)'], upper:['ЗАГЛАВНЫМИ','UPPERCASE'],
  city:['Город','City'], search:['Найти','Search'], service:['Сервис погоды','Weather service'], units:['Единицы','Units'],
  showIcon:['Иконка','Icon'], showCity:['Название города','City name'], showCond:['Описание погоды','Condition'], showHiLo:['Мин/макс за день','Day high/low'],
  list:['Список уведомлений','Show list'], max:['Макс. строк','Max lines'],
  name:['Название','Name'], cols:['Колонок','Columns'], rows:['Строк','Rows'], bg:['Фон','Background'], bg2:['Градиент (2-й цвет)','Gradient (2nd color)'],
  bgA:['Непрозрачность фона','Bg opacity'], pad:['Внутренний отступ','Padding'], fg:['Цвет текста','Text color'],
  dividers:['Разделители между блоками','Dividers between blocks'], divOn:['Включить разделители','Enable dividers'],
  divColor:['Цвет линии','Line color'], divA:['Непрозрачность линии','Line opacity'], divW:['Толщина','Thickness'], divStyle:['Стиль','Style'],
  solid:['Сплошная','Solid'], dashed:['Пунктир','Dashed'], dotted:['Точки','Dotted'], divInset:['Отступ от краёв','Inset'],
  divHint:['Линия рисуется на границе блока, если включено «Разделитель справа/снизу» у блока.','Lines are drawn at a block edge if “Divider right/bottom” is on for that block.'],
  refreshBtn:['Кнопка обновления на виджете','Refresh button on widget'], corner:['Угол','Corner'],
  tl:['Лево-верх','Top-left'], tr:['Право-верх','Top-right'], bl:['Лево-низ','Bottom-left'], br:['Право-низ','Bottom-right'],
  theme:['Тема','Theme'], sys:['Системная','System'], light:['Светлая','Light'], dark:['Тёмная','Dark'],
  wDefaults:['Погода по умолчанию','Weather defaults'], interval:['Обновлять погоду, часов','Weather refresh, hours'],
  notifAccess:['Доступ к уведомлениям','Notification access'], granted:['Выдан','Granted'], notGranted:['Не выдан','Not granted'], open:['Открыть','Open'],
  notifWhy:['Нужен только для блока «Уведомления»: приложение считает активные уведомления и показывает заголовки на виджете. Данные не покидают устройство.','Only for the Notifications block: the app counts active notifications and shows titles on the widget. Data never leaves the device.'],
  updates:['Как обновляются части','How parts update'],
  updatesText:['Часы — системные, каждую минуту. Дата — раз в сутки при первом включении экрана. Погода — раз в N часов. При выключенном экране виджет не обновляется.','Clock — system-driven, every minute. Date — once a day on first screen-on. Weather — every N hours. Nothing updates while the screen is off.'],
  about:['О приложении','About'], privacy:['Политика конфиденциальности','Privacy policy'], version:['Версия','Version'],
  wipe:['Сбросить все данные','Erase all data'], confirmWipe:['Удалить все виджеты и настройки?','Delete all widgets and settings?'],
  pickCity:['Выберите город','Pick a city'], nothing:['Ничего не найдено','Nothing found'], back:['Назад','Back'],
  assign:['Дизайн','Design'], previewOnly:['Предпросмотр доступен только в приложении на Android','Preview is available in the Android app only'],
};
const lang = (navigator.language || 'en').toLowerCase().startsWith('ru') ? 0 : 1;
export const LANG = lang === 0 ? 'ru' : 'en';
export const t = (k) => (T[k] ? T[k][lang] : k);
T.wNew = ['Применяется к новым блокам погоды. Существующие меняются в редакторе.', 'Applies to new weather blocks. Existing ones are edited in the editor.'];
T.sizeQ = ['Размер виджета', 'Widget size'];
