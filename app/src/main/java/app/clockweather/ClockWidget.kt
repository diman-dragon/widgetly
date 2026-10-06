package app.clockweather

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, m, id)
        RefreshJob.schedule(context)
        if (Refresh.stale(context)) Refresh.async(context, false, goAsync())
    }

    override fun onAppWidgetOptionsChanged(context: Context, m: AppWidgetManager, id: Int, o: android.os.Bundle) {
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

        private fun emoji(code: Int, day: Boolean) = when (code) {
            0 -> if (day) "☀️" else "🌙"
            1, 2 -> if (day) "🌤️" else "☁️"
            3 -> "☁️"
            45, 48 -> "🌫️"
            in 51..57 -> "🌦️"
            in 61..67, in 80..82 -> "🌧️"
            in 71..77, 85, 86 -> "🌨️"
            in 95..99 -> "⛈️"
            else -> "🌡️"
        }

        private fun act(c: Context, code: Int, i: Intent) = PendingIntent.getActivity(c, code,
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun render(c: Context, m: AppWidgetManager, id: Int) {
            val cfg = Cfg.load(c)
            val p = Store.p(c)
            val o = m.getAppWidgetOptions(id)
            // размер в ячейках: n*70-30 dp
            val cols = ((o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320) + 30) / 70).coerceAtLeast(1)
            val rows = ((o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180) + 30) / 70).coerceAtLeast(1)
            val rv = RemoteViews(c.packageName, R.layout.widget)

            // фон + общая прозрачность
            rv.setInt(R.id.bg, "setColorFilter", cfg.bgColor)
            rv.setInt(R.id.bg, "setImageAlpha", cfg.bgAlpha * 255 / 100)

            // часы: в виджете ровно один TextClock нужного шрифта
            val k = when { cols >= 5 -> 1f; cols == 4 -> .88f; cols == 3 -> .72f; else -> .55f }
            val cap = when (rows) { 1 -> 34f; 2 -> 50f; else -> 99f }
            val fmt = if (cfg.h24) "HH:mm" else "h:mm"
            val cr = RemoteViews(c.packageName, CLOCK_LAYOUT[cfg.font])
            cr.setCharSequence(R.id.clk, "setFormat12Hour", fmt)
            cr.setCharSequence(R.id.clk, "setFormat24Hour", fmt)
            cr.setTextColor(R.id.clk, cfg.clockColor)
            cr.setTextViewTextSize(R.id.clk, TypedValue.COMPLEX_UNIT_SP, minOf(cfg.size * k, cap))
            rv.removeAllViews(R.id.clock_box)
            rv.addView(R.id.clock_box, cr)

            // будильник
            val an = c.getSystemService(AlarmManager::class.java).nextAlarmClock
            val alarmTxt = if (an == null) "" else "⏰ " + SimpleDateFormat(
                if (cfg.h24) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault()).format(Date(an.triggerTime))
            val alarmOn = cfg.alarmOn && rows >= 2 && cols >= 4 && alarmTxt.isNotEmpty()
            rv.setViewVisibility(R.id.alarm, if (alarmOn) View.VISIBLE else View.GONE)
            rv.setTextViewText(R.id.alarm, alarmTxt)
            rv.setTextColor(R.id.alarm, cfg.alarmColor)

            // дата
            val dateOn = cfg.dateOn && rows >= 2
            val df = DATE_FMT[cfg.dateFmt]
            rv.setCharSequence(R.id.date, "setFormat12Hour", df)
            rv.setCharSequence(R.id.date, "setFormat24Hour", df)
            rv.setTextColor(R.id.date, cfg.dateColor)
            rv.setTextViewTextSize(R.id.date, TypedValue.COMPLEX_UNIT_SP, if (cols <= 2) 13f else 16f)
            rv.setViewVisibility(R.id.date, if (dateOn) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.row_date, if (dateOn || alarmOn) View.VISIBLE else View.GONE)

            // погода (из кэша, сеть не трогаем)
            val wOn = cfg.weatherOn && cols >= 3
            rv.setViewVisibility(R.id.weather_box, if (wOn) View.VISIBLE else View.GONE)
            if (wOn) {
                val has = p.contains("w_code")
                val t = p.getFloat("w_temp", 0f)
                val v = if (cfg.fahr) t * 9 / 5 + 32 else t
                val s = if (rows == 1) .75f else 1f
                rv.setTextViewText(R.id.w_icon, if (has) emoji(p.getInt("w_code", 0), p.getInt("w_day", 1) == 1) else "🌡️")
                rv.setTextViewText(R.id.w_temp, if (has) "${v.roundToInt()}°" else "--°")
                rv.setTextViewText(R.id.w_city, p.getString("city", null) ?: "Укажите город")
                rv.setTextViewTextSize(R.id.w_icon, TypedValue.COMPLEX_UNIT_SP, 28 * s)
                rv.setTextViewTextSize(R.id.w_temp, TypedValue.COMPLEX_UNIT_SP, 26 * s)
                rv.setTextColor(R.id.w_temp, cfg.weatherColor)
                rv.setTextColor(R.id.w_city, cfg.weatherColor)
                rv.setViewVisibility(R.id.w_city, if (rows >= 2) View.VISIBLE else View.GONE)
            }

            // события календаря
            val ev = p.getString("ev", "") ?: ""
            val evOn = cfg.eventsOn && rows >= 3 && cols >= 3 && ev.isNotEmpty()
            rv.setViewVisibility(R.id.events, if (evOn) View.VISIBLE else View.GONE)
            rv.setTextViewText(R.id.events, ev)
            rv.setTextColor(R.id.events, cfg.eventsColor)

            // нажатия: часы/будильник -> Часы, дата/события -> Календарь, погода -> обновить
            val clockPi = act(c, 1, Intent(AlarmClock.ACTION_SHOW_ALARMS))
            val calPi = act(c, 2, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
            val refPi = PendingIntent.getBroadcast(c, 3, Intent(c, ClockWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            rv.setOnClickPendingIntent(R.id.clock_box, clockPi)
            rv.setOnClickPendingIntent(R.id.alarm, clockPi)
            rv.setOnClickPendingIntent(R.id.date, calPi)
            rv.setOnClickPendingIntent(R.id.events, calPi)
            rv.setOnClickPendingIntent(R.id.weather_box, refPi)

            m.updateAppWidget(id, rv)
        }
    }
}
