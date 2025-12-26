package com.tripath.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Standard spacing system for TriPath app.
 * Based on Material Design 4dp increments.
 */
object Spacing {
    val xs = 4.dp      // Extra small gaps, tight spacing within elements
    val sm = 8.dp      // Small gaps, compact layouts, list spacing
    val md = 12.dp     // Medium gaps, card content spacing
    val lg = 16.dp     // Large gaps, standard padding, primary content
    val xl = 20.dp     // Extra large gaps, featured card padding
    val xxl = 24.dp    // Screen-level padding, major sections
    val xxxl = 32.dp   // Major section separation, hero spacing
}

/**
 * Icon sizing system for consistent icon usage.
 */
object IconSize {
    val small = 16.dp   // Small inline icons, badges
    val medium = 24.dp  // Standard UI icons
    val large = 32.dp   // Workout card icons
    val xlarge = 40.dp  // Large feature icons
    val xxlarge = 48.dp // Hero/primary action icons
}

/**
 * Card sizing standards.
 */
object CardSize {
    val minActionHeight = 72.dp   // Minimum height for action cards
    val minStatHeight = 120.dp    // Minimum height for stat cards
    val minListItemHeight = 64.dp // Minimum height for list items
    val heroHeight = 150.dp       // Hero card height
    val chartHeight = 280.dp      // Standard chart container height
}

/**
 * Touch target guidelines for accessibility.
 */
object TouchTarget {
    val minimum = 48.dp // Minimum touch target size (Material Design spec)
}

