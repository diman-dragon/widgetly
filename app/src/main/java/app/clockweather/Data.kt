package app.clockweather

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Fc(val name: String, val code: Int, val hi: Float, val lo: Float)
class Wx(val city: String, val temp: Float, val code: Int, val day: Boolean, val hi: Float, val lo: Float, val fc: List<Fc>)

object Data {
    val DEMO = Wx("Санкт-Петербург", 22f, 2, true, 23f, 14f, listOf(
        Fc("ВТ", 2, 24f, 15f), Fc("СР", 61, 20f, 13f), Fc("ЧТ", 3, 21f, 14f),
        Fc("ПТ", 2, 22f, 15f), Fc("СБ", 0, 25f, 16f), Fc("ВС", 2, 24f, 15f)))
    const val DEMO_EV = "10:00 · Встреча\n14:30 · Звонок"

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun weather(p: SharedPreferences): Wx? {
        if (!p.contains("w_code")) return null
        val fc = ArrayList<Fc>()
        val ds = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dn = SimpleDateFormat("EEE", Locale.getDefault())
        for (s in (p.getString("w_fc", "") ?: "").split(';')) {
            val a = s.split('|')
            if (a.size != 4) continue
            val d = try { ds.parse(a[0]) } catch (_: Exception) { null }
            fc.add(Fc(if (d == null) "" else dn.format(d).uppercase(), a[1].toIntOrNull() ?: 0,
                a[2].toFloatOrNull() ?: 0f, a[3].toFloatOrNull() ?: 0f))
        }
        return Wx(p.getString("city", null) ?: "Укажите город", p.getFloat("w_temp", 0f), p.getInt("w_code", 0),
            p.getInt("w_day", 1) == 1, p.getFloat("w_hi", 0f), p.getFloat("w_lo", 0f), fc)
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
}
