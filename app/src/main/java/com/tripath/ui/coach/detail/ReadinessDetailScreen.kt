package com.tripath.ui.coach.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.domain.strain.LiftContributionDay
import com.tripath.domain.strain.ReadinessAssessment
import com.tripath.domain.strain.ReadinessDriver
import com.tripath.domain.strain.StrainChannel
import com.tripath.domain.strain.StrainSource
import com.tripath.ui.coach.detail.components.ChannelFreshnessCard
import com.tripath.ui.coach.detail.components.DailyLoadCard
import com.tripath.domain.health.DailyNutritionTarget
import com.tripath.ui.coach.detail.components.MuscleMapCard
import com.tripath.ui.coach.detail.components.StrainSourceSelector
import com.tripath.ui.coach.detail.components.StrainTrendWindow
import com.tripath.ui.coach.detail.components.StrainWindowSelector
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import com.tripath.ui.theme.freshnessColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Everything behind the Coach tab's freshness card, laid flat: every signal that fed the score
 * (not just the top few negative ones the card shows), the strain model's history and its state
 * right now, and — because it is the one source an athlete cannot see for themselves elsewhere in
 * the app — the LiftPath sessions and muscle groups driving the regional-load numbers.
 *
 * The order is causal rather than by importance: what the training put in, what is left of it, then
 * where that leaves each tissue today.
 *
 * The source chips at the top rebuild all of it from one data source or both — see
 * [StrainSourceSelector] for why that separation is worth the screen space here and nowhere else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessDetailScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ReadinessDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Freshness") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Above the spinner and the empty state both, so switching source stays possible while
            // the model is working and when the selected source has nothing to say.
            StrainSourceSelector(
                selected = state.source,
                onSelect = viewModel::onSourceSelected
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            val assessment = state.assessment
            if (assessment == null) {
                Text(
                    text = "No readiness data yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            ScoreHeader(assessment)

            SectionHeader(
                title = "Every signal",
                subtitle = "What moved today's score, in order of impact"
            )
            SignalsCard(assessment.drivers)

            if (state.strainTrend.days.isNotEmpty()) {
                var window by remember { mutableStateOf(StrainTrendWindow.MONTH) }
                val windowed = remember(state.strainTrend, window) {
                    state.strainTrend.lastDays(window.days)
                }

                SectionHeader(
                    title = "Over time",
                    subtitle = "Load in, freshness out — the same window for both"
                )
                StrainWindowSelector(selected = window, onSelect = { window = it })
                DailyLoadCard(windowed)
                ChannelFreshnessCard(windowed)
            }

            assessment.fuelTarget?.let { target ->
                SectionHeader(
                    title = "Fuelling",
                    subtitle = "What today's training asks you to eat"
                )
                FuellingCard(assessment, target)
            }

            SectionHeader(
                title = "Regional load",
                subtitle = "Decayed strain per tissue, against your own baseline"
            )
            RegionalLoadCard(assessment)

            SectionHeader(
                title = when (state.source) {
                    StrainSource.LIFT_PATH -> "From LiftPath"
                    StrainSource.TRI_PATH -> "From TriPath"
                    StrainSource.BOTH -> "From LiftPath and TriPath"
                },
                subtitle = "Per-muscle load behind the regional-load channels"
            )
            MuscleMapCard(trend = state.strainTrend, source = state.source)

            // LiftPath data by definition, so it has no place in the Health-Connect-only view.
            if (state.source.includesLiftDetail) {
                SectionHeader(
                    title = "Recent lifting sessions",
                    subtitle = "Still contributing to today's regional load"
                )
                LiftSessionsCard(state.liftContributions)
            }
        }
    }
}

@Composable
private fun ScoreHeader(assessment: ReadinessAssessment) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${assessment.score}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = freshnessColor(assessment.score)
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
    }
}

@Composable
private fun SignalsCard(drivers: List<ReadinessDriver>) {
    if (drivers.isEmpty()) {
        EmptyStateCard("Not enough logged data to break the score down yet.")
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            drivers.forEach { driver ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = driver.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = (if (driver.isPositive) "+" else "") +
                                driver.impact.roundToInt().toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (driver.isPositive) TriPathTheme.colors.positive
                            else TriPathTheme.colors.negative
                        )
                    }
                    Text(
                        text = driver.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * The fuelling signal turned into an instruction.
 *
 * The drivers list above can already say "averaging 700 kcal under expenditure", which tells the
 * athlete something is wrong and nothing about what to do. The screen has the day's target in hand,
 * so it may as well answer the obvious next question.
 */
@Composable
private fun FuellingCard(assessment: ReadinessAssessment, target: DailyNutritionTarget) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "%,.0f kcal".format(target.kcal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = target.dayKind.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "%.0f g carbs · %.0f g protein · %.0f g fat"
                    .format(target.carbsG, target.proteinG, target.fatG),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = target.rationale,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // What the score actually saw, so the instruction and the penalty are visibly the same
            // story rather than two unrelated numbers on one screen.
            assessment.drivers.firstOrNull { it.label == "Fuelling" }?.let { driver ->
                Text(
                    text = driver.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (driver.isPositive) TriPathTheme.colors.positive
                    else TriPathTheme.colors.negative
                )
            }
            target.warnings.forEach { warning ->
                Text(
                    text = warning.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RegionalLoadCard(assessment: ReadinessAssessment) {
    if (!assessment.strain.hasData) {
        EmptyStateCard("No training logged recently — regional load has nothing to show.")
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            StrainChannel.entries.forEach { channel ->
                val state = assessment.strain[channel] ?: return@forEach
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = channel.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = state.hoursToFresh
                                ?.let { "${state.freshness}% · ${formatHours(it)} to clear" }
                                ?: "${state.freshness}% · fresh",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FreshnessBar(state.freshness)
                }
            }
        }
    }
}

@Composable
private fun LiftSessionsCard(days: List<LiftContributionDay>) {
    if (days.isEmpty()) {
        EmptyStateCard("No LiftPath sessions in the last two weeks.")
        return
    }
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            days.forEach { day ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = describeDate(day.date, formatter),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${day.workingSets} working sets",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (day.muscleGroups.isNotEmpty()) {
                        Text(
                            text = day.muscleGroups.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (day.exercises.isNotEmpty()) {
                        Text(
                            text = day.exercises.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.lg)
        )
    }
}

@Composable
private fun FreshnessBar(freshness: Int) {
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
                .background(freshnessColor(freshness))
        )
    }
}

private fun describeDate(date: LocalDate, formatter: DateTimeFormatter): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> formatter.format(date)
    }
}

private fun formatHours(hours: Int): String =
    if (hours >= 24) "${(hours / 24.0).roundToInt()}d" else "${hours}h"
