package com.tripath.ui.health.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripath.data.local.database.entities.NutritionLog
import com.tripath.data.local.database.entities.NutritionPreset
import com.tripath.data.local.repository.NutritionMacro
import com.tripath.domain.health.EnergyAvailabilityBand
import com.tripath.domain.health.EnergyAvailabilityResult
import com.tripath.ui.components.SectionHeader
import com.tripath.ui.health.HealthTimePeriod
import com.tripath.ui.health.components.BodyMetricChart
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.TriPathTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val ProteinColor = Color(0xFF42A5F5)
private val CaloriesColor = Color(0xFF26A69A)
private val CarbsColor = Color(0xFFFFB74D)
private val FatColor = Color(0xFFE57373)

private fun NutritionLog.millis(): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionDetailScreen(
    onNavigateBack: () -> Unit = {},
    onScanBarcode: () -> Unit = {},
    viewModel: NutritionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val dayEntries by viewModel.dayEntries.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()

    var showCustomAdd by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var showTargets by remember { mutableStateOf(false) }
    var editDay by remember { mutableStateOf<NutritionLog?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Quick-add with a Snackbar "Undo" — removes the ledger entry the add just created, so the
    // day log shows no trace of it (rather than an add and a matching negative).
    fun quickAddWithUndo(macro: NutritionMacro, amount: Double) {
        viewModel.quickAdd(macro, amount)
        val label = when (macro) {
            NutritionMacro.ENERGY -> "%,.0f kcal".format(amount)
            NutritionMacro.PROTEIN -> "%.0f g protein".format(amount)
            else -> "%.0f".format(amount)
        }
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Added $label",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastEntry()
            }
        }
    }

    // Applying a preset is a custom add under the hood, so it gets the same Snackbar/Undo.
    fun applyPresetWithUndo(preset: NutritionPreset) {
        viewModel.applyPreset(preset)
        showLibrary = false
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Added ${preset.label}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastEntry()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrition") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // What the day's work is asking for, before what has been eaten towards it.
            FuelPlanCard(state)

            // Today — the primary logging surface, always visible.
            TodayCard(
                state = state,
                onQuickAdd = ::quickAddWithUndo,
                onCustomAdd = { showCustomAdd = true },
                onScanBarcode = onScanBarcode,
                onLibrary = { showLibrary = true },
                onToggleCreatine = { viewModel.setCreatine(state.todayDate, it) },
                onEditTargets = { showTargets = true },
                onViewLog = { viewModel.openDay(state.todayDate) },
                onEditCalories = { newKcal ->
                    val today = state.today
                    viewModel.editDay(state.todayDate, newKcal, today?.proteinG, today?.carbsG, today?.fatG, today?.creatineTaken ?: false)
                },
                onEditProtein = { newProtein ->
                    val today = state.today
                    viewModel.editDay(state.todayDate, today?.energyKcal, newProtein, today?.carbsG, today?.fatG, today?.creatineTaken ?: false)
                }
            )

            // History
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HealthTimePeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.selectedPeriod == period,
                        onClick = { viewModel.selectPeriod(period) },
                        label = { Text(period.label) }
                    )
                }
            }

            // Needed against eaten over the selected period, with the training that set the
            // requirement. Tapping a day opens the same sheet the history list does.
            if (state.fuelHistory.isNotEmpty()) {
                FuelHistorySection(
                    days = state.fuelHistory,
                    onSelectDay = { viewModel.openDay(it) }
                )
            }

            if (state.logs.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("No nutrition logged yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Use the quick-add buttons or “Custom add” above to log today’s calories and macros. Your history and averages will build up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                HistorySection(state = state, onOpenDay = { viewModel.openDay(it.date) })
            }
        }
    }

    selectedDay?.let { date ->
        // The day's row comes from the list the screen already observes, so the sheet updates
        // live as entries are undone — including the case where the last one empties the day.
        val log = if (date == state.todayDate) state.today else state.logs.firstOrNull { it.date == date }
        NutritionDaySheet(
            date = date,
            log = log,
            entries = dayEntries,
            isToday = date == state.todayDate,
            fuel = state.fuelHistory.firstOrNull { it.date == date },
            onUndoEntry = { viewModel.undoEntry(it) },
            onEditTotals = { editDay = log ?: NutritionLog(date = date) },
            onClearDay = {
                viewModel.clearDay(date)
                viewModel.closeDay()
            },
            onDismiss = { viewModel.closeDay() }
        )
    }

    if (showCustomAdd) {
        CustomAddDialog(
            todayDate = state.todayDate,
            onDismiss = { showCustomAdd = false },
            onConfirm = { kcal, p, label, date ->
                viewModel.addCustom(kcal, p, null, null, label, date)
                showCustomAdd = false
            },
            onSaveToLibrary = { kcal, p, label ->
                viewModel.saveAsPreset(label, kcal, p)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Saved to library", duration = SnackbarDuration.Short)
                }
            }
        )
    }

    if (showLibrary) {
        LibraryDialog(
            presets = presets,
            onApply = ::applyPresetWithUndo,
            onDelete = { viewModel.deletePreset(it) },
            onDismiss = { showLibrary = false }
        )
    }

    if (showTargets) {
        TargetsDialog(
            initialProtein = state.userProteinTargetG,
            initialCalories = state.userCalorieTarget,
            onDismiss = { showTargets = false },
            onConfirm = { protein, calories ->
                viewModel.saveTargets(protein, calories)
                showTargets = false
            }
        )
    }

    editDay?.let { log ->
        EditDayDialog(
            log = log,
            onDismiss = { editDay = null },
            onConfirm = { kcal, p, creatineTaken ->
                // Preserve any previously logged carbs/fat for this day (no longer shown in the UI).
                viewModel.editDay(log.date, kcal, p, log.carbsG, log.fatG, creatineTaken)
                editDay = null
            },
            onClear = {
                viewModel.clearDay(log.date)
                editDay = null
                viewModel.closeDay()
            }
        )
    }
}

/**
 * What today needs, and why.
 *
 * [TodayCard] answers "what have I eaten"; this answers "what is the work asking for". They are
 * different questions, and the app has been able to answer the second one for a while without ever
 * saying so: the day kind, the carbohydrate band, the reasoning and the warnings were all computed
 * and then used only as a fallback number behind a progress bar.
 *
 * Carbohydrate and fat are a prescription rather than a progress bar, because only calories and
 * protein are logged. A bar against a number nothing can ever fill would read as permanent failure.
 */
@Composable
private fun FuelPlanCard(state: NutritionUiState) {
    val target = state.dynamicTarget
    if (target == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Fuel plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Log a body weight and set a goal to get a target that follows your " +
                        "training — how much a day needs depends on both.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fuel plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = target.dayKind.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PlanMacro(Modifier.weight(1f), "Energy", "%,.0f".format(target.kcal), "kcal", CaloriesColor)
                PlanMacro(Modifier.weight(1f), "Protein", "%.0f".format(target.proteinG), "g", ProteinColor)
                PlanMacro(Modifier.weight(1f), "Carbs", "%.0f".format(target.carbsG), "g", CarbsColor)
                PlanMacro(Modifier.weight(1f), "Fat", "%.0f".format(target.fatG), "g", FatColor)
            }

            Text(
                text = target.rationale,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Only calories and protein are logged, so say which of these can be tracked and which
            // is guidance. Better than letting the athlete assume the app is watching all four.
            Text(
                text = "Carbs and fat are guidance — only calories and protein are tracked.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // A manual target overrides this one in the bars below, and a card quoting a different
            // number without explaining itself is worse than no card at all.
            val overridden = listOfNotNull(
                state.userCalorieTarget?.let { "calories" },
                state.userProteinTargetG?.let { "protein" }
            )
            if (overridden.isNotEmpty()) {
                Text(
                    text = "Your own ${overridden.joinToString(" and ")} " +
                        "${if (overridden.size > 1) "targets are" else "target is"} in use below " +
                        "instead of this one.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            target.warnings.forEach { warning ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TriPathTheme.colors.neutral
                    )
                    Text(
                        text = warning.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = TriPathTheme.colors.neutral
                    )
                }
            }

            // Tomorrow is *why* today's carbohydrate is what it is when a big day is coming.
            state.tomorrowTarget?.let { tomorrow ->
                Text(
                    text = "Tomorrow: ${tomorrow.dayKind.phrase} — " +
                        "%,.0f kcal, %.0f g carbs".format(tomorrow.kcal, tomorrow.carbsG) +
                        " (from what's planned)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlanMacro(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayCard(
    state: NutritionUiState,
    onQuickAdd: (NutritionMacro, Double) -> Unit,
    onCustomAdd: () -> Unit,
    onScanBarcode: () -> Unit,
    onLibrary: () -> Unit,
    onToggleCreatine: (Boolean) -> Unit,
    onEditTargets: () -> Unit,
    onViewLog: () -> Unit,
    onEditCalories: (Double) -> Unit,
    onEditProtein: (Double) -> Unit
) {
    val today = state.today
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onViewLog) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("  Log", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = onEditTargets) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (state.userProteinTargetG == null && state.userCalorieTarget == null) "  Set targets" else "  Targets",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            EnergyAvailabilityRow(state.energyAvailability)

            // Calories, Protein and Creatine as three equal-weight peers — same tile footprint,
            // so Creatine gets the same visual weight as the two numeric metrics.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val calTarget = state.effectiveCalorieTarget
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Calories",
                    valueText = "%,.0f".format(today?.energyKcal ?: 0.0),
                    rawValue = today?.energyKcal ?: 0.0,
                    targetText = calTarget?.let { "/ %,.0f kcal".format(it) } ?: "no target",
                    progressValue = today?.energyKcal ?: 0.0,
                    progressTarget = calTarget,
                    color = CaloriesColor,
                    onQuickAdd = { onQuickAdd(NutritionMacro.ENERGY, 100.0) },
                    quickAddDescription = "Add 100 kcal",
                    onValueEdit = onEditCalories
                )
                val proteinTarget = state.effectiveProteinTargetG
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Protein",
                    valueText = "%.0f".format(today?.proteinG ?: 0.0),
                    rawValue = today?.proteinG ?: 0.0,
                    targetText = proteinTarget?.let { "/ %.0f g".format(it) } ?: "no target",
                    progressValue = today?.proteinG ?: 0.0,
                    progressTarget = proteinTarget,
                    color = ProteinColor,
                    onQuickAdd = { onQuickAdd(NutritionMacro.PROTEIN, 10.0) },
                    quickAddDescription = "Add 10 g protein",
                    onValueEdit = onEditProtein
                )
                CreatineTile(
                    modifier = Modifier.weight(1f),
                    taken = today?.creatineTaken == true,
                    onToggle = { onToggleCreatine(!(today?.creatineTaken ?: false)) }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onCustomAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Custom add")
                }
                OutlinedButton(onClick = onScanBarcode, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Text("  Scan barcode")
                }
                OutlinedButton(onClick = onLibrary, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Bookmark, contentDescription = null)
                    Text("  Library")
                }
            }
        }
    }
}

/**
 * A soft fuelling-readiness screening signal from [com.tripath.domain.health.EnergyAvailability].
 * Silent when unknown or adequate — this is meant to be noticed only when it is worth noticing.
 */
@Composable
private fun EnergyAvailabilityRow(result: EnergyAvailabilityResult) {
    if (result.band == EnergyAvailabilityBand.UNKNOWN || result.band == EnergyAvailabilityBand.ADEQUATE) return
    val color = when (result.band) {
        EnergyAvailabilityBand.LOW_SIGNAL -> TriPathTheme.colors.negative
        EnergyAvailabilityBand.REDUCED -> TriPathTheme.colors.neutral
        else -> TriPathTheme.colors.positive
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Text(
            text = "Energy availability: ${result.band.label} (7-day)",
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

/**
 * A metric tile with a fixed label/value/subtext/indicator/action vertical rhythm, so any two
 * tiles placed side by side (via equal weights) come out the same height without extra sizing.
 */
@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    label: String,
    valueText: String,
    rawValue: Double,
    targetText: String,
    progressValue: Double,
    progressTarget: Double?,
    color: Color,
    onQuickAdd: () -> Unit,
    quickAddDescription: String,
    onValueEdit: (Double) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
            if (isEditing) {
                val focusRequester = remember { FocusRequester() }
                var hasFocused by remember { mutableStateOf(false) }
                fun commit() {
                    editText.toNutrientOrNull()?.let(onValueEdit)
                    isEditing = false
                }
                BasicTextField(
                    value = editText,
                    onValueChange = { if (isNutrientInput(it)) editText = it },
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = color,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(color),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) hasFocused = true
                            else if (hasFocused) commit()
                        }
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            } else {
                Text(
                    valueText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        editText = if (rawValue == 0.0) "" else "%.0f".format(rawValue)
                        isEditing = true
                    }
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) {
            Text(
                targetText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
            if (progressTarget != null && progressTarget > 0) {
                SoftProgressBar(value = progressValue, target = progressTarget, color = color)
            }
        }
        Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = onQuickAdd, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = quickAddDescription,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Same tile footprint as [MetricTile], but the whole tile is the tap target for the toggle. */
@Composable
private fun CreatineTile(modifier: Modifier = Modifier, taken: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = modifier.toggleable(value = taken, role = Role.Checkbox, onValueChange = { onToggle() }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            "Creatine",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (taken) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (taken) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Box(Modifier.fillMaxWidth().height(16.dp))
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
            Text(
                text = if (taken) "Taken" else "Not yet",
                style = MaterialTheme.typography.labelSmall,
                color = if (taken) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Box(Modifier.fillMaxWidth().height(32.dp))
    }
}

/**
 * A soft progress bar: clamps the bar fill to [0,1] but never signals failure. Values over the
 * target simply read as full (the "/ target" number already communicates the overage).
 */
@Composable
private fun SoftProgressBar(value: Double, target: Double, color: Color) {
    LinearProgressIndicator(
        progress = { softProgressFraction(value, target) },
        modifier = Modifier.fillMaxWidth(),
        color = color
    )
}

@Composable
private fun HistorySection(state: NutritionUiState, onOpenDay: (NutritionLog) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        // Averages
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatCard("Avg calories", state.avgCalories?.let { "%,.0f".format(it) } ?: "—", Modifier.weight(1f))
            StatCard("Avg protein", state.avgProtein?.let { "%.0f g".format(it) } ?: "—", Modifier.weight(1f))
        }

        // Calories trend
        val caloriePoints = state.logs.mapNotNull { l -> l.energyKcal?.let { l.millis() to it } }.sortedBy { it.first }
        if (caloriePoints.size >= 2) {
            val maintenance = state.maintenanceCalories
            SectionHeader(
                title = "Calories",
                subtitle = maintenance?.let { "kcal / day · maintenance ≈ %,.0f".format(it) } ?: "kcal / day"
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                BodyMetricChart(
                    dataPoints = caloriePoints,
                    accentColor = CaloriesColor,
                    modifier = Modifier.padding(Spacing.md),
                    referenceBand = maintenance?.let { it..it }
                )
            }
        }

        // Protein vs target band
        val proteinPoints = state.logs.mapNotNull { l -> l.proteinG?.let { l.millis() to it } }.sortedBy { it.first }
        if (proteinPoints.size >= 2) {
            val target = state.proteinTarget
            SectionHeader(
                title = "Protein",
                subtitle = target?.let { "g / day · target %.0f–%.0f".format(it.min, it.max) } ?: "g / day"
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                BodyMetricChart(
                    dataPoints = proteinPoints,
                    accentColor = ProteinColor,
                    modifier = Modifier.padding(Spacing.md),
                    referenceBand = target?.range
                )
            }
        }

        SectionHeader(title = "Days", subtitle = "${state.logs.size} logged · tap to view")
        state.logs.forEach { log -> NutritionDayRow(log, onClick = { onOpenDay(log) }) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NutritionDayRow(log: NutritionLog, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(log.date.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = log.energyKcal?.let { "%,.0f kcal".format(it) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = CaloriesColor
                )
            }
            val macros = listOfNotNull(
                log.proteinG?.let { "P %.0fg".format(it) },
                if (log.creatineTaken) "Creatine ✓" else null
            )
            if (macros.isNotEmpty()) {
                Text(
                    text = macros.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---- Dialogs --------------------------------------------------------------------------------

/** Parses a nutrition field: blank -> null (preserving the null-vs-zero distinction). */
internal fun String.toNutrientOrNull(): Double? = trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

internal fun isNutrientInput(s: String): Boolean = s.isEmpty() || s.matches(Regex("^\\d*\\.?\\d*$"))

@Composable
internal fun NutrientField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (isNutrientInput(it)) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

private val CustomAddDateFormat = DateTimeFormatter.ofPattern("EEEE d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomAddDialog(
    todayDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (kcal: Double?, protein: Double?, label: String?, date: LocalDate) -> Unit,
    onSaveToLibrary: (kcal: Double, protein: Double, label: String) -> Unit
) {
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    // A preset needs a name and both macros to be worth finding again later.
    val kcalValue = kcal.toNutrientOrNull()
    val proteinValue = protein.toNutrientOrNull()
    val canSaveToLibrary = label.isNotBlank() && kcalValue != null && proteinValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (date == todayDate) "Add to today" else "Add to ${date.format(CustomAddDateFormat)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Amounts are added to the selected day's totals. Leave a field blank to skip it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Defaults to today but can be backdated, e.g. for an entry logged the morning after.
                OutlinedTextField(
                    value = if (date == todayDate) "Today" else date.format(CustomAddDateFormat),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Day") },
                    trailingIcon = {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = "Pick a day",
                            modifier = Modifier.clickable { showDatePicker = true }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                )
                // Names the entry in the day log, which is what makes a wrong one easy to spot.
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("What was it? (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                NutrientField("Calories (kcal)", kcal) { kcal = it }
                NutrientField("Protein (g)", protein) { protein = it }
                TextButton(
                    onClick = { onSaveToLibrary(kcalValue!!, proteinValue!!, label.trim()) },
                    enabled = canSaveToLibrary
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  Save to library")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(kcal.toNutrientOrNull(), protein.toNutrientOrNull(), label.trim().takeIf { it.isNotEmpty() }, date)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                // Nutrition is logged retrospectively — no adding to a day that hasn't happened yet.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.systemDefault()).toLocalDate().isAfter(todayDate)
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun LibraryDialog(
    presets: List<NutritionPreset>,
    onApply: (NutritionPreset) -> Unit,
    onDelete: (NutritionPreset) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(presets, query) {
        presets.filter { it.label.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Library") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = if (presets.isEmpty()) {
                            "No presets yet — save one from Custom add."
                        } else {
                            "No presets match \"$query\"."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.md)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(filtered, key = { it.id }) { preset ->
                            PresetRow(preset = preset, onApply = { onApply(preset) }, onDelete = { onDelete(preset) })
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun PresetRow(preset: NutritionPreset, onApply: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApply),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(preset.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val macros = listOfNotNull(
                preset.kcal?.let { "%,.0f kcal".format(it) },
                preset.proteinG?.let { "%.0f g protein".format(it) }
            )
            if (macros.isNotEmpty()) {
                Text(
                    text = macros.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${preset.label}")
        }
    }
}

@Composable
private fun EditDayDialog(
    log: NutritionLog,
    onDismiss: () -> Unit,
    onConfirm: (kcal: Double?, protein: Double?, creatineTaken: Boolean) -> Unit,
    onClear: () -> Unit
) {
    var kcal by remember { mutableStateOf(log.energyKcal?.let { "%.0f".format(it) } ?: "") }
    var protein by remember { mutableStateOf(log.proteinG?.let { "%.0f".format(it) } ?: "") }
    var creatineTaken by remember { mutableStateOf(log.creatineTaken) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${log.date}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Sets exact totals for this day. A blank field is left unlogged (not zero).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NutrientField("Calories (kcal)", kcal) { kcal = it }
                NutrientField("Protein (g)", protein) { protein = it }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Creatine taken", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = creatineTaken, onCheckedChange = { creatineTaken = it })
                }
                TextButton(onClick = onClear) { Text("Clear this day") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(kcal.toNutrientOrNull(), protein.toNutrientOrNull(), creatineTaken)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TargetsDialog(
    initialProtein: Float?,
    initialCalories: Float?,
    onDismiss: () -> Unit,
    onConfirm: (protein: Float?, calories: Float?) -> Unit
) {
    var protein by remember { mutableStateOf(initialProtein?.let { "%.0f".format(it) } ?: "") }
    var calories by remember { mutableStateOf(initialCalories?.let { "%.0f".format(it) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nutrition targets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Soft daily goals — progress only, never pass/fail. Protein is the primary target; the calorie target is optional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NutrientField("Protein target (g)", protein) { protein = it }
                NutrientField("Calorie target (kcal, optional)", calories) { calories = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    protein.toNutrientOrNull()?.toFloat(),
                    calories.toNutrientOrNull()?.toFloat()
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
