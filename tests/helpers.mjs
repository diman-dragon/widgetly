import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { pathToFileURL, fileURLToPath } from 'node:url'
import ts from 'typescript'

export const root = fileURLToPath(new URL('..', import.meta.url))

/** Компилирует TS-модуль без зависимостей и импортирует его (работает на любой версии Node). */
export async function loadTs(rel) {
  const src = readFileSync(join(root, rel), 'utf8')
  const out = ts.transpileModule(src, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
  const dir = mkdtempSync(join(tmpdir(), 'widgetly-'))
  const file = join(dir, rel.split('/').pop().replace(/\.ts$/, '.mjs'))
  writeFileSync(file, out)
  return import(pathToFileURL(file).href)
}
