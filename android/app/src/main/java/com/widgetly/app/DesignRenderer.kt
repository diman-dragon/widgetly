package com.widgetly.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Рисует JSON-дизайн (см. web/src/design/types.ts) в Bitmap.
 * Геометрия и правила идентичны SVG-рендереру редактора (web/src/design/render.tsx).
 *
 * Базовый холст: W0 = 70*cols - 30, H0 = 70*rows - 30 (dp дизайна).
 * Масштаб k = min(wPx / W0, hPx / H0) — пикселей на dp дизайна.
 * Позиции слоёв — доли реального размера bitmap.
 */
object DesignRenderer {

    fun baseWidth(cols: Int): Int = 70 * cols - 30
    fun baseHeight(rows: Int): Int = 70 * rows - 30

    fun render(design: JSONObject, wPx: Int, hPx: Int, now: Calendar, weather: WeatherData?): Bitmap {
        val w = max(1, wPx)
        val h = max(1, hPx)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cols = design.optInt("cols", 4).coerceIn(1, 8)
        val rows = design.optInt("rows", 2).coerceIn(1, 8)
        val locale = if (design.optString("locale", "ru") == "en") "en" else "ru"
        val k = min(w.toFloat() / baseWidth(cols), h.toFloat() / baseHeight(rows))

        drawBackground(canvas, design.optJSONObject("bg"), w.toFloat(), h.toFloat(), k)

        val layers = design.optJSONArray("layers")
        if (layers != null) {
            for (i in 0 until layers.length()) {
                val layer = layers.optJSONObject(i) ?: continue
                try {
                    drawLayer(canvas, layer, w.toFloat(), h.toFloat(), k, locale, now, weather)
                } catch (e: Exception) {
                    // Один сломанный слой не должен ронять весь виджет.
                }
            }
        }
        return bitmap
    }

    // ---------- фон ----------

    private fun drawBackground(canvas: Canvas, bg: JSONObject?, w: Float, h: Float, k: Float) {
        if (bg == null) return
        val type = bg.optString("type", "solid")
        if (type == "none") return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val c1 = parseColor(bg.optString("c1", "#000000"), Color.BLACK)
        if (type == "gradient") {
            val c2 = parseColor(bg.optString("c2", "#000000"), Color.BLACK)
            val a = Math.toRadians(bg.optDouble("angle", 135.0))
            val dx = sin(a).toFloat()
            val dy = (-cos(a)).toFloat()
            val half = (abs(w * dx) + abs(h * dy)) / 2f
            val cx = w / 2f
            val cy = h / 2f
            paint.shader = LinearGradient(
                cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half,
                c1, c2, Shader.TileMode.CLAMP,
            )
        } else {
            paint.color = c1
        }
        paint.alpha = alpha255(bg.optDouble("opacity", 1.0))
        val r = bg.optDouble("radius", 0.0).toFloat() * k
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint)
    }

    // ---------- слои ----------

    private fun drawLayer(
        canvas: Canvas, l: JSONObject, w: Float, h: Float, k: Float,
        locale: String, now: Calendar, weather: WeatherData?,
    ) {
        val cx = l.optDouble("x", 0.5).toFloat() * w
        val cy = l.optDouble("y", 0.5).toFloat() * h
        when (l.optString("type")) {
            "clockAnalog" -> drawAnalog(canvas, l, cx, cy, k, now)
            "shape" -> drawShape(canvas, l, cx, cy, k)
            else -> {
                val text = layerText(l, locale, now, weather)
                if (text.isNotEmpty()) drawText(canvas, l, text, cx, cy, k)
            }
        }
    }

    /** Идентично layerText() в web/src/design/content.ts. */
    fun layerText(l: JSONObject, locale: String, now: Calendar, weather: WeatherData?): String {
        val unit = if (l.optString("unit", "C") == "F") "F" else "C"
        return when (l.optString("type")) {
            "text" -> l.optString("text", "")
            "clockDigital" -> TextFormat.format(l.optString("format", "HH:mm"), now, locale)
            "date" -> TextFormat.format(l.optString("format", "EEE, d MMM"), now, locale)
            "weatherIcon" -> if (weather != null) TextFormat.weatherIcon(weather.code, weather.isDay) else "❓"
            "weatherTemp" ->
                if (weather != null) TextFormat.tempText(weather.tempC, unit, l.optBoolean("showUnit", false)) else "--°"
            "weatherCondition" ->
                if (weather != null) TextFormat.conditionText(weather.code, locale) else TextFormat.noDataText(locale)
            "weatherCity" -> TextFormat.cityText(weather?.city, locale)
            "weatherRange" ->
                if (weather != null) TextFormat.rangeText(weather.hiC, weather.loC, unit) else "--"
            else -> ""
        }
    }

    private fun drawText(canvas: Canvas, l: JSONObject, text: String, cx: Float, cy: Float, k: Float) {
        val size = l.optDouble("size", 16.0).toFloat() * k
        val bold = l.optString("weight", "normal") == "bold"
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = size
        paint.color = parseColor(l.optString("color", "#ffffff"), Color.WHITE)
        paint.alpha = alpha255(l.optDouble("opacity", 1.0))
        paint.typeface = typeface(l.optString("font", "sans"), bold)
        paint.letterSpacing = l.optDouble("letterSpacing", 0.0).toFloat()
        paint.textAlign = when (l.optString("align", "center")) {
            "left" -> Paint.Align.LEFT
            "right" -> Paint.Align.RIGHT
            else -> Paint.Align.CENTER
        }
        // Базовая линия: центр слоя + 0.35 размера (то же правило в SVG).
        canvas.drawText(text, cx, cy + 0.35f * size, paint)
    }

    private fun typeface(font: String, bold: Boolean): Typeface {
        val family = when (font) {
            "sans-light" -> "sans-serif-light"
            "sans-thin" -> "sans-serif-thin"
            "condensed" -> "sans-serif-condensed"
            "serif" -> "serif"
            "mono" -> "monospace"
            else -> "sans-serif"
        }
        return Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun drawShape(canvas: Canvas, l: JSONObject, cx: Float, cy: Float, k: Float) {
        val sw = l.optDouble("w", 60.0).toFloat() * k
        val sh = l.optDouble("h", 30.0).toFloat() * k
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = parseColor(l.optString("color", "#ffffff"), Color.WHITE)
        paint.alpha = alpha255(l.optDouble("opacity", 1.0))
        val rect = RectF(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)
        if (l.optString("shape", "rect") == "ellipse") {
            canvas.drawOval(rect, paint)
        } else {
            val r = l.optDouble("radius", 0.0).toFloat() * k
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }

    private fun drawAnalog(canvas: Canvas, l: JSONObject, cx: Float, cy: Float, k: Float, now: Calendar) {
        val r = l.optDouble("size", 80.0).toFloat() * k / 2f
        val opacity = alpha255(l.optDouble("opacity", 1.0))
        val hour = now.get(Calendar.HOUR_OF_DAY) % 12
        val minute = now.get(Calendar.MINUTE)
        val hourAngle = Math.toRadians(((hour + minute / 60.0) * 30.0))
        val minAngle = Math.toRadians(minute * 6.0)

        fun px(angle: Double, len: Float): Float = cx + (sin(angle) * len).toFloat()
        fun py(angle: Double, len: Float): Float = cy - (cos(angle) * len).toFloat()

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val face = l.optString("face", "")
        if (face.isNotEmpty()) {
            fill.style = Paint.Style.FILL
            fill.color = parseColor(face, Color.WHITE)
            fill.alpha = opacity
            canvas.drawCircle(cx, cy, r, fill)
        }

        val ring = l.optString("ring", "")
        val ringWidth = l.optDouble("ringWidth", 2.0).toFloat() * k
        if (ring.isNotEmpty() && ringWidth > 0f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeWidth = ringWidth
            p.color = parseColor(ring, Color.WHITE)
            p.alpha = opacity
            canvas.drawCircle(cx, cy, r - ringWidth / 2f, p)
        }

        val ticks = l.optString("ticks", "hours")
        if (ticks != "none") {
            val count = if (ticks == "quarters") 4 else 12
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.color = parseColor(l.optString("tickColor", "#ffffff"), Color.WHITE)
            p.alpha = opacity
            for (i in 0 until count) {
                val ang = Math.toRadians(i * 360.0 / count)
                val main = ticks == "quarters" || i % 3 == 0
                p.strokeWidth = max(1f * k, r * (if (main) 0.05f else 0.03f))
                val inner = r * (if (main) 0.82f else 0.88f)
                val outer = r * 0.95f
                canvas.drawLine(px(ang, inner), py(ang, inner), px(ang, outer), py(ang, outer), p)
            }
        }

        val handWidth = l.optDouble("handWidth", 4.0).toFloat() * k
        val hands = Paint(Paint.ANTI_ALIAS_FLAG)
        hands.style = Paint.Style.STROKE
        hands.strokeCap = Paint.Cap.ROUND

        hands.color = parseColor(l.optString("hourColor", "#ffffff"), Color.WHITE)
        hands.alpha = opacity
        hands.strokeWidth = handWidth
        canvas.drawLine(cx, cy, px(hourAngle, r * 0.5f), py(hourAngle, r * 0.5f), hands)

        val minuteColor = parseColor(l.optString("minuteColor", "#ffffff"), Color.WHITE)
        hands.color = minuteColor
        hands.alpha = opacity
        hands.strokeWidth = max(1f * k, handWidth * 0.75f)
        canvas.drawLine(cx, cy, px(minAngle, r * 0.78f), py(minAngle, r * 0.78f), hands)

        fill.style = Paint.Style.FILL
        fill.color = minuteColor
        fill.alpha = opacity
        canvas.drawCircle(cx, cy, max(2f * k, handWidth * 0.8f), fill)
    }

    // ---------- утилиты ----------

    private fun parseColor(s: String, fallback: Int): Int = try {
        Color.parseColor(s)
    } catch (e: IllegalArgumentException) {
        fallback
    }

    private fun alpha255(opacity: Double): Int = (opacity.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()
}
