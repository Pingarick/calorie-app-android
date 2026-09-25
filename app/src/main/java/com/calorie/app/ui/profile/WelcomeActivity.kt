package com.calorie.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.calorie.app.CalorieApp
import com.calorie.app.R
import com.calorie.app.databinding.ActivityWelcomeBinding
import com.calorie.app.data.local.model.ActivityLevel
import com.calorie.app.data.local.model.Gender
import com.calorie.app.data.local.model.Goal
import com.calorie.app.data.local.model.UserProfile
import com.calorie.app.data.repository.DiaryRepository
import com.calorie.app.ui.main.MainActivity
import com.calorie.app.util.dailyTargetKcal
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import android.text.Editable
import android.text.TextWatcher
import java.util.Locale

/**
 * Онбординг/редактирование анкеты. До сохранения считаем норму вживую и показываем.
 *
 * Экран — launcher-активность, поэтому на холодном старте он показывается первым.
 * Если анкета уже есть и это не явное редактирование ([EXTRA_EDIT]), сразу уходим в Дневник —
 * иначе пользователь видел бы анкету при каждом запуске.
 */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityWelcomeBinding
    private val repo: DiaryRepository by lazy {
        DiaryRepository((application as CalorieApp).database)
    }

    /** true — открыто с Профиля для правки анкеты; false — холодный старт. */
    private val editMode: Boolean get() = intent.getBooleanExtra(EXTRA_EDIT, false)

    // Текущие выбранные значения анкеты.
    private var gender = Gender.MALE.name
    private var activity = ActivityLevel.SEDENTARY.name
    private var goal = Goal.KEEP.name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        applyModeWording()
        wireButtons()
        wireFields()
    }

    private fun applyModeWording() {
        if (!editMode) return
        b.txtTitle.setText(R.string.welcome_edit_title)
        b.txtSubtitle.setText(R.string.welcome_edit_subtitle)
        b.btnSaveProfile.setText(R.string.dialog_save)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val p = repo.getProfile()
            if (p != null && !editMode) {
                // Анкета есть и это холодный старт — показываем сразу Дневник.
                goToDiary()
                return@launch
            }
            p?.let(::prefill)
            refreshPreview()
        }
    }

    private fun prefill(p: UserProfile) {
        b.edtAge.setText(p.ageYears.toString())
        b.edtHeight.setText(p.heightCm.toString())
        b.edtWeight.setText(if (p.weightKg % 1.0 == 0.0) p.weightKg.toInt().toString()
            else p.weightKg.toString())
        gender = p.gender
        activity = p.activity
        goal = p.goal
        if (p.customTargetKcal > 0) {
            b.swCustomTarget.isChecked = true
            b.edtCustomTarget.setText(p.customTargetKcal.toString())
        }
        applyCustomVisibility()
        selectGender()
        selectActivity()
        selectGoal()
    }

    // ---------- Гендер ----------

    private fun wireButtons() {
        b.btnMale.setOnClickListener { gender = Gender.MALE.name; selectGender(); refreshPreview() }
        b.btnFemale.setOnClickListener { gender = Gender.FEMALE.name; selectGender(); refreshPreview() }

        b.btnLow.setOnClickListener { activity = ActivityLevel.SEDENTARY.name; selectActivity(); refreshPreview() }
        b.btnMid.setOnClickListener { activity = ActivityLevel.LIGHT.name; selectActivity(); refreshPreview() }
        b.btnHigh.setOnClickListener { activity = ActivityLevel.MODERATE.name; selectActivity(); refreshPreview() }

        b.btnLose.setOnClickListener { goal = Goal.LOSE.name; selectGoal(); refreshPreview() }
        b.btnKeep.setOnClickListener { goal = Goal.KEEP.name; selectGoal(); refreshPreview() }
        b.btnGain.setOnClickListener { goal = Goal.GAIN.name; selectGoal(); refreshPreview() }

        b.btnSaveProfile.setOnClickListener { save() }
        b.swCustomTarget.setOnCheckedChangeListener { _, _ ->
            applyCustomVisibility()
            refreshPreview()
        }
    }

    private fun applyCustomVisibility() {
        b.boxCustomTarget.visibility = if (b.swCustomTarget.isChecked) View.VISIBLE else View.GONE
    }

    private fun selectGender() {
        setOn(b.btnMale, gender == Gender.MALE.name)
        setOn(b.btnFemale, gender == Gender.FEMALE.name)
    }

    private fun selectActivity() {
        setOn(b.btnLow, activity == ActivityLevel.SEDENTARY.name)
        setOn(b.btnMid, activity == ActivityLevel.LIGHT.name)
        setOn(b.btnHigh, activity == ActivityLevel.MODERATE.name)
    }

    private fun selectGoal() {
        setOn(b.btnLose, goal == Goal.LOSE.name)
        setOn(b.btnKeep, goal == Goal.KEEP.name)
        setOn(b.btnGain, goal == Goal.GAIN.name)
    }

    private fun setOn(btn: Button, on: Boolean) {
        btn.isSelected = on
        btn.alpha = if (on) 1f else 0.5f
    }

    // ---------- Поля ----------

    private fun wireFields() {
        listOf(b.edtAge, b.edtHeight, b.edtWeight, b.edtCustomTarget).forEach { edt ->
            edt.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    clearErrors()
                    refreshPreview()
                }
            })
        }
    }

    private fun intOf(edt: EditText): Int = edt.text.toString().trim().toIntOrNull() ?: 0
    private fun dblOf(edt: EditText): Double = edt.text.toString().trim().toDoubleOrNull() ?: 0.0

    private fun currentCustomTarget(): Int =
        if (b.swCustomTarget.isChecked) intOf(b.edtCustomTarget) else 0

    private fun refreshPreview() {
        val age = intOf(b.edtAge)
        val height = intOf(b.edtHeight)
        val weight = dblOf(b.edtWeight)
        val custom = currentCustomTarget()

        if (age <= 0 || height <= 0 || weight <= 0.0) {
            showPreviewMessage(getString(R.string.welcome_preview_fill))
            return
        }
        // Диапазоны те же, что и в save(): иначе превью показывало бы норму,
        // которую «Сохранить» тут же отвергает (например, для возраста 999).
        if (age > 100 || height !in 80..250 || weight !in 20.0..300.0) {
            showPreviewMessage(getString(R.string.welcome_preview_ranges))
            return
        }
        val kcal = dailyTargetKcal(
            age = age,
            height = height,
            weight = weight,
            gender = gender,
            activity = activity,
            goal = goal,
            customTarget = custom
        )
        b.txtPreviewTarget.text = String.format(Locale.US, "%d", kcal)
        b.txtPreviewTarget.setTextSize(
            TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_hero_big)
        )
        b.txtPreviewTarget.setTextColor(ContextCompat.getColor(this, R.color.calorie_primary))
        b.txtPreviewCaption.setText(
            if (custom > 0) R.string.welcome_norm_unit_custom else R.string.welcome_norm_unit
        )
    }

    /** Подсказка вместо цифры: тот же TextView, но обычным размером — в 42sp текст не влезал. */
    private fun showPreviewMessage(msg: String) {
        b.txtPreviewTarget.text = msg
        b.txtPreviewTarget.setTextSize(
            TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_preview_message)
        )
        b.txtPreviewTarget.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        b.txtPreviewCaption.text = ""
    }

    private fun clearErrors() {
        listOf(b.boxAge, b.boxHeight, b.boxWeight, b.boxCustomTarget).forEach { it.error = null }
    }

    // ---------- Сохранение ----------

    private fun save() {
        clearErrors()
        val age = intOf(b.edtAge)
        val height = intOf(b.edtHeight)
        val weight = dblOf(b.edtWeight)
        val custom = currentCustomTarget()

        var firstBad: EditText? = null
        fun fail(field: EditText, box: TextInputLayout, msg: String) {
            box.error = msg
            if (firstBad == null) firstBad = field
        }

        // Диапазоны те же, что и в превью, — иначе сохранённая норма расходилась бы с показанной.
        if (age !in 1..100) fail(b.edtAge, b.boxAge, getString(R.string.welcome_err_age))
        if (height !in 80..250) fail(b.edtHeight, b.boxHeight, getString(R.string.welcome_err_height))
        if (weight !in 20.0..300.0) fail(b.edtWeight, b.boxWeight, getString(R.string.welcome_err_weight))
        if (b.swCustomTarget.isChecked && custom !in 500..10000) {
            fail(b.edtCustomTarget, b.boxCustomTarget, getString(R.string.welcome_err_custom))
        }
        val bad = firstBad
        if (bad != null) {
            showPreviewMessage(getString(R.string.welcome_preview_check_fields))
            bad.requestFocus()
            return
        }
        val profile = UserProfile(
            gender = gender,
            ageYears = age,
            heightCm = height,
            weightKg = weight,
            activity = activity,
            goal = goal,
            customTargetKcal = custom
        )
        lifecycleScope.launch {
            repo.saveProfile(profile)
            if (editMode) finish() else goToDiary()
        }
    }

    /** Открывает Дневник и закрывает этот экран, чтобы не осталось «пустого» таска. */
    private fun goToDiary() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_EDIT = "extra_edit"
    }
}
