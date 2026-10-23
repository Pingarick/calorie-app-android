package com.calorie.app.data.vision

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Данные, которые приложение ожидает от vision-модели (Gemini Flash через
 * OpenAI-совместимый прокси).
 */
data class NutritionEstimate(
    val ok: Boolean,
    val name: String,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    /** Оценка веса всей порции на фото, г. 0 — модель вес не дала. */
    val portionG: Double,
    val message: String
) {
    companion object {
        /** Ошибка/невозможность распознать. */
        fun fail(reason: String) = NutritionEstimate(
            ok = false, name = "", kcal = 0.0, proteinG = 0.0,
            fatG = 0.0, carbsG = 0.0, portionG = 0.0, message = reason
        )

        /** Разобрать JSON-ответ модели. */
        fun fromModel(raw: String): NutritionEstimate {
            // Срезаем возможную ```json обёртку markdown.
            var s = raw.trim()
            val a = s.indexOf('{')
            val b = s.lastIndexOf('}') + 1
            if (a >= 0 && b > a) s = s.substring(a, b)
            return try {
                val m = Gson().fromJson(s, ModelAnswer::class.java)
                val kcal = m.kcal
                if (kcal == null || kcal <= 0) {
                    fail(m.note ?: "Не удалось оценить порцию")
                } else {
                    NutritionEstimate(
                        ok = true,
                        name = m.name.orEmpty(),
                        kcal = kcal,
                        proteinG = m.proteinG ?: 0.0,
                        fatG = m.fatG ?: 0.0,
                        carbsG = m.carbsG ?: 0.0,
                        portionG = m.portionG ?: 0.0,
                        message = m.note ?: "Оценка модели"
                    )
                }
            } catch (e: Exception) {
                fail("Не удалось разобрать ответ: ${raw.take(160)}")
            }
        }
    }
}

// ---- Payload OpenAI /chat/completions (vision) ----

internal data class ChatRequest(
    val model: String,
    val messages: List<ReqMessage>,
    @SerializedName("temperature") val temperature: Double = 0.2,
    @SerializedName("max_tokens") val maxTokens: Int = 1024,
    @SerializedName("response_format") val responseFormat: RespFmt? = RespFmt(),
    /**
     * DeepSeek включает thinking по умолчанию. Для распознавания фото он не нужен:
     * только съедает бюджет max_tokens и время ответа.
     */
    val thinking: Thinking = Thinking()
)

internal data class RespFmt(val type: String = "json_object")
internal data class Thinking(val type: String = "disabled")
internal data class ReqMessage(val role: String, val content: List<ContentPart>)
internal data class ContentPart(
    val type: String, // "text" | "image_url"
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: ImageUrl? = null
)
internal data class ImageUrl(
    val url: String,
    /** low — модель сжимает фото до 512×512: для оценки блюда достаточно и дешевле по токенам. */
    val detail: String = "low"
)

internal data class ChatResponse(
    val choices: List<Choice>? = null,
    val error: Err? = null
)
internal data class Choice(val message: RespMessage)
internal data class RespMessage(val content: String)
internal data class Err(val message: String?)

// ---- Parsed fields inside model answer ----
internal data class ModelAnswer(
    var name: String? = null,
    var kcal: Double? = null,
    @SerializedName("protein_g") var proteinG: Double? = null,
    @SerializedName("fat_g") var fatG: Double? = null,
    @SerializedName("carbs_g") var carbsG: Double? = null,
    @SerializedName("portion_g") var portionG: Double? = null,
    var note: String? = null
)
