package com.tripath.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.tripath.data.model.RoutePoint

@Composable
fun RouteViewer(
    route: List<RoutePoint>,
    modifier: Modifier = Modifier,
    pathColor: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Float = 5f
) {
    if (route.isEmpty()) return

    // Calculate bounds
    val minLat = remember(route) { route.minOf { it.lat } }
    val maxLat = remember(route) { route.maxOf { it.lat } }
    val minLon = remember(route) { route.minOf { it.lon } }
    val maxLon = remember(route) { route.maxOf { it.lon } }

    val latRange = maxLat - minLat
    val lonRange = maxLon - minLon

    // Avoid division by zero
    if (latRange == 0.0 && lonRange == 0.0) return

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val padding = 20.dp.toPx()

            val effectiveWidth = width - (padding * 2)
            val effectiveHeight = height - (padding * 2)

            // Calculate scaling factors to fit the route within the canvas
            // We want to maintain aspect ratio, but also fill as much as possible
            // Note: Latitude scaling depends on actual latitude, but for small areas linear is fine
            // Simple linear projection (Mercator-ish would be better for large areas, but this is fine for workouts)
            
            // Determine scale to fit
            val scaleX = if (lonRange > 0) effectiveWidth / lonRange else 0.0
            val scaleY = if (latRange > 0) effectiveHeight / latRange else 0.0
            
            // Use the smaller scale to maintain aspect ratio
            val scale = if (scaleX > 0 && scaleY > 0) minOf(scaleX, scaleY) else maxOf(scaleX, scaleY)

            // Center the route
            val scaledWidth = lonRange * scale
            val scaledHeight = latRange * scale
            val offsetX = padding + (effectiveWidth - scaledWidth) / 2
            val offsetY = padding + (effectiveHeight - scaledHeight) / 2

            val path = Path()

            route.forEachIndexed { index, point ->
                // Map lat/lon to x/y
                // Longitude -> X (Left to Right)
                // Latitude -> Y (Top to Bottom, so we invert it because higher lat is "up")
                val x = (offsetX + ((point.lon - minLon) * scale)).toFloat()
                val y = (offsetY + ((maxLat - point.lat) * scale)).toFloat() // Invert Y

                if (index == 0) {
                    path.moveTo(x, y)
                    // Draw Start Marker (Green Circle)
                    drawCircle(
                        color = Color.Green,
                        radius = 8.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                } else {
                    path.lineTo(x, y)
                }

                // Draw End Marker (Red Square-ish)
                if (index == route.lastIndex) {
                    drawCircle(
                        color = Color.Red,
                        radius = 8.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }

            drawPath(
                path = path,
                color = pathColor,
                style = Stroke(
                    width = strokeWidth.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

