package com.tripath.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tripath.ui.coach.CoachScreen
import com.tripath.ui.dashboard.DashboardScreen
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
    object Settings : Screen("settings")
    object ProfileEditor : Screen("profile_editor")
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
            CoachScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
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
    }
}
