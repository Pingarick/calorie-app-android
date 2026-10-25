package com.calorie.app.ui.plan

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.calorie.app.R
import com.calorie.app.databinding.DialogRecipeBinding
import com.calorie.app.databinding.FragmentPlanBinding
import com.calorie.app.util.weight
import com.calorie.app.ui.week.WeekActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Экран рациона: день недели, сводка и список приёмов пищи. */
class PlanFragment : Fragment(R.layout.fragment_plan) {

    private var _b: FragmentPlanBinding? = null
    private val b get() = _b!!
    private val vm: PlanViewModel by viewModels()
    private val recipeVm: RecipeViewModel by viewModels()
    private lateinit var adapter: PlanAdapter

    private val weekLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val offset = result.data?.getIntExtra(WeekActivity.EXTRA_OFFSET, -1) ?: -1
            if (offset >= 0) vm.goToOffset(offset)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentPlanBinding.bind(view)

        adapter = PlanAdapter(
            onCook = { vm.cook(it) },
            onReplace = { vm.replace(it) },
            onRemove = { vm.remove(it) },
            onPostpone = { vm.postpone(it) },
            onOpen = { showRecipe(it) }
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.btnPrevDay.setOnClickListener { vm.goToPrev() }
        b.btnNextDay.setOnClickListener { vm.goToNext() }
        b.btnGenerate.setOnClickListener { vm.generateWeek() }
        b.btnWeek.setOnClickListener {
            weekLauncher.launch(android.content.Intent(requireContext(), WeekActivity::class.java))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.dayTitle.collect { b.txtDay.text = it }
                }
                launch {
                    vm.canGoPrev.collect { enabled ->
                        b.btnPrevDay.isEnabled = enabled
                        b.btnPrevDay.alpha = if (enabled) 1f else 0.3f
                    }
                }
                launch {
                    vm.canGoNext.collect { enabled ->
                        b.btnNextDay.isEnabled = enabled
                        b.btnNextDay.alpha = if (enabled) 1f else 0.3f
                    }
                }
                launch {
                    vm.rows.collect { adapter.submit(it) }
                }
                launch {
                    vm.summary.collect { summary ->
                        b.txtPlanSummary.text = if (summary.plannedKcal > 0.0) {
                            getString(
                                R.string.plan_summary,
                                summary.plannedKcal.toInt(),
                                summary.targetKcal
                            )
                        } else {
                            getString(R.string.plan_empty)
                        }
                    }
                }
                launch {
                    vm.busy.collect { busy ->
                        b.btnGenerate.isEnabled = !busy
                        b.btnGenerate.text = getString(
                            if (busy) R.string.plan_generating else R.string.plan_generate
                        )
                    }
                }
                launch {
                    vm.message.collect { message ->
                        if (message != null) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                            vm.clearMessage()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    /** Диалог рецепта: состав под текущую порцию и шаги, если их уже генерировали. */
    private fun showRecipe(item: com.calorie.app.data.local.model.PlanItem) {
        val binding = DialogRecipeBinding.inflate(layoutInflater)
        binding.btnRecipeGenerate.setOnClickListener { recipeVm.generate() }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setNegativeButton(R.string.recipe_close, null)
            .create()

        val job = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                recipeVm.state.collect { state -> renderRecipe(binding, state) }
            }
        }
        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
        recipeVm.open(item.dishId, item.servings, item.batchServings)
    }

    private fun renderRecipe(b: DialogRecipeBinding, state: RecipeUiState) {
        b.tvRecipeTitle.text = state.title
        b.tvRecipePortion.text = state.portion
        b.tvRecipePortion.visibility = if (state.portion.isBlank()) View.GONE else View.VISIBLE

        b.tvRecipeIngredients.text = state.ingredients.joinToString("\n") { line ->
            "• ${line.name} — ${weight(line.grams)}"
        }

        b.tvRecipeSteps.text = state.steps.mapIndexed { index, step ->
            val timer = step.timerMin?.let { " · таймер $it мин" }.orEmpty()
            "${index + 1}. ${step.text}$timer"
        }.joinToString("\n\n")

        b.tvRecipeError.text = state.error
        b.tvRecipeError.visibility = if (state.error == null) View.GONE else View.VISIBLE
        b.progressRecipe.visibility = if (state.loading) View.VISIBLE else View.GONE
        b.btnRecipeGenerate.visibility =
            if (!state.hasRecipe && !state.loading) View.VISIBLE else View.GONE
    }
}
