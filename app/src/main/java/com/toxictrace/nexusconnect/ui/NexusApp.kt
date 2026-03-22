package com.toxictrace.nexusconnect.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toxictrace.nexusconnect.data.model.AppTheme
import androidx.compose.ui.res.stringResource
import com.toxictrace.nexusconnect.R
import com.toxictrace.nexusconnect.ui.screens.ContactsScreen
import com.toxictrace.nexusconnect.ui.screens.LayoutScreen
import com.toxictrace.nexusconnect.ui.screens.PermissionsScreen
import com.toxictrace.nexusconnect.ui.screens.PreferencesScreen
import com.toxictrace.nexusconnect.ui.screens.REQUIRED_PERMISSIONS
import com.toxictrace.nexusconnect.ui.theme.NexusConnectTheme
import com.toxictrace.nexusconnect.viewmodel.MainViewModel

sealed class Screen(val route: String, val labelRes: Int, val icon: ImageVector) {
    object Contacts    : Screen("contacts",    R.string.nav_contacts,    Icons.Default.Person)
    object Layout      : Screen("layout",      R.string.nav_layout,      Icons.Default.GridView)
    object Preferences : Screen("preferences", R.string.nav_preferences, Icons.Default.Tune)
}

private val bottomNavItems = listOf(Screen.Contacts, Screen.Layout, Screen.Preferences)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentDest = navBackStack?.destination
    val settings by viewModel.settings.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionsGranted by remember {
        mutableStateOf(
            REQUIRED_PERMISSIONS.filter { it.required }.all {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, it.permission
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    if (!permissionsGranted) {
        val isDark2 = isSystemInDarkTheme()
        NexusConnectTheme(darkTheme = isDark2) {
            PermissionsScreen(onAllGranted = { permissionsGranted = true })
        }
        return
    }

    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (settings.theme) {
        AppTheme.DARK   -> true
        AppTheme.LIGHT  -> false
        AppTheme.SYSTEM -> isSystemDark
    }

    NexusConnectTheme(
        darkTheme    = isDark,
        dynamicColor = settings.dynamicColors,
        accentIndex  = settings.accentColorIndex
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
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
                            icon = { Icon(screen.icon, contentDescription = stringResource(screen.labelRes)) },
                            label = { Text(stringResource(screen.labelRes).uppercase()) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            val density = LocalDensity.current
            val imeBottom = WindowInsets.ime.getBottom(density)
            val barBottom = paddingValues.calculateBottomPadding()
            val bottomPadding = with(density) { maxOf(imeBottom.toDp(), barBottom) }

            Column(
                modifier = Modifier.padding(
                    top    = paddingValues.calculateTopPadding(),
                    bottom = bottomPadding
                )
            ) {
                BatteryOptimizationBanner()
                NavHost(
                    navController = navController,
                    startDestination = Screen.Contacts.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Screen.Contacts.route)    { ContactsScreen(viewModel = viewModel) }
                    composable(Screen.Layout.route)      { LayoutScreen(viewModel = viewModel) }
                    composable(Screen.Preferences.route) { PreferencesScreen(viewModel = viewModel) }
                }
            }
        }
    }
}
