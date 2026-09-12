package com.nexwatch.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.nexwatch.ui.PlaceholderScreen

@Composable
fun NexWatchNavHost() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = currentBackStackEntry?.destination

            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
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
                        icon = { androidx.compose.material3.Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
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
            composable<NexWatchDestination.Today> { PlaceholderScreen(title = "Today") }
            composable<NexWatchDestination.Health> { PlaceholderScreen(title = "Health") }
            composable<NexWatchDestination.Watch> { PlaceholderScreen(title = "Watch") }
            composable<NexWatchDestination.Data> { PlaceholderScreen(title = "Data") }
        }
    }
}
