package com.calorie.app

import com.calorie.app.domain.recipe.RecipeParser
import com.calorie.app.domain.recipe.RecipeStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeParserTest {

    private val valid = """
        {"steps":[
          {"text":"Промыть гречку и залить водой в пропорции один к двум."},
          {"text":"Довести до кипения и варить под крышкой.","timer_min":15},
          {"text":"Курицу нарезать и обжарить до золотистой корочки.","timer_min":10},
          {"text":"Смешать всё, посолить и дать настояться пару минут."}
        ]}
    """.trimIndent()

    @Test
    fun parsesValidAnswer() {
        val steps = RecipeParser.parse(valid)
        assertNotNull(steps)
        assertEquals(4, steps!!.size)
        assertEquals(15, steps[1].timerMin)
        assertNull(steps[0].timerMin)
    }

    @Test
    fun stripsMarkdownFence() {
        val fenced = "```json\n$valid\n```"
        assertNotNull(RecipeParser.parse(fenced))
    }

    @Test
    fun rejectsTooFewSteps() {
        val two = """{"steps":[{"text":"Промыть крупу и залить водой."},{"text":"Варить до готовности."}]}"""
        assertNull(RecipeParser.parse(two))
    }

    @Test
    fun rejectsTooShortStep() {
        val short = """{"steps":[{"text":"Взять"},{"text":"Промыть крупу и залить водой."},{"text":"Варить до готовности."}]}"""
        assertNull(RecipeParser.parse(short))
    }

    @Test
    fun dropsImpossibleTimer() {
        val json = """
            {"steps":[
              {"text":"Промыть крупу и залить водой в кастрюле."},
              {"text":"Варить на медленном огне под крышкой.","timer_min":999},
              {"text":"Дать настояться и подать к столу."}
            ]}
        """.trimIndent()
        val steps = RecipeParser.parse(json)
        assertNotNull(steps)
        assertNull(steps!![1].timerMin)
    }

    @Test
    fun garbageReturnsNull() {
        assertNull(RecipeParser.parse("не удалось определить"))
        assertNull(RecipeParser.parse("""{"steps":"nope"}"""))
        assertNull(RecipeParser.parse(""))
    }

    @Test
    fun jsonRoundTripKeepsSteps() {
        val steps = listOf(
            RecipeStep("Промыть крупу и залить водой в кастрюле.", 0),
            RecipeStep("Варить на медленном огне под крышкой.", 15),
            RecipeStep("Дать настояться пять минут и подать к столу.", null)
        ).map { if (it.timerMin == 0) it.copy(timerMin = null) else it }

        val parsed = RecipeParser.parse(RecipeParser.toJson(steps))
        assertEquals(steps, parsed)
    }
}
