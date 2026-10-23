package com.calorie.app.ui.plan

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.calorie.app.R
import com.calorie.app.data.local.model.PlanItem
import com.calorie.app.databinding.ItemPlanEmptyBinding
import com.calorie.app.databinding.ItemPlanEntryBinding
import com.calorie.app.databinding.ItemPlanHeaderBinding
import com.calorie.app.domain.planner.MealSlot
import java.util.Locale

/** Список рациона: заголовки слотов, блюда и пустые слоты. */
class PlanAdapter(
    private val onCook: (PlanItem) -> Unit,
    private val onReplace: (PlanItem) -> Unit,
    private val onRemove: (PlanItem) -> Unit,
    private val onPostpone: (PlanItem) -> Unit,
    private val onOpen: (PlanItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<PlanRow> = emptyList()

    fun submit(newRows: List<PlanRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is PlanRow.MealHeader -> TYPE_HEADER
        is PlanRow.EntryRow -> TYPE_ENTRY
        is PlanRow.EmptySlot -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemPlanHeaderBinding.inflate(inflater, parent, false))
            TYPE_ENTRY -> EntryHolder(ItemPlanEntryBinding.inflate(inflater, parent, false))
            else -> EmptyHolder(ItemPlanEmptyBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PlanRow.MealHeader -> (holder as HeaderHolder).bind(row)
            is PlanRow.EntryRow -> (holder as EntryHolder).bind(row, onCook, onReplace, onRemove, onPostpone, onOpen)
            is PlanRow.EmptySlot -> Unit
        }
    }

    private class HeaderHolder(private val b: ItemPlanHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: PlanRow.MealHeader) {
            b.txtMealTitle.text = row.slot.label
            b.txtMealKcal.text = if (row.kcal > 0) {
                String.format(Locale.US, "%d ккал", row.kcal.toInt())
            } else {
                ""
            }
            b.mealAccent.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(b.root.context, accentFor(row.slot)))
        }
    }

    private class EntryHolder(private val b: ItemPlanEntryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(
            row: PlanRow.EntryRow,
            onCook: (PlanItem) -> Unit,
            onReplace: (PlanItem) -> Unit,
            onRemove: (PlanItem) -> Unit,
            onPostpone: (PlanItem) -> Unit,
            onOpen: (PlanItem) -> Unit
        ) {
            val item = row.item
            b.txtDishTitle.text = row.dish.title
            b.txtDishMeta.text = String.format(
                Locale.US,
                "%.1f порц. · %d г · Б %.0f · Ж %.0f · У %.0f",
                item.servings, item.grams.toInt(), item.protein, item.fat, item.carbs
            )
            b.txtDishKcal.text = String.format(Locale.US, "%d ккал", item.kcal.toInt())
            b.btnCook.isEnabled = !item.cooked
            b.btnCook.alpha = if (item.cooked) 0.35f else 1f
            b.btnCook.setOnClickListener { onCook(item) }
            b.btnReplace.setOnClickListener { onReplace(item) }
            b.btnRemove.setOnClickListener { onRemove(item) }
            // Тап по карточке открывает рецепт; долгое нажатие — «не сегодня».
            b.root.setOnClickListener { onOpen(item) }
            b.root.setOnLongClickListener {
                onPostpone(item)
                true
            }
        }
    }

    private class EmptyHolder(b: ItemPlanEmptyBinding) : RecyclerView.ViewHolder(b.root)

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ENTRY = 1
        const val TYPE_EMPTY = 2

        fun accentFor(slot: MealSlot): Int = when (slot) {
            MealSlot.BREAKFAST -> R.color.meal_breakfast
            MealSlot.LUNCH -> R.color.meal_lunch
            MealSlot.DINNER -> R.color.meal_dinner
            MealSlot.SNACK -> R.color.meal_snack
        }
    }
}
