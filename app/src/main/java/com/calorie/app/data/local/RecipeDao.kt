package com.calorie.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.calorie.app.data.local.model.Recipe

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipe WHERE dishId = :dishId")
    suspend fun byDish(dishId: String): Recipe?

    @Upsert
    suspend fun upsert(recipe: Recipe)
}
