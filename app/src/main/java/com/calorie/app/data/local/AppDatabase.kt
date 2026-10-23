package com.calorie.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.calorie.app.data.local.model.DiaryEntry
import com.calorie.app.data.local.model.Dish
import com.calorie.app.data.local.model.DishIngredient
import com.calorie.app.data.local.model.PlanItem
import com.calorie.app.data.local.model.Preference
import com.calorie.app.data.local.model.PreferenceEvent
import com.calorie.app.data.local.model.Product
import com.calorie.app.data.local.model.UserProfile

const val DB_NAME = "calorie.db"

@Database(
    entities = [
        UserProfile::class,
        DiaryEntry::class,
        Product::class,
        Dish::class,
        DishIngredient::class,
        PlanItem::class,
        PreferenceEvent::class,
        Preference::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun diaryDao(): DiaryDao
    abstract fun productDao(): ProductDao
    abstract fun dishDao(): DishDao
    abstract fun planDao(): PlanDao
    abstract fun preferenceDao(): PreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Версия 1 знала только профиль и дневник. Новые таблицы создаются здесь,
         * а не через destructive-миграцию: у пользователя в diary_entry уже есть записи.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `kcal100` REAL NOT NULL,
                        `protein100` REAL NOT NULL,
                        `fat100` REAL NOT NULL,
                        `carbs100` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dish` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `slots` TEXT NOT NULL,
                        `cuisine` TEXT NOT NULL,
                        `technique` TEXT NOT NULL,
                        `timeMin` INTEGER NOT NULL,
                        `baseServings` INTEGER NOT NULL,
                        `gramsPerServing` REAL NOT NULL,
                        `kcalPerServing` REAL NOT NULL,
                        `proteinPerServing` REAL NOT NULL,
                        `fatPerServing` REAL NOT NULL,
                        `carbsPerServing` REAL NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dish_ingredient` (
                        `dishId` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `gramsPerServing` REAL NOT NULL,
                        PRIMARY KEY(`dishId`, `productId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `plan_item` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dateKey` TEXT NOT NULL,
                        `slot` TEXT NOT NULL,
                        `dishId` TEXT NOT NULL,
                        `servings` REAL NOT NULL,
                        `grams` REAL NOT NULL,
                        `kcal` REAL NOT NULL,
                        `protein` REAL NOT NULL,
                        `fat` REAL NOT NULL,
                        `carbs` REAL NOT NULL,
                        `locked` INTEGER NOT NULL,
                        `cooked` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Версия 3: журнал событий и выученные веса предпочтений. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `preference_event` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `weight` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `preference` (
                        `key` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `weight` REAL NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent()
                )
            }
        }

        /** Версия 4: цена за 100 г у продукта и недельный бюджет в профиле. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `product` ADD COLUMN `pricePer100` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `weeklyBudget` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
            }
    }
}
