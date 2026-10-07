import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { root } from './helpers.mjs'

const read = p => readFileSync(join(root, p), 'utf8')
const exists = p => { try { statSync(join(root, p)); return true } catch { return false } }
const pkg = JSON.parse(read('package.json'))
const walk = dir => readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)])

test('project is editor-only: no auth / server / db dependencies or folders', () => {
  for (const bad of ['better-auth', '@electric-sql/pglite', 'kysely', 'pg', 'nitro', 'express', 'firebase', 'supabase'])
    assert.equal(pkg.dependencies?.[bad], undefined, bad)
  for (const d of ['server', 'db', 'auth', 'multiplayer']) assert.equal(exists(d), false, d)
  assert.equal(walk(join(root, 'src')).some(f => /auth|server|p2p|multiplayer/i.test(f)), false)
})

test('version is identical in package.json, VERSION and CHANGELOG', () => {
  assert.equal(read('VERSION').trim(), pkg.version)
  assert.match(read('CHANGELOG.md'), new RegExp(`## ${pkg.version.replaceAll('.', '\\.')}`))
})

test('Node requirement matches Capacitor 8 (Node 22+) and CI uses Node 22', () => {
  assert.match(pkg.engines.node, /22/)
  const wf = read('.github/workflows/android-debug.yml')
  assert.match(wf, /node-version:\s*22/)
  assert.doesNotMatch(wf, /cache:\s*npm/)
  assert.match(wf, /npm install --no-audit --no-fund/)
})

test('capacitor config has no removed options', () => {
  assert.doesNotMatch(read('capacitor.config.ts'), /bundledWebRuntime/)
})

test('all expected source files exist', () => {
  for (const f of ['src/App.tsx', 'src/model.ts', 'src/history.ts', 'src/storage.ts', 'src/hooks.ts', 'src/widgets.tsx', 'src/styles.css',
    'src/components/Canvas.tsx', 'src/components/Inspector.tsx', 'src/components/Catalog.tsx', 'src/components/Sheet.tsx'])
    assert.equal(exists(f), true, f)
})

test('split buttons pass the leaf id and both directions', () => {
  const canvas = read('src/components/Canvas.tsx')
  assert.match(canvas, /ctx\.onSplit\(node\.id, 'horizontal'\)/)
  assert.match(canvas, /ctx\.onSplit\(node\.id, 'vertical'\)/)
})

test('placing mode is wired to leaf clicks', () => {
  assert.match(read('src/components/Canvas.tsx'), /if \(ctx\.placing\) ctx\.onPlace\(node\.id, ctx\.placing\)/)
})
