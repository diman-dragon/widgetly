package com.widgetly.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.max
import kotlin.math.sqrt

/** Отрисовка виджетов и минутный тик. */
object WidgetUpdater {
    const val ACTION_TICK = "com.widgetly.app.TICK"

    private const val FALLBACK_W_DP = 250
    private const val FALLBACK_H_DP = 110
    private const val MAX_PIXELS_CAP = 1_200_000

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
        updateWidgets(context, mgr, ids)
    }

    fun updateWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val store = Store(context)
        val started = System.currentTimeMillis()
        val weather = store.getWeather()
        var lastDesc = "—"
        var failed = false
        for (id in ids) {
            try {
                lastDesc = updateOne(context, mgr, store, id, weather)
            } catch (e: Throwable) {
                failed = true
                store.recordError("widget $id: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        if (!failed) store.clearError()
        if (ids.isNotEmpty()) store.recordRender(lastDesc, System.currentTimeMillis() - started, ids.size)
        if (ids.isNotEmpty()) scheduleNextTick(context) else cancelTick(context)
    }

    private fun updateOne(context: Context, mgr: AppWidgetManager, store: Store, id: Int, weather: WeatherData?): String {
        val design = pickDesign(store, id)
        val (wDp, hDp) = sizeDp(context, mgr, id)
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        var wPx = max(1, (wDp * density).toInt())
        var hPx = max(1, (hDp * density).toInt())

        // Лимит памяти RemoteViews: не больше половины пикселей экрана и не больше MAX_PIXELS_CAP.
        val limit = max(200_000, minOf(MAX_PIXELS_CAP, (metrics.widthPixels.toLong() * metrics.heightPixels / 2).toInt()))
        val pixels = wPx.toLong() * hPx
        if (pixels > limit) {
            val f = sqrt(limit.toDouble() / pixels)
            wPx = max(1, (wPx * f).toInt())
            hPx = max(1, (hPx * f).toInt())
        }

        val bitmap = DesignRenderer.render(design, wPx, hPx, Calendar.getInstance(), weather)
        val views = RemoteViews(context.packageName, R.layout.widget_clock)
        views.setImageViewBitmap(R.id.widget_image, bitmap)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launch != null) {
            val pi = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pi)
        }
        mgr.updateAppWidget(id, views)
        return "${wPx}x$hPx"
    }

    /** Привязанный дизайн → ожидающий (только что закреплённый) → стандартный. */
    fun pickDesign(store: Store, widgetId: Int): JSONObject {
        val assigned = store.getAssignment(widgetId)
        if (assigned != null) {
            val d = store.getDesign(assigned)
            if (d != null) return d
        }
        if (assigned == null) {
            val pending = store.pendingDesignId
            if (pending != null) {
                val d = store.getDesign(pending)
                if (d != null) {
                    store.setAssignment(widgetId, pending)
                    store.pendingDesignId = null
                    return d
                }
            }
        }
        return DefaultDesign.create()
    }

    fun sizeDp(context: Context, mgr: AppWidgetManager, id: Int): Pair<Int, Int> {
        val o = mgr.getAppWidgetOptions(id)
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val w = if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val h = if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return Pair(if (w > 0) w else FALLBACK_W_DP, if (h > 0) h else FALLBACK_H_DP)
    }

    // ---------- минутный тик ----------

    private fun tickIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Не-будящий будильник на начало следующей минуты: батарею не тратит, при пробуждении срабатывает сразу. */
    fun scheduleNextTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()
        val next = (now / 60_000L + 1) * 60_000L + 300L
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC, next, tickIntent(context))
        } catch (e: Exception) {
            Store(context).recordError("alarm: ${e.message}")
        }
    }

    fun cancelTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(tickIntent(context))
    }
}
