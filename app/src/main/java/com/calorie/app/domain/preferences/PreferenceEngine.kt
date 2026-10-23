package com.calorie.app.domain.preferences

/**
 * Что планировщик знает о блюде: выученный вес и когда его готовили в последний раз.
 */
data class DishPreference(
    val weight: Double = 0.0,
    val lastCookedAt: Long? = null
)

/**
 * Превращает события в веса. Формула — экспоненциальное сглаживание:
 *
 *     weight = weight * (1 - alpha) + alpha * signal
 *
 * Новое событие весит больше старого, но не обнуляет историю. Одно удаление
 * не делает блюдо ненавидимым: сигнал -2 при alpha 0.15 сдвигает вес на -0.3.
 */
object PreferenceEngine {

    const val ALPHA = 0.15
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val NOVELTY_DAYS = 21.0

    fun signal(type: String): Double = when (type) {
        TYPE_COOKED -> 1.0
        TYPE_RATED_POS -> 3.0
        TYPE_RATED_NEG -> -3.0
        TYPE_POSTPONED -> -1.0
        TYPE_REMOVED -> -2.0
        TYPE_REPLACED -> -1.0
        else -> 0.0
    }

    fun update(current: Double, signal: Double): Double =
        (current * (1 - ALPHA) + ALPHA * signal).coerceIn(-1.0, 1.0)

    fun keyFor(entityType: String, entityId: String): String = "$entityType:$entityId"

    /** 1.0 — никогда не готовили, 0.0 — готовили только что. */
    fun novelty(lastCookedAt: Long?, now: Long): Double {
        if (lastCookedAt == null) return 1.0
        val days = (now - lastCookedAt).coerceAtLeast(0L).toDouble() / DAY_MS
        return (days / NOVELTY_DAYS).coerceIn(0.0, 1.0)
    }

    /** Штраф за то, что блюдо было совсем недавно. */
    fun recencyPenalty(lastCookedAt: Long?, now: Long): Double {
        if (lastCookedAt == null) return 0.0
        val days = (now - lastCookedAt).coerceAtLeast(0L).toDouble() / DAY_MS
        return when {
            days < 3 -> -1.5
            days < 7 -> -0.8
            else -> 0.0
        }
    }

    const val TYPE_COOKED = "COOKED"
    const val TYPE_REMOVED = "REMOVED"
    const val TYPE_REPLACED = "REPLACED"
    const val TYPE_POSTPONED = "POSTPONED"
    const val TYPE_RATED_POS = "RATED_POS"
    const val TYPE_RATED_NEG = "RATED_NEG"

    const val ENTITY_DISH = "DISH"
}
