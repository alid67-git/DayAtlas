package com.dayatlas.app.route

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dayatlas.app.R
import com.dayatlas.app.databinding.ItemDayStatBinding

/**
 * 3-column grid of day-stat tiles. Visible tiles only (hidden ones are
 * filtered out before [submit]); long-press drags a tile to reorder within
 * the visible set. Reordering fires [onReordered] with the full (visible-only)
 * order so the caller can persist it.
 */
class DayStatsAdapter(
    private val onReordered: (List<DayStatKind>) -> Unit,
) : RecyclerView.Adapter<DayStatsAdapter.ViewHolder>() {

    private val items = mutableListOf<Pair<DayStatKind, String>>()

    fun submit(newItems: List<Pair<DayStatKind, String>>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun attachTo(recyclerView: RecyclerView) {
        recyclerView.layoutManager = GridLayoutManager(recyclerView.context, 3)
        recyclerView.isNestedScrollingEnabled = false
        ItemTouchHelper(TouchCallback()).attachToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDayStatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (kind, value) = items[position]
        holder.binding.statLabel.setText(kind.labelRes)
        holder.binding.statValue.text = value
        holder.binding.root.setBackgroundResource(
            kind.cardBackgroundRes ?: R.drawable.bg_dash_card,
        )
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val binding: ItemDayStatBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class TouchCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        0,
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            items.add(to, items.removeAt(from))
            notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            onReordered(items.map { it.first })
        }
    }
}
