package com.calorie.app.data.remote

import com.calorie.app.BuildConfig
import com.calorie.app.domain.chat.ChatActionParser
import com.calorie.app.domain.chat.ChatPrompt
import com.calorie.app.domain.chat.ChatReply
import com.calorie.app.domain.chat.ChatTurn
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Чат с приложением: модель возвращает текст и одно действие в строгом JSON. */
class ChatClient(
    private val apiKey: String = BuildConfig.VISION_API_KEY,
    baseUrl: String = BuildConfig.VISION_BASE_URL,
    private val model: String = "deepseek-flash"
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        // Жёсткий предел на весь вызов, включая DNS: иначе при мёртвой сети
        // запрос может висеть минутами, а чат всё это время молчит.
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val endpoint: String =
        baseUrl.trim().trimEnd('/') + "/chat/completions"

    suspend fun reply(
        message: String,
        context: String,
        history: List<ChatTurn> = emptyList()
    ): ChatReply = withContext(Dispatchers.IO) {
        val turns = ChatPrompt.build(SYSTEM_PROMPT, history, context, message)
        val body = mapOf(
            "model" to model,
            "messages" to turns.map { mapOf("role" to it.role, "content" to it.text) },
            "temperature" to 0.3,
            "max_tokens" to 800,
            "thinking" to mapOf("type" to "disabled"),
            "response_format" to mapOf("type" to "json_object")
        )
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: " + text.take(160))
            }
            val content = gson.fromJson(text, CompletionResponse::class.java)
                ?.choices?.firstOrNull()?.message?.content
                ?: throw IOException("Пустой ответ модели")

            ChatActionParser.parse(content)?.let { return@withContext it }

            // Ответ мог прийти обрезанным или с лишним текстом вокруг JSON.
            // Достаём reply регуляркой, чтобы не терять ответ целиком.
            val replyMatch = Regex("\"reply\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .find(content)
                ?.groupValues?.getOrNull(1)
                ?.let { unescape(it) }
                ?.trim()
            if (!replyMatch.isNullOrBlank()) {
                return@withContext ChatReply(text = replyMatch, action = null, actionJson = "")
            }

            // Модель иногда отвечает обычным текстом, игнорируя JSON-режим.
            val plain = content.trim()
            if (plain.isNotEmpty() && !plain.startsWith("{") && !plain.startsWith("```")) {
                return@withContext ChatReply(text = plain.take(400), action = null, actionJson = "")
            }
            throw IOException("Модель вернула некорректный ответ")
        }
    }

    private data class CompletionResponse(val choices: List<Choice>? = null)
    private data class Choice(val message: Message)
    private data class Message(val content: String?)

    /** Снимает экранирование JSON-строки: \" \\ \n и \uXXXX. */
    private fun unescape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        val hex = value.substring(i + 2, minOf(i + 6, value.length))
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            sb.append(code.toChar())
                            i += 6
                        } else {
                            sb.append(next)
                            i += 2
                        }
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private companion object {
        val SYSTEM_PROMPT = """
            Ты помощник в приложении для питания. Отвечай по-русски, коротко, 1-3 предложения.
            Ты можешь выполнить одно действие. Верни строго JSON без markdown:
            {"reply":"текст","action":null}

            Доступные действия:
            {"type":"set_budget","amount":4000} — бюджет на неделю в рублях.
            {"type":"set_target","kcal":2000} — дневная норма калорий.
            {"type":"replace_meal","day":"today|tomorrow","slot":"BREAKFAST|LUNCH|DINNER|SNACK"}.
            {"type":"remove_meal","day":"today|tomorrow","slot":"..."}.
            "{\"type\":\"exclude_dish\",\"dish\":\"название блюда\"} — больше не предлагать.
            {"type":"set_people","count":4} — готовить на N человек.
            {"type":"exclude_product","product":"лук"} — убрать продукт из всех блюд.
            {"type":"allow_product","product":"лук"} — вернуть продукт обратно.
            {"type":"regenerate_week"} — пересобрать рацион на неделю.

            Если запрос не про питание или действие не нужно — action: null.
            Если просят изменить план (заменить, убрать блюдо, пересобрать неделю) — верни
            действие, а не предложение.
            Если запрос неоднозначен (например, «подешевле» без числа) — задай уточняющий
            вопрос и верни action: null, не придумывай сумму.
            Не выдумывай действия, которых нет в списке. Не обещай того, чего не можешь.
        """.trimIndent()
    }
}
