package com.calorie.app.data.repository

import com.calorie.app.data.local.AppDatabase
import com.calorie.app.data.local.model.DiaryEntry
import com.calorie.app.data.local.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** Единый доступ к дневнику и профилю. */
class DiaryRepository(private val db: AppDatabase) {

    // ---- Profile ----
    fun observeProfile(): Flow<UserProfile?> = db.profileDao().observe()

    suspend fun getProfile(): UserProfile? = db.profileDao().get()

    suspend fun saveProfile(profile: UserProfile) {
        val dao = db.profileDao()
        if (dao.get() == null) dao.insert(profile) else dao.update(profile)
    }

    // ---- Diary ----
    fun observeDay(dateKey: String): Flow<List<DiaryEntry>> =
        db.diaryDao().observeForDate(dateKey)

    fun observeDayKbju(dateKey: String) = db.diaryDao().aggregatesForDate(dateKey)

    suspend fun addEntry(entry: DiaryEntry): Long = db.diaryDao().insert(entry)

    suspend fun deleteEntry(id: Long) = db.diaryDao().deleteById(id)
}
