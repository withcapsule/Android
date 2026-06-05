package com.sean.capsule.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.sean.capsule.ui.components.LargeDropdownMenu
import com.sean.capsule.ui.viewmodel.SettingsViewModel
import com.sean.capsule.ui.viewmodel.ServerOption

@Composable
fun OnboardingScreen(settingsViewModel: SettingsViewModel) {
    var currentPage by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut())
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut())
                }
            },
            label = "OnboardingContent"
        ) { page ->
            when (page) {
                0 -> WelcomePage(onNext = { currentPage = 1 })
                1 -> ServerConfigPage(
                    settingsViewModel = settingsViewModel,
                    onNext = { currentPage = 2 }
                )
                2 -> CameraPermissionPage(onNext = { currentPage = 3 })
                3 -> DownloadFolderPage(
                    settingsViewModel = settingsViewModel,
                    onNext = { currentPage = 4 }
                )
                4 -> CompletionPage(onFinish = { settingsViewModel.setOnboardingCompleted(true) })
            }
        }
    }
}

@Composable
fun CameraPermissionPage(onNext: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> onNext() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Camera Access",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Capsule can use your camera to scan QR codes for quick receiving. This is completely optional.",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                
                if (hasPermission) {
                    onNext()
                } else {
                    launcher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Grant Permission", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Maybe Later", fontSize = 18.sp)
        }
    }
}

@Composable
fun WelcomePage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.RocketLaunch,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Welcome to Capsule",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "The simplest way to move your files securely across your devices.",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Get Started", fontSize = 18.sp)
        }
    }
}

@Composable
fun ServerConfigPage(settingsViewModel: SettingsViewModel, onNext: () -> Unit) {
    val serverOptionStr by settingsViewModel.serverOption.collectAsState()
    val selectedOption = try {
        ServerOption.valueOf(serverOptionStr)
    } catch (e: Exception) {
        ServerOption.Default
    }
    val customUrl by settingsViewModel.customUrl.collectAsState()
    val selectedProtocolIndex by settingsViewModel.customProtocolIndex.collectAsState()
    val protocols = listOf("https://", "http://")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding( 32.dp, 0.dp, 32.dp, 0.dp )
            .navigationBarsPadding()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Default.Storage,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Server Configuration",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text( "Select Default for an out-of-the-box working experience, or select Custom if self-hosting", textAlign = TextAlign.Center );
        Spacer(modifier = Modifier.height(22.dp))


        Column(
            modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 0.dp).fillMaxWidth()
        ) {
            ServerOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (option == selectedOption),
                            onClick = {
                                settingsViewModel.updateServerOption(option.name)
                            },
                            role = Role.RadioButton
                        )
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (option == selectedOption),
                        onClick = null
                    )
                    Text(
                        text = option.displayName,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }


        if (selectedOption == ServerOption.Custom) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                LargeDropdownMenu(
                    label = "Protocol",
                    items = protocols,
                    selectedIndex = selectedProtocolIndex,
                    onItemSelected = { index, _ -> settingsViewModel.updateCustomProtocolIndex(index) },
                    modifier = Modifier.width(124.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { settingsViewModel.updateCustomUrl(it) },
                    label = { Text("URL") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }

//        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {
                if (selectedOption == ServerOption.Custom) {
                    settingsViewModel.saveServerConfig()
                }
                onNext()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(0.dp, 12.dp, 0.dp, 0.dp),
            enabled = selectedOption == ServerOption.Default || customUrl.isNotBlank()
        ) {
            Text("Continue", fontSize = 18.sp)
        }
    }
}

@Composable
fun DownloadFolderPage(settingsViewModel: SettingsViewModel, onNext: () -> Unit) {
    val context = LocalContext.current
    val downloadDirUri by settingsViewModel.downloadDirUri.collectAsState()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                settingsViewModel.setDownloadDirUri(it.toString())
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Receive Directory",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Choose where you want to save your received files.",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = downloadDirUri == null,
                            onClick = { settingsViewModel.setDownloadDirUri(null) },
                            role = Role.RadioButton
                        )
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = downloadDirUri == null, onClick = null)
                    Text(text = "Default (Downloads/Capsules)", modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = downloadDirUri != null,
                            onClick = { launcher.launch(null) },
                            role = Role.RadioButton
                        )
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = downloadDirUri != null, onClick = null)
                    Text(
                        text = if (downloadDirUri == null) "Custom Directory..." 
                               else "Custom: ${Uri.parse(downloadDirUri).path?.split("/")?.lastOrNull() ?: "Selected"}", 
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Continue", fontSize = 18.sp)
        }
    }
}

@Composable
fun CompletionPage(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "You're all set!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Capsule is ready to help you move files. Enjoy the seamless experience!",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Enter App", fontSize = 18.sp)
        }
    }
}
