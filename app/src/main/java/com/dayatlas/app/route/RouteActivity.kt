package com.dayatlas.app.route

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.dayatlas.app.R
import com.dayatlas.app.data.DayStore
import com.dayatlas.app.data.DayTitle
import com.dayatlas.app.databinding.ActivityRouteBinding
import java.time.LocalDate

/**
 * A simple, dependency-free day-by-day route sketch: no basemap tiles, just
 * the recorded points connected in order and scaled to fit (see
 * RoutePathView). Opens on today by default; previous/next day arrows browse
 * other recorded days.
 */
class RouteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRouteBinding
    private val store by lazy { DayStore(this) }
    private lateinit var shownDate: LocalDate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRouteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val extraDate = intent.getStringExtra(EXTRA_DATE)
        shownDate = extraDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: DayTitle.localToday()

        binding.previousDay.setOnClickListener {
            shownDate = shownDate.minusDays(1)
            refresh()
        }
        binding.nextDay.setOnClickListener {
            if (shownDate < DayTitle.localToday()) {
                shownDate = shownDate.plusDays(1)
                refresh()
            }
        }

        refresh()
    }

    private fun refresh() {
        val record = store.load(DayTitle.iso(shownDate))
        binding.dayTitle.text = DayTitle.format(shownDate)
        binding.nextDay.isEnabled = shownDate < DayTitle.localToday()

        val hasPoints = record != null && record.points.isNotEmpty()
        binding.routePath.setPoints(record?.points.orEmpty())
        binding.emptyState.visibility = if (hasPoints) View.GONE else View.VISIBLE
        binding.distance.visibility = if (hasPoints) View.VISIBLE else View.GONE
        binding.pointCount.visibility = if (hasPoints) View.VISIBLE else View.GONE
        if (hasPoints && record != null) {
            binding.distance.text = DayTitle.formatDistance(record.distanceMeters)
            binding.pointCount.text = getString(R.string.route_point_count, record.points.size)
        }
    }

    companion object {
        private const val EXTRA_DATE = "date"

        fun start(context: Context, dateIso: String? = null) {
            context.startActivity(
                Intent(context, RouteActivity::class.java).apply {
                    if (dateIso != null) putExtra(EXTRA_DATE, dateIso)
                },
            )
        }
    }
}
