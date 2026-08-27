package com.tripath.ui.coach.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tripath.domain.strain.StrainSource
import com.tripath.domain.strain.StrainTrend
import com.tripath.ui.components.musclemap.MuscleMap
import com.tripath.ui.components.musclemap.MuscleMapAssets
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import com.tripath.ui.theme.freshnessColor
import com.tripath.ui.theme.muscleLoadColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Per-muscle load on the body diagram, for any day in the recent window.
 *
 * ## Why a diagram and not ten more bars
 * Ten labelled bars are precise and almost unreadable — the athlete has to hold a mental picture of
 * where "Hamstrings & glutes" is relative to "Hips" to notice that one side of the body is doing all
 * the work. The diagram answers that at a glance, which is the only question a per-muscle breakdown
 * is really for. The bars stay underneath because the diagram cannot give an exact figure.
 *
 * ## Why a day selector
 * A single day's map is a snapshot, and muscle load is the slowest-moving thing the app tracks — a
 * group hit hard on Monday is still visibly loaded on Wednesday. Stepping day by day is what turns
 * the diagram into a record of a training week: load lands, spreads, and clears.
 *
 * Days come from a [StrainTrend] that was computed once, so changing the selection costs a bitmap
 * composite and no model work at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleMapCard(
    trend: StrainTrend,
    source: StrainSource = StrainSource.BOTH,
    modifier: Modifier = Modifier
) {
    val selectableDays = remember(trend) {
        trend.days.takeLast(SELECTABLE_DAYS).reversed()
    }
    if (selectableDays.isEmpty()) {
        // Only reachable before the trend has finished building — the model has not been asked
        // about any day yet, which is not the same as it having nothing to say.
        StrainEmptyCard("Working out per-muscle load…", modifier)
        return
    }

    var selectedDate by remember(trend) { mutableStateOf(selectableDays.first().date) }
    val selected = selectableDays.firstOrNull { it.date == selectedDate } ?: selectableDays.first()
    val freshnessByGroup = selected.state.muscleFreshness

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(selectableDays, key = { it.date.toString() }) { day ->
                    FilterChip(
                        selected = day.date == selected.date,
                        onClick = { selectedDate = day.date },
                        label = { Text(day.date.chipLabel()) }
                    )
                }
            }

            if (freshnessByGroup.isEmpty()) {
                Text(
                    text = emptyDayMessage(source, selected.date.chipLabel().lowercase()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            MuscleMap(freshnessByGroup = freshnessByGroup)

            MuscleLoadScale()

            MuscleGroupBars(freshnessByGroup)
        }
    }
}

/**
 * The load ramp, as a swatch strip.
 *
 * Needed because the map's single-hue ramp is only self-explanatory in one direction — darker is
 * more — and says nothing about where the ends are.
 */
@Composable
private fun MuscleLoadScale(modifier: Modifier = Modifier) {
    val colors = TriPathTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        ) {
            // Sampled across the freshness scale rather than a gradient brush, so the strip is
            // literally the same function the diagram is painted with.
            listOf(100, 80, 60, 40, 20, 0).forEach { freshness ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(muscleLoadColor(freshness, colors))
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Clear or never logged",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Fully loaded",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The exact per-group figures, for the precision the diagram cannot give. */
@Composable
private fun MuscleGroupBars(
    freshnessByGroup: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val colors = TriPathTheme.colors
    val groups = MuscleMapAssets.displayableGroups.filter { freshnessByGroup.containsKey(it) }
    if (groups.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        groups.forEach { group ->
            val freshness = freshnessByGroup.getValue(group)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = group,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$freshness% fresh",
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
                            .fillMaxWidth(freshness / 100f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(freshnessColor(freshness, colors))
                    )
                }
            }
        }
    }
}

/**
 * Why a day has nothing to paint, in the terms of whichever source is selected.
 *
 * Worth spelling out per source rather than saying "no data": under [StrainSource.LIFT_PATH] an
 * empty body means the athlete did not lift, which is a fact about their week, while under
 * [StrainSource.TRI_PATH] it means Health Connect has no session for the day — and if they lifted
 * without their watch running, those are two very different statements about the same blank diagram.
 */
private fun emptyDayMessage(source: StrainSource, day: String): String = when (source) {
    StrainSource.LIFT_PATH ->
        "No LiftPath sets behind $day — the diagram only knows what sets it was sent."
    StrainSource.TRI_PATH ->
        "No Health Connect session behind $day, so there is no muscular load to place."
    StrainSource.BOTH ->
        "Nothing behind $day from either source — no LiftPath sets and no synced session."
}

private val chipFormatter = DateTimeFormatter.ofPattern("EEE d")

private fun LocalDate.chipLabel(): String = when (this) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> format(chipFormatter)
}

/**
 * Two weeks of selectable days. Past that even the slowest channel has decayed to a few percent of
 * a session's cost, so the map would be showing the same near-fresh body over and over.
 */
private const val SELECTABLE_DAYS = 14
