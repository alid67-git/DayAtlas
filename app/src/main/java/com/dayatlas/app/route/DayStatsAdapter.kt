package com.dayatlas.app.route

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dayatlas.app.R
import com.dayatlas.app.databinding.ItemDayStatBinding
import kotlin.math.ceil

/**
 * 3-column grid of day-stat tiles. Visible tiles only (hidden ones are
 * filtered out before [submit]); long-press drags a tile to reorder within
 * the visible set. The last incomplete row is horizontally centered.
 *
 * Height is capped at R.integer.day_stat_max_visible_rows rows so a long
 * tile list can never starve the map below it of space — a 3rd+ row used to
 * squeeze the map pane down to almost nothing, which is what caused the
 * blank/striped map regression to come back. Extra rows beyond the cap
 * scroll internally instead of growing the grid.
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

    fun attachTo(recyclerView: RecyclerView, spanCount: Int = 3) {
        val span = spanCount.coerceAtLeast(1)
        recyclerView.layoutManager = GridLayoutManager(recyclerView.context, span)
        recyclerView.isNestedScrollingEnabled = false
        while (recyclerView.itemDecorationCount > 0) {
            recyclerView.removeItemDecorationAt(0)
        }
        recyclerView.addItemDecoration(CenterLastRowDecoration(span))
        ItemTouchHelper(TouchCallback()).attachToRecyclerView(recyclerView)
        capHeightAfterFirstLayout(recyclerView, span)
    }

    /** Measures one row's real height (font scale / density can move it) once
     * the grid has laid out its first rows, then locks in a max-row height
     * if there are more rows than that so the rest scroll internally. The
     * row cap itself comes from [R.integer.day_stat_max_visible_rows], which
     * is lower on short/small screens (values-h600dp) so the map below
     * always keeps most of the available space. */
    private fun capHeightAfterFirstLayout(recyclerView: RecyclerView, span: Int) {
        recyclerView.doOnPreDraw {
            val maxRows = recyclerView.resources.getInteger(R.integer.day_stat_max_visible_rows)
            val rowCount = ceil(itemCount.toDouble() / span).toInt()
            if (rowCount <= maxRows) return@doOnPreDraw
            val rowHeight = recyclerView.getChildAt(0)?.height ?: return@doOnPreDraw
            if (rowHeight <= 0) return@doOnPreDraw
            val lp = recyclerView.layoutParams ?: return@doOnPreDraw
            lp.height = rowHeight * maxRows
            recyclerView.layoutParams = lp
        }
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

        override fun isLongPressDragEnabled(): Boolean = true

        // Visible lift while dragging - otherwise a long-press-drag gives no
        // feedback that the tile is actually grabbed.
        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.let {
                    it.animate().scaleX(1.06f).scaleY(1.06f).alpha(0.9f).setDuration(120).start()
                    it.elevation = DRAG_ELEVATION_PX
                }
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start()
            viewHolder.itemView.elevation = 0f
            onReordered(items.map { it.first })
        }
    }

    companion object {
        private const val DRAG_ELEVATION_PX = 16f
    }
}
