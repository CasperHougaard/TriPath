package com.tripath.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripath.ui.model.FormStatus
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme

@Composable
fun FormStatusCard(
    tsb: Double,
    status: FormStatus,
    modifier: Modifier = Modifier
) {
    val statusColor = when (status) {
        FormStatus.FRESHNESS -> Color(0xFF4CAF50) // Green
        FormStatus.OPTIMAL -> MaterialTheme.colorScheme.primary // Blue
        FormStatus.OVERREACHING -> Color(0xFFFF5252) // Red/Warning
    }

    val statusLabel = when (status) {
        FormStatus.FRESHNESS -> "Freshness"
        FormStatus.OPTIMAL -> "Optimal Training"
        FormStatus.OVERREACHING -> "Overreaching"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = statusColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            
            Text(
                text = String.format("%.1f", tsb),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "TSB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FormStatusCardPreview() {
    TriPathTheme {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            FormStatusCard(
                tsb = 12.5,
                status = FormStatus.FRESHNESS
            )
            FormStatusCard(
                tsb = -15.0,
                status = FormStatus.OPTIMAL
            )
            FormStatusCard(
                tsb = -35.0,
                status = FormStatus.OVERREACHING
            )
        }
    }
}

