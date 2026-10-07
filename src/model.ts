export type Direction = 'horizontal' | 'vertical'
export type WidgetType = 'clock' | 'weather' | 'calendar' | 'events' | 'notifications' | 'text' | 'metric' | 'world'
export type Node = Leaf | Split
export type Leaf = { id: string; kind: 'leaf'; widgetId: string | null }
export type Split = { id: string; kind: 'split'; direction: Direction; ratio: number; first: Node; second: Node }
export type Widget = { id: string; type: WidgetType; title: string; city?: string; text?: string; value?: string; fontSize: number; opacity: number; align: 'left'|'center'|'right'; accent: boolean }
export type Snapshot = { root: Node; widgets: Record<string, Widget> }
let seq = 1
export const id = (prefix='id') => `${prefix}-${Date.now().toString(36)}-${seq++}`
export const clone = <T,>(v:T):T => structuredClone(v)
export const leaf = (widgetId:string|null=null):Leaf => ({id:id('leaf'),kind:'leaf',widgetId})
export const isLeaf = (n:Node):n is Leaf => n.kind==='leaf'
export const find = (n:Node, target:string):Node|null => n.id===target?n:(n.kind==='split'?(find(n.first,target)??find(n.second,target)):null)
export const update = (n:Node,target:string,fn:(n:Node)=>Node):Node => n.id===target?fn(n):n.kind==='split'?{...n,first:update(n.first,target,fn),second:update(n.second,target,fn)}:n
export const split = (root:Node,target:string,direction:Direction,newWidgetId:string|null=null):Node => update(root,target,n=>isLeaf(n)?{id:id('split'),kind:'split',direction,ratio:.5,first:n,second:leaf(newWidgetId)}:n)
export const setRatio = (root:Node,target:string,ratio:number):Node => update(root,target,n=>n.kind==='split'?{...n,ratio:Math.max(.12,Math.min(.88,ratio))}:n)
export const remove = (root:Node,target:string):Node => { if(root.id===target)return root; if(root.kind!=='split')return root; if(root.first.id===target)return root.second;if(root.second.id===target)return root.first;return {...root,first:remove(root.first,target),second:remove(root.second,target)} }
export const firstLeaf = (n:Node):Leaf => isLeaf(n)?n:firstLeaf(n.first)
export const collect = (n:Node,out:string[]=[]):string[] => {if(isLeaf(n)){if(n.widgetId)out.push(n.widgetId);return out}collect(n.first,out);return collect(n.second,out)}
export const widgetName:Record<WidgetType,string>={clock:'Часы',weather:'Погода',calendar:'Календарь',events:'События',notifications:'Уведомления',text:'Текст',metric:'Показатель',world:'Мировое время'}
export const createWidget=(type:WidgetType):Widget=>({id:id('widget'),type,title:widgetName[type],fontSize:type==='clock'?46:20,opacity:1,align:'center',accent:false,city:type==='weather'?'Белград':undefined,text:type==='text'?'Ваш текст':undefined,value:type==='metric'?'128':undefined})
export const starter=():Snapshot=>{const a=createWidget('clock'),b=createWidget('weather'),c=createWidget('calendar'),d=createWidget('events');const root:Node={id:id('root'),kind:'split',direction:'horizontal',ratio:.31,first:leaf(a.id),second:{id:id('split'),kind:'split',direction:'horizontal',ratio:.58,first:{id:id('split'),kind:'split',direction:'vertical',ratio:.5,first:leaf(b.id),second:leaf(c.id)},second:leaf(d.id)}};return {root,widgets:Object.fromEntries([a,b,c,d].map(w=>[w.id,w]))}}
