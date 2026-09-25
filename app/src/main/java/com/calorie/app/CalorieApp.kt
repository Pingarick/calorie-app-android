package com.calorie.app

import android.app.Application
import com.calorie.app.data.local.AppDatabase

class CalorieApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
}
