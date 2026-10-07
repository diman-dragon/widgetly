# Widget Editor — Clean Edition

Простой визуальный редактор информационного экрана.

## Что видит пользователь
- каталог виджетов;
- рабочее пространство с областями;
- разделение областей по горизонтали и вертикали;
- изменение размеров разделителей;
- drag & drop виджетов;
- настройки выбранного виджета;
- Undo / Redo;
- дублирование и удаление областей;
- полноэкранный просмотр.

## В проекте намеренно нет
- авторизации;
- регистрации;
- аккаунтов;
- сервера;
- базы данных;
- P2P / multiplayer;
- платёжной логики;
- внешней SaaS-инфраструктуры.

Это именно редактор виджетов.

## Запуск
```bash
npm install
npm test
npm run lint
npm run build
npm run dev
```

## Android
```bash
npx cap add android
npm run cap:sync
npm run device:android
```

Debug APK:
```bash
npm run android:debug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions

CI intentionally uses `npm install` without npm dependency caching. The project does not require a committed lock file, so GitHub Actions will not fail at `actions/setup-node` looking for `package-lock.json`.

The workflow then runs tests, TypeScript checking, the web build and the Android debug APK build.
