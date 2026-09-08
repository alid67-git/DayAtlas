package com.dayatlas.app.route

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.dayatlas.app.data.TrackPoint
import kotlin.math.cos
import kotlin.math.min

/**
 * Draws a day's recorded points as a simple line sketch, scaled to fit the
 * view - no basemap, no tiles, no network. Longitude is compressed by
 * cos(avg latitude) so the shape isn't stretched east-west; this is a local
 * flat approximation, fine for a single day's short walking-scale path.
 */
class RoutePathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var points: List<TrackPoint> = emptyList()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#1F6F5B")
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1F6F5B")
    }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#B3261E")
    }

    fun setPoints(newPoints: List<TrackPoint>) {
        points = newPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return
        if (points.size == 1) {
            canvas.drawCircle(width / 2f, height / 2f, 12f, startPaint)
            return
        }

        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val avgLatRad = Math.toRadians((minLat + maxLat) / 2.0)
        val lonScale = cos(avgLatRad).coerceAtLeast(0.1)

        val xs = points.map { it.lon * lonScale }
        val ys = points.map { -it.lat }
        val minX = xs.min()
        val minY = ys.min()
        val spanX = (xs.max() - minX).let { if (it < 1e-9) 1.0 else it }
        val spanY = (ys.max() - minY).let { if (it < 1e-9) 1.0 else it }

        val padding = 32f
        val drawW = width - padding * 2
        val drawH = height - padding * 2
        val scale = min(drawW / spanX, drawH / spanY).toFloat()
        val offsetX = padding + (drawW - (spanX * scale).toFloat()) / 2f
        val offsetY = padding + (drawH - (spanY * scale).toFloat()) / 2f

        fun screenX(lon: Double) = offsetX + ((lon * lonScale - minX) * scale).toFloat()
        fun screenY(lat: Double) = offsetY + ((-lat - minY) * scale).toFloat()

        val path = Path()
        points.forEachIndexed { i, p ->
            val sx = screenX(p.lon)
            val sy = screenY(p.lat)
            if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
        }
        canvas.drawPath(path, linePaint)

        val first = points.first()
        val last = points.last()
        canvas.drawCircle(screenX(first.lon), screenY(first.lat), 12f, startPaint)
        canvas.drawCircle(screenX(last.lon), screenY(last.lat), 12f, endPaint)
    }
}
