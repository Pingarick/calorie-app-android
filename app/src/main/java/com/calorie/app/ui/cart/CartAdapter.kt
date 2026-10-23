package com.calorie.app.ui.cart

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.calorie.app.databinding.ItemCartHeaderBinding
import com.calorie.app.databinding.ItemCartLineBinding
import com.calorie.app.util.money
import com.calorie.app.util.weight

/** Список покупок: отделы магазина и продукты с граммами и ценой. */
class CartAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<CartRow> = emptyList()

    fun submit(newRows: List<CartRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is CartRow.Section) TYPE_SECTION else TYPE_LINE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SECTION) {
            SectionHolder(ItemCartHeaderBinding.inflate(inflater, parent, false))
        } else {
            LineHolder(ItemCartLineBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is CartRow.Section -> (holder as SectionHolder).bind(row)
            is CartRow.Line -> (holder as LineHolder).bind(row)
        }
    }

    private class SectionHolder(private val b: ItemCartHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: CartRow.Section) {
            b.txtCategory.text = row.category.replaceFirstChar { it.uppercaseChar() }
            b.txtSectionTotal.text = money(row.total)
        }
    }

    private class LineHolder(private val b: ItemCartLineBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: CartRow.Line) {
            b.txtProduct.text = row.line.name
            b.txtAmount.text = weight(row.line.grams)
            b.txtPrice.text = money(row.line.price)
        }
    }

    private companion object {
        const val TYPE_SECTION = 0
        const val TYPE_LINE = 1
    }
}
