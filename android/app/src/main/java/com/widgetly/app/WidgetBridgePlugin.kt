package com.widgetly.app

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import org.json.JSONArray
import org.json.JSONObject

/** Мост редактор (WebView) ↔ нативные виджеты. Контракт — web/src/bridge/definitions.ts. */
@CapacitorPlugin(
    name = "WidgetBridge",
    permissions = [Permission(strings = [Manifest.permission.ACCESS_COARSE_LOCATION], alias = "location")],
)
class WidgetBridgePlugin : Plugin() {

    private val store: Store get() = Store(context)

    @PluginMethod
    fun getInfo(call: PluginCall) {
        val mgr = AppWidgetManager.getInstance(context)
        val pin = Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported
        val ret = JSObject()
        ret.put("native", true)
        ret.put("version", "0.1.0")
        ret.put("sdk", Build.VERSION.SDK_INT)
        ret.put("pinSupported", pin)
        call.resolve(ret)
    }

    // ---------- дизайны ----------

    @PluginMethod
    fun listDesigns(call: PluginCall) {
        val ret = JSObject()
        ret.put("designs", store.listDesigns())
        call.resolve(ret)
    }

    @PluginMethod
    fun saveDesign(call: PluginCall) {
        val design = call.getObject("design")
        if (design == null || design.optString("id").isEmpty()) {
            call.reject("Нет дизайна или id")
            return
        }
        store.saveDesign(JSONObject(design.toString()))
        WidgetUpdater.updateAll(context)
        call.resolve()
    }

    @PluginMethod
    fun deleteDesign(call: PluginCall) {
        val id = call.getString("id")
        if (id.isNullOrEmpty()) {
            call.reject("Нет id")
            return
        }
        store.deleteDesign(id)
        WidgetUpdater.updateAll(context)
        call.resolve()
    }

    // ---------- виджеты ----------

    @PluginMethod
    fun pinWidget(call: PluginCall) {
        val designId = call.getString("designId")
        if (designId.isNullOrEmpty() || store.getDesign(designId) == null) {
            call.reject("Дизайн не найден. Сначала сохраните его")
            return
        }
        val mgr = AppWidgetManager.getInstance(context)
        val ret = JSObject()
        if (Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported) {
            store.pendingDesignId = designId
            val ok = mgr.requestPinAppWidget(ComponentName(context, ClockWidgetProvider::class.java), null, null)
            ret.put("requested", ok)
        } else {
            ret.put("requested", false)
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun getActiveWidgets(call: PluginCall) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
        val st = store
        val list = JSONArray()
        for (id in ids) {
            val size = WidgetUpdater.sizeDp(context, mgr, id)
            val o = JSONObject()
            o.put("widgetId", id)
            o.put("designId", st.getAssignment(id) ?: "")
            o.put("widthDp", size.first)
            o.put("heightDp", size.second)
            list.put(o)
        }
        val ret = JSObject()
        ret.put("widgets", list)
        call.resolve(ret)
    }

    @PluginMethod
    fun applyDesign(call: PluginCall) {
        val widgetId = call.getInt("widgetId")
        val designId = call.getString("designId")
        if (widgetId == null || designId.isNullOrEmpty()) {
            call.reject("Нужны widgetId и designId")
            return
        }
        store.setAssignment(widgetId, designId)
        WidgetUpdater.updateWidgets(context, AppWidgetManager.getInstance(context), intArrayOf(widgetId))
        call.resolve()
    }

    @PluginMethod
    fun refreshWidgets(call: PluginCall) {
        WidgetUpdater.updateAll(context)
        call.resolve()
    }

    // ---------- погода ----------

    @PluginMethod
    fun getWeatherSettings(call: PluginCall) {
        val st = store
        val ret = JSObject()
        ret.put("mode", st.getWeatherMode())
        val city = st.getCity()
        ret.put("city", city ?: JSONObject.NULL)
        ret.put("hasLocationPermission", WeatherRepo.hasLocationPermission(context))
        call.resolve(ret)
    }

    @PluginMethod
    fun setWeatherSettings(call: PluginCall) {
        val mode = call.getString("mode") ?: "city"
        val cityObj = call.getObject("city")
        val city = if (cityObj != null && cityObj.has("lat") && cityObj.has("lon")) JSONObject(cityObj.toString()) else null
        store.setWeatherSettings(mode, city)
        call.resolve()
    }

    @PluginMethod
    fun getWeather(call: PluginCall) {
        val w = store.getWeather()
        val ret = JSObject()
        ret.put("weather", if (w != null) w.toJson() else JSONObject.NULL)
        call.resolve(ret)
    }

    @PluginMethod
    fun refreshWeather(call: PluginCall) {
        Thread {
            try {
                val w = WeatherRepo.refresh(context, true)
                WidgetUpdater.updateAll(context)
                val ret = JSObject()
                ret.put("weather", w.toJson())
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject(e.message ?: "Не удалось получить погоду")
            }
        }.start()
    }

    @PluginMethod
    fun requestLocationPermission(call: PluginCall) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            val ret = JSObject()
            ret.put("granted", true)
            call.resolve(ret)
            return
        }
        requestPermissionForAlias("location", call, "locationPermissionCallback")
    }

    @PermissionCallback
    fun locationPermissionCallback(call: PluginCall) {
        val ret = JSObject()
        ret.put("granted", getPermissionState("location") == PermissionState.GRANTED)
        call.resolve(ret)
    }

    // ---------- сервис ----------

    @PluginMethod
    fun getDiagnostics(call: PluginCall) {
        call.resolve(JSObject(store.diagnostics().toString()))
    }

    @PluginMethod
    fun openBatterySettings(call: PluginCall) {
        val ret = JSObject()
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ret.put("opened", true)
        } catch (e: Exception) {
            ret.put("opened", false)
        }
        call.resolve(ret)
    }
}
