package com.tripath.ui.coach.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tripath.domain.strain.StrainSource
import com.tripath.ui.theme.Spacing

/**
 * Which data sources the screen's strain figures are built from.
 *
 * ## Why this belongs on this screen and nowhere else
 * A body does not care which app logged the session, so every other surface in TriPath reads the
 * whole picture. This screen exists to answer "why does the model think that", and separating the
 * two sources is the sharpest form of that answer: it is the only way to see whether the hole in
 * your legs came from Saturday's ride or Monday's squats.
 *
 * The caveat line is not decoration. Freshness is scored against a 42-day baseline built from
 * whatever is selected, so restricting the source does not merely subtract load — it re-normalises
 * the scale underneath it, and lifting alone judged against a lifting-only baseline reads very
 * differently from lifting inside a full week. Sleep, HRV, TSB and fuelling are not filtered at all,
 * because none of them has a per-app provenance to filter on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrainSourceSelector(
    selected: StrainSource,
    onSelect: (StrainSource) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StrainSource.entries.forEach { source ->
                FilterChip(
                    selected = selected == source,
                    onClick = { onSelect(source) },
                    label = { Text(source.label) }
                )
            }
        }
        Text(
            text = selected.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (selected != StrainSource.BOTH) {
            Text(
                text = "Freshness here is scored against a baseline built from this source alone, " +
                    "and sleep, HRV, form and fuelling are never filtered — so this score will not " +
                    "match the Coach tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
