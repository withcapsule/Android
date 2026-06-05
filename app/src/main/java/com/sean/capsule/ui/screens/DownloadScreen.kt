package com.sean.capsule.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sean.capsule.QRScanner
import com.sean.capsule.ui.viewmodel.DownloadState
import com.sean.capsule.ui.viewmodel.DownloadViewModel
import com.sean.capsule.ui.viewmodel.SettingsViewModel
import java.util.concurrent.Executors

enum class ScannerTarget {
    ID_URL, MNEMONIC
}

@Composable
fun DownloadScreen(
    paddingValues: PaddingValues,
    navController: NavController,
    settingsViewModel: SettingsViewModel,
    downloadViewModel: DownloadViewModel
) {
    var idOrUrl by remember { mutableStateOf("") }
    var mnemonic by remember { mutableStateOf("") }

    var pendingTargetName by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingTarget = pendingTargetName?.let { ScannerTarget.valueOf(it) }
    
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val baseUrl by settingsViewModel.effectiveBaseUrl.collectAsState()
    val downloadDirUri by settingsViewModel.downloadDirUri.collectAsState()
    val downloadState by downloadViewModel.downloadState.collectAsState()
    val focusManager = LocalFocusManager.current

    val navBackStackEntry = navController.currentBackStackEntry

    LaunchedEffect(navBackStackEntry) {
        navBackStackEntry?.savedStateHandle?.getStateFlow<String?>("scan_result", null)
            ?.collect { result ->
                if (result != null) {
                    if (pendingTarget == ScannerTarget.ID_URL) {
                        idOrUrl = result
                    } else if (pendingTarget == ScannerTarget.MNEMONIC) {
                        mnemonic = result
                    }
                    pendingTargetName = null
                    // Clear the result so it doesn't trigger again on next entry
                    navBackStackEntry.savedStateHandle.remove<String>("scan_result")
                }
            }
    }

    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission.value = granted
            if (granted && pendingTargetName != null) {
                navController.navigate(QRScanner(pendingTargetName!!))
            }
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
            imageVector = Icons.Default.Download,
            contentDescription = "Receive",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Receive File",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = {
                pendingTargetName = ScannerTarget.ID_URL.name
                if (hasCameraPermission.value) {
                    navController.navigate(QRScanner(ScannerTarget.ID_URL.name))
                } else {
                    launcher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Scan ID/URL QR Code")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = idOrUrl,
            onValueChange = { idOrUrl = it },
            label = { Text("Enter ID or URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = downloadState is DownloadState.Idle || downloadState is DownloadState.Success || downloadState is DownloadState.Error
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        when (val state = downloadState) {
            is DownloadState.Downloading -> {
                DownloadProgress(label = "Receiving...", progress = state.progress)
            }
            is DownloadState.Decrypting -> {
                DownloadProgress(label = "Decrypting...", progress = state.progress)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Decryption Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This file is encrypted. Please enter your 12-word mnemonic phrase to proceed:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = mnemonic,
                            onValueChange = { mnemonic = it },
                            label = { Text("Mnemonic Phrase") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    mnemonic = mnemonic.lowercase().trim().replace("\\s+".toRegex(), " ")
                                    focusManager.clearFocus()
                                }
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    pendingTargetName = ScannerTarget.MNEMONIC.name
                                    if (hasCameraPermission.value) {
                                        navController.navigate(QRScanner(ScannerTarget.MNEMONIC.name))
                                    } else {
                                        launcher.launch(Manifest.permission.CAMERA)
                                    }
                                }) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Mnemonic")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { downloadViewModel.resetState() }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { 
                                    downloadViewModel.decryptAndSave(context, state.tempFile, state.suggestedName, mnemonic, downloadDirUri)
                                    mnemonic = ""
                                },
                                enabled = mnemonic.isNotBlank() && state.progress == 0f
                            ) {
                                Text("Decrypt & Save")
                            }
                        }
                    }
                }
            }
            else -> {
                Button(
                    onClick = { downloadViewModel.startDownload(context, baseUrl, idOrUrl, downloadDirUri) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = idOrUrl.isNotBlank()
                ) {
                    Text("Receive")
                }
            }
        }

        AnimatedVisibility(
            visible = downloadState is DownloadState.Success || downloadState is DownloadState.Error,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val state = downloadState
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                if (state is DownloadState.Success) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Receive Success!",
                                fontWeight = FontWeight.Bold, 
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text("Saved: ${state.fileName}", fontSize = 12.sp)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(state.uri, context.contentResolver.getType(state.uri) ?: "*/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Open file"))
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }) {
                                    Text("Open File")
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Button(
                                    onClick = { downloadViewModel.resetState() }
                                ) {
                                    Text("OK")
                                }
                            }
                        }
                    }
                } else if (state is DownloadState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text(state.message, fontSize = 12.sp)
                            Button(
                                onClick = { downloadViewModel.resetState() },
                                modifier = Modifier.padding(top = 8.dp),
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
fun QRScannerScreen(
    target: ScannerTarget,
    onResult: (String) -> Unit,
    onClose: () -> Unit
) {
    var hasScanned by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        QRScannerView(onCodeScanned = { result ->
            if (!hasScanned) {
                hasScanned = true
                onResult(result)
            }
        })

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            
            Text(
                text = if (target == ScannerTarget.ID_URL) "Scan ID or URL" else "Scan Mnemonic Phrase",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun DownloadProgress(label: String, progress: Float) {
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

@OptIn(ExperimentalGetImage::class, ExperimentalCamera2Interop::class)
@Composable
fun QRScannerView(onCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val hasScanned = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                val cameraInfo = cameraProvider.getCameraInfo(cameraSelector)

                val barcodeScanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                )

                // Query 60 FPS range
                val cameraCharacteristics = Camera2CameraInfo.from(cameraInfo)
                    .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                
                val targetFpsRange = cameraCharacteristics?.find { range ->
                    range.upper >= 60 || range.lower >= 60
                }

                val previewBuilder = Preview.Builder()
                val imageAnalysisBuilder = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

                if (targetFpsRange != null) {
                    Camera2Interop.Extender(previewBuilder)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFpsRange)
                    Camera2Interop.Extender(imageAnalysisBuilder)
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFpsRange)
                }

                val preview = previewBuilder.build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = imageAnalysisBuilder.build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    if (hasScanned.value) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val firstCode = barcodes.firstOrNull()?.rawValue
                                if (firstCode != null && !hasScanned.value) {
                                    hasScanned.value = true
                                    onCodeScanned(firstCode)
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
