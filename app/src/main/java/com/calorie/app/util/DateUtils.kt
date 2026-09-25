package com.calorie.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayKey(): String = dayKeyFmt.format(Date())

    fun keyFor(offsetDays: Int): String {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
        return dayKeyFmt.format(c.time)
    }

    /** Отображаемый заголовок дня: "Сегодня" / "Вчера" / "пн, 8 сен". */
    fun display(dateKey: String): String {
        val today = todayKey()
        if (dateKey == today) return "Сегодня"
        if (dateKey == keyFor(-1)) return "Вчера"
        val c = Calendar.getInstance()
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey)?.let {
            c.time = it
        }
        val d = SimpleDateFormat("EEE, d MMM", Locale("ru")).format(c.time)
        return d.replaceFirstChar { it.uppercaseChar() }
    }
}
