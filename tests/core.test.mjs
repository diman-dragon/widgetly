import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync,readdirSync,statSync} from 'node:fs'
import {join} from 'node:path'
const root=new URL('..',import.meta.url).pathname
const pkg=JSON.parse(readFileSync(join(root,'package.json'),'utf8'))
test('project is editor-only',()=>{assert.equal(pkg.name,'widget-editor-clean');for(const bad of ['better-auth','@electric-sql/pglite','kysely','pg','nitro']) assert.equal(pkg.dependencies?.[bad],undefined)})
test('no server/auth infrastructure exists',()=>{const dirs=['server','db','auth','multiplayer'];for(const d of dirs) assert.equal(statSafe(join(root,d)),false)})
test('visual editor files exist',()=>{for(const f of ['src/App.tsx','src/model.ts','src/widgets.tsx','src/styles.css','capacitor.config.ts']) assert.equal(statSafe(join(root,f)),true)})
test('no auth words in source filenames',()=>{const files=walk(join(root,'src'));assert.equal(files.some(f=>/auth|server|p2p|multiplayer/i.test(f)),false)})
function statSafe(p){try{statSync(p);return true}catch{return false}}
function walk(dir){let out=[];for(const e of readdirSync(dir,{withFileTypes:true})){const p=join(dir,e.name);if(e.isDirectory())out.push(...walk(p));else out.push(p)}return out}
