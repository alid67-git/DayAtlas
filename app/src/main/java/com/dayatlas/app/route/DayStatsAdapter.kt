package com.dayatlas.app.route

import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dayatlas.app.R
import com.dayatlas.app.databinding.ItemDayStatBinding
import kotlin.math.ceil

/**
 * Day-stat tiles.
 *
 * Normal: **2-2-3** on a 6-column grid (pair rows larger; bottom trio
 * compact/centered). Fullscreen: **4+3** on a 12-column grid (two rows,
 * all compact) so the map keeps most of the screen.
 *
 * Typography follows the **slot**, not the kind — dragging into/out of a
 * compact slot rebinds size.
 */
class DayStatsAdapter(
    private val onReordered: (List<DayStatKind>) -> Unit,
) : RecyclerView.Adapter<DayStatsAdapter.ViewHolder>() {

    private val items = mutableListOf<Pair<DayStatKind, String>>()
    private var gridLayoutManager: GridLayoutManager? = null
    private var twoRowLayout = false
    private var boundRecycler: RecyclerView? = null

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

    /** Switch between normal 2-2-3 and fullscreen 4+3. */
    fun setTwoRowLayout(enabled: Boolean) {
        if (twoRowLayout == enabled) return
        twoRowLayout = enabled
        val glm = gridLayoutManager ?: return
        glm.spanCount = activeSpanCount()
        glm.spanSizeLookup.invalidateSpanIndexCache()
        // Drop any height cap from the previous mode so wrap_content remeasures.
        boundRecycler?.layoutParams?.let { lp ->
            if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                boundRecycler?.layoutParams = lp
            }
        }
        notifyDataSetChanged()
        boundRecycler?.let { capHeightAfterFirstLayout(it, glm.spanCount) }
    }

    fun attachTo(recyclerView: RecyclerView, spanCount: Int = GRID_SPAN) {
        boundRecycler = recyclerView
        val span = spanCount.coerceAtLeast(1)
        val glm = GridLayoutManager(recyclerView.context, span)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                spanSizeFor(position, items.size, glm.spanCount, twoRowLayout)
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

    private fun activeSpanCount(): Int =
        if (twoRowLayout) GRID_SPAN_TWO_ROW else GRID_SPAN

    private fun capHeightAfterFirstLayout(recyclerView: RecyclerView, span: Int) {
        recyclerView.doOnPreDraw {
            val maxRows = if (twoRowLayout) {
                2
            } else {
                recyclerView.resources.getInteger(R.integer.day_stat_max_visible_rows)
            }
            val rowCount = rowCountFor(items.size, span, twoRowLayout)
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
        val compact = isCompactSlot(position, items.size, activeSpanCount(), twoRowLayout)
        applySlotStyle(holder.binding, compact)
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
            gridLayoutManager?.spanSizeLookup?.invalidateSpanIndexCache()
            notifyDataSetChanged()
        }
    }

    companion object {
        private const val DRAG_ELEVATION_PX = 16f
        const val GRID_SPAN = 6
        /** 12-col grid: four tiles ×3 + three tiles ×4 = two rows. */
        const val GRID_SPAN_TWO_ROW = 12

        fun isCompactSlot(
            position: Int,
            count: Int,
            gridSpan: Int = GRID_SPAN,
            twoRow: Boolean = false,
        ): Boolean {
            if (twoRow) return true
            return spanSizeFor(position, count, gridSpan, twoRow = false) < 3
        }

        private fun applySlotStyle(binding: ItemDayStatBinding, compact: Boolean) {
            val ctx = binding.root.context
            val textCol = binding.statLabel.parent as LinearLayout
            val iconLp = binding.statIcon.layoutParams
            val density = ctx.resources.displayMetrics.density
            if (compact) {
                textCol.gravity = Gravity.CENTER_HORIZONTAL
                binding.statLabel.gravity = Gravity.CENTER
                binding.statValue.gravity = Gravity.CENTER
                binding.statLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                binding.statValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                iconLp.width = (14 * density).toInt()
                iconLp.height = (14 * density).toInt()
                (binding.statIcon.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd =
                    (4 * density).toInt()
            } else {
                textCol.gravity = Gravity.START
                binding.statLabel.gravity = Gravity.START
                binding.statValue.gravity = Gravity.START
                binding.statLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                binding.statValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                iconLp.width = (16 * density).toInt()
                iconLp.height = (16 * density).toInt()
                (binding.statIcon.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd =
                    (6 * density).toInt()
            }
            binding.statIcon.layoutParams = iconLp
        }

        /**
         * Normal (span 6): 7 → 2-2-3.
         * Two-row (span 12): 7 → 4+3.
         */
        fun spanSizeFor(
            position: Int,
            count: Int,
            gridSpan: Int = GRID_SPAN,
            twoRow: Boolean = false,
        ): Int {
            if (count <= 0 || gridSpan <= 0) return 1
            if (twoRow && gridSpan == GRID_SPAN_TWO_ROW) {
                return when (count) {
                    7 -> if (position < 4) 3 else 4
                    6, 4, 2 -> gridSpan / 2
                    5 -> if (position < 2) 6 else 4
                    3 -> 4
                    1 -> gridSpan
                    else -> 3
                }
            }
            return when (count) {
                7 -> if (position < 4) 3 else 2
                6, 4, 2 -> 3
                5 -> if (position < 2) 3 else 2
                3 -> 2
                1 -> gridSpan
                else -> 2
            }
        }

        fun rowCountFor(
            count: Int,
            gridSpan: Int = GRID_SPAN,
            twoRow: Boolean = false,
        ): Int {
            if (twoRow && count == 7) return 2
            return when (count) {
                0 -> 0
                1, 2, 3 -> 1
                4, 5, 6 -> 2
                7 -> 3
                else -> ceil(count * 2.0 / gridSpan.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            }
        }
    }
}
