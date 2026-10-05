package com.widgetly.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        WidgetUpdater.updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = Store(context)
        for (id in appWidgetIds) store.clearAssignment(id)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.scheduleNextTick(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.cancelTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            super.onReceive(context, intent)
            when (intent.action) {
                WidgetUpdater.ACTION_TICK,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED -> WidgetUpdater.updateAll(context)
            }
            refreshWeatherIfStale(context)
        } catch (e: Throwable) {
            Store(context).recordError("receiver: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Погода обновляется в фоне прямо из приёмника (goAsync даёт ~10 с), без WorkManager. */
    private fun refreshWeatherIfStale(context: Context) {
        val store = Store(context)
        if (!store.weatherNeedsRefresh(System.currentTimeMillis())) return
        val pending = goAsync()
        Thread {
            try {
                WeatherRepo.refresh(context.applicationContext, false)
                WidgetUpdater.updateAll(context.applicationContext)
            } catch (e: Exception) {
                // причина уже записана в Store.weatherError
            } finally {
                pending.finish()
            }
        }.start()
    }
}
