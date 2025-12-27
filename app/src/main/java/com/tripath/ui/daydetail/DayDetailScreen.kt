package com.tripath.ui.daydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tripath.data.local.database.entities.DayTemplate
import com.tripath.data.local.database.entities.TrainingPlan
import com.tripath.data.local.database.entities.WorkoutLog
import com.tripath.data.model.WorkoutType
import com.tripath.ui.navigation.Screen
import com.tripath.ui.planner.AddWorkoutBottomSheet
import com.tripath.ui.theme.Spacing
import com.tripath.ui.theme.toColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    date: LocalDate,
    navController: NavController,
    viewModel: DayDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allTemplates by viewModel.allTemplates.collectAsStateWithLifecycle()
    var showAddEditSheet by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<TrainingPlan?>(null) }
    var noteText by remember { mutableStateOf("") }
    
    // Load data when screen opens
    LaunchedEffect(date) {
        viewModel.loadData(date)
    }

    // Update note text when data loads
    LaunchedEffect(uiState.dayNote) {
        noteText = uiState.dayNote?.note ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM")),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (date == LocalDate.now()) {
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingActivity = null
                    showAddEditSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Activity")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
            ) {
                // Notes Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            text = "Notes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { 
                                noteText = it
                                viewModel.saveNote(it)
                            },
                            placeholder = { Text("Add a note for this day...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(Spacing.md)
                        )
                    }
                }

                // Planned Activities Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Planned Activities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        IconButton(onClick = { showTemplateDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Day Templates",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (uiState.plannedActivities.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xl),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No activities planned",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.plannedActivities) { activity ->
                        PlannedActivityCard(
                            activity = activity,
                            onClick = {
                                navController.navigate(Screen.WorkoutDetail.createRoute(activity.id, true))
                            },
                            onEdit = {
                                editingActivity = activity
                                showAddEditSheet = true
                            },
                            onDelete = { viewModel.deleteActivity(activity) }
                        )
                    }
                }

                // Completed Workouts Section
                if (uiState.completedWorkouts.isNotEmpty()) {
                    item {
                        Text(
                            text = "Completed Workouts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = Spacing.md)
                        )
                    }

                    items(uiState.completedWorkouts) { log ->
                        CompletedWorkoutCard(
                            log = log,
                            onClick = {
                                navController.navigate(Screen.WorkoutDetail.createRoute(log.connectId, false))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddEditSheet) {
        AddWorkoutBottomSheet(
            selectedDate = date,
            initialWorkout = editingActivity,
            userProfile = uiState.userProfile,
            onDismiss = { showAddEditSheet = false },
            onSave = { updatedActivity ->
                if (editingActivity != null) {
                    viewModel.updateActivity(updatedActivity.copy(id = editingActivity!!.id))
                } else {
                    viewModel.addActivity(updatedActivity)
                }
                showAddEditSheet = false
            }
        )
    }

    if (showTemplateDialog) {
        TemplateManagementDialog(
            templates = allTemplates,
            canSaveCurrent = uiState.plannedActivities.isNotEmpty(),
            onDismiss = { showTemplateDialog = false },
            onSaveCurrent = { name ->
                viewModel.saveCurrentAsTemplate(name)
            },
            onApplyTemplate = { template ->
                viewModel.applyTemplate(template)
                showTemplateDialog = false
            },
            onDeleteTemplate = { template ->
                viewModel.deleteTemplate(template)
            }
        )
    }
}

@Composable
fun TemplateManagementDialog(
    templates: List<DayTemplate>,
    canSaveCurrent: Boolean,
    onDismiss: () -> Unit,
    onSaveCurrent: (String) -> Unit,
    onApplyTemplate: (DayTemplate) -> Unit,
    onDeleteTemplate: (DayTemplate) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(if (canSaveCurrent) 0 else 1) }
    var templateName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Day Templates") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Save Current", style = MaterialTheme.typography.labelMedium) },
                        enabled = canSaveCurrent
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Apply Saved", style = MaterialTheme.typography.labelMedium) }
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                if (selectedTab == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            text = "Save current activities as a reusable template.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = templateName,
                            onValueChange = { templateName = it },
                            label = { Text("Template Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                } else {
                    if (templates.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No templates saved yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            items(templates) { template ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.sm),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = template.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row {
                                            IconButton(onClick = { onApplyTemplate(template) }) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = "Apply",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(onClick = { onDeleteTemplate(template) }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                TextButton(
                    onClick = {
                        if (templateName.isNotBlank()) {
                            onSaveCurrent(templateName)
                            templateName = ""
                            selectedTab = 1
                        }
                    },
                    enabled = templateName.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun PlannedActivityCard(
    activity: TrainingPlan,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(Spacing.md),
        colors = CardDefaults.cardColors(
            containerColor = activity.type.toColor().copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.md)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(activity.type.toColor()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = activity.type.toIcon(),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.subType ?: activity.type.name.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "${activity.durationMinutes} min",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${activity.plannedTSS} TSS",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = activity.type.toColor()
                    )
                }
            }

            IconButton(
                onClick = {
                    onEdit()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun CompletedWorkoutCard(
    log: WorkoutLog,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(Spacing.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            log.type.toColor().copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.md)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(log.type.toColor().copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = log.type.toIcon(),
                    contentDescription = null,
                    tint = log.type.toColor(),
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Imported ${log.type.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "${log.durationMinutes} min",
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (log.computedTSS != null) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "${log.computedTSS} TSS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Completed",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun WorkoutType.toIcon(): ImageVector {
    return when (this) {
        WorkoutType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
        WorkoutType.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
        WorkoutType.SWIM -> Icons.Default.Pool
        WorkoutType.STRENGTH -> Icons.Default.FitnessCenter
        WorkoutType.OTHER -> Icons.AutoMirrored.Filled.DirectionsWalk
    }
}

