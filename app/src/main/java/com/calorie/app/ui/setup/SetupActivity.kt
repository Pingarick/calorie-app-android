package com.calorie.app.ui.setup

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.calorie.app.CalorieApp
import com.calorie.app.R
import com.calorie.app.data.local.model.BannedProduct
import com.calorie.app.data.local.model.UserProfile
import com.calorie.app.data.repository.DiaryRepository
import com.calorie.app.data.repository.PreferenceRepository
import com.calorie.app.databinding.ActivitySetupBinding
import com.calorie.app.domain.plan.PlanEditor
import com.calorie.app.ui.main.MainActivity
import com.calorie.app.util.DateUtils
import com.calorie.app.util.dailyTargetKcal
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Быстрая настройка рациона после анкеты: сколько человек, бюджет, что не любите.
 * Без этого планировщик стартует нейтральным, а корзина не знает бюджета.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var b: ActivitySetupBinding
    private val db by lazy { (application as CalorieApp).database }
    private val repo by lazy { DiaryRepository(db) }
    private val preferences by lazy { PreferenceRepository(db) }
    private val editor by lazy { PlanEditor(db) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnSetupSave.setOnClickListener { save() }

        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) { repo.getProfile() } ?: UserProfile()
            val products = withContext(Dispatchers.IO) { db.productDao().all().sortedBy { it.name } }
            val banned = withContext(Dispatchers.IO) { db.bannedDao().all().map { it.productId }.toSet() }

            buildPeopleChips(profile.peopleCount)
            buildProductChips(products.map { it.id to it.name }, banned)
            if (profile.weeklyBudget > 0) {
                b.etSetupBudget.setText(profile.weeklyBudget.toString())
            }
        }
    }

    private fun buildPeopleChips(selected: Int) {
        b.chipsPeople.removeAllViews()
        for (count in 1..6) {
            val chip = Chip(this).apply {
                text = getString(R.string.setup_people_chip, count)
                isCheckable = true
                isChecked = count == selected
                tag = count
                setId(View.generateViewId())
            }
            b.chipsPeople.addView(chip)
        }
    }

    private fun buildProductChips(products: List<Pair<String, String>>, banned: Set<String>) {
        b.chipsProducts.removeAllViews()
        for ((productId, name) in products) {
            val chip = Chip(this).apply {
                text = name
                isCheckable = true
                isChecked = productId in banned
                tag = productId
                setId(View.generateViewId())
            }
            b.chipsProducts.addView(chip)
        }
    }

    private fun save() {
        val checkedId = b.chipsPeople.checkedChipId
        val people = if (checkedId != View.NO_ID) {
            (findViewById<Chip>(checkedId)?.tag as? Int) ?: 1
        } else {
            1
        }
        val budget = b.etSetupBudget.text?.toString()?.toIntOrNull()?.coerceIn(0, 1_000_000) ?: 0

        val banned = buildList {
            for (index in 0 until b.chipsProducts.childCount) {
                val chip = b.chipsProducts.getChildAt(index) as? Chip ?: continue
                if (chip.isChecked) (chip.tag as? String)?.let { add(it) }
            }
        }

        b.btnSetupSave.isEnabled = false
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val current = repo.getProfile() ?: UserProfile()
                val updated = current.copy(peopleCount = people, weeklyBudget = budget)
                repo.saveProfile(updated)

                db.bannedDao().clear()
                banned.forEach { db.bannedDao().insert(BannedProduct(it)) }

                // Если рацион уже собран, сразу учитываем новые ограничения.
                val dates = (0..6).map { DateUtils.keyFor(it) }
                val hasPlan = db.planDao().range(dates.first(), dates.last()).isNotEmpty()
                if (hasPlan) {
                    editor.regenerate(
                        dates = dates,
                        targetKcal = dailyTargetKcal(updated),
                        preferences = preferences.snapshot()
                    )
                }
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()

            if (intent.getBooleanExtra(EXTRA_EDIT, false)) {
                finish()
            } else {
                startActivity(Intent(this@SetupActivity, MainActivity::class.java))
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_EDIT = "extra_edit"
        private const val PREFS = "setup"
        private const val KEY_DONE = "done"

        fun isDone(context: android.content.Context): Boolean =
            context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DONE, false)
    }
}
