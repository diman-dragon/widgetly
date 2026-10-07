# Widget Studio

Android-приложение (Capacitor) для создания и редактирования виджета на рабочем столе:
часы, дата, погода, уведомления, будильник. Интерфейс редактора — HTML/JS (`www/`),
виджет, обновления и сетевые запросы — нативный Java-код (`native/android/`).

## Как это устроено

| Слой | Где | Что делает |
|---|---|---|
| UI | `www/` | Главная, редактор (превью, drag & drop, панели), настройки ⚙. Без сборщика — чистые ES-модули |
| Мост | `native/android/java/WidgetPlugin.java` | Плагин `WidgetBridge`: хранение, рендер превью, закрепление виджета, поиск города |
| Рендер | `Renderer.java` | Рисует виджет в Bitmap по JSON-дизайну. **Тот же код** рисует превью в редакторе и сам виджет |
| Обновления | `Updater.java`, `TickReceiver`, `SysReceiver` | Политика обновления частей (см. ниже) |
| Погода | `WeatherService.java` | Open-Meteo (по умолчанию) и MET Norway — без регистрации и ключей |
| Батарея | `Renderer.batteryText()` | Заряд батареи без разрешений |

Дизайн виджета — JSON: сетка `cols × rows` (по умолчанию 5×3), блоки `{type,x,y,w,h,font,color,scale,align,opt…}`,
фон/градиент/скругление, разделители. Пресеты (Samsung One UI, Apple, HTC Sense) — в `www/js/presets.js`.

### Как обновляется каждая часть
- **Часы** — системный `TextClock` внутри виджета: тикает каждую минуту сам, без запуска приложения. Секунд нет.
- **Дата** — раз в сутки: на первом «тике» с включённым экраном после смены дня.
- **Погода** — раз в 6 часов (настройка 1/3/6/12), только при включённом экране; ошибка сети → повтор не чаще раза в 10 минут.
- **Уведомления / будильник** — по событию и на «тике», только при включённом экране.
- **Кнопка ↻ на виджете** — принудительно обновляет всё (в т.ч. погоду).
- «Тик» — неточный **не-wakeup** `AlarmManager` раз в 5 минут: при выключенном экране устройство не будится и ничего не рисуется.
  Точные будильники (`SCHEDULE_EXACT_ALARM`) не нужны.

## Сборка только с VS Code + git

Android Studio не нужна: сборку делает GitHub Actions (`.github/workflows/android.yml`).

1. Создайте репозиторий на GitHub, `git init`, `git add .`, `git commit`, `git push` в ветку `main`.
2. Вкладка **Actions** → *Android build* → в артефактах `widget-studio-debug-apk`. Скачайте APK и поставьте на телефон.
3. Для Google Play добавьте секреты репозитория (Settings → Secrets → Actions): `KEYSTORE_B64`
   (`base64 -w0 upload.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — получите артефакт `widget-studio-release-aab`.
   Подробности — `docs/PLAY_STORE.md`.

Локально (нужны Node 22+, JDK 21, Android SDK):
```bash
npm install
npm run android:setup      # создаёт android/, копирует native/, патчит манифест, cap sync
cd android && ./gradlew assembleDebug   # apk: android/app/build/outputs/apk/debug/
```
После правок в `www/` или `native/` просто повторите `npm run android:setup` (идемпотентно).
Быстрая проверка вёрстки в браузере: `npm run web` (превью рисует только Android-версия).

## Перед первым запуском
- Поменяйте `appId` в `capacitor.config.json` (после публикации менять нельзя).
- Поменяйте `PRIVACY_URL` в `www/js/app.js`.
- Иконки: положите `assets/icon-only.png` (1024×1024) и выполните `npx @capacitor/assets generate --android`.

## Ограничения (осознанные)
- Клик по виджету открывает редактор этого дизайна; отдельных зон нажатия на часы/погоду нет (виджет — картинка + TextClock).
- Блок часов — один на дизайн (системный TextClock). Для мировых часов задайте часовой пояс блока.
- Шрифты — системные семейства Android (10 штук). Свои `.ttf`: понадобится `res/font` и расширение `Renderer.tf()` + копия TextClock в `widget_root.xml`.
- Эмодзи-иконки погоды зависят от системного шрифта эмодзи.
