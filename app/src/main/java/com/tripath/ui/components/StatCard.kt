package com.tripath.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tripath.ui.theme.CardSize
import com.tripath.ui.theme.IconSize
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme

/**
 * Stat card component for displaying key metrics and statistics.
 * Follows TriPath design guidelines with consistent sizing and spacing.
 *
 * @param label Label text for the statistic
 * @param value Value to display
 * @param icon Icon to display at the top
 * @param modifier Modifier for the card
 * @param onClick Optional click handler for interactive stat cards
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = modifier.size(width = 160.dp, height = CardSize.minStatHeight)
    
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            StatCardContent(label = label, value = value, icon = icon)
        }
    } else {
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            StatCardContent(label = label, value = value, icon = icon)
        }
    }
}

@Composable
private fun StatCardContent(
    label: String,
    value: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSize.medium)
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StatCardPreview() {
    TriPathTheme {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatCard(
                label = "This Week",
                value = "240 TSS",
                icon = Icons.AutoMirrored.Filled.TrendingUp
            )
            StatCard(
                label = "Total Distance",
                value = "42.5 km",
                icon = Icons.Default.Route,
                onClick = { }
            )
        }
    }
}

