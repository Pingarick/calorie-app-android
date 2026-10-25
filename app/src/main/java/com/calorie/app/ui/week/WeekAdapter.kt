package com.calorie.app.ui.week

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.calorie.app.databinding.ItemWeekDayBinding
import com.calorie.app.databinding.ItemWeekDishBinding
import com.calorie.app.util.money
import java.util.Locale

/** Список недели: заголовки дней (кликабельные) и блюда. */
class WeekAdapter(
    private val onDayClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<WeekRow> = emptyList()

    fun submit(newRows: List<WeekRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is WeekRow.Day) TYPE_DAY else TYPE_DISH

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_DAY) {
            DayHolder(ItemWeekDayBinding.inflate(inflater, parent, false), onDayClick)
        } else {
            DishHolder(ItemWeekDishBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is WeekRow.Day -> (holder as DayHolder).bind(row)
            is WeekRow.Dish -> (holder as DishHolder).bind(row)
        }
    }

    private class DayHolder(
        private val b: ItemWeekDayBinding,
        private val onDayClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: WeekRow.Day) {
            b.txtDay.text = row.title
            b.txtDayKcal.text =
                if (row.kcal > 0) {
                    String.format(Locale.US, "%d ккал", row.kcal.toInt())
                } else {
                    ""
                }
            b.root.setOnClickListener { onDayClick(row.offset) }
        }
    }

    private class DishHolder(private val b: ItemWeekDishBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: WeekRow.Dish) {
            b.txtDish.text = "${row.slot} · ${row.title}"
            b.txtDishKcal.text =
                if (row.kcal > 0) {
                    String.format(Locale.US, "%d", row.kcal.toInt())
                } else {
                    ""
                }
            b.txtLeftover.visibility =
                if (row.leftover) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private companion object {
        const val TYPE_DAY = 0
        const val TYPE_DISH = 1
    }
}
