package com.sean.capsule

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sean.capsule.ui.screens.DownloadScreen
import com.sean.capsule.ui.screens.HistoryScreen
import com.sean.capsule.ui.screens.SettingsScreen
import com.sean.capsule.ui.screens.UploadScreen
import com.sean.capsule.ui.theme.CapsuleTheme
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CapsuleTheme {
                AppNavigation()
            }
        }
    }
}

@Serializable object Upload
@Serializable object Download
@Serializable object History
@Serializable object Settings

data class TopLevelRoute<T : Any>(val name: String, val route: T, val icon: ImageVector)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val routes = listOf(
        TopLevelRoute("Upload", Upload, Icons.Default.Upload),
        TopLevelRoute("Download", Download, Icons.Default.Download),
        TopLevelRoute("History", History, Icons.Default.History),
        TopLevelRoute("Settings", Settings, Icons.Default.Settings)
    )

    Row(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                Spacer(Modifier.weight(1f))
                routes.forEachIndexed { index, topLevelRoute ->
                    NavigationRailItem(
                        icon = { Icon(topLevelRoute.icon, contentDescription = topLevelRoute.name) },
                        label = { Text(topLevelRoute.name) },
                        selected = currentDestination?.hierarchy?.any { it.hasRoute(topLevelRoute.route::class) } == true,
                        onClick = {
                            navController.navigate(topLevelRoute.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    if (index < routes.lastIndex) {
                        Spacer(Modifier.height(48.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isLandscape) {
                    NavigationBar {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        
                        routes.forEach { topLevelRoute ->
                            NavigationBarItem(
                                icon = { Icon(topLevelRoute.icon, contentDescription = topLevelRoute.name) },
                                label = { Text(topLevelRoute.name) },
                                selected = currentDestination?.hierarchy?.any { it.hasRoute(topLevelRoute.route::class) } == true,
                                onClick = {
                                    navController.navigate(topLevelRoute.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Upload,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(animationSpec = tween(250)) },
                exitTransition = { fadeOut(animationSpec = tween(250)) }
            ) {
                composable<Upload> { UploadScreen() }
                composable<Download> { DownloadScreen() }
                composable<History> { HistoryScreen() }
                composable<Settings> { SettingsScreen() }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AppNavigationPreview() {
    CapsuleTheme {
        AppNavigation()
    }
}
