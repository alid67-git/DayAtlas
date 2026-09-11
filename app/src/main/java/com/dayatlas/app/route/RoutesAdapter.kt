package com.dayatlas.app.route

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dayatlas.app.databinding.ItemRouteDayBinding
import java.time.LocalDate

data class RouteDayRow(
    val date: LocalDate,
    val title: String,
    val meta: String,
)

class RoutesAdapter(
    private val onOpen: (LocalDate) -> Unit,
    private val onExport: (LocalDate) -> Unit,
) : RecyclerView.Adapter<RoutesAdapter.ViewHolder>() {

    private val items = mutableListOf<RouteDayRow>()

    fun submit(rows: List<RouteDayRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRouteDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = items[position]
        holder.binding.routeDayTitle.text = row.title
        holder.binding.routeDayMeta.text = row.meta
        holder.binding.root.setOnClickListener { onOpen(row.date) }
        holder.binding.routeExport.setOnClickListener { onExport(row.date) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val binding: ItemRouteDayBinding) : RecyclerView.ViewHolder(binding.root)
}
