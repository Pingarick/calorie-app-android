package com.calorie.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.calorie.app.data.local.model.Preference
import com.calorie.app.data.local.model.PreferenceEvent
import kotlinx.coroutines.flow.Flow

/** Последняя готовка блюда — для экрана «что я знаю» и новизны в планировщике. */
data class LastCooked(val entityId: String, val lastAt: Long)

@Dao
interface PreferenceDao {
    @Insert
    suspend fun insertEvent(event: PreferenceEvent): Long

    @Query("SELECT * FROM preference_event WHERE type = :type ORDER BY createdAt DESC")
    suspend fun eventsOfType(type: String): List<PreferenceEvent>

    @Query("SELECT * FROM preference_event ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentEvents(limit: Int): List<PreferenceEvent>

    @Query("SELECT * FROM preference")
    suspend fun all(): List<Preference>

    @Query("SELECT * FROM preference")
    fun observeAll(): Flow<List<Preference>>

    @Query("SELECT * FROM preference WHERE `key` = :key")
    suspend fun byKey(key: String): Preference?

    @Upsert
    suspend fun upsert(preference: Preference)

    @Query(
        """
        SELECT entityId, MAX(createdAt) AS lastAt
        FROM preference_event
        WHERE type = 'COOKED'
        GROUP BY entityId
        """
    )
    fun observeLastCooked(): Flow<List<LastCooked>>

    @Query("DELETE FROM preference WHERE entityId = :dishId")
    suspend fun deletePreference(dishId: String)

    @Query("DELETE FROM preference_event WHERE entityId = :dishId")
    suspend fun deleteEvents(dishId: String)
}
