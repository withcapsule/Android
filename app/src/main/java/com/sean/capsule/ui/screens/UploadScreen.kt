package com.sean.capsule.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sean.capsule.ui.viewmodel.SettingsViewModel
import com.sean.capsule.ui.viewmodel.UploadState
import com.sean.capsule.ui.viewmodel.UploadViewModel

@Composable
fun UploadScreen(paddingValues: PaddingValues, settingsViewModel: SettingsViewModel, uploadViewModel: UploadViewModel) {
    var isEncrypted by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    @Suppress("DEPRECATION")
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    val hapticsEnabled by settingsViewModel.hapticsEnabled.collectAsState()
    val baseUrl by settingsViewModel.effectiveBaseUrl.collectAsState()
    val uploadState by uploadViewModel.uploadState.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            selectedFileUri = uri
        }
    )

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
            imageVector = Icons.Default.UploadFile,
            contentDescription = "Send",
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Send File",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedButton(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
            enabled = uploadState is UploadState.Idle || uploadState is UploadState.Success || uploadState is UploadState.Error
        ) {
            Text(if (selectedFileUri == null) "Select File" else "Change File")
        }

        selectedFileUri?.let { uri ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Selected: ${uri.path?.substringAfterLast('/')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Encrypt transfer")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isEncrypted,
                enabled = uploadState is UploadState.Idle || uploadState is UploadState.Success || uploadState is UploadState.Error,
                onCheckedChange = { 
                    isEncrypted = it 
                    if (hapticsEnabled) {
                        if (it) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Upload Button or Progress
        when (val state = uploadState) {
            is UploadState.Encrypting -> {
                UploadProgress(label = "Encrypting...", progress = state.progress)
            }
            is UploadState.Uploading -> {
                UploadProgress(label = "Sending...", progress = state.progress)
            }
            else -> {
                Button(
                    onClick = { 
                        selectedFileUri?.let { uri ->
                            uploadViewModel.uploadFile(context, baseUrl, uri, isEncrypted)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedFileUri != null
                ) {
                    Text("Send File")
                }
            }
        }

        // Terminal States
        AnimatedVisibility(
            visible = uploadState is UploadState.Success || uploadState is UploadState.Error,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val state = uploadState
            Column {
                Spacer(modifier = Modifier.height(24.dp))
                if (state is UploadState.Success) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Send Success!",
                                fontWeight = FontWeight.Bold, 
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("File ID: ${state.fileId}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(state.fileId))
                                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy File ID", modifier = Modifier.size(16.dp))
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Link: ${state.downloadUrl}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(state.downloadUrl))
                                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link", modifier = Modifier.size(16.dp))
                                }
                            }

                            if (state.mnemonic != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Decryption Phrases:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(state.mnemonic, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(state.mnemonic))
                                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Phrases", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Button(
                                onClick = { 
                                    uploadViewModel.resetState() 
                                    selectedFileUri = null
                                },
                                modifier = Modifier.padding(top = 8.dp).align(Alignment.End)
                            ) {
                                Text("Done")
                            }
                        }
                    }
                } else if (state is UploadState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text(state.message, fontSize = 12.sp)
                            Button(
                                onClick = { uploadViewModel.resetState() },
                                modifier = Modifier.padding(top = 8.dp).align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadProgress(label: String, progress: Float) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}
