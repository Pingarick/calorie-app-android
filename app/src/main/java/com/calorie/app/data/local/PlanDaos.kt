package com.calorie.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.calorie.app.data.local.model.Dish
import com.calorie.app.data.local.model.DishIngredient
import com.calorie.app.data.local.model.PlanItem
import com.calorie.app.data.local.model.Product
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Product>)

    @Query("SELECT * FROM product ORDER BY name")
    suspend fun all(): List<Product>

    @Query("SELECT * FROM product ORDER BY name")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT COUNT(*) FROM product")
    suspend fun count(): Int
}

@Dao
interface DishDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Dish>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(items: List<DishIngredient>)

    @Query("SELECT * FROM dish ORDER BY title")
    suspend fun all(): List<Dish>

    @Query("SELECT * FROM dish WHERE id = :id")
    suspend fun byId(id: String): Dish?

    @Query("SELECT * FROM dish_ingredient WHERE dishId = :dishId")
    suspend fun ingredientsFor(dishId: String): List<DishIngredient>

    @Query("SELECT * FROM dish_ingredient")
    suspend fun allIngredients(): List<DishIngredient>

    @Query("SELECT COUNT(*) FROM dish")
    suspend fun count(): Int

    @Query("DELETE FROM dish")
    suspend fun deleteAll()

    @Query("DELETE FROM dish_ingredient")
    suspend fun deleteAllIngredients()
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_item WHERE dateKey = :dateKey ORDER BY orderIndex")
    fun observeForDate(dateKey: String): Flow<List<PlanItem>>

    @Query("SELECT * FROM plan_item WHERE dateKey = :dateKey ORDER BY orderIndex")
    suspend fun forDate(dateKey: String): List<PlanItem>

    @Query("SELECT * FROM plan_item WHERE dateKey BETWEEN :from AND :to ORDER BY dateKey, orderIndex")
    suspend fun range(from: String, to: String): List<PlanItem>

    @Query("SELECT * FROM plan_item WHERE dateKey BETWEEN :from AND :to ORDER BY dateKey, orderIndex")
    fun observeRange(from: String, to: String): Flow<List<PlanItem>>

    @Query("SELECT * FROM plan_item WHERE id = :id")
    suspend fun byId(id: Long): PlanItem?

    @Insert
    suspend fun insertAll(items: List<PlanItem>)

    @Query("DELETE FROM plan_item WHERE dateKey BETWEEN :from AND :to")
    suspend fun clearRange(from: String, to: String)

    @Query("DELETE FROM plan_item")
    suspend fun deleteAll()

    @Query("DELETE FROM plan_item WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE plan_item SET cooked = 1 WHERE id = :id")
    suspend fun markCooked(id: Long)
}
