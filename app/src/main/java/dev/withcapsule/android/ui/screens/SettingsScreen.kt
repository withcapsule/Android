package dev.withcapsule.android.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import dev.withcapsule.android.R
import dev.withcapsule.android.analytics
import dev.withcapsule.android.ui.components.LargeDropdownMenu
import dev.withcapsule.android.ui.viewmodel.ServerOption
import dev.withcapsule.android.ui.viewmodel.SettingsViewModel
import dev.withcapsule.android.ui.viewmodel.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(paddingValues: PaddingValues, settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val serverOptionStr by settingsViewModel.serverOption.collectAsState()
    val selectedOption = try {
        ServerOption.valueOf(serverOptionStr)
    } catch (e: Exception) {
        ServerOption.Default
    }

    val customUrl by settingsViewModel.customUrl.collectAsState()
    val selectedProtocolIndex by settingsViewModel.customProtocolIndex.collectAsState()
    val downloadDirUri by settingsViewModel.downloadDirUri.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()

    val protocols = listOf("https://", "http://")
    val scrollState = rememberScrollState()
    val hapticsEnabled by settingsViewModel.hapticsEnabled.collectAsState()
    val anonymousAnalyticsEnabled by settingsViewModel.anonymousAnalyticsEnabled.collectAsState()
    val haptic = LocalHapticFeedback.current

    val pingResponse by settingsViewModel.pingResponse.collectAsState()
    val isPinging by settingsViewModel.isPinging.collectAsState()

    val folderLauncher = rememberLauncherForActivityResult(
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

    LaunchedEffect(pingResponse) {
        pingResponse?.let { response ->
            analytics.event(url = "/settings", name = "ping_result", data = mapOf("success" to (!response.lowercase().contains("error")).toString()))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(paddingValues)
            .padding( 16.dp, 64.dp, 16.dp, 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.settings_icon_desc),
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_server_config_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ServerOption.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (option == selectedOption),
                            onClick = {
                                settingsViewModel.updateServerOption(option.name)
                                CoroutineScope(Dispatchers.IO).launch {
                                    analytics.event(url = "/settings", name = "server_option_changed", data = mapOf("option" to option.name))
                                }
                            },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (option == selectedOption),
                        onClick = null
                    )
                    Text(
                        text = option.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }

        if (selectedOption == ServerOption.Custom) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LargeDropdownMenu(
                    label = stringResource(R.string.settings_protocol_label),
                    items = protocols,
                    selectedIndex = selectedProtocolIndex,
                    onItemSelected = { index, _ ->
                        settingsViewModel.updateCustomProtocolIndex(index)
                    },
                    modifier = Modifier.width(124.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                OutlinedTextField(
                    value = customUrl,
                    onValueChange = {
                        settingsViewModel.updateCustomUrl(it)
                    },
                    label = { Text(stringResource(R.string.settings_custom_url_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_custom_url_placeholder)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                settingsViewModel.saveAndPingServer()
                CoroutineScope(Dispatchers.IO).launch {
                    analytics.event(url = "/settings", name = "ping_server", data = mapOf("option" to selectedOption.name))
                }
                if (hapticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isPinging && (selectedOption == ServerOption.Default || customUrl.isNotBlank())
        ) {
            if (isPinging) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    if (selectedOption == ServerOption.Default) stringResource(R.string.btn_ping_server)
                    else stringResource(R.string.btn_ping_server_and_save)
                )
            }
        }

        pingResponse?.let { response ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (response.lowercase().contains("error"))
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = response,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_receive_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_receive_dir_label)) },
                supportingContent = {
                    Text(
                        if (downloadDirUri == null) stringResource(R.string.receive_dir_default)
                        else downloadDirUri?.toUri()?.path?.split("/")?.lastOrNull()
                            ?: stringResource(R.string.receive_dir_custom)
                    )
                },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                trailingContent = {
                    TextButton(onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            analytics.event(url = "/settings", name = "change_download_dir")
                        }
                        folderLauncher.launch(null)
                    }) {
                        Text(stringResource(R.string.btn_change_dir))
                    }
                },
                modifier = Modifier.clickable {
                    CoroutineScope(Dispatchers.IO).launch {
                        analytics.event(url = "/settings", name = "change_download_dir")
                    }
                    folderLauncher.launch(null)
                }
            )

            if (downloadDirUri != null) {
                TextButton(
                    onClick = {
                        settingsViewModel.setDownloadDirUri(null)
                        CoroutineScope(Dispatchers.IO).launch {
                            analytics.event(url = "/settings", name = "reset_download_dir")
                        }
                    },
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text(stringResource(R.string.btn_reset_to_default))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_app_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .selectable(
                        selected = hapticsEnabled,
                        onClick = {
                            val newState = !hapticsEnabled
                            settingsViewModel.setHapticsEnabled(newState)
                            CoroutineScope(Dispatchers.IO).launch {
                                analytics.event(url = "/settings", name = "haptics_toggled", data = mapOf("enabled" to newState.toString()))
                            }
                            if (newState) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        role = Role.Switch
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_haptics_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = hapticsEnabled,
                    onCheckedChange = null
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_theme_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                        onClick = {
                            settingsViewModel.setThemeMode(mode)
                            CoroutineScope(Dispatchers.IO).launch {
                                analytics.event(url = "/settings", name = "theme_changed", data = mapOf("mode" to mode.name))
                            }
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        selected = themeMode == mode
                    ) {
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_privacy_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .selectable(
                        selected = anonymousAnalyticsEnabled,
                        onClick = {
                            val newState = !anonymousAnalyticsEnabled
                            if (!newState) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    analytics.event(url = "/settings", name = "analytics_opt_out")
                                }
                            }
                            settingsViewModel.setAnonymousAnalyticsEnabled(newState)
                            if (hapticsEnabled) {
                                if (newState) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        role = Role.Switch
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_analytics_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = anonymousAnalyticsEnabled,
                    onCheckedChange = null
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://withcapsule.dev?utm_source=capsule_android&utm_medium=app&utm_campaign=settings".toUri())) }) {
                    Text("withcapsule.dev", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                Text("·", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://byseansingh.com?utm_source=capsule_android&utm_medium=app&utm_campaign=settings".toUri())) }) {
                    Text("Made byseansingh.com", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://docs.withcapsule.dev/terms/".toUri())) }) {
                    Text("Terms of Service", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                Text("·", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://docs.withcapsule.dev/privacy/".toUri())) }) {
                    Text("Privacy Policy", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }


        }

        Text(
            text = stringResource(R.string.app_version),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 8.dp)
        )

//        Spacer( modifier = Modifier.height(12.dp) )


    }
}
