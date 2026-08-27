package com.tripath.ui.dashboard.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripath.domain.strain.ChannelState
import com.tripath.domain.strain.ReadinessAssessment
import com.tripath.domain.strain.ReadinessBand
import com.tripath.ui.model.FormStatus
import com.tripath.ui.theme.Spacing
import java.time.LocalDate
import kotlin.math.roundToInt

private val ReadinessFresh = Color(0xFF4CAF50)
private val ReadinessReady = Color(0xFF8BC34A)
private val ReadinessCompromised = Color(0xFFFFA726)
private val ReadinessDepleted = Color(0xFFE53935)

private fun ReadinessBand.tileColor(): Color = when (this) {
    ReadinessBand.FRESH -> ReadinessFresh
    ReadinessBand.READY -> ReadinessReady
    ReadinessBand.COMPROMISED -> ReadinessCompromised
    ReadinessBand.DEPLETED -> ReadinessDepleted
}

private fun freshnessTileColor(freshness: Int): Color = when {
    freshness >= 80 -> ReadinessFresh
    freshness >= 55 -> ReadinessReady
    freshness >= 35 -> ReadinessCompromised
    else -> ReadinessDepleted
}

private fun formatHoursToClear(hours: Int): String =
    if (hours >= 24) "${(hours / 24.0).roundToInt()}d" else "${hours}h"

/**
 * The dashboard's single daily-status tile: training load (Form/Fitness/Fatigue), readiness score
 * with a 14-day trend, and the tightest regional recovery bars, all in one card. The full breakdown
 * (drivers, per-discipline verdicts) lives on the Coach screen this tile links to.
 *
 * Readiness needs Smart Planning on and a profile to compute, so [assessment] may be null — the
 * Form/Fitness/Fatigue figures degrade gracefully on their own since they come from the workout
 * log directly, independent of readiness.
 */
@Composable
fun DashboardStatusTile(
    formStatus: FormStatus,
    tsb: Double,
    ctl: Double,
    atl: Double,
    assessment: ReadinessAssessment?,
    history: List<Pair<LocalDate, Int>>,
    /**
     * Freshness over the same window for whichever channel is currently most loaded.
     *
     * A second trend rather than a second line on the readiness sparkline: both are 0–100, but each
     * is drawn against its own range, and overlaying two differently-scaled lines in a 56dp box with
     * no axis invites exactly the misreading a tile has no room to correct.
     */
    mostLoadedTrend: List<Pair<LocalDate, Int>> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (formStatus) {
        FormStatus.FRESHNESS -> ReadinessFresh
        FormStatus.OPTIMAL -> MaterialTheme.colorScheme.primary
        FormStatus.OVERREACHING -> ReadinessDepleted
    }
    val statusText = when (formStatus) {
        FormStatus.FRESHNESS -> "Fresh"
        FormStatus.OPTIMAL -> "Optimal"
        FormStatus.OVERREACHING -> "Tired"
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "TODAY'S STATUS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (assessment != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${assessment.score}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = assessment.band.tileColor()
                            )
                            Column(modifier = Modifier.padding(start = Spacing.md)) {
                                Text(
                                    text = assessment.band.label,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = assessment.action.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    Text(
                        text = "Form (TSB): ${String.format("%+.0f", tsb)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                )

                Column(
                    modifier = Modifier
                        .weight(0.8f)
                        .padding(start = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    StatusMetricRow(label = "Fitness", value = String.format("%.0f", ctl), color = MaterialTheme.colorScheme.primary)
                    StatusMetricRow(label = "Fatigue", value = String.format("%.0f", atl), color = MaterialTheme.colorScheme.secondary)
                }
            }

            if (assessment != null && history.size >= 2) {
                Sparkline(
                    history = history,
                    lineColor = assessment.band.tileColor(),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                )
            }

            // The channel in the deepest hole, over time. The bars below say where it is; this says
            // whether it is on the way out of the hole or on the way in, which is the part that
            // decides whether today's session is a good idea.
            val mostLoaded = assessment?.strain?.mostLoaded
            if (mostLoaded != null && mostLoadedTrend.size >= 2) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = mostLoaded.channel.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = mostLoaded.hoursToFresh
                                ?.let { "${mostLoaded.freshness}% · ${formatHoursToClear(it)} to clear" }
                                ?: "${mostLoaded.freshness}% · fresh",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Sparkline(
                        history = mostLoadedTrend,
                        lineColor = freshnessTileColor(mostLoaded.freshness),
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    )
                }
            }

            if (assessment != null && assessment.strain.hasData) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    assessment.strain.channels.values.sortedBy { it.freshness }.take(3).forEach { channel ->
                        CondensedChannelBar(channel)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMetricRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * A bare 0–100 trend line, scaled to its own range so a ten-point swing is visible in 32dp.
 *
 * Used for both readiness and channel freshness, which is why it is named for neither.
 */
@Composable
private fun Sparkline(
    history: List<Pair<LocalDate, Int>>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val min = history.minOf { it.second }.toFloat()
        val max = history.maxOf { it.second }.toFloat()
        val range = (max - min).coerceAtLeast(5f)

        val path = Path()
        history.forEachIndexed { index, (_, score) ->
            val x = (width / (history.size - 1).coerceAtLeast(1)) * index
            val normalized = ((score - min) / range).coerceIn(0f, 1f)
            val y = height - (height * normalized)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun CondensedChannelBar(state: ChannelState) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = state.channel.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${state.freshness}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.freshness / 100f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(freshnessTileColor(state.freshness))
            )
        }
    }
}
