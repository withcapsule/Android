package com.sean.capsule.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.sean.capsule.ui.components.LargeDropdownMenu
import com.sean.capsule.ui.viewmodel.SettingsViewModel
import com.sean.capsule.ui.viewmodel.ServerOption
import com.sean.capsule.ui.viewmodel.ThemeMode

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(paddingValues)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Server Configuration",
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
                    label = "Protocol",
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
                    label = { Text("Custom Server URL") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("your-server.com") }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                settingsViewModel.saveAndPingServer()
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
                    if (selectedOption == ServerOption.Default) "Ping Server" 
                    else "Ping Server URL and Save"
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
                text = "Download Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("Download Directory") },
                supportingContent = { 
                    Text(
                        if (downloadDirUri == null) "Default (Downloads/Capsules)"
                        else downloadDirUri?.toUri()?.path?.split("/")?.lastOrNull() ?: "Custom"
                    )
                },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                trailingContent = {
                    TextButton(onClick = { folderLauncher.launch(null) }) {
                        Text("Change")
                    }
                },
                modifier = Modifier.clickable { folderLauncher.launch(null) }
            )
            
            if (downloadDirUri != null) {
                TextButton(
                    onClick = { settingsViewModel.setDownloadDirUri(null) },
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text("Reset to Default")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "App Settings",
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
                    text = "In-app Haptics",
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
                text = "App Theme",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (themeMode == mode),
                            onClick = { settingsViewModel.setThemeMode(mode) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (themeMode == mode),
                        onClick = null
                    )
                    Text(
                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
        
//        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Capsule Android v1.0",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
