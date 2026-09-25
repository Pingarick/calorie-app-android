package com.calorie.app.data.local.model

/** Пол пользователя (для формулы Миффлина-Сан Жеора). */
enum class Gender { MALE, FEMALE }

/** Уровень физической активности — коэффициент к базовому метаболизму. */
enum class ActivityLevel(val factor: Double, val label: String) {
    SEDENTARY(1.2, "Сидячий (офис)"),
    LIGHT(1.375, "Немного активный (1–3 тренировки/нед)"),
    MODERATE(1.55, "Умеренный (3–5 тренировок/нед)"),
    ACTIVE(1.725, "Активный (6–7 тренировок/нед)"),
    VERY_ACTIVE(1.9, "Очень активный (тяжёлая работа)");

    companion object {
        fun byName(name: String?): ActivityLevel =
            entries.firstOrNull { it.name == name } ?: SEDENTARY
    }
}

/** Цель: чего пользователь хочет добиться. */
enum class Goal(val multiplier: Double, val label: String) {
    LOSE(0.84, "Похудеть"),
    KEEP(1.0, "Держать форму"),
    GAIN(1.1, "Набрать массу");
}
