package com.calorie.app.ui.main

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.calorie.app.R
import com.calorie.app.data.local.model.DiaryEntry
import com.calorie.app.databinding.ItemDiaryEntryBinding
import com.calorie.app.databinding.ItemMealEmptyBinding
import com.calorie.app.databinding.ItemMealHeaderBinding
import java.io.File
import java.util.Locale

/**
 * Адаптер таблицы дня: заголовок приёма пищи (с подытогом ккал и «+» в этот слот),
 * подпись пустого слота и карточки записей с миниатюрой фото, макросами и удалением.
 */
class DiaryGroupAdapter(
    private val onDelete: (DiaryEntry) -> Unit,
    private val onAddToMeal: (String) -> Unit
) : ListAdapter<DiaryRow, RecyclerView.ViewHolder>(DIFF) {

    private val TYPE_HEADER = 0
    private val TYPE_ENTRY = 1
    private val TYPE_EMPTY = 2

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DiaryRow.MealHeader -> TYPE_HEADER
        is DiaryRow.EntryRow -> TYPE_ENTRY
        is DiaryRow.EmptySlot -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(ItemMealHeaderBinding.inflate(inflater, parent, false))
            TYPE_EMPTY -> EmptyVH(ItemMealEmptyBinding.inflate(inflater, parent, false))
            else -> EntryVH(ItemDiaryEntryBinding.inflate(inflater, parent, false), onDelete)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is DiaryRow.MealHeader -> (holder as HeaderVH).bind(row)
            is DiaryRow.EntryRow -> (holder as EntryVH).bind(row.entry)
            is DiaryRow.EmptySlot -> Unit
        }
    }

    inner class HeaderVH(private val b: ItemMealHeaderBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(row: DiaryRow.MealHeader) {
            b.txtMealTitle.text = row.meal
            val accent = mealColor(row.meal, b.root.context)
            b.mealAccent.backgroundTintList = ColorStateList.valueOf(accent)
            b.txtMealKcal.setTextColor(accent)
            // У пустого слота подытога нет — «0 ккал» выглядело бы как съеденное.
            b.txtMealKcal.text =
                if (row.kcal > 0) "%d ккал".format(Locale.US, row.kcal.toInt()) else ""
            b.btnMealAdd.visibility = if (row.canAdd) View.VISIBLE else View.GONE
            b.btnMealAdd.setOnClickListener { onAddToMeal(row.meal) }
        }
    }

    /** Цвет акцента приёма пищи; неизвестный слот (напр. из старой записи) — брендовый. */
    private fun mealColor(meal: String, context: android.content.Context): Int {
        val res = when (meal) {
            DiaryViewModel.MEAL_SLOTS[0] -> R.color.meal_breakfast
            DiaryViewModel.MEAL_SLOTS[1] -> R.color.meal_lunch
            DiaryViewModel.MEAL_SLOTS[2] -> R.color.meal_dinner
            DiaryViewModel.MEAL_SLOTS[3] -> R.color.meal_snack
            else -> R.color.calorie_primary
        }
        return ContextCompat.getColor(context.applicationContext, res)
    }

    inner class EmptyVH(b: ItemMealEmptyBinding) : RecyclerView.ViewHolder(b.root)

    inner class EntryVH(
        private val b: ItemDiaryEntryBinding,
        private val onDelete: (DiaryEntry) -> Unit
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(e: DiaryEntry) {
            b.txtEntryName.text = e.name
            b.txtEntryMeta.text = macroLine(b.root.context, e)
            b.txtEntryKcal.text = "%d ккал".format(Locale.US, e.kcal.toInt())
            b.btnDeleteEntry.setOnClickListener { onDelete(e) }
            val p = e.photoPath
            if (p.isNotBlank() && File(p).let { it.exists() && it.length() > 0 }) {
                b.imgEntryPhoto.visibility = View.VISIBLE
                b.imgEntryPhoto.load(File(p)) {
                    crossfade(true)
                    placeholder(com.calorie.app.R.drawable.ic_restaurant)
                    error(com.calorie.app.R.drawable.ic_restaurant)
                }
            } else {
                b.imgEntryPhoto.visibility = View.GONE
            }
        }
    }

    private fun macroLine(context: android.content.Context, e: DiaryEntry): String {
        val amount = if (e.grams > 0) "%.0f г".format(Locale.US, e.grams)
        else context.getString(R.string.entry_portion)
        return "%s · Б %.0f · Ж %.0f · У %.0f".format(
            Locale.US, amount, e.protein, e.fat, e.carbs
        )
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DiaryRow>() {
            override fun areItemsTheSame(a: DiaryRow, b: DiaryRow): Boolean =
                (a as DiaryRow.HasId).id == (b as DiaryRow.HasId).id

            override fun areContentsTheSame(a: DiaryRow, b: DiaryRow): Boolean = a == b
        }
    }
}
