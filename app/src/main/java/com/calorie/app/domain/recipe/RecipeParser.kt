package com.calorie.app.domain.recipe

import com.google.gson.JsonParser

/** Один шаг приготовления. Таймер опционален: не у каждого шага есть отсчёт. */
data class RecipeStep(val text: String, val timerMin: Int? = null)

/**
 * Разбор и проверка ответа модели. Модель может вернуть markdown-обёртку,
 * лишние поля или слишком короткие шаги — всё это отсекается до сохранения.
 */
object RecipeParser {

    const val MIN_STEPS = 3
    const val MAX_STEPS = 12
    const val MIN_LEN = 15
    const val MAX_LEN = 400

    fun parse(raw: String): List<RecipeStep>? {
        val json = stripFences(raw)
        val root = try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            return null
        }
        if (root == null || !root.isJsonObject) return null
        val stepsElement = root.asJsonObject.get("steps") ?: return null
        if (!stepsElement.isJsonArray) return null

        val steps = stepsElement.asJsonArray.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val obj = element.asJsonObject
            val text = obj.get("text")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                .orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            val timer = obj.get("timer_min")
                ?.takeIf { it.isJsonPrimitive }
                ?.let { runCatching { it.asInt }.getOrNull() }
            RecipeStep(text, timer)
        }
        return validate(steps)
    }

    fun validate(steps: List<RecipeStep>): List<RecipeStep>? {
        if (steps.size !in MIN_STEPS..MAX_STEPS) return null
        val cleaned = ArrayList<RecipeStep>(steps.size)
        for (step in steps) {
            val text = step.text.replace(Regex("\\s+"), " ").trim()
            if (text.length !in MIN_LEN..MAX_LEN) return null
            if (text.startsWith("{") || text.startsWith("```")) return null
            val timer = step.timerMin?.takeIf { it in 1..240 }
            cleaned += RecipeStep(text, timer)
        }
        return cleaned
    }

    fun toJson(steps: List<RecipeStep>): String = buildString {
        append("{\"steps\":[")
        steps.forEachIndexed { index, step ->
            if (index > 0) append(',')
            append("{\"text\":")
            append(escape(step.text))
            step.timerMin?.let {
                append(",\"timer_min\":")
                append(it)
            }
            append('}')
        }
        append("]}")
    }

    private fun escape(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

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
