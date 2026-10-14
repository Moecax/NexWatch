package com.nexwatch.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.theme.SkyBlue
import com.nexwatch.core.designsystem.theme.WatchTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import com.nexwatch.feature.health.HealthRoute
import com.nexwatch.feature.today.TodayRoute
import com.nexwatch.feature.watch.WatchRoute
import com.nexwatch.ui.data.DataRoute
import com.nexwatch.ui.diagnostics.DiagnosticsScreen

@Composable
fun NexWatchNavHost() {
    val navController = rememberNavController()

    val hairline = WatchTheme.colors.border.copy(alpha = 0.5f)
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = currentBackStackEntry?.destination

            // Tab switches are instant on purpose (Part C: never animate an action repeated all day).
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceDim.copy(alpha = 0.94f),
                modifier = Modifier.drawBehind {
                    drawLine(hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                },
            ) {
                NexWatchDestination.bottomNavItems.forEach { destination ->
                    val selected = currentDestination?.hasRoute(destination::class) == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon(selected), contentDescription = null) },
                        label = { Text(destination.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SkyBlue,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NexWatchDestination.Today,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<NexWatchDestination.Today> { TodayRoute() }
            composable<NexWatchDestination.Health> { HealthRoute() }
            composable<NexWatchDestination.Watch> {
                WatchRoute(onOpenDiagnostics = { navController.navigate(WatchDiagnostics) })
            }
            composable<WatchDiagnostics> { DiagnosticsScreen(onBack = { navController.navigateUp() }) }
            composable<NexWatchDestination.Data> { DataRoute() }
        }
    }
}
