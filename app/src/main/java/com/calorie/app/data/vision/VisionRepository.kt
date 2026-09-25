package com.calorie.app.data.vision

import android.util.Base64
import android.util.Log
import com.calorie.app.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** Источник, умеющий по фото еды оценить КБЖУ порции. */
interface VisionSource {
    val modelName: String
    suspend fun estimate(file: File): NutritionEstimate
}

/**
 * Распознавание по фото через OpenAI-совместимый vision endpoint
 * (routerai.ru / Gemini-вид). Конфиг берётся из BuildConfig, который
 * заполняется на этапе сборки из gradle.properties переменных VISION_OPENAI_*.
 * Схема: POST {base}/chat/completions с multimodal-сообщениями (text + base64 image).
 */
class RouterVisionSource(
    private val apiKey: String = BuildConfig.VISION_API_KEY,
    baseUrl: String = BuildConfig.VISION_BASE_URL,
    override val modelName: String = "google/gemini-3.1-flash-lite-image"
) : VisionSource {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val endpoint: String = buildString {
        val base = baseUrl.trim().trimEnd('/')
        require(base.isNotBlank()) { "VISION_OPENAI_BASE_URL не задан (gradle.properties)" }
        append(base)
        append("/chat/completions")
    }

    override suspend fun estimate(file: File): NutritionEstimate = withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        // Подсказка модели: фото + обоснование порции на фото.
        val sys = ReqMessage(
            role = "system",
            content = listOf(
                ContentPart(
                    type = "text",
                    text = "Ты — диетолог, работающий по фото блюда. Определи, что это и примерный " +
                        "объём порции. Верни ТОЛЬКО валидный JSON без markdown и пояснений, строго в поле " +
                        "name/kcal/protein_g/fat_g/carbs_g/portion_g/note. Числа — вещественные " +
                        "(килокалории и граммы БЖУ). kcal и БЖУ относятся ко ВСЕЙ порции на фото, " +
                        "portion_g — оценка её веса в граммах. " +
                        "Если по фото определить нельзя — верни {\"note\":\"не удалось определить\"}."
                )
            )
        )
        val user = ReqMessage(
            role = "user",
            content = listOf(
                ContentPart(type = "text", text = "Оцени КБЖУ порции на этом фото:"),
                ContentPart(type = "image_url", imageUrl = ImageUrl("data:image/jpeg;base64,$b64"))
            )
        )
        val chat = ChatRequest(model = modelName, messages = listOf(sys, user))

        try {
            val req = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(gson.toJson(chat).toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val parsed = gson.fromJson(text, ChatResponse::class.java)
                parsed.error?.message?.let { err ->
                    return@withContext NutritionEstimate.fail(err)
                }
                val content = parsed.choices?.firstOrNull()?.message?.content
                    ?: return@withContext NutritionEstimate.fail("Пустой ответ модели")
                NutritionEstimate.fromModel(content)
            }
        } catch (e: Exception) {
            Log.e("CalorieVision", "Request failed", e)
            NutritionEstimate.fail(e.message ?: "Сетевая ошибка")
        }
    }
}
