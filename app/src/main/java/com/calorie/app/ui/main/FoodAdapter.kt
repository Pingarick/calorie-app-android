package com.calorie.app.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.calorie.app.data.model.FoodItem
import com.calorie.app.databinding.ItemFoodBinding
import java.util.Locale

/** Список продуктов для ручного добавления: название + состав на 100 г. */
class FoodAdapter(private val onClick: (FoodItem) -> Unit) :
    ListAdapter<FoodItem, FoodAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFoodBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemFoodBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(f: FoodItem) {
            b.txtFoodName.text = f.name
            b.txtFoodSub.text =
                "%d ккал · Б %.1f · Ж %.1f · У %.1f (на 100 г)".format(
                    Locale.US, f.kcalPer100, f.proteinPer100, f.fatPer100, f.carbsPer100
                )
            b.root.setOnClickListener { onClick(f) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FoodItem>() {
            override fun areItemsTheSame(a: FoodItem, b: FoodItem) = a.id == b.id
            override fun areContentsTheSame(a: FoodItem, b: FoodItem) = a == b
        }
    }
}
