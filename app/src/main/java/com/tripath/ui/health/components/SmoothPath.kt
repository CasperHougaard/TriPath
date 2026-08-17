package com.tripath.ui.health.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.sqrt

/**
 * Appends a centripetal Catmull-Rom spline through [points] to this path. The path's current
 * position must already be at `points.first()` (e.g. via a preceding `moveTo`) — this only draws
 * the curve segments connecting each subsequent point.
 *
 * Centripetal parameterization (as opposed to uniform Catmull-Rom) scales each segment's control
 * points by the actual pixel distance between points, so it stays smooth and loop-free even when
 * points are very unevenly spaced on the x-axis (e.g. a long gap between weigh-ins).
 */
fun Path.curveThrough(points: List<Offset>) {
    if (points.size < 2) return
    if (points.size < 3) {
        lineTo(points[1].x, points[1].y)
        return
    }
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]

        val d1 = centripetalDistance(p0, p1)
        val d2 = centripetalDistance(p1, p2)
        val d3 = centripetalDistance(p2, p3)

        val cp1x = p1.x + (p2.x - p0.x) * (d2 / (3f * (d1 + d2)))
        val cp1y = p1.y + (p2.y - p0.y) * (d2 / (3f * (d1 + d2)))
        val cp2x = p2.x - (p3.x - p1.x) * (d2 / (3f * (d2 + d3)))
        val cp2y = p2.y - (p3.y - p1.y) * (d2 / (3f * (d2 + d3)))

        cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
}

private fun centripetalDistance(a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(sqrt(dx * dx + dy * dy)).coerceAtLeast(0.0001f)
}
