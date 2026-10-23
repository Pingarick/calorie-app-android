package com.calorie.app.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Рубли без копеек: для списка покупок точность до рубля достаточна. */
fun money(value: Double): String =
    NumberFormat.getNumberInstance(Locale("ru")).format(value.roundToInt()) + " ₽"

/** Граммы до килограмма, дальше — килограммы с одним знаком. */
fun weight(value: Double): String =
    if (value >= 1000) {
        String.format(Locale.US, "%.1f кг", value / 1000.0)
    } else {
        "${value.roundToInt()} г"
    }
