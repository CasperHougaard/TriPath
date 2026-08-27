package com.tripath.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripath.domain.strain.SessionStrain
import com.tripath.domain.strain.StrainChannel
import com.tripath.ui.components.charts.ChartLegend
import com.tripath.ui.components.charts.ChartLegendEntry
import com.tripath.ui.health.components.ChartSeries
import com.tripath.ui.health.components.MultiSeriesLineChart
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.toColor
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * What one session cost, per tissue, and how that cost decays away.
 *
 * ## Why this belongs on a workout rather than only on the freshness screen
 * The freshness bars are an aggregate: they say the legs are at 42% without saying what put them
 * there. Standing on a single session — the one the athlete just opened, and remembers — is the only
 * place the model can be checked against how a session actually felt. A long easy ride reading as
 * heavy muscular and near-zero impact is either obviously right or obviously wrong, and either way
 * the athlete learns something about the numbers they are being shown elsewhere.
 *
 * ## Curves, not a countdown
 * Each channel clears on its own clock, so the same session leaves the systemic cost gone in three
 * days and the impact cost lingering for nine. A single "recovered by" date would have to pick one
 * and hide the rest; four curves show why the answer depends on what is being asked next.
 */
@Composable
fun SessionStrainCard(
    strain: SessionStrain,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier
) {
    val channels = strain.loadedChannels
    if (channels.isEmpty()) return

    val sessionMillis = strain.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val peak = channels.maxOf { strain.added[it] }
    val curve = strain.decayCurve()

    val series = channels.map { channel ->
        ChartSeries(
            label = channel.label,
            color = channel.toColor(),
            points = curve.map { (dayOffset, residual) ->
                sessionMillis + (dayOffset * MILLIS_PER_DAY).toLong() to residual[channel]
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = "Strain & recovery",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            channels.forEach { channel ->
                ChannelCostRow(channel, strain, today)
            }

            MultiSeriesLineChart(
                series = series,
                height = 160.dp,
                // One shared scale, anchored at zero: these are four slices of one session's cost,
                // and their relative sizes are the entire point. Self-normalizing would draw the
                // trivial channel and the dominant one as the same line.
                valueRange = 0.0..peak
            )

            ChartLegend(
                entries = channels.map { channel ->
                    ChartLegendEntry(label = channel.label, color = channel.toColor())
                }
            )

            Text(
                text = if (strain.fromLiftDetail) {
                    "Scored from LiftPath's sets, so the split follows the muscles that did the work."
                } else {
                    "Scored from this session's load and distance. Heights are in the strain model's " +
                        "own units — read the shapes, not the numbers."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One channel's line: what the session put on it, how long that takes to clear, and — for a session
 * recent enough for the answer to be anything but "none" — how much is still there.
 */
@Composable
private fun ChannelCostRow(
    channel: StrainChannel,
    strain: SessionStrain,
    today: LocalDate
) {
    val remaining = strain.remainingFraction(channel, today)
    val clearsIn = strain.daysUntilSpent(channel)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.padding(end = Spacing.md)) {
            Text(
                text = channel.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = clearsIn?.let { "clears in ~${formatDays(it)}" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (remaining >= SHOW_REMAINING_ABOVE) {
                "${(remaining * 100).roundToInt()}% still on you"
            } else {
                "spent"
            },
            style = MaterialTheme.typography.labelLarge,
            color = channel.toColor()
        )
    }
}

private fun formatDays(days: Double): String =
    if (days < 1.0) "${(days * 24).roundToInt()}h" else "%.1fd".format(days)

/** Below one percent, "still on you" is noise dressed as information. */
private const val SHOW_REMAINING_ABOVE = 0.01

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000.0
