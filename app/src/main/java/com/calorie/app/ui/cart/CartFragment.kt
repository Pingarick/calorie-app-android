package com.calorie.app.ui.cart

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.calorie.app.R
import com.calorie.app.databinding.FragmentCartBinding
import com.calorie.app.util.money
import com.calorie.app.util.weight
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Вкладка «Покупки»: корзина из рациона, итог, бюджет недели и продукты дома. */
class CartFragment : Fragment(R.layout.fragment_cart) {

    private var _b: FragmentCartBinding? = null
    private val b get() = _b!!
    private val vm: CartViewModel by viewModels()
    private lateinit var adapter: CartAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentCartBinding.bind(view)

        adapter = CartAdapter { line ->
            vm.markAtHome(line)
            Toast.makeText(requireContext(), R.string.cart_home_added, Toast.LENGTH_SHORT).show()
        }
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.btnBudget.setOnClickListener { showBudgetDialog() }
        b.btnPantry.setOnClickListener { showPantryDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.rows.collect { adapter.submit(it) } }
                launch { vm.state.collect { render(it) } }
            }
        }
    }

    private fun render(state: CartUiState) {
        b.txtTotal.text = if (state.people > 1) {
            getString(R.string.cart_total_people, money(state.total), state.people)
        } else {
            getString(R.string.cart_total, money(state.total))
        }
        b.txtEmpty.visibility = if (state.hasPlan) View.GONE else View.VISIBLE

        b.btnPantry.text = getString(R.string.cart_pantry_button, state.pantry.size)
        b.btnPantry.isEnabled = state.pantry.isNotEmpty()

        val context = requireContext()
        when {
            state.budget <= 0 -> {
                b.txtBudget.text = getString(R.string.cart_budget_unset)
                b.txtBudget.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            state.total <= state.budget -> {
                val left = state.budget - state.total
                b.txtBudget.text = getString(
                    R.string.cart_budget,
                    money(state.budget.toDouble()),
                    money(left)
                )
                b.txtBudget.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }
            else -> {
                val over = state.total - state.budget
                b.txtBudget.text = getString(
                    R.string.cart_budget_over,
                    money(state.budget.toDouble()),
                    money(over)
                )
                b.txtBudget.setTextColor(ContextCompat.getColor(context, R.color.danger))
            }
        }
    }

    private fun showBudgetDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.cart_budget_hint)
            val current = vm.state.value.budget
            if (current > 0) setText(current.toString())
        }
        val container = FrameLayout(requireContext()).apply {
            setPadding(48, 16, 48, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cart_budget_dialog)
            .setView(container)
            .setPositiveButton(R.string.cart_save) { _, _ ->
                vm.setBudget(input.text.toString().toIntOrNull() ?: 0)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPantryDialog() {
        val pantry = vm.state.value.pantry
        if (pantry.isEmpty()) {
            Toast.makeText(requireContext(), R.string.cart_pantry_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = pantry.map { "${it.name} — ${weight(it.grams)}" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cart_pantry_title)
            .setItems(labels) { _, which ->
                vm.removeFromPantry(pantry[which].productId)
            }
            .setPositiveButton(R.string.cart_pantry_clear) { _, _ -> vm.clearPantry() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
