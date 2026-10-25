package com.calorie.app.data.remote

import com.calorie.app.BuildConfig
import com.calorie.app.domain.recipe.RecipeParser
import com.calorie.app.domain.recipe.RecipeStep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Генерация шагов рецепта. Ингредиенты и граммовки уже известны — модель пишет только технологию. */
class RecipeClient(
    private val apiKey: String = BuildConfig.VISION_API_KEY,
    baseUrl: String = BuildConfig.VISION_BASE_URL,
    private val model: String = "deepseek-flash"
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private val endpoint: String = buildString {
        val base = baseUrl.trim().trimEnd('/')
        require(base.isNotBlank()) { "VISION_OPENAI_BASE_URL не задан" }
        append(base)
        append("/chat/completions")
    }

    suspend fun steps(
        title: String,
        ingredients: List<Pair<String, Double>>,
        servings: Double
    ): List<RecipeStep> = withContext(Dispatchers.IO) {
        val list = ingredients.joinToString("\n") { (name, grams) ->
            "- $name: ${grams.roundToInt()} г"
        }
        val user = """
            Блюдо: $title
            Порций: ${"%.1f".format(java.util.Locale.US, servings)}
            Ингредиенты уже отмерены:
            $list

            Напиши шаги приготовления на русском языке. Не добавляй продукты, которых нет в списке,
            кроме воды, соли и специй. Каждый шаг — одно действие, 1-2 предложения.
            Верни строго JSON без markdown: {"steps":[{"text":"...","timer_min":10}]}
            timer_min указывай только там, где есть отсчёт времени (варка, запекание, обжарка).
        """.trimIndent()

        val body = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to user)
            ),
            "temperature" to 0.3,
            "max_tokens" to 1200,
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
            RecipeParser.parse(content)
                ?: throw IOException("Модель вернула некорректные шаги")
        }
    }

    private data class CompletionResponse(val choices: List<Choice>? = null)
    private data class Choice(val message: Message)
    private data class Message(
        @SerializedName("content") val content: String?
    )

    private companion object {
        const val SYSTEM_PROMPT =
            "Ты повар. Ты пишешь короткие точные шаги приготовления. " +
                "Ты не выдумываешь продукты и не меняешь граммовки. Отвечаешь только JSON."
    }
}
