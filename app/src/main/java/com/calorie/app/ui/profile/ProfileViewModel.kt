package com.calorie.app.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calorie.app.CalorieApp
import com.calorie.app.data.local.model.UserProfile
import com.calorie.app.data.repository.DiaryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Профиль: текущая анкета (норма считается из неё напрямую). */
class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DiaryRepository((app as CalorieApp).database)

    val profile: StateFlow<UserProfile?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** На сколько человек готовим: влияет на корзину и состав рецептов. */
    fun setPeople(count: Int) {
        viewModelScope.launch {
            val current = repo.getProfile() ?: UserProfile()
            repo.saveProfile(current.copy(peopleCount = count.coerceIn(1, 12)))
        }
    }
}
