package dev.withcapsule.android.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.withcapsule.android.R
import dev.withcapsule.android.analytics
import dev.withcapsule.android.ui.components.LargeDropdownMenu
import dev.withcapsule.android.ui.viewmodel.ServerOption
import dev.withcapsule.android.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(settingsViewModel: SettingsViewModel) {
    var currentPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentPage) {
        val pageName = when (currentPage) {
            0 -> "onboarding_welcome"
            1 -> "onboarding_server_config"
            2 -> "onboarding_camera_permission"
            3 -> "onboarding_download_folder"
            4 -> "onboarding_completion"
            else -> "onboarding_unknown"
        }
        CoroutineScope(Dispatchers.IO).launch {
            analytics.event(url = "/onboarding", name = pageName)
        }
    }

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
            text = stringResource(R.string.onboarding_camera_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_camera_message),
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
            Text(stringResource(R.string.btn_grant_permission), fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.btn_maybe_later), fontSize = 18.sp)
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
            text = stringResource(R.string.onboarding_welcome_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_message),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.btn_get_started), fontSize = 18.sp)
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
            .verticalScroll(rememberScrollState())
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
            text = stringResource(R.string.onboarding_server_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(stringResource(R.string.onboarding_server_message), textAlign = TextAlign.Center)
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
                    label = stringResource(R.string.settings_protocol_label),
                    items = protocols,
                    selectedIndex = selectedProtocolIndex,
                    onItemSelected = { index, _ -> settingsViewModel.updateCustomProtocolIndex(index) },
                    modifier = Modifier.width(124.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { settingsViewModel.updateCustomUrl(it) },
                    label = { Text(stringResource(R.string.onboarding_url_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (selectedOption == ServerOption.Custom) {
                    settingsViewModel.saveServerConfig()
                }
                CoroutineScope(Dispatchers.IO).launch {
                    analytics.event(url = "/onboarding/server_config", name = "server_option_selected", data = mapOf("option" to selectedOption.name))
                }
                onNext()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(0.dp, 12.dp, 0.dp, 0.dp),
            enabled = selectedOption == ServerOption.Default || customUrl.isNotBlank()
        ) {
            Text(stringResource(R.string.btn_continue), fontSize = 18.sp)
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
            .verticalScroll( rememberScrollState() )
            .padding( 32.dp, 48.dp, 32.dp, 48.dp ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_download_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_download_message),
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
                    Text(text = stringResource(R.string.receive_dir_default), modifier = Modifier.padding(start = 8.dp))
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
                        text = if (downloadDirUri == null) stringResource(R.string.onboarding_download_custom)
                               else stringResource(
                                   R.string.onboarding_download_custom_selected,
                                   Uri.parse(downloadDirUri).path?.split("/")?.lastOrNull()
                                       ?: stringResource(R.string.receive_dir_custom)
                               ),
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
            Text(stringResource(R.string.btn_continue), fontSize = 18.sp)
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
            text = stringResource(R.string.onboarding_complete_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_complete_message),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    analytics.event(url = "/onboarding/completion", name = "onboarding_finished")
                }
                onFinish()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.btn_enter_app), fontSize = 18.sp)
        }
    }
}
