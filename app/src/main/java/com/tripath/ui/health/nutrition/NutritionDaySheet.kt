package com.tripath.ui.health.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripath.data.local.database.entities.NutritionEntry
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val DayTitleFormat = DateTimeFormatter.ofPattern("EEEE d MMM")
private val EntryTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The itemised log for one day: every quick-add, custom add and total edit, each with an undo
 * that reverses only that action.
 *
 * [log] can be null (a day whose row was just emptied), and [entries] is empty for days logged
 * before the ledger existed — both are shown as an explanatory empty state rather than a blank
 * sheet, since editing the totals directly still works from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionDaySheet(
    date: LocalDate,
    log: NutritionLog?,
    entries: List<NutritionEntry>,
    isToday: Boolean,
    onUndoEntry: (Long) -> Unit,
    onEditTotals: () -> Unit,
    onClearDay: () -> Unit,
    onDismiss: () -> Unit,
    /** What this day required and what it was doing. Null on a day the fuel model could not size. */
    fuel: FuelHistoryDay? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = if (isToday) "Today" else date.format(DayTitleFormat),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = dayTotalsLine(log),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            fuel?.let { DayRequirement(it) }

            HorizontalDivider()

            if (entries.isEmpty()) {
                Text(
                    text = "No itemised entries for this day — its totals were set directly, or it " +
                        "was logged before entry tracking. You can still edit the totals below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${entries.size} ${if (entries.size == 1) "entry" else "entries"} · newest first",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entries.forEach { entry ->
                    NutritionEntryRow(entry = entry, onUndo = { onUndoEntry(entry.id) })
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(onClick = onEditTotals, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  Edit totals")
                }
                if (log != null) {
                    TextButton(onClick = onClearDay, modifier = Modifier.weight(1f)) {
                        Text("Clear day")
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionEntryRow(entry: NutritionEntry, onUndo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.timeLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.xs)) {
            val title = entryTitle(entry)
            Text(title, style = MaterialTheme.typography.bodyLarge)
            // Amounts go on their own line unless the title already is them (an unlabelled quick-add).
            val detail = entryDetailLine(entry)
                ?: entryDeltaSummary(entry).takeIf { it.isNotEmpty() && it != title }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onUndo) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo ${entryTitle(entry)}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * What the day asked for, against what it got, with the sessions that set the requirement.
 *
 * The gap is stated in words rather than left as arithmetic between two rows: "830 kcal short" is
 * the sentence the athlete came here to read, and making them subtract is how a screen full of
 * numbers ends up telling nobody anything.
 */
@Composable
private fun DayRequirement(fuel: FuelHistoryDay) {
    val needed = listOfNotNull(
        fuel.neededKcal?.let { "%,.0f kcal".format(it) },
        fuel.neededProteinG?.let { "%.0f g protein".format(it) },
        fuel.neededCarbsG?.let { "%.0f g carbs".format(it) }
    )
    if (needed.isEmpty() && fuel.activities.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (needed.isNotEmpty()) {
            Text(
                text = "Needed ${needed.joinToString(" · ")}" +
                    (fuel.dayKind?.let { " (${it.phrase})" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val energyGap = gapLine("Energy", fuel.eatenKcal, fuel.neededKcal, "kcal")
        val proteinGap = gapLine("Protein", fuel.eatenProteinG, fuel.neededProteinG, "g")
        listOfNotNull(energyGap, proteinGap).forEach { (text, isShort) ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isShort) TriPathTheme.colors.negative else TriPathTheme.colors.positive
            )
        }

        if (fuel.activities.isNotEmpty()) {
            Text(
                text = fuel.activities.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * "Energy: 830 kcal short" / "Protein: 12 g over", or null when there is nothing to compare.
 *
 * A missing intake is a day that was not logged, not a day of eating nothing, so it produces no line
 * at all rather than a shortfall equal to the whole requirement.
 */
private fun gapLine(label: String, eaten: Double?, needed: Double?, unit: String): Pair<String, Boolean>? {
    if (eaten == null || needed == null) return null
    val delta = eaten - needed
    if (abs(delta) < 1.0) return "$label: on target" to false
    val short = delta < 0
    val amount = "%,.0f $unit".format(abs(delta))
    return "$label: $amount ${if (short) "short" else "over"}" to short
}

/** "1,840 kcal · 112 g protein · creatine ✓", or a plain note when the day holds nothing. */
private fun dayTotalsLine(log: NutritionLog?): String {
    if (log == null) return "Nothing logged"
    val parts = listOfNotNull(
        log.energyKcal?.let { "%,.0f kcal".format(it) },
        log.proteinG?.let { "%.0f g protein".format(it) },
        if (log.creatineTaken) "creatine ✓" else null
    )
    return parts.joinToString(" · ").ifEmpty { "Nothing logged" }
}

private fun NutritionEntry.timeLabel(): String =
    Instant.ofEpochMilli(loggedAt).atZone(ZoneId.systemDefault()).format(EntryTimeFormat)
