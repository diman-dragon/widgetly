#!/usr/bin/env node
/*
 * Генерирует android/ (если нет), копирует нативную часть из native/android, патчит манифест, подпись,
 * заставку и версию, затем делает cap sync. Идемпотентен: можно запускать сколько угодно раз.
 */
const fs = require('fs');
const path = require('path');
const cp = require('child_process');

const root = path.join(__dirname, '..');
const src = path.join(root, 'native/android');
const run = (c) => cp.execSync(c, { cwd: root, stdio: 'inherit' });
const cfg = JSON.parse(fs.readFileSync(path.join(root, 'capacitor.config.json'), 'utf8'));
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
const appId = cfg.appId;

if (!fs.existsSync(path.join(src, 'java'))) {
  console.error('Не найдена папка native/android/java. Проверьте, что она закоммичена в git (git add -f native).');
  process.exit(1);
}
if (!fs.existsSync(path.join(root, 'android'))) run('npx cap add android');

const main = path.join(root, 'android/app/src/main');
const tpl = (s) => s.split('__APP_ID__').join(appId);

function copyDir(from, to) {
  fs.mkdirSync(to, { recursive: true });
  for (const f of fs.readdirSync(from, { withFileTypes: true })) {
    const a = path.join(from, f.name), b = path.join(to, f.name);
    if (f.isDirectory()) copyDir(a, b);
    else if (/\.(png|webp|jpg|ttf|otf)$/i.test(f.name)) fs.copyFileSync(a, b);
    else fs.writeFileSync(b, tpl(fs.readFileSync(a, 'utf8')));
  }
}

function walk(dir, cb) {
  for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, f.name);
    if (f.isDirectory()) walk(p, cb); else cb(p);
  }
}

copyDir(path.join(src, 'java'), path.join(main, 'java', ...appId.split('.')));
copyDir(path.join(src, 'res'), path.join(main, 'res'));

// --- манифест: вставляем блоки между маркерами; отключаем облачный бэкап (в сторе — «данные не покидают устройство») ---
const mf = path.join(main, 'AndroidManifest.xml');
let m = fs.readFileSync(mf, 'utf8');
m = m.replace(/\s*<!-- WS-APP-BEGIN -->[\s\S]*?<!-- WS-APP-END -->/g, '')
     .replace(/\s*<!-- WS-TOP-BEGIN -->[\s\S]*?<!-- WS-TOP-END -->/g, '');
const appBlock = tpl(fs.readFileSync(path.join(src, 'manifest-application.xml'), 'utf8'));
const topBlock = tpl(fs.readFileSync(path.join(src, 'manifest-top.xml'), 'utf8'));
m = m.replace('</application>', () => `    <!-- WS-APP-BEGIN -->\n${appBlock}\n    <!-- WS-APP-END -->\n    </application>`);
m = m.replace('</manifest>', () => `    <!-- WS-TOP-BEGIN -->\n${topBlock}\n    <!-- WS-TOP-END -->\n</manifest>`);
m = m.replace(/android:allowBackup="true"/, 'android:allowBackup="false"');
fs.writeFileSync(mf, m);

// --- фирменная заставка: заменяем splash.png Capacitor во всех плотностях (светлая / ночная, портрет / ландшафт) ---
walk(path.join(main, 'res'), (p) => {
  if (path.basename(p) !== 'splash.png') return;
  const dir = path.basename(path.dirname(p));
  const name = (dir.includes('land') ? 'land' : 'port') + (dir.includes('night') ? '-night' : '');
  fs.copyFileSync(path.join(src, 'splash', name + '.png'), p);
});

// --- версия + подпись релиза ---
const g = path.join(root, 'android/app/build.gradle');
if (fs.existsSync(g)) {
  const [a = 1, b = 0, c = 0] = pkg.version.split('.').map(Number);
  const vc = process.env.VERSION_CODE || process.env.GITHUB_RUN_NUMBER || (a * 10000 + b * 100 + c);
  let s = fs.readFileSync(g, 'utf8');
  s = s.replace(/versionCode\s+\d+/, `versionCode ${vc}`).replace(/versionName\s+"[^"]*"/, `versionName "${pkg.version}"`);

  // Подпись берётся из переменных окружения (KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD).
  // Нет переменных — релиз собирается неподписанным (для Play нужен подписанный AAB).
  s = s.replace(/\n\s*\/\/ WS-SIGNING-BEGIN[\s\S]*?\/\/ WS-SIGNING-END\n?/g, '\n');
  const signing = `
    // WS-SIGNING-BEGIN
    signingConfigs {
        release {
            if (System.getenv("KEYSTORE_PATH")) {
                storeFile file(System.getenv("KEYSTORE_PATH"))
                storePassword System.getenv("KEYSTORE_PASSWORD")
                keyAlias System.getenv("KEY_ALIAS")
                keyPassword System.getenv("KEY_PASSWORD")
            }
        }
    }
    // WS-SIGNING-END
`;
  s = s.replace(/(\n\s*)buildTypes\s*\{/, (mm) => signing + mm.replace(/^\n?/, '\n'));
  s = s.replace(/\n\s*\/\/ WS-RELEASE-SIGN\n[^\n]*\n/g, '\n');
  s = s.replace(/(buildTypes\s*\{\s*release\s*\{)/, `$1\n            // WS-RELEASE-SIGN\n            if (System.getenv("KEYSTORE_PATH")) { signingConfig signingConfigs.release }`);
  fs.writeFileSync(g, s);
}

run('npx cap sync android');
console.log('\nГотово. Android Studio: npm run android:open | CI: .github/workflows/android.yml');
