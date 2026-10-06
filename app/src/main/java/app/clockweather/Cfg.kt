package app.clockweather

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Настройки внешнего вида + список шрифтов.
 *
 * Шрифт задаётся одним значением [Cfg.font] и применяется ко **всему** виджету: часы, дата,
 * день недели, будильник, погода, прогноз и события (см. `ClockWidget.applyFont`). Для этого
 * каждая гарнитура объявлена ещё и в `res/font/*.xml`: только так `RemoteViews` позволяет
 * менять семейство шрифта у уже созданного TextClock (`setTextViewFont`, API 31+).
 */
class Fnt(val title: String, val family: String, val res: Int)

/** Совместимость со старым сохранённым индексом: массив имён для Spinner. */
val FONT_NAMES: Array<String> = arrayOf(
    "Тонкий", "Лёгкий", "Обычный", "Средний", "Жирный", "Узкий", "Узкий средний",
    "С засечками", "Засечки + моно", "Моноширинный", "Рукописный", "Курсив")

private val FONT_FAMS = arrayOf(
    "sans-serif-thin", "sans-serif-light", "sans-serif", "sans-serif-medium", "sans-serif-black",
    "sans-serif-condensed", "sans-serif-condensed-medium", "serif", "serif-monospace",
    "monospace", "casual", "cursive")

private val FONT_RES = intArrayOf(
    R.font.sans_serif_thin, R.font.sans_serif_light, R.font.sans_serif, R.font.sans_serif_medium,
    R.font.sans_serif_black, R.font.sans_serif_condensed, R.font.sans_serif_condensed_medium,
    R.font.serif, R.font.serif_monospace, R.font.monospace, R.font.casual, R.font.cursive)

val FONTS: List<Fnt> = FONT_NAMES.mapIndexed { i, n -> Fnt(n, FONT_FAMS[i], FONT_RES[i]) }
private const val NF = FONT_NAMES.size

fun fontOf(i: Int): Fnt = FONTS[i.coerceIn(0, NF - 1)]

/** Гарнитура для предпросмотра внутри приложения (обычный View, не RemoteViews). */
fun uiTypeface(c: Context, i: Int): android.graphics.Typeface =
    androidx.core.content.res.ResourcesCompat.getFont(c, fontOf(i).res)
        ?: android.graphics.Typeface.create(fontOf(i).family, android.graphics.Typeface.NORMAL)

val DATE_FMT = arrayOf("EEE, d MMM", "EEEE, d MMMM", "d MMMM")
val DATE_FMT_SPLIT = arrayOf("d MMM yyyy", "d MMMM yyyy", "d MMMM")
val LAYOUT_NAMES = arrayOf("Стопка (Samsung, Apple, Pixel)", "Две панели (Huawei, Honor)", "По центру (HTC)")
val SCENE_NAMES = arrayOf("Без картинки", "Закат", "Океан", "Ночь", "Лес")

/** Варианты длины прогноза: 0 = выключен, N = N дней. */
val FC_DAYS = intArrayOf(0, 1, 2, 3, 5, 6)
val FC_NAMES = arrayOf("Выключен", "1 день", "2 дня", "3 дня", "5 дней", "6 дней")
const val FC_MAX = 6

/** Сколько ячеек прогноза помещается в строку при данной раскладке и ширине. */
fun fcCols(layout: Int, cols: Int): Int = when (layout) {
    1 -> minOf(cols, FC_MAX)
    else -> if (cols >= 6) 6 else if (cols >= 4) 4 else 3
}

/** Все настройки внешнего вида. Хранятся одной строкой чисел. */
data class Cfg(
    val font: Int = 1, val size: Int = 64, val clockColor: Int = WHITE, val h24: Boolean = true,
    val dateOn: Boolean = true, val dateFmt: Int = 1, val dateColor: Int = WHITE,
    val alarmOn: Boolean = true, val alarmColor: Int = WHITE,
    val weatherOn: Boolean = true, val fahr: Boolean = false, val weatherColor: Int = WHITE,
    val eventsOn: Boolean = true, val eventsColor: Int = WHITE,
    val bgColor: Int = BLACK, val bgAlpha: Int = 35,
    val layout: Int = 0, val scene: Int = 0, val forecastDays: Int = 3
) {
    private fun b(v: Boolean) = if (v) 1 else 0

    /** Число дней прогноза (0 = выключен). */
    val forecastOn: Boolean get() = forecastDays > 0

    fun save(c: Context) {
        val s = listOf(font, size, clockColor, b(h24), b(dateOn), dateFmt, dateColor, b(alarmOn), alarmColor,
            b(weatherOn), b(fahr), weatherColor, b(eventsOn), eventsColor, bgColor, bgAlpha,
            layout, scene, forecastDays).joinToString(",")
        Store.p(c).edit().putString("cfg3", s).apply()
    }

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        private const val N = 19
        private fun iv(v: Int, max: Int) = if (v in 0..max) v else 0

        fun load(c: Context): Cfg {
            val sp = Store.p(c)
            var l = sp.getString("cfg3", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            if (l == null || l.size != N) {
                // разовая миграция со старой схемы (forecastOn: 0/1 -> число дней)
                val o = sp.getString("cfg2", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
                if (o == null || o.size != N) return PRESETS[0].second
                l = o.toMutableList(); l[18] = if (o[18] == 1) 3 else 0
            }
            val days = if (FC_DAYS.contains(l[18])) l[18] else if (l[18] > 0) 3 else 0
            return Cfg(iv(l[0], NF - 1), l[1], l[2], iv(l[3], 1) == 1, iv(l[4], 1) == 1, iv(l[5], 2), l[6],
                iv(l[7], 1) == 1, l[8], iv(l[9], 1) == 1, iv(l[10], 1) == 1, l[11], iv(l[12], 1) == 1, l[13],
                l[14], l[15].coerceIn(0, 100), iv(l[16], 2), iv(l[17], 4), days)
        }
    }
}

private fun h(v: Long) = v.toInt()

/** Пресеты повторяют подход прошивок: раскладка + шрифт + цвета + фон. */
val PRESETS: List<Pair<String, Cfg>> = listOf(
    // Huawei: часы на «обоях» слева, погода и прогноз справа (как референс)
    "Huawei EMUI" to Cfg(font = 0, size = 72, dateFmt = 1, bgColor = h(0xFF1A2347), bgAlpha = 88,
        layout = 1, scene = 1, forecastDays = 3),
    // Honor: тёмное синее стекло, акцент синий, без картинки
    "Honor MagicOS" to Cfg(font = 1, size = 72, dateColor = h(0xFF6DB3FF), alarmColor = h(0xFF6DB3FF),
        weatherColor = h(0xFFE6F1FF), bgColor = h(0xFF0B1B33), bgAlpha = 82, layout = 1, scene = 0,
        forecastDays = 3),
    // Samsung One UI: крупные лёгкие цифры, без фона, минимум деталей
    "Samsung One UI" to Cfg(font = 1, size = 72, dateFmt = 0, bgAlpha = 0, layout = 0, forecastDays = 0),
    // Apple iOS: тёмная плитка, красный акцент у даты, прогноз внизу
    "Apple iOS" to Cfg(font = 3, size = 64, dateFmt = 0, dateColor = h(0xFFFF453A), alarmColor = h(0xFFFF9F0A),
        weatherColor = h(0xFFFFFFFF), bgColor = h(0xFF1C1C1E), bgAlpha = 82, layout = 0, forecastDays = 3),
    // HTC Sense: всё по центру, узкие цифры, зелёный акцент
    "HTC Sense" to Cfg(font = 5, size = 72, dateColor = h(0xFFB5E26B), alarmColor = h(0xFFB5E26B),
        weatherColor = h(0xFFFFFFFF), bgAlpha = 30, layout = 2, forecastDays = 0),
    // Google Pixel: пастельная Material-плитка
    "Google Pixel" to Cfg(font = 2, size = 64, clockColor = h(0xFFE6DEFF), dateColor = h(0xFFD0BCFF),
        alarmColor = h(0xFFD0BCFF), weatherColor = h(0xFFD0BCFF), bgColor = h(0xFF1D1B20), bgAlpha = 82,
        layout = 0, forecastDays = 0),
    // Классика с засечками: serif во всех блоках, три дня прогноза
    "Классика (Serif)" to Cfg(font = 7, size = 60, dateFmt = 2, dateColor = h(0xFFF5E1A4),
        alarmColor = h(0xFFF5E1A4), weatherColor = h(0xFFFFFFFF), bgColor = h(0xFF2B2118), bgAlpha = 78,
        layout = 0, scene = 4, forecastDays = 3),
    // Узкий техно: моноширинные цифры, холодный акцент, две панели
    "Техно (Моно)" to Cfg(font = 9, size = 58, dateFmt = 0, dateColor = h(0xFF7FE3C8),
        alarmColor = h(0xFF7FE3C8), weatherColor = h(0xFFDCEBF5), bgColor = h(0xFF08111A), bgAlpha = 90,
        layout = 1, scene = 3, forecastDays = 3)
)

/** Полдень ближайшего числа со смещением [offset] — для демо-прогноза. */
private fun dayAt(offset: Int): Calendar = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, offset); set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0)
}

private val DEMO_CODES = intArrayOf(0, 2, 3, 61, 1, 71)

/** Плотность точек прогноза: чем больше дней запрошено, тем короче шаг. */
fun hourStep(days: Int): Int = when {
    days <= 1 -> 1
    days <= 2 -> 3
    days <= 3 -> 6
    days <= 5 -> 12
    else -> 24
}

/**
 * Демо-данные для предпросмотра. Генерируются под реально запрошенное число дней и под
 * фактический размер ячейки, чтобы «3 дня» не выглядели как «6 дней» в маленькой плитке.
 */
object Demo {
    fun wx(days: Int, layout: Int, cols: Int, rows: Int): Wx {
        val cap = fcCols(layout, cols).coerceAtLeast(1)
        val n = minOf(days.coerceAtLeast(1), cap, FC_MAX)
        val list = ArrayList<Fc>()
        for (i in 1..n) {
            val c = DEMO_CODES[(i - 1) % DEMO_CODES.size]
            list.add(Fc(Data.shortName(dayAt(i).time), c, 25f - i, 14f - i / 2f))
        }
        return Wx("Санкт-Петербург", 22f, 2, true, 23f, 14f, list, hourly(days, rows))
    }

    /** Почасовые точки для компактного почерка температуры. */
    fun hourly(days: Int, rows: Int): List<Hp> {
        val step = hourStep(days)
        val span = 24L * 3_600_000L * minOf(days.coerceAtLeast(1), 3)
        val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()
        val out = ArrayList<Hp>()
        var t = now
        var i = 0
        while (t < now + span && i < 24) {
            val hr = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY)
            val temp = 18f + 7f * Math.sin((hr - 9) / 24.0 * 2 * Math.PI).toFloat()
            out.add(Hp(tf.format(Date(t)), temp.roundToInt(), DEMO_CODES[(hr / 6) % DEMO_CODES.size]))
            t += step * 3_600_000L; i++
        }
        return out
    }

    const val EV = "10:00 · Встреча\n14:30 · Звонок"
}
