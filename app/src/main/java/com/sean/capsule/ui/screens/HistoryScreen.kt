package com.sean.capsule.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sean.capsule.data.local.HistoryEntry
import com.sean.capsule.data.remote.ApiService
import com.sean.capsule.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(paddingValues: PaddingValues, settingsViewModel: SettingsViewModel) {
    val history by settingsViewModel.history.collectAsState()
    val baseUrl by settingsViewModel.effectiveBaseUrl.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val hapticsEnabled by settingsViewModel.hapticsEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showStatusDialog by remember { mutableStateOf(false) }
    var selectedStatusJson by remember { mutableStateOf<JSONObject?>(null) }
    var selectedId by remember { mutableStateOf("") }
    var isLoadingStatus by remember { mutableStateOf(false) }
    var statusError by remember { mutableStateOf<String?>(null) }

    var itemToDelete by remember { mutableStateOf<HistoryEntry?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp, 64.dp, 16.dp, 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "History",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Recent Activity",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            if (history.isEmpty()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text("No recent activity", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEach { entry ->
                        val swipeToDismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                                    itemToDelete = entry
                                    false
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = swipeToDismissState,
                            backgroundContent = {
                                val color = MaterialTheme.colorScheme.errorContainer
                                val alignment = if (swipeToDismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                val icon = Icons.Default.Delete

                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(color, shape = MaterialTheme.shapes.medium)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = alignment
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true
                        ) {
                            HistoryItem(
                                entry = entry,
                                onInfoClick = {
                                    selectedId = entry.id
                                    showStatusDialog = true
                                    isLoadingStatus = true
                                    statusError = null
                                    selectedStatusJson = null
                                    
                                    coroutineScope.launch {
                                        try {
                                            val apiService = createTempApiService(baseUrl)
                                            val response = apiService.getFileStatus(entry.id)
                                            if (response.isSuccessful) {
                                                val body = response.body()?.string()
                                                if (body != null) {
                                                    selectedStatusJson = JSONObject(body)
                                                } else {
                                                    statusError = "Empty response from server"
                                                }
                                            } else {
                                                val code = response.code();
                                                statusError = if ( code == 404 ) {
                                                    "File not found on server. Either it expired or it was deleted. (Error code 404)"
                                                } else {
                                                    "Server returned $code"
                                                }

                                            }
                                        } catch (e: Exception) {
                                            statusError = e.message ?: "Unknown error"
                                        } finally {
                                            isLoadingStatus = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                modifier = Modifier.padding( 16.dp ),
                enabled = history.isNotEmpty(),
                onClick = { settingsViewModel.clearHistory() }
            ) {
                Text( "Clear history" )
            }
        }
    }

    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text("File Status") },
            text = {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp), contentAlignment = Alignment.Center) {
                    if (isLoadingStatus) {
                        CircularProgressIndicator()
                    } else if (statusError != null) {
                        Text(statusError!!, color = MaterialTheme.colorScheme.error)
                    } else if (selectedStatusJson != null) {
                        StatusContent(selectedStatusJson!!, selectedId, hapticsEnabled)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatusDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    itemToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Entry") },
            text = { Text("What would you like to do with ${entry.fileName}?") },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (entry.isUpload) {
                        Button(
                            onClick = {
                                val id = entry.id
                                settingsViewModel.deleteFileFromServer(
                                    fileId = id,
                                    onSuccess = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("File deleted from server and recents")
                                        }
                                    },
                                    onError = { error ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Error: $error")
                                        }
                                    }
                                )
                                itemToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete from server")
                        }
                    }

                    Button(
                        onClick = {
                            settingsViewModel.removeHistoryItem(entry.id)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Removed from recents")
                            }
                            itemToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Delete from recents")
                    }

                    TextButton(
                        onClick = { itemToDelete = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
fun StatusContent(json: JSONObject, fileId: String, hapticsEnabled: Boolean) {
    val fileName = json.optString("file_name", "Unknown")
    val fileSize = json.optLong("file_size", 0)
    val timeRemaining = json.optLong("time_remaining", 0)
    val isEncrypted = json.optBoolean("is_encrypted", false)
    
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var showCopied by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusRow("ID (tap to copy)", fileId, modifier = Modifier.clickable {
            clipboardManager.setText(AnnotatedString(fileId))
            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            coroutineScope.launch {
                showCopied = true
                delay(2000)
                showCopied = false
            }
        })
        
        AnimatedVisibility(visible = showCopied) {
            Text(
                "ID copied to clipboard!",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        StatusRow("Name", fileName)
        StatusRow("Size", formatFileSize(fileSize))
        StatusRow("Encryption", if (isEncrypted) "Active" else "None")
        StatusRow("Expires in", formatDuration(timeRemaining))
    }
}

@Composable
fun StatusRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HistoryItem(entry: HistoryEntry, onInfoClick: () -> Unit) {
    val timeAgo = formatTimestamp(entry.timestamp)
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (entry.isUpload) Icons.Default.FileUpload else Icons.Default.FileDownload,
                contentDescription = null,
                tint = if (entry.isUpload) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (entry.isEncrypted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Text(
                    text = "ID: ${entry.id}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeAgo,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                if (entry.isUpload) {
                    IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Status",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun createTempApiService(baseUrl: String): ApiService {
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl.let { if (it.endsWith("/")) it else "$it/" })
        .client(okHttpClient)
        .build()
        .create(ApiService::class.java)
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "Expired"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
