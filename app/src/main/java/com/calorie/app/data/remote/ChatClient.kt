package com.calorie.app.data.remote

import com.calorie.app.BuildConfig
import com.calorie.app.domain.chat.ChatActionParser
import com.calorie.app.domain.chat.ChatReply
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
        .build()

    private val endpoint: String =
        baseUrl.trim().trimEnd('/') + "/chat/completions"

    suspend fun reply(message: String, context: String): ChatReply = withContext(Dispatchers.IO) {
        val body = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to "Контекст:\n$context\n\nЗапрос: $message")
            ),
            "temperature" to 0.3,
            "max_tokens" to 500,
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
            ChatActionParser.parse(content)
                ?: throw IOException("Модель вернула некорректный ответ")
        }
    }

    private data class CompletionResponse(val choices: List<Choice>? = null)
    private data class Choice(val message: Message)
    private data class Message(val content: String?)

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
            {"type":"exclude_dish","dish":"название блюда"} — больше не предлагать.
            {"type":"regenerate_week"} — пересобрать рацион на неделю.

            Если запрос не про питание или действие не нужно — action: null.
            Не выдумывай действия, которых нет в списке. Не обещай того, чего не можешь.
        """.trimIndent()
    }
}
