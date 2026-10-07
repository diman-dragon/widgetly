#!/usr/bin/env node
/*
 * Генерирует android/ (если нет), копирует нативную часть из native/android,
 * патчит манифест и версию, затем делает cap sync. Идемпотентен.
 */
const fs = require('fs');
const path = require('path');
const cp = require('child_process');

const root = path.join(__dirname, '..');
const run = (c) => cp.execSync(c, { cwd: root, stdio: 'inherit' });
const cfg = JSON.parse(fs.readFileSync(path.join(root, 'capacitor.config.json'), 'utf8'));
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
const appId = cfg.appId;

if (!fs.existsSync(path.join(root, 'android'))) run('npx cap add android');

const main = path.join(root, 'android/app/src/main');
const src = path.join(root, 'native/android');
const tpl = (s) => s.split('__APP_ID__').join(appId);

function copyDir(from, to) {
  fs.mkdirSync(to, { recursive: true });
  for (const f of fs.readdirSync(from, { withFileTypes: true })) {
    const a = path.join(from, f.name), b = path.join(to, f.name);
    if (f.isDirectory()) copyDir(a, b);
    else fs.writeFileSync(b, tpl(fs.readFileSync(a, 'utf8')));
  }
}

copyDir(path.join(src, 'java'), path.join(main, 'java', ...appId.split('.')));
copyDir(path.join(src, 'res'), path.join(main, 'res'));

// --- манифест: вставляем блок между маркерами ---
const mf = path.join(main, 'AndroidManifest.xml');
let m = fs.readFileSync(mf, 'utf8');
m = m.replace(/\s*<!-- WS-APP-BEGIN -->[\s\S]*?<!-- WS-APP-END -->/g, '')
     .replace(/\s*<!-- WS-TOP-BEGIN -->[\s\S]*?<!-- WS-TOP-END -->/g, '');
const appBlock = tpl(fs.readFileSync(path.join(src, 'manifest-application.xml'), 'utf8'));
const topBlock = tpl(fs.readFileSync(path.join(src, 'manifest-top.xml'), 'utf8'));
m = m.replace('</application>', () => `    <!-- WS-APP-BEGIN -->\n${appBlock}\n    <!-- WS-APP-END -->\n    </application>`);
m = m.replace('</manifest>', () => `    <!-- WS-TOP-BEGIN -->\n${topBlock}\n    <!-- WS-TOP-END -->\n</manifest>`);
fs.writeFileSync(mf, m);

// --- версия ---
const g = path.join(main, '..', '..', 'build.gradle');
if (fs.existsSync(g)) {
  const [a = 1, b = 0, c = 0] = pkg.version.split('.').map(Number);
  const vc = process.env.VERSION_CODE || process.env.GITHUB_RUN_NUMBER || (a * 10000 + b * 100 + c);
  let s = fs.readFileSync(g, 'utf8');
  s = s.replace(/versionCode\s+\d+/, `versionCode ${vc}`).replace(/versionName\s+"[^"]*"/, `versionName "${pkg.version}"`);
  fs.writeFileSync(g, s);
}

run('npx cap sync android');
console.log('\nГотово. Открыть в Android Studio: npm run android:open  |  или собирать в CI (.github/workflows/android.yml)');
