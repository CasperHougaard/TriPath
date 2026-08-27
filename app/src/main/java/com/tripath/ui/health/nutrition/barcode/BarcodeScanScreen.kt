package com.tripath.ui.health.nutrition.barcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.tripath.ui.health.nutrition.NutrientField
import com.tripath.ui.health.nutrition.isNutrientInput
import com.tripath.ui.health.nutrition.toNutrientOrNull
import com.tripath.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: BarcodeScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.addedEvents.collect { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan barcode") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            CameraPermissionGate(
                onGranted = {
                    CameraPreview(
                        isScanningEnabled = uiState is BarcodeScanUiState.Scanning,
                        onBarcodeDetected = viewModel::onBarcodeDetected
                    )
                }
            )

            if (uiState is BarcodeScanUiState.LookingUp) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }

    (uiState as? BarcodeScanUiState.Result)?.let { result ->
        FoodResultDialog(
            result = result,
            onAccept = viewModel::onAccept,
            onClose = viewModel::onClose
        )
    }
}

/** Requests the camera permission once on entry; shows a rationale with a retry button if denied. */
@Composable
private fun CameraPermissionGate(onGranted: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        onGranted()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Camera access is needed to scan a barcode. You can still log food by hand with " +
                    "“Custom add” on the Nutrition screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera access")
            }
        }
    }
}

/** CameraX preview bound to the host lifecycle, running an ML Kit barcode analyzer over each frame. */
@Composable
private fun CameraPreview(
    isScanningEnabled: Boolean,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isScanningEnabledState = rememberUpdatedState(isScanningEnabled)
    val onBarcodeDetectedState = rememberUpdatedState(onBarcodeDetected)

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E
                )
                .build()
        )
    }
    DisposableEffect(Unit) {
        onDispose { scanner.close() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            ProcessCameraProvider.getInstance(ctx).addListener(
                {
                    val cameraProvider = ProcessCameraProvider.getInstance(ctx).get()
                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply {
                            setAnalyzer(executor) { imageProxy ->
                                analyzeFrame(scanner, imageProxy, isScanningEnabledState.value, onBarcodeDetectedState.value)
                            }
                        }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    } catch (e: Exception) {
                        // No usable camera on this device — the preview stays blank; the "couldn't
                        // find this product" fallback still lets the user log it by hand.
                    }
                },
                executor
            )
            previewView
        },
        // The preview is tied to the Activity's lifecycle, not this composable's — without this,
        // navigating back would leave the camera bound and running in the background.
        onRelease = {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    )
}

private fun analyzeFrame(
    scanner: BarcodeScanner,
    imageProxy: ImageProxy,
    isScanningEnabled: Boolean,
    onBarcodeDetected: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (!isScanningEnabled || mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull()?.rawValue?.let(onBarcodeDetected)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

@Composable
private fun FoodResultDialog(
    result: BarcodeScanUiState.Result,
    onAccept: (grams: Double, name: String?, kcalPer100g: Double?, proteinPer100g: Double?) -> Unit,
    onClose: () -> Unit
) {
    var kcalText by remember(result.barcode) { mutableStateOf(result.kcalPer100g?.let { "%.0f".format(it) } ?: "") }
    var proteinText by remember(result.barcode) { mutableStateOf(result.proteinPer100g?.let { "%.0f".format(it) } ?: "") }
    var gramsText by remember(result.barcode) { mutableStateOf("") }

    val kcalPer100g = kcalText.toNutrientOrNull()
    val proteinPer100g = proteinText.toNutrientOrNull()
    val grams = gramsText.toNutrientOrNull()

    val previewKcal = if (kcalPer100g != null && grams != null) kcalPer100g * grams / 100.0 else null
    val previewProtein = if (proteinPer100g != null && grams != null) proteinPer100g * grams / 100.0 else null

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(result.name?.takeIf { it.isNotBlank() } ?: "Scanned product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                when (result.outcome) {
                    BarcodeScanUiState.Outcome.NOT_FOUND -> Text(
                        "Couldn't find this product — enter its per-100g values to log it (and " +
                            "to speed up the next scan).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Distinct from "not found" on purpose: nothing is wrong with the product, and
                    // what is typed here is used for this entry only rather than being remembered.
                    BarcodeScanUiState.Outcome.OFFLINE -> Text(
                        "Couldn't reach the food database — you can enter the per-100g values to " +
                            "log it now, or try again when you're back online.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BarcodeScanUiState.Outcome.FOUND -> Unit
                }
                NutrientField("Calories per 100g", kcalText) { kcalText = it }
                NutrientField("Protein per 100g (g)", proteinText) { proteinText = it }
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { if (isNutrientInput(it)) gramsText = it },
                    label = { Text("Grams eaten") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (previewKcal != null || previewProtein != null) {
                    Text(
                        text = "= " + listOfNotNull(
                            previewKcal?.let { "%,.0f kcal".format(it) },
                            previewProtein?.let { "%.0f g protein".format(it) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAccept(grams ?: 0.0, result.name, kcalPer100g, proteinPer100g) },
                enabled = grams != null && grams > 0 && (kcalPer100g != null || proteinPer100g != null)
            ) { Text("Add to today") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
