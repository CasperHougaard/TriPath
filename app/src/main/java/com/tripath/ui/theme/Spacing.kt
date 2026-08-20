package com.tripath.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The 4dp spacing grid.
 *
 * These names predate the design-system port and are kept verbatim — all 457 call sites
 * already use them, and the values happen to match LiftPath's `lp_space_*` scale step for
 * step, so there was nothing to reconcile. Use these for gaps *inside* a component; use
 * [Layout] for the page-level rhythm.
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
 * Page-level rhythm, ported from LiftPath's `lp_dimens.xml`.
 *
 * LiftPath keeps two density scales — airy for surfaces you browse, dense for working surfaces
 * used mid-set with a barbell waiting. **TriPath only needs the airy one.** Every screen here
 * is a planning or review surface: you read the dashboard, you author a week, you study a
 * chart. There is no equivalent of a screen operated one-handed between sets, so the dense
 * scale is deliberately not ported rather than ported and left unused.
 *
 * The gutter is 20dp rather than 24dp because the hairline card treatment reads tighter than
 * shadowed cards did.
 */
object Layout {
    /** Horizontal page margin. */
    val gutter = 20.dp
    /** Padding inside a card. */
    val cardPadding = 16.dp
    /** Gap between sibling cards in a row, column or grid. */
    val cardGap = 10.dp
    /** Space above a new section heading. */
    val sectionGap = 32.dp
    /** Cards are defined by this, not by a shadow. */
    val hairline = 1.dp
    /** Minimum tappable list-row height; comfortably above the 48dp a11y floor. */
    val rowMinHeight = 56.dp
    /** Square secondary action, and the icon drawn inside it. */
    val iconAction = 40.dp
    val iconActionPadding = 10.dp
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
