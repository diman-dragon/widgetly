package app.clockweather

import android.content.Context

object Store {
    fun p(c: Context) = c.getSharedPreferences("w", Context.MODE_PRIVATE)
}

val FONT_FAMILY = arrayOf("sans-serif-thin", "sans-serif-light", "sans-serif", "sans-serif-medium",
    "sans-serif-black", "sans-serif-condensed", "serif", "monospace", "casual")
val FONT_NAMES = arrayOf("Тонкий", "Лёгкий", "Обычный", "Средний", "Жирный", "Узкий", "С засечками", "Моно", "Рукописный")
val DATE_FMT = arrayOf("EEE, d MMM", "EEEE, d MMMM", "d MMMM")

/** Все настройки внешнего вида. Хранятся одной строкой чисел. */
data class Cfg(
    val font: Int = 1, val size: Int = 64, val clockColor: Int = WHITE, val h24: Boolean = true,
    val dateOn: Boolean = true, val dateFmt: Int = 1, val dateColor: Int = WHITE,
    val alarmOn: Boolean = true, val alarmColor: Int = WHITE,
    val weatherOn: Boolean = true, val fahr: Boolean = false, val weatherColor: Int = WHITE,
    val eventsOn: Boolean = true, val eventsColor: Int = WHITE,
    val bgColor: Int = BLACK, val bgAlpha: Int = 35
) {
    private fun b(v: Boolean) = if (v) 1 else 0

    fun save(c: Context) {
        val s = listOf(font, size, clockColor, b(h24), b(dateOn), dateFmt, dateColor, b(alarmOn), alarmColor,
            b(weatherOn), b(fahr), weatherColor, b(eventsOn), eventsColor, bgColor, bgAlpha).joinToString(",")
        Store.p(c).edit().putString("cfg", s).apply()
    }

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()

        fun load(c: Context): Cfg {
            val l = Store.p(c).getString("cfg", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            if (l == null || l.size != 16) return Cfg()
            return Cfg(l[0].coerceIn(0, 8), l[1], l[2], l[3] == 1, l[4] == 1, l[5].coerceIn(0, 2), l[6],
                l[7] == 1, l[8], l[9] == 1, l[10] == 1, l[11], l[12] == 1, l[13], l[14], l[15].coerceIn(0, 100))
        }
    }
}

/** Идеи у лидеров: Samsung One UI, Apple iOS, Huawei EMUI, Honor MagicOS, HTC Sense, Google Pixel. */
val PRESETS: List<Pair<String, Cfg>> = listOf(
    "Samsung One UI" to Cfg(font = 1, size = 72, dateFmt = 0, bgAlpha = 0),
    "Apple iOS" to Cfg(font = 3, size = 64, dateColor = 0xFFFF453A.toInt(), alarmColor = 0xFFFF9F0A.toInt(),
        weatherColor = 0xFF64D2FF.toInt(), bgColor = 0xFF1C1C1E.toInt(), bgAlpha = 70),
    "Huawei EMUI" to Cfg(font = 0, size = 72, bgAlpha = 12),
    "Honor MagicOS" to Cfg(font = 3, size = 64, dateColor = 0xFF4DA3FF.toInt(), alarmColor = 0xFF4DA3FF.toInt(),
        weatherColor = 0xFF4DA3FF.toInt(), bgColor = 0xFF0B1B33.toInt(), bgAlpha = 60),
    "HTC Sense" to Cfg(font = 5, size = 72, dateColor = 0xFFB5E26B.toInt(), alarmColor = 0xFFB5E26B.toInt(),
        weatherColor = 0xFFB5E26B.toInt(), bgAlpha = 25),
    "Google Pixel" to Cfg(font = 2, size = 64, clockColor = 0xFFE6DEFF.toInt(), dateColor = 0xFFD0BCFF.toInt(),
        alarmColor = 0xFFD0BCFF.toInt(), weatherColor = 0xFFD0BCFF.toInt(), bgColor = 0xFF1D1B20.toInt(), bgAlpha = 75)
)
