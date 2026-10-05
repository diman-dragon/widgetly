package com.widgetly.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Единое хранилище на SharedPreferences: дизайны, привязка виджетов, настройки погоды, кэш, диагностика.
 * Плагин и AppWidgetProvider работают в одном процессе и читают одни и те же данные.
 */
class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- дизайны ----------

    fun listDesigns(): JSONArray = synchronized(LOCK) {
        val raw = prefs.getString(K_DESIGNS, null) ?: return JSONArray()
        try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    fun getDesign(id: String): JSONObject? {
        val all = listDesigns()
        for (i in 0 until all.length()) {
            val d = all.optJSONObject(i) ?: continue
            if (d.optString("id") == id) return d
        }
        return null
    }

    /** Новые и обновлённые дизайны ставятся в начало списка. */
    fun saveDesign(design: JSONObject) = synchronized(LOCK) {
        val id = design.optString("id")
        val out = JSONArray()
        out.put(design)
        val all = listDesigns()
        for (i in 0 until all.length()) {
            val d = all.optJSONObject(i) ?: continue
            if (d.optString("id") != id) out.put(d)
        }
        prefs.edit().putString(K_DESIGNS, out.toString()).apply()
    }

    fun deleteDesign(id: String) = synchronized(LOCK) {
        val out = JSONArray()
        val all = listDesigns()
        for (i in 0 until all.length()) {
            val d = all.optJSONObject(i) ?: continue
            if (d.optString("id") != id) out.put(d)
        }
        val editor = prefs.edit().putString(K_DESIGNS, out.toString())
        // Виджеты, привязанные к удалённому дизайну, вернутся к стандартному.
        for (key in prefs.all.keys) {
            if (key.startsWith(K_ASSIGN) && prefs.getString(key, null) == id) editor.remove(key)
        }
        editor.apply()
    }

    // ---------- привязка виджетов ----------

    fun getAssignment(widgetId: Int): String? = prefs.getString(K_ASSIGN + widgetId, null)

    fun setAssignment(widgetId: Int, designId: String) {
        prefs.edit().putString(K_ASSIGN + widgetId, designId).apply()
    }

    fun clearAssignment(widgetId: Int) {
        prefs.edit().remove(K_ASSIGN + widgetId).apply()
    }

    /** Дизайн, ожидающий привязки к только что закреплённому виджету. Живёт 2 минуты. */
    var pendingDesignId: String?
        get() {
            val id = prefs.getString(K_PENDING, null) ?: return null
            val at = prefs.getLong(K_PENDING_AT, 0L)
            return if (System.currentTimeMillis() - at < PENDING_TTL_MS) id else null
        }
        set(value) {
            val e = prefs.edit()
            if (value == null) {
                e.remove(K_PENDING).remove(K_PENDING_AT)
            } else {
                e.putString(K_PENDING, value).putLong(K_PENDING_AT, System.currentTimeMillis())
            }
            e.apply()
        }

    // ---------- погода ----------

    fun getWeatherMode(): String = prefs.getString(K_W_MODE, "city") ?: "city"

    fun getCity(): JSONObject? {
        val raw = prefs.getString(K_W_CITY, null) ?: return null
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            null
        }
    }

    fun setWeatherSettings(mode: String, city: JSONObject?) {
        val e = prefs.edit().putString(K_W_MODE, if (mode == "gps") "gps" else "city")
        if (city != null) e.putString(K_W_CITY, city.toString())
        e.apply()
    }

    fun getWeather(): WeatherData? {
        val raw = prefs.getString(K_W_CACHE, null) ?: return null
        return try {
            WeatherData.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            null
        }
    }

    fun saveWeather(w: WeatherData) {
        prefs.edit().putString(K_W_CACHE, w.toJson().toString()).apply()
    }

    fun getSavedCoords(): DoubleArray? {
        if (!prefs.contains(K_LAT) || !prefs.contains(K_LON)) return null
        return doubleArrayOf(
            java.lang.Double.longBitsToDouble(prefs.getLong(K_LAT, 0L)),
            java.lang.Double.longBitsToDouble(prefs.getLong(K_LON, 0L)),
        )
    }

    fun saveCoords(lat: Double, lon: Double) {
        prefs.edit()
            .putLong(K_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(K_LON, java.lang.Double.doubleToRawLongBits(lon))
            .apply()
    }

    var lastWeatherAttempt: Long
        get() = prefs.getLong(K_W_ATTEMPT, 0L)
        set(v) {
            prefs.edit().putLong(K_W_ATTEMPT, v).apply()
        }

    var weatherError: String
        get() = prefs.getString(K_W_ERR, "") ?: ""
        set(v) {
            prefs.edit().putString(K_W_ERR, v).apply()
        }

    /** Нужно ли тянуть погоду: данных нет или они старше 30 минут, и последняя попытка была давно. */
    fun weatherNeedsRefresh(now: Long): Boolean {
        val w = getWeather()
        val stale = w == null || now - w.fetchedAt > STALE_MS
        val recentlyTried = now - lastWeatherAttempt < RETRY_MS
        return stale && !recentlyTried
    }

    // ---------- диагностика ----------

    fun recordRender(bitmapDesc: String, ms: Long, widgetCount: Int) {
        prefs.edit()
            .putLong(K_D_AT, System.currentTimeMillis())
            .putLong(K_D_MS, ms)
            .putString(K_D_BMP, bitmapDesc)
            .putInt(K_D_COUNT, widgetCount)
            .apply()
    }

    fun recordError(message: String) {
        prefs.edit().putString(K_D_ERR, message.take(300)).apply()
    }

    fun clearError() {
        prefs.edit().remove(K_D_ERR).apply()
    }

    fun diagnostics(): JSONObject = JSONObject().apply {
        put("native", true)
        put("lastRenderAt", prefs.getLong(K_D_AT, 0L))
        put("lastRenderMs", prefs.getLong(K_D_MS, 0L))
        put("lastBitmap", prefs.getString(K_D_BMP, "—") ?: "—")
        put("widgetCount", prefs.getInt(K_D_COUNT, 0))
        put("lastError", prefs.getString(K_D_ERR, "") ?: "")
        put("weatherFetchedAt", getWeather()?.fetchedAt ?: 0L)
        put("weatherError", weatherError)
        put("designCount", listDesigns().length())
    }

    companion object {
        private const val PREFS = "widgetly"
        private val LOCK = Any()

        private const val K_DESIGNS = "designs"
        private const val K_ASSIGN = "assign_"
        private const val K_PENDING = "pending_design"
        private const val K_PENDING_AT = "pending_design_at"
        private const val K_W_MODE = "weather_mode"
        private const val K_W_CITY = "weather_city"
        private const val K_W_CACHE = "weather_cache"
        private const val K_W_ATTEMPT = "weather_attempt"
        private const val K_W_ERR = "weather_error"
        private const val K_LAT = "coord_lat"
        private const val K_LON = "coord_lon"
        private const val K_D_AT = "diag_at"
        private const val K_D_MS = "diag_ms"
        private const val K_D_BMP = "diag_bmp"
        private const val K_D_COUNT = "diag_count"
        private const val K_D_ERR = "diag_err"

        private const val PENDING_TTL_MS = 2 * 60 * 1000L
        private const val STALE_MS = 30 * 60 * 1000L
        private const val RETRY_MS = 10 * 60 * 1000L
    }
}
