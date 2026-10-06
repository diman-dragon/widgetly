package app.clockweather

import android.content.Context

object Store {
    fun p(c: Context) = c.getSharedPreferences("w", Context.MODE_PRIVATE)
}

val FONT_FAMILY = arrayOf("sans-serif-thin", "sans-serif-light", "sans-serif", "sans-serif-medium",
    "sans-serif-black", "sans-serif-condensed", "serif", "monospace", "casual")
val FONT_NAMES = arrayOf("Тонкий", "Лёгкий", "Обычный", "Средний", "Жирный", "Узкий", "С засечками", "Моно", "Рукописный")
val DATE_FMT = arrayOf("EEE, d MMM", "EEEE, d MMMM", "d MMMM")
val DATE_FMT_SPLIT = arrayOf("d MMM yyyy", "d MMMM yyyy", "d MMMM")
val LAYOUT_NAMES = arrayOf("Стопка (Samsung, Apple, Pixel)", "Две панели (Huawei, Honor)", "По центру (HTC)")
val SCENE_NAMES = arrayOf("Без картинки", "Закат", "Океан", "Ночь", "Лес")

/** Все настройки внешнего вида. Хранятся одной строкой чисел. */
data class Cfg(
    val font: Int = 1, val size: Int = 64, val clockColor: Int = WHITE, val h24: Boolean = true,
    val dateOn: Boolean = true, val dateFmt: Int = 1, val dateColor: Int = WHITE,
    val alarmOn: Boolean = true, val alarmColor: Int = WHITE,
    val weatherOn: Boolean = true, val fahr: Boolean = false, val weatherColor: Int = WHITE,
    val eventsOn: Boolean = true, val eventsColor: Int = WHITE,
    val bgColor: Int = BLACK, val bgAlpha: Int = 35,
    val layout: Int = 0, val scene: Int = 0, val forecastOn: Boolean = true
) {
    private fun b(v: Boolean) = if (v) 1 else 0

    fun save(c: Context) {
        val s = listOf(font, size, clockColor, b(h24), b(dateOn), dateFmt, dateColor, b(alarmOn), alarmColor,
            b(weatherOn), b(fahr), weatherColor, b(eventsOn), eventsColor, bgColor, bgAlpha,
            layout, scene, b(forecastOn)).joinToString(",")
        Store.p(c).edit().putString("cfg2", s).apply()
    }

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()

        fun load(c: Context): Cfg {
            val l = Store.p(c).getString("cfg2", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            if (l == null || l.size != 19) return PRESETS[0].second
            return Cfg(l[0].coerceIn(0, 8), l[1], l[2], l[3] == 1, l[4] == 1, l[5].coerceIn(0, 2), l[6],
                l[7] == 1, l[8], l[9] == 1, l[10] == 1, l[11], l[12] == 1, l[13], l[14], l[15].coerceIn(0, 100),
                l[16].coerceIn(0, 2), l[17].coerceIn(0, 4), l[18] == 1)
        }
    }
}

private fun h(v: Long) = v.toInt()

/** Пресеты повторяют подход прошивок: раскладка + шрифт + цвета + фон. */
val PRESETS: List<Pair<String, Cfg>> = listOf(
    // Huawei: часы на «обоях» слева, погода и прогноз справа (как референс)
    "Huawei EMUI" to Cfg(font = 0, size = 72, dateFmt = 1, bgColor = h(0xFF1A2347), bgAlpha = 88,
        layout = 1, scene = 1, forecastOn = true),
    // Honor: тёмное синее стекло, акцент синий, без картинки
    "Honor MagicOS" to Cfg(font = 1, size = 72, dateColor = h(0xFF6DB3FF), alarmColor = h(0xFF6DB3FF),
        weatherColor = h(0xFFE6F1FF), bgColor = h(0xFF0B1B33), bgAlpha = 82, layout = 1, scene = 0, forecastOn = true),
    // Samsung One UI: крупные лёгкие цифры, без фона, минимум деталей
    "Samsung One UI" to Cfg(font = 1, size = 72, dateFmt = 0, bgAlpha = 0, layout = 0, forecastOn = false),
    // Apple iOS: тёмная плитка, красный акцент у даты, прогноз внизу
    "Apple iOS" to Cfg(font = 3, size = 64, dateFmt = 0, dateColor = h(0xFFFF453A), alarmColor = h(0xFFFF9F0A),
        weatherColor = h(0xFFFFFFFF), bgColor = h(0xFF1C1C1E), bgAlpha = 82, layout = 0, forecastOn = true),
    // HTC Sense: всё по центру, узкие цифры, зелёный акцент
    "HTC Sense" to Cfg(font = 5, size = 72, dateColor = h(0xFFB5E26B), alarmColor = h(0xFFB5E26B),
        weatherColor = h(0xFFFFFFFF), bgAlpha = 30, layout = 2, forecastOn = false),
    // Google Pixel: пастельная Material-плитка
    "Google Pixel" to Cfg(font = 2, size = 64, clockColor = h(0xFFE6DEFF), dateColor = h(0xFFD0BCFF),
        alarmColor = h(0xFFD0BCFF), weatherColor = h(0xFFD0BCFF), bgColor = h(0xFF1D1B20), bgAlpha = 82,
        layout = 0, forecastOn = false)
)
