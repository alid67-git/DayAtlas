package com.dayatlas.app.route

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dayatlas.app.R
import com.dayatlas.app.databinding.ItemDayStatBinding
import kotlin.math.ceil

/**
 * Day-stat tiles in a **2-2-3** pattern when 7 are visible (6-column grid:
 * four tiles span 3, three span 2). Other counts keep even rows. Long-press
 * drags to reorder within the visible set.
 *
 * Each tile uses a soft tinted background and a centered accent icon for
 * its [DayStatKind]. Height is capped at [R.integer.day_stat_max_visible_rows]
 * so the map below keeps room; with the 2-2-3 layout that cap is 3 rows.
 */
class DayStatsAdapter(
    private val onReordered: (List<DayStatKind>) -> Unit,
) : RecyclerView.Adapter<DayStatsAdapter.ViewHolder>() {

    private val items = mutableListOf<Pair<DayStatKind, String>>()
    private var gridLayoutManager: GridLayoutManager? = null

    fun submit(newItems: List<Pair<DayStatKind, String>>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
        gridLayoutManager?.spanSizeLookup?.invalidateSpanIndexCache()
    }

    fun currentItems(): List<Pair<DayStatKind, String>> = items.toList()

    fun updateValueAt(index: Int, value: String) {
        if (index !in items.indices) return
        val kind = items[index].first
        items[index] = kind to value
        notifyItemChanged(index)
    }

    fun attachTo(recyclerView: RecyclerView, spanCount: Int = GRID_SPAN) {
        val span = spanCount.coerceAtLeast(1)
        val glm = GridLayoutManager(recyclerView.context, span)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                spanSizeFor(position, items.size, span)
        }
        gridLayoutManager = glm
        recyclerView.layoutManager = glm
        recyclerView.isNestedScrollingEnabled = false
        while (recyclerView.itemDecorationCount > 0) {
            recyclerView.removeItemDecorationAt(0)
        }
        ItemTouchHelper(TouchCallback()).attachToRecyclerView(recyclerView)
        capHeightAfterFirstLayout(recyclerView, span)
    }

    private fun capHeightAfterFirstLayout(recyclerView: RecyclerView, span: Int) {
        recyclerView.doOnPreDraw {
            val maxRows = recyclerView.resources.getInteger(R.integer.day_stat_max_visible_rows)
            val rowCount = rowCountFor(items.size, span)
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
        val ctx = holder.binding.root.context
        holder.binding.statLabel.setText(kind.labelRes)
        holder.binding.statValue.text = value
        holder.binding.root.setBackgroundResource(kind.cardBackgroundRes)
        holder.binding.statIcon.setImageResource(kind.iconRes)
        holder.binding.statIcon.setColorFilter(
            ContextCompat.getColor(ctx, kind.iconTintRes),
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
            gridLayoutManager?.spanSizeLookup?.invalidateSpanIndexCache()
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun isLongPressDragEnabled(): Boolean = true

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
        const val GRID_SPAN = 6

        /** 7 → 2-2-3; 6 → 2-2-2; 5 → 2-3; 4 → 2-2; 3 → row of 3; etc. */
        fun spanSizeFor(position: Int, count: Int, gridSpan: Int = GRID_SPAN): Int {
            if (count <= 0 || gridSpan <= 0) return 1
            return when (count) {
                7 -> if (position < 4) 3 else 2
                6, 4, 2 -> 3
                5 -> if (position < 2) 3 else 2
                3 -> 2
                1 -> gridSpan
                else -> 2
            }
        }

        fun rowCountFor(count: Int, gridSpan: Int = GRID_SPAN): Int = when (count) {
            0 -> 0
            1, 2, 3 -> 1
            4, 5, 6 -> 2
            7 -> 3
            else -> ceil(count * 2.0 / gridSpan).toInt().coerceAtLeast(1)
        }
    }
}
