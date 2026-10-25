package com.calorie.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.app.CalorieApp
import com.calorie.app.data.local.model.ChatMessage
import com.calorie.app.data.local.model.BannedProduct
import com.calorie.app.data.local.model.UserProfile
import com.calorie.app.data.remote.ChatClient
import com.calorie.app.data.repository.DiaryRepository
import com.calorie.app.data.repository.PreferenceRepository
import com.calorie.app.domain.chat.ChatAction
import com.calorie.app.domain.chat.ChatConfirmation
import com.calorie.app.domain.chat.ChatPrompt
import com.calorie.app.domain.chat.ChatTurn
import com.calorie.app.domain.plan.PlanEditor
import com.calorie.app.domain.planner.MealSlot
import com.calorie.app.domain.preferences.PreferenceEngine
import com.calorie.app.util.DateUtils
import com.calorie.app.util.dailyTargetKcal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Чат управляет приложением через действия: модель возвращает намерение,
 * код проверяет и применяет его, а не наоборот.
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as CalorieApp).database
    private val client = ChatClient()
    private val editor = PlanEditor(db)
    private val diary = DiaryRepository(db)
    private val preferences = PreferenceRepository(db)

    private val _sending = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val sending: StateFlow<Boolean> = _sending
    val error: StateFlow<String?> = _error

    val messages: StateFlow<List<ChatMessage>> = db.chatDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Действие, которое ждёт «да»: модель предложила сумму, но в запросе числа не было. */
    private var pendingAction: ChatAction? = null

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _sending.value) return

        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            try {
                val pending = pendingAction
                if (pending != null) {
                    when {
                        ChatConfirmation.isYes(text) -> {
                            pendingAction = null
                            saveUser(text)
                            val outcome = withContext(Dispatchers.IO) { apply(pending) }
                            saveAssistant(
                                buildString {
                                    append("Готово")
                                    if (!outcome.isNullOrBlank()) append(": $outcome")
                                }
                            )
                            return@launch
                        }

                        ChatConfirmation.isNo(text) -> {
                            pendingAction = null
                            saveUser(text)
                            saveAssistant("Отменил, ничего не менял")
                            return@launch
                        }

                        else -> pendingAction = null
                    }
                }

                // История читается до вставки текущего сообщения, иначе оно задвоится.
                // Технические ошибки в память не отдаём: они сбивают модель.
                val history = withContext(Dispatchers.IO) {
                    db.chatDao().recent(ChatPrompt.MAX_HISTORY).reversed()
                        .filterNot { it.text.startsWith("Не получилось") }
                        .map { ChatTurn(it.role, it.text) }
                }
                saveUser(text)

                val context = withContext(Dispatchers.IO) { buildContext() }
                val reply = client.reply(text, context, history)

                val action = reply.action
                if (action != null && ChatConfirmation.needsConfirmation(action, text)) {
                    pendingAction = action
                    saveAssistant(
                        reply.text + "\n\nПоставить " + ChatConfirmation.describe(action) +
                            "? Скажите «да» или назовите сумму."
                    )
                    return@launch
                }

                val outcome = withContext(Dispatchers.IO) { apply(action) }
                val finalText = if (outcome == null) reply.text else reply.text + "\n\n" + outcome
                saveAssistant(finalText, reply.actionJson)
            } catch (e: Exception) {
                val message = e.message ?: "ошибка сети"
                _error.value = message
                saveAssistant("Не получилось: $message")
            } finally {
                _sending.value = false
            }
        }
    }

    private suspend fun saveUser(text: String) {
        withContext(Dispatchers.IO) {
            db.chatDao().insert(ChatMessage(role = "user", text = text))
        }
    }

    private suspend fun saveAssistant(text: String, actionJson: String = "") {
        withContext(Dispatchers.IO) {
            db.chatDao().insert(
                ChatMessage(role = "assistant", text = text, actionJson = actionJson)
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.chatDao().clear() }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private suspend fun buildContext(): String {
        val profile = diary.getProfile()
        val today = DateUtils.todayKey()
        val items = db.planDao().forDate(today)
        val dishes = db.dishDao().all()
        val byId = dishes.associateBy { it.id }
        val plan = items.joinToString("; ") { item ->
            val title = byId[item.dishId]?.title ?: "блюдо"
            "${MealSlot.byName(item.slot).label}: $title (${item.kcal.roundToInt()} ккал)"
        }.ifEmpty { "пусто" }
        return buildString {
            append("Сегодня $today. ")
            append("Норма ${dailyTargetKcal(profile)} ккал. ")
            append("Бюджет ${profile?.weeklyBudget ?: 0} ₽. ")
            append("Готовим на ${profile?.peopleCount ?: 1} чел. ")
            append("В плане на сегодня: $plan. ")
            append("Блюда в каталоге: ${dishes.joinToString(", ") { it.title }}. ")
            append("Продукты в каталоге: ${db.productDao().all().joinToString(", ") { it.name }}.")
        }
    }

    private suspend fun apply(action: ChatAction?): String? {
        if (action == null) return null
        return when (action) {
            is ChatAction.SetBudget -> {
                val profile = diary.getProfile() ?: UserProfile()
                diary.saveProfile(profile.copy(weeklyBudget = action.amount))
                "Бюджет: ${action.amount} ₽"
            }

            is ChatAction.SetTarget -> {
                val profile = diary.getProfile() ?: UserProfile()
                diary.saveProfile(profile.copy(customTargetKcal = action.kcal))
                "Норма: ${action.kcal} ккал"
            }

            is ChatAction.ReplaceMeal -> {
                val date = resolveDay(action.day)
                val item = editor.itemFor(date, action.slot)
                    ?: return "На этот день такого приёма пищи нет"
                preferences.record(PreferenceEngine.TYPE_REPLACED, item.dishId)
                if (editor.replace(item) != null) "Блюдо заменено" else "Замену не нашёл"
            }

            is ChatAction.RemoveMeal -> {
                val date = resolveDay(action.day)
                val item = editor.itemFor(date, action.slot)
                    ?: return "На этот день такого приёма пищи нет"
                preferences.record(PreferenceEngine.TYPE_REMOVED, item.dishId)
                editor.remove(item)
                "Убрал из плана"
            }

            is ChatAction.ExcludeDish -> {
                val query = action.dish.lowercase()
                val dish = db.dishDao().all().firstOrNull {
                    it.id == action.dish || it.title.lowercase() == query
                } ?: return "Не нашёл такое блюдо"
                preferences.record(PreferenceEngine.TYPE_RATED_NEG, dish.id)
                "Больше не буду предлагать «${dish.title}»"
            }

            is ChatAction.SetPeople -> {
                val profile = diary.getProfile() ?: UserProfile()
                diary.saveProfile(profile.copy(peopleCount = action.count))
                "Готовлю на ${action.count} чел. Порции и корзина пересчитаны"
            }

            is ChatAction.ExcludeProduct -> {
                val query = action.product.lowercase()
                val product = db.productDao().all().firstOrNull {
                    it.id == action.product ||
                        it.name.lowercase() == query ||
                        query.contains(it.name.lowercase()) ||
                        it.name.lowercase().contains(query)
                } ?: return "Не нашёл такой продукт"
                db.bannedDao().insert(BannedProduct(product.id))

                val dates = (0..6).map { DateUtils.keyFor(it) }
                val ok = editor.regenerate(
                    dates = dates,
                    targetKcal = dailyTargetKcal(diary.getProfile()),
                    preferences = preferences.snapshot()
                )
                val left = editor.minDishesPerSlot()
                buildString {
                    if (ok) {
                        append("Убрал «${product.name}» из рецептов и пересобрал рацион")
                    } else {
                        append("Убрал «${product.name}», но рацион не пересобрался")
                    }
                    if (left < 3) {
                        val word = dishesWord(left)
                        append(". В некоторых приёмах пищи осталось $left $word — разнообразие упадёт. Скажи «верни ${product.name.lowercase()}», если передумал")
                    }
                }
            }

            is ChatAction.AllowProduct -> {
                val query = action.product.lowercase()
                val product = db.productDao().all().firstOrNull {
                    it.id == action.product ||
                        it.name.lowercase() == query ||
                        query.contains(it.name.lowercase()) ||
                        it.name.lowercase().contains(query)
                } ?: return "Не нашёл такой продукт"
                db.bannedDao().delete(product.id)

                val dates = (0..6).map { DateUtils.keyFor(it) }
                val ok = editor.regenerate(
                    dates = dates,
                    targetKcal = dailyTargetKcal(diary.getProfile()),
                    preferences = preferences.snapshot()
                )
                if (ok) "Вернул «${product.name}» и пересобрал рацион" else "Вернул «${product.name}»"
            }

            ChatAction.RegenerateWeek -> {
                val dates = (0..6).map { DateUtils.keyFor(it) }
                val ok = editor.regenerate(
                    dates = dates,
                    targetKcal = dailyTargetKcal(diary.getProfile()),
                    preferences = preferences.snapshot()
                )
                if (ok) "Рацион пересобран на неделю" else "Не удалось пересобрать"
            }
        }
    }

    private fun resolveDay(day: String): String =
        if (day == "tomorrow") DateUtils.keyFor(1) else DateUtils.keyFor(0)

    /** «1 блюдо», «2 блюда», «5 блюд» — иначе в чате получается «осталось 1 блюд». */
    private fun dishesWord(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        return when {
            mod100 in 11..14 -> "блюд"
            mod10 == 1 -> "блюдо"
            mod10 in 2..4 -> "блюда"
            else -> "блюд"
        }
    }
}
