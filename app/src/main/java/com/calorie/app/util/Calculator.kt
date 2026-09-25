package com.calorie.app.util

import com.calorie.app.data.local.DayAggRow
import com.calorie.app.data.local.model.ActivityLevel
import com.calorie.app.data.local.model.Gender
import com.calorie.app.data.local.model.Goal
import com.calorie.app.data.local.model.UserProfile
import kotlin.math.roundToInt

/**
 * Подсчёт дневной нормы калорий по формуле Миффлина-Сан Жеора с учётом
 * уровня активности и цели. Если пользователь задал manual-цель (customTargetKcal>0),
 * возвращаем её вместо формулы.
 */
fun dailyTargetKcal(profile: UserProfile?): Int {
    if (profile == null) return 2000
    return dailyTargetKcal(
        age = profile.ageYears,
        height = profile.heightCm,
        weight = profile.weightKg,
        gender = profile.gender,
        activity = profile.activity,
        goal = profile.goal,
        customTarget = profile.customTargetKcal
    )
}

/**
 * Прямой расчёт из параметров. Если [customTarget] > 0 — возвращается он (ручная цель),
 * иначе норма Миффлина-Сан Жеора с учётом активности и цели (не ниже 1200 ккал).
 */
fun dailyTargetKcal(age: Int, height: Int, weight: Double, gender: String,
                    activity: String, goal: String, customTarget: Int): Int {
    if (customTarget > 0) return customTarget
    val sex = when (gender) {
        Gender.FEMALE.name -> -161.0
        else -> 5.0
    }
    // BMR (ккал/день), Миффлин-Сан Жеор
    val bmr = 10 * weight + 6.25 * height - 5 * age + sex
    val amr = bmr * ActivityLevel.byName(activity).factor
    val t = amr * Goal.entries.first { it.name == goal }.multiplier
    return (t.roundToInt()).coerceAtLeast(1200)
}

/**
 * Собирает итоги за сегодня/выбранный день: съедено vs норма, остаток, КБЖУ.
 */
data class DaySummary(
    val targetKcal: Int = 0,
    val eatenKcal: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0
) {
    val remainingKcal: Double get() = targetKcal - eatenKcal
    val progress: Float
        get() = if (targetKcal <= 0) 0f
        else (eatenKcal / targetKcal).toFloat().coerceIn(0f, 1f)
}

fun buildDaySummary(target: Int, agg: DayAggRow?) = DaySummary(
    targetKcal = target,
    eatenKcal = agg?.kcal ?: 0.0,
    protein = agg?.protein ?: 0.0,
    fat = agg?.fat ?: 0.0,
    carbs = agg?.carbs ?: 0.0
)

/**
 * Оценка БЖУ из расчёта на заданное количество грамм, если известен состав на 100 г.
 */
fun macrosForGrams(per100gKcal: Double, per100gProtein: Double, per100gFat: Double,
                   per100gCarbs: Double, grams: Double): Macros {
    val f = grams / 100.0
    return Macros(
        kcal = per100gKcal * f,
        protein = per100gProtein * f,
        fat = per100gFat * f,
        carbs = per100gCarbs * f
    )
}

data class Macros(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

/**
 * Пересчёт КБЖУ с одной порции на другую: значения даны для [baseGrams],
 * пользователь указал [targetGrams]. Если база или цель неизвестны (<= 0),
 * масштабировать нечем — возвращаем исходные значения, а не нули.
 */
fun scalePortion(kcal: Double, protein: Double, fat: Double, carbs: Double,
                 baseGrams: Double, targetGrams: Double): Macros {
    if (baseGrams <= 0.0 || targetGrams <= 0.0) return Macros(kcal, protein, fat, carbs)
    val f = targetGrams / baseGrams
    return Macros(kcal * f, protein * f, fat * f, carbs * f)
}

/** Целевые макросы (г) на дневную норму: белки 30%, жиры 30%, углеводы 40% калорий. */
fun macroTargets(kcal: Int): Macros {
    val k = kcal.coerceAtLeast(0).toDouble()
    return Macros(
        kcal = k,
        protein = k * 0.30 / 4.0,
        fat = k * 0.30 / 9.0,
        carbs = k * 0.40 / 4.0
    )
}
