# Подготовка к публикации в Google Play

## 1. Техническое
- [ ] `appId` в `capacitor.config.json` — свой (например `com.yourname.widgetstudio`). После публикации менять нельзя.
- [ ] targetSdk задаёт мажорная версия Capacitor (сейчас 8 → API 36). Google каждый год повышает минимум — к августу обновляйте Capacitor (`npm i @capacitor/core@latest @capacitor/android@latest @capacitor/cli@latest`) и перепроверяйте требования в Play Console.
- [ ] Версия: `package.json → version` (это `versionName`), `versionCode` = номер запуска GitHub Actions (`GITHUB_RUN_NUMBER`) — растёт сам.
- [ ] Иконка: `assets/icon-only.png` 1024×1024 → `npx @capacitor/assets generate --android`.
- [ ] Ключ загрузки (upload key), один раз:
  `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`
  Храните файл и пароли вне репозитория (`*.jks` в `.gitignore`). В Play Console включите Play App Signing.
- [ ] Секреты GitHub: `KEYSTORE_B64` (`base64 -w0 upload.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. CI соберёт и подпишет `app-release.aab`.
- [ ] Для новых личных аккаунтов разработчика Google требует закрытое тестирование (набор тестировщиков на срок, указанный в Play Console) до выхода в продакшен — проверьте актуальные условия.

## 2. Карточка приложения
- Название, краткое (80) и полное (4000) описание, скриншоты телефона (2–8), иконка 512×512, feature graphic 1024×500.
- Категория: Personalization.
- Политика конфиденциальности: опубликуйте `docs/PRIVACY_POLICY.md` на публичном URL (GitHub Pages подойдёт) и пропишите его в `PRIVACY_URL` (`www/js/app.js`) и в Play Console.

## 3. Формы Play Console
- **Data safety**: приложение не собирает данные на свои серверы. Сетевые запросы: координаты выбранного города уходят в Open-Meteo / MET Norway (получение погоды), текст поиска города — в геокодер Open-Meteo. Аккаунтов, рекламы, аналитики нет. Заполните форму в соответствии с этим.
- **Permissions declaration**: `NotificationListenerService` (доступ к уведомлениям) — чувствительное разрешение. Пользователь включает его вручную; используется только для счётчика/заголовков в блоке «Уведомления», данные не покидают устройство и не сохраняются дольше, чем нужно для показа. Опишите это и покажите в видео-демо, если запросят. Если захотите упростить ревью — можно убрать блок и сервис (`NotifListener`, запись в `manifest-application.xml`).
- **Content rating**, **Target audience** (не для детей), **Ads** (нет рекламы).
- `RECEIVE_BOOT_COMPLETED` — перезапуск периодического обновления после перезагрузки.

## 4. Прогон перед релизом
- Android 8, 12, 14 и новее; тёмная/светлая тема; разные лаунчеры (Pixel, Samsung One UI).
- Растягивание виджета; поворот экрана; смена часового пояса; режим энергосбережения.
- Выключенный экран: за ночь виджет не должен ничего перерисовывать.
- Нет сети: на виджете остаётся последняя погода.
- Тест на устройстве без выданного доступа к уведомлениям — блок показывает «Нет доступа».
