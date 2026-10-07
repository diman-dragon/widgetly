import {CalendarDays, CloudSun, Bell, Clock3, Globe2, List, Gauge, Type} from 'lucide-react'
import type {Widget, WidgetType} from './model'
export const catalog:{type:WidgetType;hint:string;icon:typeof Clock3}[]=[
 {type:'clock',hint:'Текущее время',icon:Clock3},{type:'weather',hint:'Температура и погода',icon:CloudSun},{type:'calendar',hint:'Дата и календарь',icon:CalendarDays},{type:'events',hint:'Список событий',icon:List},{type:'notifications',hint:'Уведомления',icon:Bell},{type:'text',hint:'Произвольный текст',icon:Type},{type:'metric',hint:'Число или показатель',icon:Gauge},{type:'world',hint:'Время в городах',icon:Globe2}
]
export function WidgetView({widget}:{widget:Widget}){
 const now=new Date(); const time=new Intl.DateTimeFormat('ru-RU',{hour:'2-digit',minute:'2-digit',second:'2-digit'}).format(now); const date=new Intl.DateTimeFormat('ru-RU',{weekday:'long',day:'numeric',month:'long'}).format(now)
 return <div className="widget-content" style={{fontSize:widget.fontSize,opacity:widget.opacity,textAlign:widget.align}}>
  {widget.type==='clock'&&<><div className="eyebrow">ТЕКУЩЕЕ ВРЕМЯ</div><div className="clock-value tabular">{time}</div><div className="muted">{date}</div></>}
  {widget.type==='weather'&&<><div className="eyebrow">{widget.city||'ГОРОД'}</div><div className="weather-value">18°</div><div className="muted">Переменная облачность</div></>}
  {widget.type==='calendar'&&<><div className="eyebrow">КАЛЕНДАРЬ</div><div className="calendar-day">{now.getDate()}</div><div className="muted">{date}</div></>}
  {widget.type==='events'&&<><div className="eyebrow">СОБЫТИЯ</div><div className="list"><div><b>14:30</b><span>Встреча</span></div><div><b>18:00</b><span>Позвонить</span></div></div></>}
  {widget.type==='notifications'&&<><div className="eyebrow">УВЕДОМЛЕНИЯ</div><div className="list"><div><b>2</b><span>новых сообщения</span></div><div><b>1</b><span>напоминание</span></div></div></>}
  {widget.type==='text'&&<><div className="eyebrow">ТЕКСТ</div><div>{widget.text||'Ваш текст'}</div></>}
  {widget.type==='metric'&&<><div className="eyebrow">ПОКАЗАТЕЛЬ</div><div className="metric-value">{widget.value||'0'}</div></>}
  {widget.type==='world'&&<><div className="eyebrow">МИРОВОЕ ВРЕМЯ</div><div className="list"><div><b>Белград</b><span>{time.slice(0,5)}</span></div><div><b>Токио</b><span>+8 ч</span></div></div></>}
 </div>
}
