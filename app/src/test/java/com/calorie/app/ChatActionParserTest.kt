package com.calorie.app

import com.calorie.app.domain.chat.ChatAction
import com.calorie.app.domain.chat.ChatActionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatActionParserTest {

    @Test
    fun parsesReplyWithBudget() {
        val reply = ChatActionParser.parse(
            """{"reply":"Поставил бюджет 4000 ₽","action":{"type":"set_budget","amount":4000}}"""
        )
        assertNotNull(reply)
        assertEquals("Поставил бюджет 4000 ₽", reply!!.text)
        assertEquals(ChatAction.SetBudget(4000), reply.action)
        assertTrue(reply.actionJson.contains("set_budget"))
    }

    @Test
    fun parsesReplyWithoutAction() {
        val reply = ChatActionParser.parse(
            """{"reply":"В плане 2132 ккал","action":null}"""
        )
        assertNotNull(reply)
        assertNull(reply!!.action)
        assertEquals("", reply.actionJson)
    }

    @Test
    fun replacesMealTodayByDefault() {
        val reply = ChatActionParser.parse(
            """{"reply":"Ок","action":{"type":"replace_meal","slot":"dinner"}}"""
        )
        assertEquals(ChatAction.ReplaceMeal("today", "DINNER"), reply!!.action)
    }

    @Test
    fun rejectsUnknownActionType() {
        val reply = ChatActionParser.parse(
            """{"reply":"Сделал","action":{"type":"delete_everything"}}"""
        )
        assertNotNull(reply)
        assertNull(reply!!.action)
        assertEquals("Сделал", reply.text)
    }

    @Test
    fun rejectsInvalidSlot() {
        val reply = ChatActionParser.parse(
            """{"reply":"Ок","action":{"type":"remove_meal","slot":"SUPPER"}}"""
        )
        assertNull(reply!!.action)
    }

    @Test
    fun rejectsImplausibleBudget() {
        val zero = ChatActionParser.parse(
            """{"reply":"Ок","action":{"type":"set_budget","amount":0}}"""
        )
        assertNull(zero!!.action)

        val huge = ChatActionParser.parse(
            """{"reply":"Ок","action":{"type":"set_budget","amount":999999999}}"""
        )
        assertNull(huge!!.action)
    }

    @Test
    fun stripsMarkdownFence() {
        val fenced = """
            ```json
            {"reply":"Пересобрал","action":{"type":"regenerate_week"}}
            ```
        """.trimIndent()
        val reply = ChatActionParser.parse(fenced)
        assertEquals(ChatAction.RegenerateWeek, reply!!.action)
    }

    @Test
    fun parsesPeopleCount() {
        val reply = ChatActionParser.parse(
            """{"reply":"Готовлю на 4","action":{"type":"set_people","count":4}}"""
        )
        assertEquals(ChatAction.SetPeople(4), reply!!.action)

        val bad = ChatActionParser.parse(
            """{"reply":"Ок","action":{"type":"set_people","count":0}}"""
        )
        assertNull(bad!!.action)
    }

    @Test
    fun parsesProductExclusion() {
        val reply = ChatActionParser.parse(
            """{"reply":"Убрал лук","action":{"type":"exclude_product","product":"лук"}}"""
        )
        assertEquals(ChatAction.ExcludeProduct("лук"), reply!!.action)

        val back = ChatActionParser.parse(
            """{"reply":"Вернул","action":{"type":"allow_product","product":"лук"}}"""
        )
        assertEquals(ChatAction.AllowProduct("лук"), back!!.action)
    }

    @Test
    fun garbageReturnsNull() {
        assertNull(ChatActionParser.parse("просто текст"))
        assertNull(ChatActionParser.parse("""{"action":null}"""))
        assertNull(ChatActionParser.parse(""))
    }
}
