package com.calorie.app.ui.cart

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Вкладка «Покупки»: корзина из рациона, итог и бюджет недели. */
class CartFragment : Fragment(R.layout.fragment_cart) {

    private var _b: FragmentCartBinding? = null
    private val b get() = _b!!
    private val vm: CartViewModel by viewModels()
    private val adapter = CartAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentCartBinding.bind(view)

        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.btnBudget.setOnClickListener { showBudgetDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.rows.collect { adapter.submit(it) } }
                launch { vm.state.collect { render(it) } }
            }
        }
    }

    private fun render(state: CartUiState) {
        b.txtTotal.text = getString(R.string.cart_total, money(state.total))
        b.txtEmpty.visibility = if (state.hasPlan) View.GONE else View.VISIBLE

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

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
