package com.tripath.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tripath.ui.activities.AddManualActivityScreen
import com.tripath.ui.coach.CoachScreen
import com.tripath.ui.data.DataCategory
import com.tripath.ui.data.DataCategoryScreen
import com.tripath.ui.data.MyDataScreen
import com.tripath.ui.planner.AutoPlannerSettingsScreen
import com.tripath.ui.planner.RunningGoalEditorScreen
import com.tripath.ui.dashboard.DashboardScreen
import com.tripath.ui.health.HealthScreen
import com.tripath.ui.health.ManageHealthDataScreen
import com.tripath.ui.health.bodyscan.BodyScanDetailScreen
import com.tripath.ui.health.nutrition.NutritionDetailScreen
import com.tripath.ui.health.nutrition.barcode.BarcodeScanScreen
import com.tripath.ui.health.sleep.SleepDetailScreen
import com.tripath.ui.planner.WeeklyPlannerScreen
import com.tripath.ui.progress.ProgressScreen
import com.tripath.ui.settings.ProfileEditorScreen
import com.tripath.ui.settings.SettingsScreen
import com.tripath.ui.stats.StatsScreen
import java.time.LocalDate

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object WeeklyPlanner : Screen("planner")
    object Stats : Screen("stats")
    object Coach : Screen("coach")
    object ReadinessDetail : Screen("readiness_detail")
    object Health : Screen("health")
    object ManageHealthData : Screen("health_manage_data")
    object BodyScanDetail : Screen("body_scan_detail")
    object SleepDetail : Screen("sleep_detail")
    object NutritionDetail : Screen("nutrition_detail")
    object NutritionBarcodeScan : Screen("nutrition_barcode_scan")
    object Settings : Screen("settings")
    object SyncedExercises : Screen("synced_exercises")
    object ExerciseImportDetail : Screen("exercise_import_detail/{sessionId}") {
        fun createRoute(sessionId: String): String {
            return "exercise_import_detail/$sessionId"
        }
    }
    object ProfileEditor : Screen("profile_editor")
    object MyData : Screen("my_data")
    object DataCategoryDetail : Screen("data_category/{categoryId}") {
        fun createRoute(category: DataCategory): String = "data_category/${category.id}"
    }
    object AutoPlannerSettings : Screen("planner_auto_planner_settings")
    object LegacyPlanningSettings : Screen("planning_settings")
    object RunningGoalEditor : Screen("running_goal_editor")
    object Progress : Screen("progress") // Kept for backward compatibility or deep linking
    object WorkoutDetail : Screen("workout_detail/{workoutId}/{isPlanned}") {
        fun createRoute(workoutId: String, isPlanned: Boolean): String {
            return "workout_detail/$workoutId/$isPlanned"
        }
    }
    object DayDetail : Screen("day_detail/{epochDay}") {
        fun createRoute(date: LocalDate): String {
            return "day_detail/${date.toEpochDay()}"
        }
    }
    object AddManualActivity : Screen("add_manual_activity/{epochDay}") {
        fun createRoute(date: LocalDate): String = "add_manual_activity/${date.toEpochDay()}"
    }
}

@Composable
fun TriPathNavigation(
    navController: NavHostController,
    startDestination: String = Screen.Dashboard.route,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(Screen.WeeklyPlanner.route) {
            WeeklyPlannerScreen(navController = navController)
        }
        composable(Screen.Stats.route) {
            StatsScreen()
        }
        composable(Screen.Coach.route) {
            CoachScreen(navController = navController)
        }
        composable(Screen.ReadinessDetail.route) {
            com.tripath.ui.coach.detail.ReadinessDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AutoPlannerSettings.route) {
            AutoPlannerSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGoalEditor = { navController.navigate(Screen.RunningGoalEditor.route) }
            )
        }
        composable(Screen.Health.route) {
            HealthScreen(
                onNavigateToBodyScanDetail = { navController.navigate(Screen.BodyScanDetail.route) },
                onNavigateToSleepDetail = { navController.navigate(Screen.SleepDetail.route) },
                onNavigateToNutritionDetail = { navController.navigate(Screen.NutritionDetail.route) }
            )
        }
        composable(Screen.ManageHealthData.route) {
            ManageHealthDataScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.BodyScanDetail.route) {
            BodyScanDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToManageData = { navController.navigate(Screen.ManageHealthData.route) }
            )
        }
        composable(Screen.SleepDetail.route) {
            SleepDetailScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.NutritionDetail.route) {
            NutritionDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onScanBarcode = { navController.navigate(Screen.NutritionBarcodeScan.route) }
            )
        }
        composable(Screen.NutritionBarcodeScan.route) {
            BarcodeScanScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.LegacyPlanningSettings.route) {
            AutoPlannerSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGoalEditor = { navController.navigate(Screen.RunningGoalEditor.route) }
            )
        }
        composable(Screen.RunningGoalEditor.route) {
            RunningGoalEditorScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.MyData.route) {
            MyDataScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCategory = { category ->
                    navController.navigate(Screen.DataCategoryDetail.createRoute(category))
                }
            )
        }
        composable(
            route = Screen.DataCategoryDetail.route,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val category = DataCategory.fromId(backStackEntry.arguments?.getString("categoryId"))
            if (category == null) {
                // Unknown id (e.g. a stale deep link) — nothing to show, so go back.
                navController.popBackStack()
            } else {
                DataCategoryScreen(
                    category = category,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.SyncedExercises.route) {
            com.tripath.ui.settings.healthconnect.SyncedExercisesScreen(
                onNavigateBack = { navController.popBackStack() },
                onExerciseClick = { sessionId ->
                    navController.navigate(Screen.ExerciseImportDetail.createRoute(sessionId))
                }
            )
        }
        composable(
            route = Screen.ExerciseImportDetail.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            com.tripath.ui.settings.healthconnect.ExerciseImportDetailScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ProfileEditor.route) {
            ProfileEditorScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Progress.route) {
            ProgressScreen()
        }
        composable(
            route = Screen.WorkoutDetail.route,
            arguments = listOf(
                navArgument("workoutId") { type = NavType.StringType },
                navArgument("isPlanned") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getString("workoutId") ?: ""
            val isPlanned = backStackEntry.arguments?.getBoolean("isPlanned") ?: false
            com.tripath.ui.details.WorkoutDetailScreen(
                workoutId = workoutId,
                isPlanned = isPlanned,
                navController = navController
            )
        }
        composable(
            route = Screen.DayDetail.route,
            arguments = listOf(
                navArgument("epochDay") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val epochDay = backStackEntry.arguments?.getLong("epochDay") ?: LocalDate.now().toEpochDay()
            val date = LocalDate.ofEpochDay(epochDay)
            com.tripath.ui.daydetail.DayDetailScreen(
                date = date,
                navController = navController
            )
        }
        composable(
            route = Screen.AddManualActivity.route,
            arguments = listOf(
                navArgument("epochDay") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val epochDay = backStackEntry.arguments?.getLong("epochDay") ?: LocalDate.now().toEpochDay()
            val date = LocalDate.ofEpochDay(epochDay)
            AddManualActivityScreen(
                prefillDate = date,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
