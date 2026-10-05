package com.widgetly.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Получение погоды из Open-Meteo (без ключа) и определение места. Вызывать только из фонового потока. */
object WeatherRepo {

    private class Place(val lat: Double, val lon: Double, val name: String)

    fun hasLocationPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Обновляет кэш погоды. allowFresh = true разрешает запросить свежую точку (только когда приложение на экране).
     * Бросает исключение с понятным текстом, если место определить или данные получить не удалось.
     */
    fun refresh(context: Context, allowFresh: Boolean): WeatherData {
        val store = Store(context)
        store.lastWeatherAttempt = System.currentTimeMillis()
        try {
            val place = resolvePlace(context, store, allowFresh)
            val data = fetch(place)
            store.saveWeather(data)
            store.weatherError = ""
            return data
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            store.weatherError = msg
            throw e
        }
    }

    private fun resolvePlace(context: Context, store: Store, allowFresh: Boolean): Place {
        if (store.getWeatherMode() == "gps") {
            var loc: Location? = if (hasLocationPermission(context)) lastKnown(context) else null
            if (loc == null && allowFresh && hasLocationPermission(context)) loc = freshLocation(context)
            if (loc != null) {
                store.saveCoords(loc.latitude, loc.longitude)
                return Place(loc.latitude, loc.longitude, "")
            }
            val saved = store.getSavedCoords()
            if (saved != null) return Place(saved[0], saved[1], "")
            throw IllegalStateException("Не удалось определить местоположение. Разрешите геолокацию или выберите город вручную.")
        }
        val city = store.getCity() ?: throw IllegalStateException("Город не выбран. Выберите город в разделе «Погода».")
        return Place(city.optDouble("lat"), city.optDouble("lon"), city.optString("name", ""))
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: Location? = null
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            try {
                val l = lm.getLastKnownLocation(provider)
                val b = best
                if (l != null && (b == null || l.time > b.time)) best = l
            } catch (e: Exception) {
                // провайдер недоступен или нет прав — пробуем следующий
            }
        }
        return best
    }

    @SuppressLint("MissingPermission")
    private fun freshLocation(context: Context): Location? {
        if (Build.VERSION.SDK_INT < 30) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val latch = CountDownLatch(1)
        var result: Location? = null
        try {
            lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor) { loc ->
                result = loc
                latch.countDown()
            }
            latch.await(8, TimeUnit.SECONDS)
        } catch (e: Exception) {
            return null
        }
        return result
    }

    private fun fetch(p: Place): WeatherData {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
            p.lat, p.lon,
        )
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) throw IllegalStateException("Open-Meteo: HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val cur = json.optJSONObject("current") ?: throw IllegalStateException("Open-Meteo: нет текущих данных")
            if (!cur.has("temperature_2m")) throw IllegalStateException("Open-Meteo: нет температуры")
            val temp = cur.getDouble("temperature_2m")
            val daily = json.optJSONObject("daily")
            val hi = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, temp) ?: temp
            val lo = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, temp) ?: temp
            return WeatherData(
                tempC = temp,
                code = cur.optInt("weather_code", -1),
                isDay = cur.optInt("is_day", 1) == 1,
                hiC = hi,
                loC = lo,
                city = p.name,
                fetchedAt = System.currentTimeMillis(),
            )
        } finally {
            conn.disconnect()
        }
    }
}
