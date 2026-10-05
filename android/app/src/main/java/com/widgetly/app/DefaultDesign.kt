package com.widgetly.app

import org.json.JSONObject

/** Дизайн, который показывается, пока пользователь не выбрал свой. Совпадает с шаблоном «Ночной». */
object DefaultDesign {
    const val ID = "default"

    private const val JSON = """
{
  "v": 1, "id": "default", "name": "Стандартный", "cols": 4, "rows": 2, "locale": "ru",
  "bg": { "type": "gradient", "c1": "#1b1b2f", "c2": "#162447", "angle": 135, "radius": 24, "opacity": 1 },
  "layers": [
    { "id": "a", "type": "clockDigital", "x": 0.07, "y": 0.4, "size": 46, "color": "#ffffff", "align": "left", "font": "sans", "weight": "bold", "letterSpacing": 0, "format": "HH:mm", "opacity": 1 },
    { "id": "b", "type": "date", "x": 0.07, "y": 0.78, "size": 15, "color": "#c8c8d0", "align": "left", "font": "sans", "weight": "normal", "letterSpacing": 0, "format": "EEE, d MMM", "opacity": 1 },
    { "id": "c", "type": "weatherIcon", "x": 0.85, "y": 0.3, "size": 34, "align": "center", "opacity": 1 },
    { "id": "d", "type": "weatherTemp", "x": 0.85, "y": 0.7, "size": 26, "color": "#ffffff", "align": "center", "font": "sans", "weight": "bold", "letterSpacing": 0, "unit": "C", "showUnit": false, "opacity": 1 }
  ]
}
"""

    fun create(): JSONObject = JSONObject(JSON)
}
