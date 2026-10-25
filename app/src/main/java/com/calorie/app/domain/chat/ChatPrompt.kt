package com.calorie.app.domain.chat

/** Одна реплика диалога, которую видит модель. */
data class ChatTurn(val role: String, val text: String)

/**
 * Сборка запроса к модели: системный промпт, последние реплики и текущее сообщение
 * с контекстом приложения. Вынесено в чистую функцию, чтобы порядок и роли
 * проверялись тестом, а не глазами.
 */
object ChatPrompt {

    const val MAX_HISTORY = 8
    private const val MAX_TURN_CHARS = 500

    fun build(
        system: String,
        history: List<ChatTurn>,
        context: String,
        message: String
    ): List<ChatTurn> {
        val memory = history
            .takeLast(MAX_HISTORY)
            .map { ChatTurn(it.role, it.text.take(MAX_TURN_CHARS)) }

        return buildList {
            add(ChatTurn("system", system))
            addAll(memory)
            add(ChatTurn("user", "Контекст:\n$context\n\nЗапрос: $message"))
        }
    }
}
