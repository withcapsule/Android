package com.sean.capsule

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sean.capsule.data.local.SettingsRepository
import com.sean.capsule.ui.screens.*
import com.sean.capsule.ui.theme.CapsuleTheme
import com.sean.capsule.ui.viewmodel.DownloadViewModel
import com.sean.capsule.ui.viewmodel.UploadViewModel
import com.sean.capsule.ui.viewmodel.SettingsViewModel
import com.sean.capsule.ui.viewmodel.ThemeMode
import kotlinx.serialization.Serializable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private lateinit var uploadViewModel: UploadViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepository = SettingsRepository(this)
        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(settingsRepository) as T
                    modelClass.isAssignableFrom(UploadViewModel::class.java) -> UploadViewModel(settingsRepository) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
        
        uploadViewModel = ViewModelProvider(this, viewModelFactory)[UploadViewModel::class.java]
        val settingsViewModel = ViewModelProvider(this, viewModelFactory)[SettingsViewModel::class.java]

        handleIntent(intent)

        setContent {
            val isReady by settingsViewModel.isReady.collectAsState()
            splashScreen.setKeepOnScreenCondition { !isReady }

            val themeMode by settingsViewModel.themeMode.collectAsState()

            CapsuleTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                }
            ) {
                MainContent(settingsViewModel, uploadViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
            }
            uri?.let { uploadViewModel.setSharedFileUri(it) }
        }
    }
}

@Composable
fun MainContent(settingsViewModel: SettingsViewModel, uploadViewModel: UploadViewModel) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }

    val downloadViewModel: DownloadViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DownloadViewModel(settingsRepository) as T
            }
        }
    )
    
    val onboardingCompleted by settingsViewModel.onboardingCompleted.collectAsState()

    if (onboardingCompleted) {
        AppNavigation(settingsViewModel, downloadViewModel, uploadViewModel)
    } else {
        OnboardingScreen(settingsViewModel)
    }
}

@Serializable object Upload
@Serializable object Download
@Serializable object History
@Serializable object Settings
@Serializable data class QRScanner(val target: String)

data class TopLevelRoute<T : Any>(val name: String, val route: T, val icon: ImageVector)

@Composable
fun AppNavigation(
    settingsViewModel: SettingsViewModel,
    downloadViewModel: DownloadViewModel,
    uploadViewModel: UploadViewModel
) {
    val navController = rememberNavController()
    val configuration = LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hapticsEnabled by settingsViewModel.hapticsEnabled.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isScannerVisible = currentDestination?.hasRoute(QRScanner::class) == true
    
    val sharedFileUri by uploadViewModel.sharedFileUri.collectAsState()
    
    LaunchedEffect(sharedFileUri) {
        if (sharedFileUri != null) {
            navController.navigate(Upload) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val routes = listOf(
        TopLevelRoute("Send", Upload, Icons.Default.Upload),
        TopLevelRoute("Receive", Download, Icons.Default.Download),
        TopLevelRoute("History", History, Icons.Default.History),
        TopLevelRoute("Settings", Settings, Icons.Default.Settings)
    )

    Row(modifier = Modifier.fillMaxSize()) {
        if (isLandscape && !isScannerVisible) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
            ) {
                Spacer(Modifier.weight(1f))
                routes.forEachIndexed { index, topLevelRoute ->
                    NavigationRailItem(
                        icon = { Icon(topLevelRoute.icon, contentDescription = topLevelRoute.name) },
                        label = { Text(topLevelRoute.name) },
                        selected = currentDestination?.hierarchy?.any { it.hasRoute(topLevelRoute.route::class) } == true,
                        onClick = {
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
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
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!isLandscape && !isScannerVisible) {
                    NavigationBar {
                        routes.forEach { topLevelRoute ->
                            NavigationBarItem(
                                icon = { Icon(topLevelRoute.icon, contentDescription = topLevelRoute.name) },
                                label = { Text(topLevelRoute.name) },
                                selected = currentDestination?.hierarchy?.any { it.hasRoute(topLevelRoute.route::class) } == true,
                                onClick = {
                                    if (hapticsEnabled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
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
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(animationSpec = tween(250)) },
                exitTransition = { fadeOut(animationSpec = tween(250)) }
            ) {
                composable<Upload> { 
                    UploadScreen(innerPadding, settingsViewModel, uploadViewModel) 
                }
                composable<Download> { 
                    DownloadScreen(innerPadding, navController, settingsViewModel, downloadViewModel) 
                }
                composable<History> { 
                    HistoryScreen(innerPadding, settingsViewModel) 
                }
                composable<Settings> { 
                    SettingsScreen(innerPadding, settingsViewModel)
                }
                composable<QRScanner>(
                    enterTransition = {
                        slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn()
                    },
                    exitTransition = {
                        slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut()
                    }
                ) { backStackEntry ->
                    val route: QRScanner = backStackEntry.arguments?.let { 
                        QRScanner(target = backStackEntry.arguments?.getString("target") ?: "ID_URL")
                    } ?: QRScanner("ID_URL")
                    
                    QRScannerScreen(
                        target = ScannerTarget.valueOf(route.target),
                        onResult = { result ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("scan_result", result)
                            navController.popBackStack()
                        },
                        onClose = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AppNavigationPreview() {
    CapsuleTheme {
        Text("App Navigation Preview")
    }
}
