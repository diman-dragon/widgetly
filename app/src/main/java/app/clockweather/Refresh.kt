package app.clockweather

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Сеть и календарь. Вызывается максимум раз в сутки (или вручную). */
object Refresh {
    private const val H = 3_600_000L
    private val lock = Any()

    fun stale(c: Context): Boolean {
        val p = Store.p(c); val n = System.currentTimeMillis()
        return (p.contains("lat") && n - p.getLong("w_t", 0) > 20 * H) || n - p.getLong("e_t", 0) > 20 * H
    }

    fun async(c: Context, force: Boolean, pr: android.content.BroadcastReceiver.PendingResult?) {
        val a = c.applicationContext
        Thread { try { run(a, force) } finally { pr?.finish() } }.start()
    }

    fun run(c: Context, force: Boolean) {
        synchronized(lock) {
            val p = Store.p(c); val n = System.currentTimeMillis()
            val gap = if (force) 60_000L else 30 * 60_000L
            if ((force || n - p.getLong("w_t", 0) > 20 * H) && n - p.getLong("w_try", 0) > gap) {
                p.edit().putLong("w_try", n).apply()
                weather(p)
            }
            if (force || n - p.getLong("e_t", 0) > 20 * H) events(c, p)
            ClockWidget.renderAll(c)
        }
    }

    private fun get(u: String): String {
        val cn = URL(u).openConnection() as HttpURLConnection
        cn.connectTimeout = 5000; cn.readTimeout = 5000
        try { return cn.inputStream.bufferedReader().use { it.readText() } } finally { cn.disconnect() }
    }

    private fun weather(p: SharedPreferences) {
        if (!p.contains("lat")) return
        try {
            val u = "https://api.open-meteo.com/v1/forecast?latitude=${p.getFloat("lat", 0f)}" +
                "&longitude=${p.getFloat("lon", 0f)}&current=temperature_2m,weather_code,is_day&timezone=auto"
            val j = JSONObject(get(u)).getJSONObject("current")
            p.edit().putFloat("w_temp", j.getDouble("temperature_2m").toFloat())
                .putInt("w_code", j.getInt("weather_code")).putInt("w_day", j.getInt("is_day"))
                .putLong("w_t", System.currentTimeMillis()).apply()
        } catch (_: Exception) { }
    }

    /** Ищет город по названию, сохраняет координаты. Возвращает имя или null. */
    fun geocode(c: Context, q: String): String? = try {
        val u = "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&language=" +
            Locale.getDefault().language + "&name=" + URLEncoder.encode(q, "UTF-8")
        val r = JSONObject(get(u)).optJSONArray("results")?.getJSONObject(0)
        if (r == null) null else {
            val name = r.getString("name")
            Store.p(c).edit().putFloat("lat", r.getDouble("latitude").toFloat())
                .putFloat("lon", r.getDouble("longitude").toFloat())
                .putString("city", name).putLong("w_t", 0).putLong("w_try", 0).apply()
            name
        }
    } catch (_: Exception) { null }

    private fun events(c: Context, p: SharedPreferences) {
        val out = StringBuilder()
        if (c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            try {
                val now = System.currentTimeMillis()
                val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(b, now); ContentUris.appendId(b, now + 36 * H)
                val proj = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.ALL_DAY)
                val day = SimpleDateFormat("EEE", Locale.getDefault())
                val tf = DateFormat.getTimeFormat(c)
                c.contentResolver.query(b.build(), proj, null, null, "begin ASC")?.use { q ->
                    var n = 0
                    while (n < 2 && q.moveToNext()) {
                        val title = q.getString(0) ?: continue
                        val begin = q.getLong(1)
                        val line = if (q.getInt(2) == 1) title else {
                            val d = if (DateUtils.isToday(begin)) "" else day.format(Date(begin)) + " "
                            d + tf.format(Date(begin)) + " · " + title
                        }
                        if (n > 0) out.append('\n')
                        out.append(line); n++
                    }
                }
            } catch (_: Exception) { }
        }
        p.edit().putString("ev", out.toString()).putLong("e_t", System.currentTimeMillis()).apply()
    }
}
