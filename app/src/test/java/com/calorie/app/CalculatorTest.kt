package com.calorie.app

import com.calorie.app.data.local.model.ActivityLevel
import com.calorie.app.data.local.model.Gender
import com.calorie.app.data.local.model.Goal
import com.calorie.app.util.dailyTargetKcal
import com.calorie.app.util.macroTargets
import com.calorie.app.util.macrosForGrams
import com.calorie.app.util.scalePortion
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorTest {

    // ---------- dailyTargetKcal (Миффлин-Сан Жеор) ----------

    /** Мужчина 30 л, 180 см, 80 кг, средняя активность, поддержание. */
    @Test
    fun mifflin_male_moderate_maintain() {
        val got = dailyTargetKcal(
            age = 30, height = 180, weight = 80.0,
            gender = Gender.MALE.name,
            activity = ActivityLevel.MODERATE.name,
            goal = Goal.KEEP.name,
            customTarget = 0
        )
        // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 1780; AMR = 1780 * 1.55 = 2759
        assertEquals(2759, got)
    }

    /** Женщина 25 л, 165 см, 60 кг, низкая активность, похудение. */
    @Test
    fun mifflin_female_light_lose() {
        val got = dailyTargetKcal(
            age = 25, height = 165, weight = 60.0,
            gender = Gender.FEMALE.name,
            activity = ActivityLevel.LIGHT.name,
            goal = Goal.LOSE.name,
            customTarget = 0
        )
        // BMR = 600 + 1031.25 - 125 - 161 = 1345.25; AMR = *1.375 = 1849.72; *0.84 = 1553.76
        assertEquals(1554, got)
    }

    /** Ручная цель перебивает формулу. */
    @Test
    fun custom_target_overrides_formula() {
        val got = dailyTargetKcal(
            age = 40, height = 170, weight = 95.0,
            gender = Gender.MALE.name,
            activity = ActivityLevel.SEDENTARY.name,
            goal = Goal.GAIN.name,
            customTarget = 1850
        )
        assertEquals(1850, got)
    }

    /** Слишком агрессивный дефицит не опускает норму ниже 1200 ккал. */
    @Test
    fun target_never_below_1200() {
        val got = dailyTargetKcal(
            age = 70, height = 150, weight = 40.0,
            gender = Gender.FEMALE.name,
            activity = ActivityLevel.SEDENTARY.name,
            goal = Goal.LOSE.name,
            customTarget = 0
        )
        assertEquals(1200, got)
    }

    // ---------- macroTargets: 30/30/40 ----------

    @Test
    fun macro_targets_split_30_30_40() {
        val t = macroTargets(2000)
        assertEquals(2000.0, t.kcal, 0.0)
        assertEquals(150.0, t.protein, 0.001) // 2000*0.30/4
        assertEquals(66.667, t.fat, 0.01)     // 2000*0.30/9
        assertEquals(200.0, t.carbs, 0.001)   // 2000*0.40/4
    }

    @Test
    fun macro_targets_floor_at_zero() {
        val t = macroTargets(-500)
        assertEquals(0.0, t.kcal, 0.0)
        assertEquals(0.0, t.protein, 0.0)
        assertEquals(0.0, t.fat, 0.0)
        assertEquals(0.0, t.carbs, 0.0)
    }

    // ---------- macrosForGrams (per-100g -> порция) ----------

    @Test
    fun macros_for_grams_scales_from_100g() {
        val m = macrosForGrams(250.0, 10.0, 5.0, 30.0, 200.0)
        assertEquals(500.0, m.kcal, 0.001)
        assertEquals(20.0, m.protein, 0.001)
        assertEquals(10.0, m.fat, 0.001)
        assertEquals(60.0, m.carbs, 0.001)
    }

    @Test
    fun macros_for_grams_zero_grams_is_zero() {
        val m = macrosForGrams(250.0, 10.0, 5.0, 30.0, 0.0)
        assertEquals(0.0, m.kcal, 0.0)
        assertEquals(0.0, m.protein, 0.0)
    }

    // ---------- scalePortion (порция -> другой вес) ----------

    @Test
    fun scale_portion_down_and_up() {
        val half = scalePortion(600.0, 30.0, 20.0, 60.0, 300.0, 150.0)
        assertEquals(300.0, half.kcal, 0.001)
        assertEquals(15.0, half.protein, 0.001)
        assertEquals(10.0, half.fat, 0.001)
        assertEquals(30.0, half.carbs, 0.001)

        val dbl = scalePortion(600.0, 30.0, 20.0, 60.0, 300.0, 600.0)
        assertEquals(1200.0, dbl.kcal, 0.001)
    }

    /** Без базового веса (модель его не дала) масштабировать нечем — значения как есть. */
    @Test
    fun scale_portion_without_base_is_identity() {
        val m = scalePortion(600.0, 30.0, 20.0, 60.0, 0.0, 250.0)
        assertEquals(600.0, m.kcal, 0.0)
        assertEquals(30.0, m.protein, 0.0)
        assertEquals(20.0, m.fat, 0.0)
        assertEquals(60.0, m.carbs, 0.0)
    }

    /** Пустой/нулевой целевой вес — тоже без изменений (не обнуляем порцию). */
    @Test
    fun scale_portion_zero_target_is_identity() {
        val m = scalePortion(600.0, 30.0, 20.0, 60.0, 300.0, 0.0)
        assertEquals(600.0, m.kcal, 0.0)
        assertEquals(60.0, m.carbs, 0.0)
    }
}
