package com.widgetly.app

import java.util.Calendar

/**
 * Форматирование времени, даты и погоды.
 * Чистый Kotlin без Android API — тестируется на JVM.
 * ВАЖНО: логика и таблицы идентичны web/src/design/format.ts. Менять только синхронно.
 */
object TextFormat {
    private val TOKEN = Regex("'[^']*'|EEEE|EEE|MMMM|MMM|MM|M|yyyy|yy|dd|d|HH|H|hh|h|mm|A|a")

    private val DAYS_RU = arrayOf("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
    private val DAYS_EN = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val DAYS_SHORT_RU = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
    private val DAYS_SHORT_EN = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val MONTHS_RU = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val MONTHS_EN = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val MONTHS_SHORT_RU = arrayOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    private val MONTHS_SHORT_EN = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun pad2(n: Int): String = if (n < 10) "0$n" else "$n"

    fun format(pattern: String, cal: Calendar, locale: String): String {
        val ru = locale == "ru"
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val dow = cal.get(Calendar.DAY_OF_WEEK) - 1 // Calendar: 1 = воскресенье
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val minute = cal.get(Calendar.MINUTE)
        return TOKEN.replace(pattern) { m ->
            val t = m.value
            when (t) {
                "EEEE" -> (if (ru) DAYS_RU else DAYS_EN)[dow]
                "EEE" -> (if (ru) DAYS_SHORT_RU else DAYS_SHORT_EN)[dow]
                "MMMM" -> (if (ru) MONTHS_RU else MONTHS_EN)[month]
                "MMM" -> (if (ru) MONTHS_SHORT_RU else MONTHS_SHORT_EN)[month]
                "MM" -> pad2(month + 1)
                "M" -> (month + 1).toString()
                "yyyy" -> year.toString()
                "yy" -> pad2(year % 100)
                "dd" -> pad2(day)
                "d" -> day.toString()
                "HH" -> pad2(hour)
                "H" -> hour.toString()
                "hh" -> pad2(h12)
                "h" -> h12.toString()
                "mm" -> pad2(minute)
                "A" -> if (hour < 12) "AM" else "PM"
                "a" -> if (hour < 12) "am" else "pm"
                else -> if (t.length == 2) "'" else t.substring(1, t.length - 1)
            }
        }
    }

    /** Идентично Math.floor(x + 0.5) в JS. */
    fun roundHalfUp(x: Double): Int = Math.floor(x + 0.5).toInt()

    fun tempText(tempC: Double, unit: String, showUnit: Boolean): String {
        val v = if (unit == "F") tempC * 9.0 / 5.0 + 32.0 else tempC
        return "${roundHalfUp(v)}°" + (if (showUnit) unit else "")
    }

    fun rangeText(hiC: Double, loC: Double, unit: String): String =
        "↑" + tempText(hiC, unit, false) + " ↓" + tempText(loC, unit, false)

    fun weatherIcon(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1 -> if (isDay) "🌤️" else "🌙"
        2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67 -> "🌧️"
        71, 73, 75, 77 -> "🌨️"
        80, 81, 82 -> "🌧️"
        85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "❓"
    }

    fun conditionText(code: Int, locale: String): String {
        val ru = locale == "ru"
        return when (code) {
            0 -> if (ru) "Ясно" else "Clear"
            1 -> if (ru) "Малооблачно" else "Mostly clear"
            2 -> if (ru) "Переменная облачность" else "Partly cloudy"
            3 -> if (ru) "Пасмурно" else "Overcast"
            45, 48 -> if (ru) "Туман" else "Fog"
            51, 53, 55 -> if (ru) "Морось" else "Drizzle"
            56, 57 -> if (ru) "Ледяная морось" else "Freezing drizzle"
            61 -> if (ru) "Небольшой дождь" else "Light rain"
            63 -> if (ru) "Дождь" else "Rain"
            65 -> if (ru) "Сильный дождь" else "Heavy rain"
            66, 67 -> if (ru) "Ледяной дождь" else "Freezing rain"
            71 -> if (ru) "Небольшой снег" else "Light snow"
            73 -> if (ru) "Снег" else "Snow"
            75 -> if (ru) "Сильный снег" else "Heavy snow"
            77 -> if (ru) "Снежная крупа" else "Snow grains"
            80, 81 -> if (ru) "Ливень" else "Showers"
            82 -> if (ru) "Сильный ливень" else "Heavy showers"
            85, 86 -> if (ru) "Снегопад" else "Snow showers"
            95 -> if (ru) "Гроза" else "Thunderstorm"
            96, 99 -> if (ru) "Гроза с градом" else "Thunderstorm with hail"
            else -> "—"
        }
    }

    fun noDataText(locale: String): String = if (locale == "ru") "Нет данных" else "No data"

    fun cityText(city: String?, locale: String): String {
        if (city == null) return ""
        if (city.trim().isNotEmpty()) return city
        return if (locale == "ru") "Моё место" else "My location"
    }
}
