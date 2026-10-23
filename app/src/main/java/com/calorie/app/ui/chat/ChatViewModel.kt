package com.calorie.app.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.app.CalorieApp
import com.calorie.app.data.local.model.ChatMessage
import com.calorie.app.data.local.model.UserProfile
import com.calorie.app.data.remote.ChatClient
import com.calorie.app.data.repository.DiaryRepository
import com.calorie.app.data.repository.PreferenceRepository
import com.calorie.app.domain.chat.ChatAction
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

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _sending.value) return

        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            withContext(Dispatchers.IO) {
                db.chatDao().insert(ChatMessage(role = "user", text = text))
            }
            try {
                val context = withContext(Dispatchers.IO) { buildContext() }
                val reply = client.reply(text, context)
                val outcome = withContext(Dispatchers.IO) { apply(reply.action) }
                val finalText = if (outcome == null) reply.text else reply.text + "\n\n" + outcome
                withContext(Dispatchers.IO) {
                    db.chatDao().insert(
                        ChatMessage(
                            role = "assistant",
                            text = finalText,
                            actionJson = reply.actionJson
                        )
                    )
                }
            } catch (e: Exception) {
                val message = e.message ?: "ошибка сети"
                _error.value = message
                withContext(Dispatchers.IO) {
                    db.chatDao().insert(
                        ChatMessage(role = "assistant", text = "Не получилось: $message")
                    )
                }
            } finally {
                _sending.value = false
            }
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
        val dishes = db.dishDao().all().associateBy { it.id }
        val plan = items.joinToString("; ") { item ->
            val title = dishes[item.dishId]?.title ?: "блюдо"
            "${MealSlot.byName(item.slot).label}: $title (${item.kcal.roundToInt()} ккал)"
        }.ifEmpty { "пусто" }
        return buildString {
            append("Сегодня $today. ")
            append("Норма ${dailyTargetKcal(profile)} ккал. ")
            append("Бюджет ${profile?.weeklyBudget ?: 0} ₽. ")
            append("В плане на сегодня: $plan.")
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
}
