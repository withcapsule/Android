package dev.withcapsule.android.ui.screens

import android.content.res.Resources
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.withcapsule.android.R
import dev.withcapsule.android.analytics
import dev.withcapsule.android.data.local.HistoryEntry
import dev.withcapsule.android.data.remote.FileStatus
import dev.withcapsule.android.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(paddingValues: PaddingValues, settingsViewModel: SettingsViewModel) {
    val history by settingsViewModel.history.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val hapticsEnabled by settingsViewModel.hapticsEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showStatusDialog by rememberSaveable { mutableStateOf(false) }
    val selectedStatus by settingsViewModel.fileStatus.collectAsState()
    val isLoadingStatus by settingsViewModel.isLoadingStatus.collectAsState()
    val statusError by settingsViewModel.statusError.collectAsState()
    val selectedId by settingsViewModel.selectedStatusId.collectAsState()

    var itemToDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteDirection by rememberSaveable { mutableStateOf<SwipeToDismissBoxValue?>(null) }
    val itemToDelete = remember(itemToDeleteId, history) {
        history.find { it.id == itemToDeleteId }
    }

    val context = LocalContext.current
    val strDeletedFromServer = stringResource(R.string.snackbar_deleted_from_server)
    val strRemovedFromRecents = stringResource(R.string.snackbar_removed_from_recents)

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
                contentDescription = stringResource(R.string.history_icon_desc),
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.history_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.history_swipe_hint),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (history.isEmpty()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.forEach { entry ->
                        key("${entry.id}_${entry.timestamp}") {
                            val isBeingDeleted = itemToDeleteId == entry.id
                            val swipeToDismissState = rememberSwipeToDismissBoxState(
                                initialValue = if (isBeingDeleted) (deleteDirection ?: SwipeToDismissBoxValue.EndToStart) else SwipeToDismissBoxValue.Settled,
                                confirmValueChange = { value ->
                                    if (value != SwipeToDismissBoxValue.Settled) {
                                        itemToDeleteId = entry.id
                                        deleteDirection = value
                                        true
                                    } else {
                                        true
                                    }
                                }
                            )

                            LaunchedEffect(isBeingDeleted) {
                                if (!isBeingDeleted && swipeToDismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                    swipeToDismissState.reset()
                                }
                            }

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
                                            contentDescription = stringResource(R.string.icon_delete_desc),
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
                                        showStatusDialog = true
                                        settingsViewModel.fetchFileStatus(entry.id)
                                        CoroutineScope(Dispatchers.IO).launch {
                                            analytics.event(url = "/history", name = "view_file_status", data = mapOf("id" to entry.id))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                modifier = Modifier.padding( 16.dp ),
                enabled = history.isNotEmpty(),
                onClick = {
                    settingsViewModel.clearHistory()
                    CoroutineScope(Dispatchers.IO).launch {
                        analytics.event(url = "/history", name = "clear_history")
                    }
                }
            ) {
                Text( stringResource(R.string.btn_clear_history) )
            }
        }
    }

    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = {
                showStatusDialog = false
                settingsViewModel.clearFileStatus()
            },
            title = { Text(stringResource(R.string.dialog_file_status_title)) },
            text = {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp), contentAlignment = Alignment.Center) {
                    if (isLoadingStatus) {
                        CircularProgressIndicator()
                    } else if (statusError != null) {
                        Text(statusError!!, color = MaterialTheme.colorScheme.error)
                    } else if (selectedStatus != null) {
                        StatusContent(selectedStatus!!, selectedId, hapticsEnabled)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showStatusDialog = false
                    settingsViewModel.clearFileStatus()
                }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    itemToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { itemToDeleteId = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message, entry.fileName)) },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (entry.isUpload) {
                        Button(
                            onClick = {
                                val id = entry.id
                                CoroutineScope(Dispatchers.IO).launch {
                                    analytics.event(url = "/history", name = "delete_from_server", data = mapOf("id" to id))
                                }
                                settingsViewModel.deleteFileFromServer(
                                    fileId = id,
                                    onSuccess = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(strDeletedFromServer)
                                        }
                                    },
                                    onError = { error ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_error, error))
                                        }
                                    }
                                )
                                itemToDeleteId = null
                                deleteDirection = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.btn_delete_from_server))
                        }
                    }

                    Button(
                        onClick = {
                            settingsViewModel.removeHistoryItem(entry.id, entry.timestamp)
                            CoroutineScope(Dispatchers.IO).launch {
                                analytics.event(url = "/history", name = "remove_from_recents", data = mapOf("id" to entry.id))
                            }
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(strRemovedFromRecents)
                            }
                            itemToDeleteId = null
                            deleteDirection = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(R.string.btn_delete_from_recents))
                    }

                    TextButton(
                        onClick = {
                            itemToDeleteId = null
                            deleteDirection = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            }
        )
    }
}

@Composable
fun StatusContent(status: FileStatus, fileId: String, hapticsEnabled: Boolean) {
    val fileName = status.file_name
    val fileSize = status.file_size
    val timeRemaining = status.time_remaining
    val isEncrypted = status.is_encrypted

    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    var showCopied by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusRow(stringResource(R.string.status_id_label), fileId, modifier = Modifier.clickable {
            clipboardManager.setText(AnnotatedString(fileId))
            CoroutineScope(Dispatchers.IO).launch {
                analytics.event(url = "/history", name = "copy_id_from_status")
            }
            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            coroutineScope.launch {
                showCopied = true
                delay(2000)
                showCopied = false
            }
        })

        AnimatedVisibility(visible = showCopied) {
            Text(
                stringResource(R.string.status_id_copied),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        StatusRow(stringResource(R.string.status_name_label), fileName)
        StatusRow(stringResource(R.string.status_size_label), formatFileSize(fileSize))
        StatusRow(
            stringResource(R.string.status_encryption_label),
            if (isEncrypted) stringResource(R.string.status_encryption_active)
            else stringResource(R.string.status_encryption_none)
        )
        StatusRow(stringResource(R.string.status_expires_label), formatDuration(resources, timeRemaining))
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
    val resources = LocalContext.current.resources
    val timeAgo = formatTimestamp(resources, entry.timestamp)

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
                            contentDescription = stringResource(R.string.history_item_encrypted_desc),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.history_item_id, entry.id),
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
                            contentDescription = stringResource(R.string.history_item_status_desc),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

private fun formatDuration(resources: Resources, seconds: Long): String {
    if (seconds <= 0) return resources.getString(R.string.duration_expired)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) resources.getString(R.string.duration_hours_minutes, h, m)
           else resources.getString(R.string.duration_minutes, m)
}

private fun formatTimestamp(resources: Resources, timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> resources.getString(R.string.timestamp_just_now)
        diff < 3600_000 -> resources.getString(R.string.timestamp_minutes_ago, (diff / 60_000).toInt())
        diff < 86400_000 -> resources.getString(R.string.timestamp_hours_ago, (diff / 3600_000).toInt())
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
