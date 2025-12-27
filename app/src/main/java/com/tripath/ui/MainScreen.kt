package com.tripath.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tripath.ui.navigation.Screen
import com.tripath.ui.navigation.TriPathNavigation
import com.tripath.ui.theme.TriPathTheme

data class NavigationItem(
    val icon: ImageVector,
    val label: String,
    val route: String
)

@Composable
private fun CustomNavigationBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (selected) indicatorColor else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) selectedColor else unselectedColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) selectedColor else unselectedColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    val navigationItems = listOf(
        NavigationItem(Icons.Default.Dashboard, "Dashboard", Screen.Dashboard.route),
        NavigationItem(Icons.Default.CalendarMonth, "Planner", Screen.WeeklyPlanner.route),
        NavigationItem(Icons.Default.BarChart, "Stats", Screen.Stats.route),
        NavigationItem(Icons.Default.Person, "Coach", Screen.Coach.route),
        NavigationItem(Icons.Default.Spa, "Recovery", Screen.Recovery.route),
        NavigationItem(Icons.Default.Settings, "Settings", Screen.Settings.route)
    )

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (item in navigationItems) {
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        
                        CustomNavigationBarItem(
                            icon = item.icon,
                            label = item.label,
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        TriPathNavigation(
            navController = navController,
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    TriPathTheme {
        MainScreen()
    }
}

