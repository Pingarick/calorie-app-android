package com.calorie.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.calorie.app.R
import com.calorie.app.data.local.model.ActivityLevel
import com.calorie.app.data.local.model.Gender
import com.calorie.app.data.local.model.Goal
import com.calorie.app.data.local.model.UserProfile
import com.calorie.app.databinding.FragmentProfileBinding
import com.calorie.app.ui.prefs.KnownPrefsActivity
import com.calorie.app.util.dailyTargetKcal
import com.calorie.app.util.macroTargets
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale

/** Вкладка «Профиль»: норма, макросы и антропометрия. Редактирование открывает WelcomeActivity. */
class ProfileFragment : Fragment() {

    private var _b: FragmentProfileBinding? = null
    private val b get() = _b!!

    private val vm: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentProfileBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnEditProfile.setOnClickListener {
            startActivity(
                Intent(requireContext(), WelcomeActivity::class.java)
                    .putExtra(WelcomeActivity.EXTRA_EDIT, true)
            )
        }

        b.btnKnownPrefs.setOnClickListener {
            startActivity(Intent(requireContext(), KnownPrefsActivity::class.java))
        }

        b.btnPeople.setOnClickListener { showPeopleDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.profile.collect { render(it) }
            }
        }
        // Норма зависит от профиля — рисуем её в том же collect.
    }

    private fun render(p: UserProfile?) {
        b.btnPeople.text = getString(R.string.profile_people, p?.peopleCount ?: 1)
        if (p == null) {
            b.txtDailyTarget.text = "—"
            b.txtMacroTarget.setText(R.string.profile_empty)
            b.txtGenderAge.text = ""
            b.txtBody.text = ""
            b.txtActivityGoal.text = ""
            b.txtBmr.text = ""
            b.txtCustomHint.text = ""
            return
        }
        val kcal = dailyTargetKcal(p)
        b.txtDailyTarget.text = String.format(Locale.US, "%d", kcal)
        b.txtMacroTarget.text = macroLine(kcal)

        b.txtGenderAge.text =
            getString(R.string.profile_gender_age, genderLabel(p.gender), p.ageYears)
        b.txtBody.text = getString(
            R.string.profile_body,
            p.heightCm,
            if (p.weightKg % 1.0 == 0.0) p.weightKg.toInt().toString() else p.weightKg.toString()
        )
        b.txtActivityGoal.text = activityLabel(p.activity) + " · " + goalLabel(p.goal)
        b.txtBmr.text = getString(R.string.profile_bmr, bmr(p))
        b.txtCustomHint.text = if (p.customTargetKcal > 0) {
            getString(R.string.profile_custom_norm, p.customTargetKcal)
        } else {
            getString(R.string.profile_formula)
        }
    }

    private fun bmr(p: UserProfile): Int {
        val sexCorrection = if (p.gender == Gender.FEMALE.name) -161.0 else 5.0
        val v = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.ageYears + sexCorrection
        return v.roundToInt()
    }

    private fun macroLine(kcal: Int): String {
        val m = macroTargets(kcal)
        return getString(
            R.string.profile_macro_line,
            m.protein.roundToInt(), m.fat.roundToInt(), m.carbs.roundToInt()
        )
    }

    private fun genderLabel(g: String): String = getString(
        if (g == Gender.FEMALE.name) R.string.profile_gender_female else R.string.profile_gender_male
    )

    private fun activityLabel(a: String): String =
        ActivityLevel.byName(a).label.split("(").first().trim()

    private fun goalLabel(g: String): String =
        Goal.entries.firstOrNull { it.name == g }?.label?.trim() ?: g

    private fun showPeopleDialog() {
        val current = vm.profile.value?.peopleCount ?: 1
        val options = (1..8).map { "$it чел." }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_people_dialog)
            .setSingleChoiceItems(options, current - 1) { dialog, which ->
                vm.setPeople(which + 1)
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
