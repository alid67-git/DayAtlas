package com.dayatlas.app.route

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dayatlas.app.R
import com.dayatlas.app.databinding.ItemDayStatBinding

/**
 * 3-column grid of day-stat tiles. Visible tiles only (hidden ones are
 * filtered out before [submit]); long-press drags a tile to reorder within
 * the visible set. The last incomplete row is horizontally centered.
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

    fun currentItems(): List<Pair<DayStatKind, String>> = items.toList()

    fun updateValueAt(index: Int, value: String) {
        if (index !in items.indices) return
        val kind = items[index].first
        items[index] = kind to value
        notifyItemChanged(index)
    }

    fun attachTo(recyclerView: RecyclerView) {
        val span = 3
        recyclerView.layoutManager = GridLayoutManager(recyclerView.context, span)
        recyclerView.isNestedScrollingEnabled = false
        while (recyclerView.itemDecorationCount > 0) {
            recyclerView.removeItemDecorationAt(0)
        }
        recyclerView.addItemDecoration(CenterLastRowDecoration(span))
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
        holder.binding.root.setBackgroundResource(R.drawable.bg_dash_card)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val binding: ItemDayStatBinding) : RecyclerView.ViewHolder(binding.root)

    /** Shifts the last incomplete row so its tiles sit centered in the grid. */
    private class CenterLastRowDecoration(private val spanCount: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val pos = parent.getChildAdapterPosition(view)
            if (pos == RecyclerView.NO_POSITION) return
            val count = state.itemCount
            if (count == 0 || spanCount <= 0) return
            val remainder = count % spanCount
            if (remainder == 0) return
            val firstOfLast = count - remainder
            if (pos != firstOfLast) return
            val totalWidth = parent.width - parent.paddingLeft - parent.paddingRight
            if (totalWidth <= 0) return
            val empty = spanCount - remainder
            outRect.left = (empty * (totalWidth / spanCount)) / 2
        }
    }

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
