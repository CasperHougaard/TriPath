package com.tripath.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tripath.ui.dashboard.DashboardScreen
import com.tripath.ui.planner.WeeklyPlannerScreen
import com.tripath.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object WeeklyPlanner : Screen("planner")
    object Settings : Screen("settings")
    object Progress : Screen("progress") // Placeholder for future CTL/ATL graph
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
            DashboardScreen()
        }
        composable(Screen.WeeklyPlanner.route) {
            WeeklyPlannerScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        // Progress screen placeholder for future implementation
        composable(Screen.Progress.route) {
            SettingsScreen() // Temporary placeholder
        }
    }
}

