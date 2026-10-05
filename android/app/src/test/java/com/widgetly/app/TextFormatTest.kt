package com.widgetly.app

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/** Те же ожидания, что в web/src/design/format.test.ts: рендереры обязаны совпадать. */
class TextFormatTest {
    // Понедельник, 5 октября 2026, 14:07
    private fun cal(): Calendar = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 5, 14, 7, 0)
    }

    @Test fun time24() = assertEquals("14:07", TextFormat.format("HH:mm", cal(), "ru"))
    @Test fun time12() {
        assertEquals("02:07 PM", TextFormat.format("hh:mm A", cal(), "en"))
        assertEquals("2:07 pm", TextFormat.format("h:mm a", cal(), "en"))
    }
    @Test fun dateRu() {
        assertEquals("Пн, 5 окт", TextFormat.format("EEE, d MMM", cal(), "ru"))
        assertEquals("Понедельник, 5 октября 2026", TextFormat.format("EEEE, d MMMM yyyy", cal(), "ru"))
    }
    @Test fun dateEn() = assertEquals("Mon, 5 Oct", TextFormat.format("EEE, d MMM", cal(), "en"))
    @Test fun numeric() = assertEquals("05.10.26", TextFormat.format("dd.MM.yy", cal(), "ru"))
    @Test fun literals() {
        assertEquals("14 ч 07 мин", TextFormat.format("HH 'ч' mm 'мин'", cal(), "ru"))
        assertEquals("'14'", TextFormat.format("''HH''", cal(), "ru"))
    }
    @Test fun temps() {
        assertEquals("21°", TextFormat.tempText(21.4, "C", false))
        assertEquals("22°C", TextFormat.tempText(21.5, "C", true))
        assertEquals("0°", TextFormat.tempText(-0.4, "C", false))
        assertEquals("-3°", TextFormat.tempText(-3.5, "C", false))
        assertEquals("68°F", TextFormat.tempText(20.0, "F", true))
    }
    @Test fun range() = assertEquals("↑12° ↓4°", TextFormat.rangeText(12.4, 3.6, "C"))
    @Test fun conditions() {
        assertEquals("Дождь", TextFormat.conditionText(63, "ru"))
        assertEquals("Thunderstorm", TextFormat.conditionText(95, "en"))
        assertEquals("—", TextFormat.conditionText(1234, "en"))
        assertEquals("❓", TextFormat.weatherIcon(999, true))
    }
}
