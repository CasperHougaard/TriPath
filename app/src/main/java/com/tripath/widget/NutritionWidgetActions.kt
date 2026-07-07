package com.tripath.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.tripath.data.local.preferences.PreferencesManager
import com.tripath.data.local.repository.NutritionMacro
import com.tripath.data.local.repository.RecoveryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Home-screen widgets are instantiated by the framework, not by Hilt, so we can't constructor-inject
 * into them. This entry point lets widget code pull the same singletons the app uses out of the
 * application-scoped Hilt component.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun recoveryRepository(): RecoveryRepository
    fun preferencesManager(): PreferencesManager
}

internal fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

/**
 * Adds a fixed amount to a single macro (or kcal) for today. [RecoveryRepository] refreshes every
 * widget instance itself once the write lands, so the app and widget stay live-synced regardless
 * of which one made the change.
 * The macro and amount are passed as action parameters so one callback backs every quick-add button.
 */
class QuickAddAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // onAction runs inside Glance's async BroadcastReceiver. If anything here throws, the
        // receiver never finishes its pending result and stops delivering further clicks — so the
        // widget appears to "work once, then freeze". Swallow failures to keep it responsive.
        try {
            val macroName = parameters[macroKey] ?: return
            val amount = parameters[amountKey] ?: return
            val macro = runCatching { NutritionMacro.valueOf(macroName) }.getOrNull() ?: return

            widgetEntryPoint(context).recoveryRepository()
                .quickAddMacro(LocalDate.now(), macro, amount)
        } catch (e: Exception) {
            Log.e(TAG, "quick-add failed", e)
        }
    }

    companion object {
        private const val TAG = "NutritionWidget"
        val macroKey = ActionParameters.Key<String>("macro")
        val amountKey = ActionParameters.Key<Double>("amount")
    }
}

/** Toggles today's creatine flag based on its current value. See [QuickAddAction] re: widget refresh. */
class ToggleCreatineAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try {
            val repo = widgetEntryPoint(context).recoveryRepository()
            val today = LocalDate.now()
            val current = repo.getNutritionLog(today).first()?.creatineTaken ?: false
            repo.setCreatine(today, !current)
        } catch (e: Exception) {
            Log.e("NutritionWidget", "creatine toggle failed", e)
        }
    }
}
