package com.calorie.app.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Профиль пользователя (один на приложение). Хранит антропометрию и цель. */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val gender: String = "MALE",          // Gender.name
    val ageYears: Int = 30,
    val heightCm: Int = 175,
    val weightKg: Double = 70.0,
    val activity: String = "SEDENTARY",   // ActivityLevel.name
    val goal: String = "KEEP",            // Goal.name
    val customTargetKcal: Int = 0,        // 0 => считаем по формуле
    /** Недельный бюджет на продукты в рублях. 0 — не задан. */
    @ColumnInfo(defaultValue = "0") val weeklyBudget: Int = 0,
    /** На сколько человек готовим. Влияет на корзину и рецепты, не на личную норму. */
    @ColumnInfo(defaultValue = "1") val peopleCount: Int = 1
)
