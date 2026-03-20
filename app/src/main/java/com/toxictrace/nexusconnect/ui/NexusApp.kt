package com.toxictrace.nexusconnect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toxictrace.nexusconnect.ui.screens.ContactsScreen
import com.toxictrace.nexusconnect.ui.screens.LayoutScreen
import com.toxictrace.nexusconnect.ui.screens.PreferencesScreen
import com.toxictrace.nexusconnect.viewmodel.MainViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Contacts : Screen("contacts", "Contacts", Icons.Default.Person)
    object Layout : Screen("layout", "Layout", Icons.Default.GridView)
    object Preferences : Screen("preferences", "Preferences", Icons.Default.Tune)
}

private val bottomNavItems = listOf(Screen.Contacts, Screen.Layout, Screen.Preferences)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentDest = navBackStack?.destination

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nexus Connect", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label.uppercase()) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Layout.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Contacts.route) { ContactsScreen(viewModel = viewModel) }
            composable(Screen.Layout.route) { LayoutScreen(viewModel = viewModel) }
            composable(Screen.Preferences.route) { PreferencesScreen(viewModel = viewModel) }
        }
    }
}
