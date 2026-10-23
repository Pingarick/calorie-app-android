package com.calorie.app.ui.prefs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.calorie.app.R
import com.calorie.app.databinding.ItemKnownPrefBinding
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Список выученных весов: что нравится, что нет и когда готовили в последний раз. */
class KnownPrefsAdapter(
    private val onLike: (KnownPrefRow) -> Unit,
    private val onDislike: (KnownPrefRow) -> Unit,
    private val onForget: (KnownPrefRow) -> Unit
) : RecyclerView.Adapter<KnownPrefsAdapter.Holder>() {

    private var rows: List<KnownPrefRow> = emptyList()

    fun submit(newRows: List<KnownPrefRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemKnownPrefBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(rows[position])

    inner class Holder(private val b: ItemKnownPrefBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: KnownPrefRow) {
            val context = b.root.context
            b.txtDish.text = row.dish.title

            val state = when {
                row.weight >= 0.3 -> context.getString(R.string.known_liked)
                row.weight <= -0.3 -> context.getString(R.string.known_disliked)
                else -> context.getString(R.string.known_neutral)
            }
            val weight = String.format(Locale.US, "%+.2f", row.weight)
            val cooked = row.lastCookedAt?.let { at ->
                val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - at).toInt()
                if (days <= 0) {
                    context.getString(R.string.known_cooked_today)
                } else {
                    context.getString(R.string.known_cooked_days, days)
                }
            } ?: context.getString(R.string.known_never)

            b.txtMeta.text = context.getString(R.string.known_meta, state, weight, cooked)
            b.btnLike.setOnClickListener { onLike(row) }
            b.btnDislike.setOnClickListener { onDislike(row) }
            b.btnForget.setOnClickListener { onForget(row) }
        }
    }
}
