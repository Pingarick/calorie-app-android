package com.calorie.app.domain.chat

import com.calorie.app.domain.planner.MealSlot
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Действие, которое чат может применить к приложению. */
sealed class ChatAction {
    data class SetBudget(val amount: Int) : ChatAction()
    data class SetTarget(val kcal: Int) : ChatAction()
    data class ReplaceMeal(val day: String, val slot: String) : ChatAction()
    data class RemoveMeal(val day: String, val slot: String) : ChatAction()
    data class ExcludeDish(val dish: String) : ChatAction()
    data class SetPeople(val count: Int) : ChatAction()
    data class ExcludeProduct(val product: String) : ChatAction()
    data class AllowProduct(val product: String) : ChatAction()
    data object RegenerateWeek : ChatAction()
}

data class ChatReply(
    val text: String,
    val action: ChatAction?,
    /** Исходный JSON действия — храним в истории, чтобы было видно, что применили. */
    val actionJson: String
)

/**
 * Разбор ответа модели. Модель обязана вернуть {"reply":"...","action":null|{...}},
 * но доверять ей нельзя: тип действия и параметры проверяются здесь.
 */
object ChatActionParser {

    private val SLOTS = MealSlot.entries.map { it.name }.toSet()
    private val DAYS = setOf("today", "tomorrow")

    fun parse(raw: String): ChatReply? {
        val json = stripFences(raw)
        val root = try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            return null
        }
        if (root == null || !root.isJsonObject) return null
        val obj = root.asJsonObject

        val text = obj.str("reply")?.trim().orEmpty()
        if (text.isEmpty()) return null

        val actionElement = obj.get("action")?.takeIf { it.isJsonObject }
        val action = actionElement?.let { parseAction(it.asJsonObject) }
        return ChatReply(
            text = text,
            action = action,
            actionJson = if (action != null) actionElement.toString() else ""
        )
    }

    private fun parseAction(obj: JsonObject): ChatAction? = when (obj.str("type")) {
        "set_budget" -> obj.int("amount")?.takeIf { it in 100..1_000_000 }
            ?.let { ChatAction.SetBudget(it) }

        "set_target" -> obj.int("kcal")?.takeIf { it in 800..6000 }
            ?.let { ChatAction.SetTarget(it) }

        "replace_meal" -> mealAction(obj) { day, slot -> ChatAction.ReplaceMeal(day, slot) }

        "remove_meal" -> mealAction(obj) { day, slot -> ChatAction.RemoveMeal(day, slot) }

        "exclude_dish" -> obj.str("dish")?.trim()?.takeIf { it.length in 2..80 }
            ?.let { ChatAction.ExcludeDish(it) }

        "set_people" -> obj.int("count")?.takeIf { it in 1..12 }
            ?.let { ChatAction.SetPeople(it) }

        "exclude_product" -> obj.str("product")?.trim()?.takeIf { it.length in 2..60 }
            ?.let { ChatAction.ExcludeProduct(it) }

        "allow_product" -> obj.str("product")?.trim()?.takeIf { it.length in 2..60 }
            ?.let { ChatAction.AllowProduct(it) }

        "regenerate_week" -> ChatAction.RegenerateWeek

        else -> null
    }

    private fun mealAction(
        obj: JsonObject,
        factory: (String, String) -> ChatAction
    ): ChatAction? {
        val slot = obj.str("slot")?.uppercase()?.takeIf { it in SLOTS } ?: return null
        val day = obj.str("day")?.lowercase()?.takeIf { it in DAYS } ?: "today"
        return factory(day, slot)
    }

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    private fun stripFences(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            val firstNewline = s.indexOf('\n')
            if (firstNewline > 0) s = s.substring(firstNewline + 1)
            if (s.endsWith("```")) s = s.dropLast(3)
        }
        return s.trim()
    }
}
