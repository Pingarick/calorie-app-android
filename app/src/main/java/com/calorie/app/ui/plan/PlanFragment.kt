package com.calorie.app.ui.plan

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.calorie.app.R
import com.calorie.app.databinding.FragmentPlanBinding
import kotlinx.coroutines.launch

/** Экран рациона: день недели, сводка и список приёмов пищи. */
class PlanFragment : Fragment(R.layout.fragment_plan) {

    private var _b: FragmentPlanBinding? = null
    private val b get() = _b!!
    private val vm: PlanViewModel by viewModels()
    private lateinit var adapter: PlanAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentPlanBinding.bind(view)

        adapter = PlanAdapter(
            onCook = { vm.cook(it) },
            onReplace = { vm.replace(it) },
            onRemove = { vm.remove(it) },
            onPostpone = { vm.postpone(it) }
        )
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.btnPrevDay.setOnClickListener { vm.goToPrev() }
        b.btnNextDay.setOnClickListener { vm.goToNext() }
        b.btnGenerate.setOnClickListener { vm.generateWeek() }

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
}
