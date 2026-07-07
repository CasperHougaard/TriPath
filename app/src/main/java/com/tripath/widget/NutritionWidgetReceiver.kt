package com.tripath.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Broadcast receiver that connects the framework's app-widget lifecycle to [NutritionWidget].
 * Registered in the manifest with the app-widget provider metadata.
 */
class NutritionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NutritionWidget()
}
