package com.sean.capsule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    
    val routes = listOf(
        TopLevelRoute("Upload", Upload, Icons.Default.Upload),
        TopLevelRoute("Download", Download, Icons.Default.Download),
        TopLevelRoute("History", History, Icons.Default.History),
        TopLevelRoute("Settings", Settings, Icons.Default.Settings)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Upload,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Upload> { UploadScreen() }
            composable<Download> { DownloadScreen() }
            composable<History> { HistoryScreen() }
            composable<Settings> { SettingsScreen() }
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
