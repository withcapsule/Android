package dev.withcapsule.android.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.withcapsule.android.QRScanner
import dev.withcapsule.android.R
import dev.withcapsule.android.analytics
import dev.withcapsule.android.ui.viewmodel.DownloadState
import dev.withcapsule.android.ui.viewmodel.DownloadViewModel
import dev.withcapsule.android.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    val strOpenFile = stringResource(R.string.download_open_chooser)

    val scanResult = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<String?>("scan_result", null)
        ?.collectAsState()

    LaunchedEffect(scanResult?.value) {
        scanResult?.value?.let { result ->
            when (pendingTarget) {
                ScannerTarget.ID_URL -> {
                    idOrUrl = result
                    analytics.event(url = "/download", name = "qr_scanned", data = mapOf("target" to (pendingTarget?.name ?: "unknown")))
                }
                ScannerTarget.MNEMONIC -> {
                    mnemonic = result
                    analytics.event(url = "/download", name = "qr_scanned", data = mapOf("target" to (pendingTarget?.name ?: "unknown")))
                }
                null -> {}
            }
            pendingTargetName = null
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("scan_result")
        }
    }

    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is DownloadState.Success -> {
                analytics.event(url = "/download", name = "download_success")
            }
            is DownloadState.Error -> {
                analytics.event(url = "/download", name = "download_error", data = mapOf("message" to state.message))
            }
            is DownloadState.Decrypting -> {
                analytics.event(url = "/download", name = "decryption_required")
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
            imageVector = Icons.Default.Download,
            contentDescription = stringResource(R.string.download_icon_desc),
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.download_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = idOrUrl,
            onValueChange = { idOrUrl = it },
            label = { Text(stringResource(R.string.download_field_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    pendingTargetName = ScannerTarget.ID_URL.name
                    analytics.event(url = "/download", name = "start_qr_scan", data = mapOf("target" to ScannerTarget.ID_URL.name))
                    navController.navigate(QRScanner(target = ScannerTarget.ID_URL.name))
                }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.btn_scan_qr))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                downloadViewModel.startDownload(context, baseUrl, idOrUrl, downloadDirUri)
                CoroutineScope(Dispatchers.IO).launch {
                    analytics.event(url = "/download", name = "download_started")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = idOrUrl.isNotBlank() && downloadState is DownloadState.Idle
        ) {
            Text(stringResource(R.string.btn_download))
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = downloadState !is DownloadState.Idle,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (val state = downloadState) {
                    is DownloadState.Downloading -> {
                        DownloadProgress(stringResource(R.string.download_downloading), state.progress)
                    }
                    is DownloadState.Decrypting -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            DownloadProgress(stringResource(R.string.download_decrypting), state.progress)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = mnemonic,
                                onValueChange = { mnemonic = it },
                                label = { Text(stringResource(R.string.download_mnemonic_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        pendingTargetName = ScannerTarget.MNEMONIC.name
                                        analytics.event(url = "/download", name = "start_qr_scan", data = mapOf("target" to ScannerTarget.MNEMONIC.name))
                                        navController.navigate(QRScanner(target = ScannerTarget.MNEMONIC.name))
                                    }) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.btn_scan_qr))
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    downloadViewModel.decryptAndSave(context, state.tempFile, state.suggestedName, mnemonic, downloadDirUri)
                                    CoroutineScope(Dispatchers.IO).launch {
                                        analytics.event(url = "/download", name = "decryption_started")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = mnemonic.isNotBlank()
                            ) {
                                Text(stringResource(R.string.btn_decrypt_save))
                            }
                        }
                    }
                    is DownloadState.Success -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.download_success_title), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.download_file_saved, state.fileName), fontSize = 12.sp)
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(state.uri, "application/octet-stream")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, strOpenFile))
                                        CoroutineScope(Dispatchers.IO).launch {
                                            analytics.event(url = "/download", name = "open_file")
                                        }
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(stringResource(R.string.btn_open_file))
                                }
                                TextButton(onClick = { downloadViewModel.resetState() }) {
                                    Text(stringResource(R.string.btn_done))
                                }
                            }
                        }
                    }
                    is DownloadState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.label_error), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Text(state.message, fontSize = 12.sp)
                                Button(
                                    onClick = { downloadViewModel.resetState() },
                                    modifier = Modifier.padding(top = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.btn_dismiss))
                                }
                            }
                        }
                    }
                    else -> {}
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
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    val strPermissionToast = stringResource(R.string.qr_permission_toast)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            val activity = context as? Activity
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
                showPermissionDeniedDialog = true
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    var hasScanned by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            analytics.event(url = "/qr_scanner", name = "qr_scanner_opened", data = mapOf("target" to target.name))
        }
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text(stringResource(R.string.qr_permission_title)) },
            text = { Text(stringResource(R.string.qr_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDeniedDialog = false
                    android.widget.Toast.makeText(context, strPermissionToast, android.widget.Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.btn_app_info))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            QRScannerView(onCodeScanned = { result ->
                if (!hasScanned) {
                    hasScanned = true
                    onResult(result)
                }
            })
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.qr_camera_rationale),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val activity = context as? Activity
                    if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
                        showPermissionDeniedDialog = true
                    } else {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                }) {
                    Text(stringResource(R.string.btn_grant_permission))
                }
            }
        }

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.qr_back_desc), tint = Color.White)
            }

            Text(
                text = if (target == ScannerTarget.ID_URL) stringResource(R.string.qr_scan_id_label)
                       else stringResource(R.string.qr_scan_mnemonic_label),
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
