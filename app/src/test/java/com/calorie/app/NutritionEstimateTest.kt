package com.calorie.app

import com.calorie.app.data.vision.NutritionEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionEstimateTest {

    @Test
    fun parses_valid_json_with_portion() {
        val e = NutritionEstimate.fromModel(
            """{"name":"Пицца","kcal":620,"protein_g":25,"fat_g":22,"carbs_g":78,"portion_g":320,"note":"ок"}"""
        )
        assertTrue(e.ok)
        assertEquals("Пицца", e.name)
        assertEquals(620.0, e.kcal, 0.001)
        assertEquals(25.0, e.proteinG, 0.001)
        assertEquals(22.0, e.fatG, 0.001)
        assertEquals(78.0, e.carbsG, 0.001)
        assertEquals(320.0, e.portionG, 0.001)
    }

    @Test
    fun strips_markdown_fence() {
        val e = NutritionEstimate.fromModel(
            "```json\n{\"name\":\"Салат\",\"kcal\":180,\"protein_g\":3,\"fat_g\":12,\"carbs_g\":14,\"portion_g\":200}\n```"
        )
        assertTrue(e.ok)
        assertEquals(180.0, e.kcal, 0.001)
        assertEquals(200.0, e.portionG, 0.001)
    }

    /** Модель не вернула вес — это не ошибка, просто масштабировать нечем. */
    @Test
    fun missing_portion_becomes_zero() {
        val e = NutritionEstimate.fromModel("""{"name":"Суп","kcal":240,"protein_g":8,"fat_g":9,"carbs_g":30}""")
        assertTrue(e.ok)
        assertEquals(0.0, e.portionG, 0.0)
    }

    /** kcal <= 0 — модель не смогла оценить; в сообщение идёт note. */
    @Test
    fun zero_kcal_fails_with_note() {
        val e = NutritionEstimate.fromModel("""{"kcal":0,"note":"не удалось определить"}""")
        assertFalse(e.ok)
        assertEquals("не удалось определить", e.message)
        assertEquals(0.0, e.portionG, 0.0)
    }

    @Test
    fun missing_kcal_fails() {
        val e = NutritionEstimate.fromModel("""{"name":"Что-то"}""")
        assertFalse(e.ok)
    }

    @Test
    fun garbage_fails_without_crash() {
        val e = NutritionEstimate.fromModel("совершенно не json")
        assertFalse(e.ok)
        assertTrue(e.message.isNotBlank())
    }

    @Test
    fun fail_factory_is_zeroed() {
        val e = NutritionEstimate.fail("boom")
        assertFalse(e.ok)
        assertEquals(0.0, e.kcal, 0.0)
        assertEquals(0.0, e.portionG, 0.0)
        assertEquals("boom", e.message)
    }
}
