package com.widgetly.app

import org.json.JSONObject

/** Снимок погоды. Температуры всегда в °C, конвертация — при отрисовке. */
data class WeatherData(
    val tempC: Double,
    val code: Int,
    val isDay: Boolean,
    val hiC: Double,
    val loC: Double,
    /** Пустая строка = «текущее место» (режим геолокации). */
    val city: String,
    val fetchedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("tempC", tempC)
        put("code", code)
        put("isDay", isDay)
        put("hiC", hiC)
        put("loC", loC)
        put("city", city)
        put("fetchedAt", fetchedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): WeatherData = WeatherData(
            tempC = o.optDouble("tempC", 0.0),
            code = o.optInt("code", -1),
            isDay = o.optBoolean("isDay", true),
            hiC = o.optDouble("hiC", 0.0),
            loC = o.optDouble("loC", 0.0),
            city = o.optString("city", ""),
            fetchedAt = o.optLong("fetchedAt", 0L),
        )
    }
}
