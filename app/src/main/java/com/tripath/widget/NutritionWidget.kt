package com.tripath.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tripath.MainActivity
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.ui.health.nutrition.softProgressFraction
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val ProteinColor = ColorProvider(Color(0xFF42A5F5))
private val CaloriesColor = ColorProvider(Color(0xFF26A69A))

/**
 * Home-screen widget for logging today's nutrition without opening the app.
 *
 * Data is read fresh each time [provideGlance] runs; every action re-invokes it via
 * [update], so the numbers reflect the latest database state after each quick-add.
 */
class NutritionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = widgetEntryPoint(context)
        val repo = entryPoint.recoveryRepository()
        val prefs = entryPoint.preferencesManager()

        val today = LocalDate.now()
        val log = repo.getNutritionLog(today).first()
        val profile = prefs.getUserProfile()

        provideContent {
            GlanceTheme {
                NutritionWidgetContent(
                    log = log,
                    proteinTargetG = profile?.proteinTargetG,
                    calorieTarget = profile?.calorieTarget
                )
            }
        }
    }
}

/**
 * Refreshes every instance of the nutrition widget from the latest DB/profile state. Called after
 * every nutrition write (from the app or the widget itself) and on app foreground/background
 * transitions, so changes that don't go through a nutrition write path (e.g. editing the protein
 * or calorie target in Settings) don't leave the widget showing stale numbers.
 */
suspend fun refreshNutritionWidget(context: Context) {
    runCatching { NutritionWidget().updateAll(context) }
        .onFailure { Log.e("NutritionWidget", "widget refresh failed", it) }
}

/**
 * Intent that opens the app straight to the nutrition detail page. Used for every non-button
 * area of the widget so a stray tap lands somewhere useful. SINGLE_TOP lets an already-running
 * MainActivity handle it via onNewIntent instead of spawning a second instance.
 */
private fun openNutritionAction(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_OPEN_DESTINATION, MainActivity.DEST_NUTRITION)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
)

@Composable
private fun NutritionWidgetContent(log: NutritionLog?, proteinTargetG: Float?, calorieTarget: Float?) {
    val context = LocalContext.current
    val kcal = log?.energyKcal ?: 0.0
    val protein = log?.proteinG ?: 0.0
    val creatine = log?.creatineTaken ?: false

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(16.dp)
            // Any tap outside a button opens the nutrition page (buttons override this in their region).
            .clickable(openNutritionAction(context)),
        verticalAlignment = Alignment.Top
    ) {
        // Header — tap to open the nutrition page.
        Text(
            text = "Today’s Nutrition",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onBackground
            ),
            modifier = GlanceModifier.clickable(openNutritionAction(context))
        )
        Spacer(GlanceModifier.height(4.dp))

        // Calories (with target when set).
        Text(
            text = "%,.0f kcal".format(kcal) + (calorieTarget?.let { " / %,.0f".format(it) } ?: ""),
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = CaloriesColor)
        )
        Spacer(GlanceModifier.height(8.dp))

        // Protein — the primary macro target. Always shown; adds a "/ target" + bar when a target exists.
        Text(
            text = "Protein %.0f g".format(protein) +
                (proteinTargetG?.takeIf { it > 0f }?.let { " / %.0f g".format(it) } ?: ""),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ProteinColor)
        )
        if (proteinTargetG != null && proteinTargetG > 0f) {
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = softProgressFraction(protein, proteinTargetG.toDouble()),
                modifier = GlanceModifier.fillMaxWidth(),
                color = ProteinColor,
                backgroundColor = GlanceTheme.colors.secondaryContainer
            )
        }

        Spacer(GlanceModifier.height(12.dp))

        // Quick-add: protein and calories only.
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            QuickAddButton("+10 P", "PROTEIN", 10.0, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            QuickAddButton("+100 kcal", "ENERGY", 100.0, GlanceModifier.defaultWeight())
        }

        Spacer(GlanceModifier.height(8.dp))
        WidgetButton(
            label = if (creatine) "Creatine ✓" else "Creatine",
            onClick = actionRunCallback<ToggleCreatineAction>(),
            modifier = GlanceModifier.fillMaxWidth(),
            filled = creatine
        )
    }
}

@Composable
private fun QuickAddButton(label: String, macro: String, amount: Double, modifier: GlanceModifier) {
    WidgetButton(
        label = label,
        onClick = actionRunCallback<QuickAddAction>(
            actionParametersOf(
                QuickAddAction.macroKey to macro,
                QuickAddAction.amountKey to amount
            )
        ),
        modifier = modifier
    )
}

/**
 * A tappable pill button built from core Glance primitives (Glance 1.1.0 has no simple
 * text `Button` composable in this configuration). [filled] uses the accent container to
 * signal an "on" state (e.g. creatine taken).
 */
@Composable
private fun WidgetButton(
    label: String,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier,
    filled: Boolean = false
) {
    val bg = if (filled) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer
    val fg = if (filled) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
    Box(
        modifier = modifier
            .background(bg)
            .cornerRadius(10.dp)
            .clickable(onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(color = fg, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        )
    }
}
