package com.tripath.ui.coach.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripath.domain.strain.ChannelState
import com.tripath.domain.strain.DisciplineVerdict
import com.tripath.domain.strain.ReadinessAction
import com.tripath.domain.strain.ReadinessAssessment
import com.tripath.domain.strain.ReadinessBand
import com.tripath.domain.strain.StrainChannel
import com.tripath.ui.theme.Spacing
import kotlin.math.roundToInt

private val Fresh = Color(0xFF4CAF50)
private val Ready = Color(0xFF8BC34A)
private val Compromised = Color(0xFFFFA726)
private val Depleted = Color(0xFFE53935)

private fun ReadinessBand.color(): Color = when (this) {
    ReadinessBand.FRESH -> Fresh
    ReadinessBand.READY -> Ready
    ReadinessBand.COMPROMISED -> Compromised
    ReadinessBand.DEPLETED -> Depleted
}

private fun freshnessColor(freshness: Int): Color = when {
    freshness >= 80 -> Fresh
    freshness >= 55 -> Ready
    freshness >= 35 -> Compromised
    else -> Depleted
}

/**
 * The readiness verdict, with the reasoning visible.
 *
 * Deliberately not a single dial. A score on its own is unactionable — it cannot say whether the
 * problem is last night's sleep or Sunday's long run, nor whether a swim would be fine anyway. The
 * bars and the driver list are the point; the number is just the headline.
 */
@Composable
fun ReadinessAssessmentCard(
    assessment: ReadinessAssessment?,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (assessment == null) return

    val cardModifier = modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

    if (onClick != null) {
        Card(onClick = onClick, modifier = cardModifier, colors = colors) {
            ReadinessAssessmentContent(assessment)
        }
    } else {
        Card(modifier = cardModifier, colors = colors) {
            ReadinessAssessmentContent(assessment)
        }
    }
}

@Composable
private fun ReadinessAssessmentContent(assessment: ReadinessAssessment) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${assessment.score}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = assessment.band.color()
                )
                Column(modifier = Modifier.padding(start = Spacing.md)) {
                    Text(
                        text = assessment.band.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = assessment.action.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (assessment.guidance.isNotBlank()) {
                Text(
                    text = assessment.guidance,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Per-channel bars. Four rather than one because they disagree usefully: legs can be
            // wrecked while the upper body is untouched, and that changes what to do today.
            if (assessment.strain.hasData) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StrainChannel.entries.forEach { channel ->
                        assessment.strain[channel]?.let { ChannelBar(it) }
                    }
                }
            }

            // Why the score is what it is. Without this it is another number to distrust.
            val negativeDrivers = assessment.drivers.filter { !it.isPositive }.take(3)
            if (negativeDrivers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    negativeDrivers.forEach { driver ->
                        Text(
                            text = "• ${driver.detail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (assessment.disciplineVerdicts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    assessment.disciplineVerdicts.forEach { DisciplineRow(it) }
                }
            }

            assessment.weeklyLoadRampPct?.let { ramp ->
                Text(
                    // Explicitly labelled: a recent-to-chronic load ratio is widely quoted as an
                    // injury predictor and that claim has not held up. Shown so a jump is visible,
                    // never used to score or to gate.
                    text = "Load ${if (ramp >= 0) "+" else ""}${ramp.roundToInt()}% vs last week " +
                        "(for information only)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (assessment.isProjected) {
                Text(
                    text = "Projected from planned training — sleep, HRV and fuelling assumed normal.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
}

@Composable
private fun ChannelBar(state: ChannelState) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = state.channel.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = state.hoursToFresh
                    ?.let { "${state.freshness}% · ${formatHours(it)} to clear" }
                    ?: "${state.freshness}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(state.freshness / 100f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(freshnessColor(state.freshness))
            )
        }
    }
}

@Composable
private fun DisciplineRow(verdict: DisciplineVerdict) {
    val color = when (verdict.action) {
        ReadinessAction.GO -> Fresh
        ReadinessAction.MODERATE -> Ready
        ReadinessAction.EASY -> Compromised
        ReadinessAction.REST -> Depleted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = "${verdict.discipline.name.lowercase().replaceFirstChar { it.uppercase() }} — " +
                verdict.action.label.lowercase(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Spacing.sm)
        )
    }
}

private fun formatHours(hours: Int): String =
    if (hours >= 24) "${(hours / 24.0).roundToInt()}d" else "${hours}h"
