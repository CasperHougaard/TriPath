package com.tripath.ui.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tripath.ui.components.EmptyState
import com.tripath.ui.theme.Spacing

/**
 * Lists every stored record in one [DataCategory].
 *
 * One screen serves all categories: records are flattened to [DataRecordUi] by the view model, so
 * adding a table to the browser needs a mapper, not another screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataCategoryScreen(
    category: DataCategory,
    onNavigateBack: () -> Unit,
    viewModel: MyDataViewModel = hiltViewModel()
) {
    var records by remember(category) { mutableStateOf<List<DataRecordUi>?>(null) }

    LaunchedEffect(category) {
        records = viewModel.loadRecords(category)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(category.title)
                        Text(
                            text = records?.let { "${it.size} records" } ?: "Loading…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        val loaded = records

        when {
            loaded == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            loaded.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                EmptyState(
                    message = "No ${category.title.lowercase()} stored",
                    description = category.description,
                    icon = Icons.Default.Inbox
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item { Spacer(modifier = Modifier.height(Spacing.sm)) }
                items(loaded, key = { it.id }) { record ->
                    RecordCard(record = record)
                }
                item { Spacer(modifier = Modifier.height(Spacing.lg)) }
            }
        }
    }
}

@Composable
private fun RecordCard(record: DataRecordUi) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (record.isIgnored) 0.45f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = if (record.isIgnored) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            record.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (record.fields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                record.fields.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1.4f)
                        )
                    }
                }
            }

            if (record.isIgnored) {
                Text(
                    text = "Excluded from charts and analysis",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
