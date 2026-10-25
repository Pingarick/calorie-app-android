package com.calorie.app

import com.calorie.app.domain.chat.ChatAction
import com.calorie.app.domain.chat.ChatConfirmation
import com.calorie.app.domain.chat.ChatPrompt
import com.calorie.app.domain.chat.ChatTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptTest {

    @Test
    fun promptStartsWithSystemThenHistoryThenCurrentMessage() {
        val history = listOf(
            ChatTurn("user", "замени ужин"),
            ChatTurn("assistant", "готово")
        )
        val turns = ChatPrompt.build("system", history, "контекст", "а подешевле?")

        assertEquals(4, turns.size)
        assertEquals("system", turns[0].role)
        assertEquals("user", turns[1].role)
        assertEquals("assistant", turns[2].role)
        assertEquals("user", turns[3].role)
        assertTrue(turns[3].text.contains("а подешевле?"))
        assertTrue(turns[3].text.contains("контекст"))
    }

    @Test
    fun historyIsLimitedAndLongTurnsTrimmed() {
        val history = (1..20).map { ChatTurn("user", "сообщение $it " + "x".repeat(600)) }
        val turns = ChatPrompt.build("system", history, "ctx", "now")

        assertEquals(ChatPrompt.MAX_HISTORY + 2, turns.size)
        assertTrue(turns.drop(1).all { it.text.length <= 500 })
        assertTrue(turns[1].text.contains("сообщение 13"))
    }

    @Test
    fun confirmationRequiredOnlyForAmountsWithoutDigits() {
        assertTrue(ChatConfirmation.needsConfirmation(ChatAction.SetBudget(3000), "and cheaper"))
        assertFalse(ChatConfirmation.needsConfirmation(ChatAction.SetBudget(3000), "бюджет 3000"))
        assertTrue(ChatConfirmation.needsConfirmation(ChatAction.SetTarget(2000), "поменяй норму"))
        assertFalse(ChatConfirmation.needsConfirmation(ChatAction.SetTarget(2000), "норма 2000"))
        assertFalse(ChatConfirmation.needsConfirmation(ChatAction.RegenerateWeek, "пересобери"))
    }

    @Test
    fun yesAndNoAreRecognized() {
        assertTrue(ChatConfirmation.isYes("да"))
        assertTrue(ChatConfirmation.isYes("Да!"))
        assertTrue(ChatConfirmation.isYes("ок"))
        assertFalse(ChatConfirmation.isYes("давай потом"))

        assertTrue(ChatConfirmation.isNo("нет"))
        assertTrue(ChatConfirmation.isNo("Отмена"))
        assertFalse(ChatConfirmation.isNo("нет, но бюджет 2500"))
    }

    @Test
    fun describeMentionsTheAmount() {
        assertEquals("бюджет 3000 ₽", ChatConfirmation.describe(ChatAction.SetBudget(3000)))
        assertEquals("норму 2000 ккал", ChatConfirmation.describe(ChatAction.SetTarget(2000)))
    }
}
