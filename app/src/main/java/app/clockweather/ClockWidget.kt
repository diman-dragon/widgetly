package app.clockweather

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, m, id)
        RefreshJob.schedule(context)
        if (Refresh.stale(context)) Refresh.async(context, false, goAsync())
    }

    override fun onAppWidgetOptionsChanged(context: Context, m: AppWidgetManager, id: Int, o: Bundle) {
        render(context, m, id)
    }

    override fun onEnabled(context: Context) = RefreshJob.schedule(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(RefreshJob.ID)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> Refresh.async(context, true, goAsync())
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> renderAll(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_REFRESH = "app.clockweather.REFRESH"
        private val CLOCK_LAYOUT = intArrayOf(R.layout.clock0, R.layout.clock1, R.layout.clock2, R.layout.clock3,
            R.layout.clock4, R.layout.clock5, R.layout.clock6, R.layout.clock7, R.layout.clock8)

        fun renderAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            for (id in m.getAppWidgetIds(ComponentName(c, ClockWidget::class.java))) render(c, m, id)
        }

        fun render(c: Context, m: AppWidgetManager, id: Int) {
            val o = m.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
            m.updateAppWidget(id, build(c, Cfg.load(c), w, h, 0))
        }

        private fun act(c: Context, code: Int, i: Intent) = PendingIntent.getActivity(c, code,
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        private fun demoAlarm(): Long = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 7); set(Calendar.MINUTE, 30)
        }.timeInMillis

        /**
         * mode 0 = настоящий виджет (реальные данные, нажатия),
         * mode 1 = предпросмотр (реальные данные, пустое заменяется примером),
         * mode 2 = образец (всегда пример, для карточек пресетов).
         */
        fun build(c: Context, cfg: Cfg, wDp: Int, hDp: Int, mode: Int): RemoteViews {
            val cols = ((wDp + 30) / 70).coerceAtLeast(1)
            val rows = ((hDp + 30) / 70).coerceAtLeast(1)
            val p = Store.p(c)
            var wx = Data.weather(p)
            var ev = p.getString("ev", "") ?: ""
            var alarmT: Long? = c.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
            if (mode == 2) { wx = Data.DEMO; ev = Data.DEMO_EV; alarmT = demoAlarm() }
            else if (mode == 1) {
                if (wx == null) wx = Data.DEMO
                if (ev.isEmpty()) ev = Data.DEMO_EV
                if (alarmT == null) alarmT = demoAlarm()
            }

            // раскладка: «две панели» нужна ширина и высота, иначе откатываемся на «стопку»
            val lay = if (cfg.layout == 1 && cols >= 4 && rows >= 2 && cfg.weatherOn) 1 else if (cfg.layout == 2) 2 else 0
            val rv = RemoteViews(c.packageName, when (lay) {
                1 -> R.layout.widget_split; 2 -> R.layout.widget_center; else -> R.layout.widget_stack })

            fun vis(id: Int, on: Boolean) = rv.setViewVisibility(id, if (on) View.VISIBLE else View.GONE)
            fun sp(id: Int, v: Float) = rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, v)
            fun deg(v: Float) = "${(if (cfg.fahr) v * 9 / 5 + 32 else v).roundToInt()}°"

            // фон и картинка + общая прозрачность
            val a = cfg.bgAlpha * 255 / 100
            rv.setInt(R.id.bg, "setColorFilter", cfg.bgColor)
            rv.setInt(R.id.bg, "setImageAlpha", a)
            vis(R.id.scene, cfg.scene > 0)
            if (cfg.scene > 0) {
                rv.setImageViewResource(R.id.scene, (if (lay == 1) SCENE_L else SCENE_F)[cfg.scene - 1])
                rv.setInt(R.id.scene, "setImageAlpha", a)
            }

            // что показывать при данном размере
            val wShow = cfg.weatherOn && when (lay) { 1 -> true; 2 -> cols >= 4 && rows >= 2; else -> cols >= 4 }
            val fcShow = cfg.forecastOn && cfg.weatherOn && wx != null && wx.fc.isNotEmpty() && rows >= 3 && cols >= 4
            val dateOn = cfg.dateOn && (lay == 1 || lay == 2 && rows >= 2 || rows >= 2)
            val alarmOn = cfg.alarmOn && alarmT != null && when (lay) { 1 -> true; 2 -> rows >= 3; else -> rows >= 2 && cols >= 4 }
            val evOn = cfg.eventsOn && ev.isNotEmpty() && rows >= 3 && (lay == 1 || cols >= 3)

            // часы: в виджете один TextClock нужного шрифта; размер подгоняется под доступное место
            val k = when { cols >= 5 -> 1f; cols == 4 -> .88f; cols == 3 -> .72f; else -> .55f }
            val capW = when (lay) {
                1 -> (wDp * 0.465f - 28) / 2.45f
                2 -> (wDp - 28) / 2.45f
                else -> (wDp - 28 - (if (wShow) 98 else 0)) / 2.45f
            }
            val capH = when (lay) {
                1 -> if (rows == 2) 36f else 99f
                2 -> when (rows) { 1 -> 34f; 2 -> 48f; else -> if (fcShow) 44f else 60f }
                else -> when (rows) { 1 -> 34f; 2 -> 50f; else -> if (fcShow) 46f else 99f }
            }
            val fmt = if (cfg.h24) "HH:mm" else "h:mm"
            val cr = RemoteViews(c.packageName, CLOCK_LAYOUT[cfg.font])
            cr.setCharSequence(R.id.clk, "setFormat12Hour", fmt)
            cr.setCharSequence(R.id.clk, "setFormat24Hour", fmt)
            cr.setTextColor(R.id.clk, cfg.clockColor)
            cr.setTextViewTextSize(R.id.clk, TypedValue.COMPLEX_UNIT_SP, minOf(cfg.size * k, capW, capH).coerceAtLeast(24f))
            rv.removeAllViews(R.id.clock_box)
            rv.addView(R.id.clock_box, cr)

            // дата (и день недели в «двух панелях»)
            val df = if (lay == 1) DATE_FMT_SPLIT[cfg.dateFmt] else DATE_FMT[cfg.dateFmt]
            rv.setCharSequence(R.id.date, "setFormat12Hour", df)
            rv.setCharSequence(R.id.date, "setFormat24Hour", df)
            rv.setTextColor(R.id.date, cfg.dateColor)
            vis(R.id.date, dateOn)
            vis(R.id.wd, dateOn)
            rv.setTextColor(R.id.wd, cfg.dateColor)

            // будильник
            val at = if (alarmT == null) "" else "⏰ " + SimpleDateFormat(
                (if (lay == 1) "" else "EEE ") + (if (cfg.h24) "HH:mm" else "h:mm a"), Locale.getDefault()).format(Date(alarmT))
            vis(R.id.alarm, alarmOn)
            rv.setTextViewText(R.id.alarm, at)
            rv.setTextColor(R.id.alarm, cfg.alarmColor)
            vis(R.id.row_date, dateOn || alarmOn)
            vis(R.id.row_wd, dateOn || alarmOn)
            vis(R.id.row_info, dateOn || wShow)

            // погода
            vis(R.id.weather_box, wShow)
            if (wShow) {
                val s = if (rows == 1) .75f else 1f
                rv.setTextViewText(R.id.w_icon, if (wx != null) Data.emoji(wx.code, wx.day) else "🌡️")
                rv.setTextViewText(R.id.w_temp, if (wx != null) deg(wx.temp) else "--°")
                val city = wx?.city ?: p.getString("city", null) ?: "Укажите город"
                rv.setTextViewText(R.id.w_city, if (lay == 1) "📍 $city" else city)
                when (lay) {
                    1 -> { sp(R.id.w_icon, 34f); sp(R.id.w_temp, 34f) }
                    2 -> { sp(R.id.w_icon, 18f); sp(R.id.w_temp, 18f) }
                    else -> { sp(R.id.w_icon, 28f * s); sp(R.id.w_temp, 26f * s) }
                }
                vis(R.id.w_city, if (lay == 2) cols >= 5 else rows >= 2)
                rv.setTextColor(R.id.w_temp, cfg.weatherColor)
                rv.setTextColor(R.id.w_city, cfg.weatherColor)
                if (lay == 1 && wx != null) {
                    rv.setTextViewText(R.id.w_hilo, "↑${deg(wx.hi)}\n↓${deg(wx.lo)}")
                    rv.setTextViewText(R.id.w_desc, Data.desc(wx.code))
                    rv.setTextColor(R.id.w_hilo, cfg.weatherColor)
                    rv.setTextColor(R.id.w_desc, cfg.weatherColor)
                }
            }

            // прогноз: 6 колонок на 5 ячейках, 4 на 4; в компактных раскладках без минимумов
            vis(R.id.fc_box, fcShow)
            vis(R.id.div, fcShow)
            if (fcShow && wx != null) {
                val fcols = if (cols >= 5) 6 else 4
                for (i in 0 until 6) {
                    val f = FC[i]
                    val on = i < fcols && i < wx.fc.size
                    vis(f[0], on)
                    if (!on) continue
                    val d = wx.fc[i]
                    rv.setTextViewText(f[1], d.name); rv.setTextViewText(f[2], Data.emoji(d.code, true))
                    rv.setTextViewText(f[3], deg(d.hi)); rv.setTextViewText(f[4], deg(d.lo))
                    rv.setTextColor(f[1], cfg.weatherColor); rv.setTextColor(f[3], cfg.weatherColor)
                    rv.setTextColor(f[4], cfg.weatherColor)
                    vis(f[4], lay == 1)
                }
            }

            // события
            vis(R.id.events, evOn)
            rv.setTextViewText(R.id.events, ev)
            rv.setTextColor(R.id.events, cfg.eventsColor)
            rv.setInt(R.id.events, "setMaxLines", if (fcShow && lay != 1) 1 else 2)

            // нажатия только у настоящего виджета
            if (mode == 0) {
                val clockPi = act(c, 1, Intent(AlarmClock.ACTION_SHOW_ALARMS))
                val calPi = act(c, 2, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                val refPi = PendingIntent.getBroadcast(c, 3, Intent(c, ClockWidget::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                rv.setOnClickPendingIntent(R.id.clock_box, clockPi)
                rv.setOnClickPendingIntent(R.id.alarm, clockPi)
                rv.setOnClickPendingIntent(R.id.wd, calPi)
                rv.setOnClickPendingIntent(R.id.date, calPi)
                rv.setOnClickPendingIntent(R.id.events, calPi)
                rv.setOnClickPendingIntent(R.id.weather_box, refPi)
                rv.setOnClickPendingIntent(R.id.fc_box, refPi)
            }
            return rv
        }
    }
}
