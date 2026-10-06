package app.clockweather

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Одна точка почасового прогноза: «15:00», 21°, код погоды. */
class Hp(val time: String, val temp: Int, val code: Int)

class Fc(val name: String, val code: Int, val hi: Float, val lo: Float)

/** Погода + прогноз. [fc] — суточные ячейки (день/ночь), [hp] — почасовые точки для почерка. */
class Wx(
    val city: String, val temp: Float, val code: Int, val day: Boolean, val hi: Float, val lo: Float,
    val fc: List<Fc>, val hp: List<Hp> = emptyList()
)

/**
 * Чтение кэша из SharedPreferences.
 *
 * Энергоэффективность: разбор CSV и создание `SimpleDateFormat` раньше происходили на горячем
 * пути — при каждой перерисовке виджета. Теперь разобранный результат кешируется в памяти и
 * переиспользуется, пока не изменится сырая строка (`w_date`/`w_fc`) или язык системы.
 */
object Data {
    /** Демо-данные для предпросмотра: 3 дня по умолчанию. */
    val DEMO = Demo.wx(3, 1, 5, 3)
    const val DEMO_EV = Demo.EV

    private val DAY_FMT = SimpleDateFormat("EEE", Locale.getDefault())
    private var cacheKey = ""
    private var cache: Wx? = null

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Короткое имя дня недели («ВТ») для ячейки прогноза. */
    fun shortName(ms: Long): String = DAY_FMT.format(Date(ms)).uppercase(Locale.getDefault())

    /** Сырые данные поменялись — сбрасываем разобранный кэш. */
    fun invalidate() { cacheKey = ""; cache = null }

    fun weather(p: SharedPreferences): Wx? {
        if (!p.contains("w_code")) return null
        val key = "${p.getString("w_date", "")}#${p.getFloat("w_temp", 0f)}#${Locale.getDefault().language}"
        val hit = cache
        if (hit != null && cacheKey == key) return hit

        val ds = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val fc = ArrayList<Fc>()
        for (s in (p.getString("w_fc", "") ?: "").split(';')) {
            val a = s.split('|')
            if (a.size != 4) continue
            val d = try { ds.parse(a[0]) } catch (_: Exception) { null }
            fc.add(Fc(if (d == null) "" else shortName(d.time), a[1].toIntOrNull() ?: 0,
                a[2].toFloatOrNull() ?: 0f, a[3].toFloatOrNull() ?: 0f))
        }
        val hp = ArrayList<Hp>()
        for (s in (p.getString("w_hr", "") ?: "").split(';')) {
            val a = s.split('|')
            if (a.size != 3) continue
            hp.add(Hp(a[0], a[1].toIntOrNull() ?: 0, a[2].toIntOrNull() ?: 0))
        }
        val w = Wx(p.getString("city", null) ?: "Укажите город", p.getFloat("w_temp", 0f), p.getInt("w_code", 0),
            p.getInt("w_day", 1) == 1, p.getFloat("w_hi", 0f), p.getFloat("w_lo", 0f), fc, hp)
        cache = w; cacheKey = key
        return w
    }

    fun emoji(code: Int, day: Boolean) = when (code) {
        0 -> if (day) "☀️" else "🌙"
        1, 2 -> if (day) "🌤️" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "🌨️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun desc(code: Int): String {
        val ru = Locale.getDefault().language == "ru"
        val (a, b) = when (code) {
            0 -> "Ясно" to "Clear"
            1 -> "Малооблачно" to "Mostly clear"
            2 -> "Облачно с прояснениями" to "Partly cloudy"
            3 -> "Пасмурно" to "Overcast"
            45, 48 -> "Туман" to "Fog"
            in 51..57 -> "Морось" to "Drizzle"
            61, 80 -> "Небольшой дождь" to "Light rain"
            63, 81, 66, 67 -> "Дождь" to "Rain"
            65, 82 -> "Сильный дождь" to "Heavy rain"
            71, 85 -> "Небольшой снег" to "Light snow"
            73, 77 -> "Снег" to "Snow"
            75, 86 -> "Сильный снег" to "Heavy snow"
            95 -> "Гроза" to "Thunderstorm"
            96, 99 -> "Гроза с градом" to "Thunderstorm, hail"
            else -> "" to ""
        }
        return if (ru) a else b
    }

    /** Полночь сегодня — старт отсчёта почасовых точек. */
    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
