package com.calorie.app.domain.chat

/**
 * Защита от догадок модели. Если она меняет бюджет или норму, а в сообщении
 * не было числа, сначала спрашиваем подтверждение: «and cheaper» не должно
 * молча превращаться в конкретную сумму.
 */
object ChatConfirmation {

    private val YES = setOf(
        "да", "ага", "ок", "окей", "давай", "конечно", "подтверждаю", "yes", "ok", "sure"
    )
    private val NO = setOf(
        "нет", "не", "не надо", "отмена", "отмени", "no", "nope", "cancel"
    )

    fun needsConfirmation(action: ChatAction, message: String): Boolean = when (action) {
        is ChatAction.SetBudget, is ChatAction.SetTarget -> message.none { it.isDigit() }
        else -> false
    }

    fun isYes(text: String): Boolean = normalize(text) in YES

    fun isNo(text: String): Boolean = normalize(text) in NO

    fun describe(action: ChatAction): String = when (action) {
        is ChatAction.SetBudget -> "бюджет ${action.amount} ₽"
        is ChatAction.SetTarget -> "норму ${action.kcal} ккал"
        is ChatAction.SetPeople -> "готовку на ${action.count} чел."
        else -> "изменение"
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().trim('.', '!', ',', '?')
}
