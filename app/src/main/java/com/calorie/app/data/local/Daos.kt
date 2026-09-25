package com.calorie.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.calorie.app.data.local.model.DiaryEntry
import com.calorie.app.data.local.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfile?

    @Insert
    suspend fun insert(profile: UserProfile)

    @Update
    suspend fun update(profile: UserProfile)
}

@Dao
interface DiaryDao {
    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Query("DELETE FROM diary_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM diary_entry WHERE dateKey = :dateKey ORDER BY mealTimeMs ASC")
    fun observeForDate(dateKey: String): Flow<List<DiaryEntry>>

    /** Суммы КБЖУ для конкретной даты. */
    @Query(
        """SELECT COALESCE(SUM(kcal),0) AS kcal,
                  COALESCE(SUM(protein),0) AS protein,
                  COALESCE(SUM(fat),0) AS fat,
                  COALESCE(SUM(carbs),0) AS carbs
           FROM diary_entry WHERE dateKey = :dateKey"""
    )
    fun aggregatesForDate(dateKey: String): Flow<DayAggRow>
}

/** Строка-агрегат суммы макро по дню. */
data class DayAggRow(
    @ColumnInfo(name = "kcal") val kcal: Double,
    @ColumnInfo(name = "protein") val protein: Double,
    @ColumnInfo(name = "fat") val fat: Double,
    @ColumnInfo(name = "carbs") val carbs: Double
)
