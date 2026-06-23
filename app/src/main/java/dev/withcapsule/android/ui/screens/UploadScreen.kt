package dev.withcapsule.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.withcapsule.android.R
import dev.withcapsule.android.analytics
import dev.withcapsule.android.ui.viewmodel.SettingsViewModel
import dev.withcapsule.android.ui.viewmodel.UploadState
import dev.withcapsule.android.ui.viewmodel.UploadViewModel
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrColors
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.QrShapes
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun UploadScreen(paddingValues: PaddingValues, settingsViewModel: SettingsViewModel, uploadViewModel: UploadViewModel) {
    var isEncrypted by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var qrDialogData by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    @Suppress("DEPRECATION")
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val hapticsEnabled by settingsViewModel.hapticsEnabled.collectAsState()
    val baseUrl by settingsViewModel.effectiveBaseUrl.collectAsState()
    val uploadState by uploadViewModel.uploadState.collectAsState()
    val sharedFileUri by uploadViewModel.sharedFileUri.collectAsState()

    val qrDecryptionTitle = stringResource(R.string.qr_dialog_decryption_title)
    val qrDownloadTitle = stringResource(R.string.qr_dialog_download_title)

    LaunchedEffect(sharedFileUri) {
        sharedFileUri?.let {
            selectedFileUri = it
            uploadViewModel.setSharedFileUri(null)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            selectedFileUri = uri
            if (uri != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    analytics.event(url = "/upload", name = "file_selected")
                }
            }
        }
    )

    LaunchedEffect(uploadState) {
        when (val state = uploadState) {
            is UploadState.Success -> {
                analytics.event(url = "/upload", name = "upload_success", data = mapOf("encrypted" to (state.mnemonic != null).toString()))
            }
            is UploadState.Error -> {
                analytics.event(url = "/upload", name = "upload_error", data = mapOf("message" to state.message))
            }
            else -> {}
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
            imageVector = Icons.Default.UploadFile,
            contentDescription = stringResource(R.string.upload_icon_desc),
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.upload_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
            enabled = uploadState is UploadState.Idle || uploadState is UploadState.Success || uploadState is UploadState.Error
        ) {
            Text(if (selectedFileUri == null) stringResource(R.string.btn_select_file) else stringResource(R.string.btn_change_file))
        }

        selectedFileUri?.let { uri ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.upload_selected_file, uri.path?.substringAfterLast('/') ?: ""),
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
            Text(stringResource(R.string.upload_encrypt_label))
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isEncrypted,
                enabled = uploadState is UploadState.Idle || uploadState is UploadState.Success || uploadState is UploadState.Error,
                onCheckedChange = {
                    isEncrypted = it
                    CoroutineScope(Dispatchers.IO).launch {
                        analytics.event(url = "/upload", name = "encryption_toggled", data = mapOf("enabled" to it.toString()))
                    }
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

        when (val state = uploadState) {
            is UploadState.Encrypting -> {
                UploadProgress(label = stringResource(R.string.upload_encrypting), progress = state.progress)
            }
            is UploadState.Uploading -> {
                UploadProgress(label = stringResource(R.string.upload_sending), progress = state.progress)
            }
            else -> {
                Button(
                    onClick = {
                        selectedFileUri?.let { uri ->
                            CoroutineScope(Dispatchers.IO).launch {
                                analytics.event(url = "/upload", name = "upload_started", data = mapOf("encrypted" to isEncrypted.toString()))
                            }
                            uploadViewModel.uploadFile(context, baseUrl, uri, isEncrypted)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedFileUri != null
                ) {
                    Text(stringResource(R.string.btn_send_file))
                }
            }
        }

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
                                stringResource(R.string.upload_success_title),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.upload_file_id, state.fileId), fontSize = 14.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(state.fileId))
                                    CoroutineScope(Dispatchers.IO).launch {
                                        analytics.event(url = "/upload", name = "copy_file_id")
                                    }
                                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.icon_copy_file_id_desc), modifier = Modifier.size(16.dp))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.upload_link, state.downloadUrl), fontSize = 12.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(state.downloadUrl))
                                    CoroutineScope(Dispatchers.IO).launch {
                                        analytics.event(url = "/upload", name = "copy_download_url")
                                    }
                                    if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.icon_copy_link_desc), modifier = Modifier.size(16.dp))
                                }
                            }

                            if (state.mnemonic != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.upload_decryption_phrases_label), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(state.mnemonic, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(state.mnemonic))
                                        CoroutineScope(Dispatchers.IO).launch {
                                            analytics.event(url = "/upload", name = "copy_mnemonic")
                                        }
                                        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.icon_copy_phrases_desc), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (state.mnemonic != null) {
                                    Button(
                                        onClick = {
                                            qrDialogData = qrDecryptionTitle to state.mnemonic
                                            CoroutineScope(Dispatchers.IO).launch {
                                                analytics.event(url = "/upload", name = "show_qr_mnemonic")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.btn_show_decryption_qr))
                                    }
                                }
                                Button(
                                    onClick = {
                                        qrDialogData = qrDownloadTitle to state.downloadUrl
                                        CoroutineScope(Dispatchers.IO).launch {
                                            analytics.event(url = "/upload", name = "show_qr_download")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.btn_show_download_qr))
                                }
                                Button(
                                    onClick = {
                                        uploadViewModel.resetState()
                                        selectedFileUri = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.btn_done))
                                }
                            }

                            qrDialogData?.let { (title, content) ->
                                AlertDialog(
                                    onDismissRequest = { qrDialogData = null },
                                    confirmButton = {
                                        TextButton(onClick = { qrDialogData = null }) {
                                            Text(stringResource(R.string.btn_close))
                                        }
                                    },
                                    title = { Text(title) },
                                    text = {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = rememberQrCodePainter(
                                                    data = content,
                                                    shapes = QrShapes(
                                                        ball = QrBallShape.roundCorners(.25f),
                                                        darkPixel = QrPixelShape.roundCorners(.5f),
                                                        frame = QrFrameShape.roundCorners(.25f)
                                                    ),
                                                    colors = QrColors(
                                                        dark = QrBrush.solid(MaterialTheme.colorScheme.primary)
                                                    )
                                                ),
                                                contentDescription = stringResource(R.string.qr_code_desc),
                                                modifier = Modifier.size(200.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else if (state is UploadState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.label_error), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text(state.message, fontSize = 12.sp)
                            Button(
                                onClick = { uploadViewModel.resetState() },
                                modifier = Modifier.padding(top = 8.dp).align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.btn_dismiss))
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
