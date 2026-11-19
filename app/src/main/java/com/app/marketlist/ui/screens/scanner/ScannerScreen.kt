package com.app.marketlist.ui.screens.scanner

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.marketlist.data.camera.BarcodeAnalyzer
import com.app.marketlist.data.camera.CameraManager
import com.app.marketlist.ui.components.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun ScannerScreen(
    onBack: (() -> Unit)?,
    onProductFound: (Long) -> Unit,
    onNewProduct: (String) -> Unit,
    onBarcodeResult: (String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        if (!state.hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ScannerEvent.ProductFound -> onProductFound(event.productId)
                is ScannerEvent.NewProduct -> onNewProduct(event.barcode)
                is ScannerEvent.ReturnBarcode -> onBarcodeResult(event.barcode)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Escanear código de barras", onBack = onBack)

        if (state.hasPermission) {
            CameraPreview(
                cameraManager = viewModel.cameraManager,
                onBarcode = viewModel::onBarcodeDetected,
                onError = viewModel::onCameraError,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                Text(
                    "Se necesita acceso a la cámara para escanear códigos de barras. " +
                        "También puedes ingresar el código manualmente.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 12.dp)
                ) { Text("Conceder permiso de cámara") }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            if (state.status == ScannerStatus.TIMEOUT) {
                Text(
                    "No se detectó ningún código. Ingrésalo manualmente.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.showManual) {
                OutlinedTextField(
                    value = state.manualCode,
                    onValueChange = viewModel::onManualCodeChange,
                    label = { Text("Código de barras (8-14 dígitos)") },
                    isError = state.manualError != null,
                    supportingText = { state.manualError?.let { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = viewModel::submitManual) { Text("Buscar código") }
            } else {
                OutlinedButton(onClick = viewModel::toggleManual) { Text("Ingresar código manualmente") }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    cameraManager: CameraManager,
    onBarcode: (String) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        var analyzer: BarcodeAnalyzer? = null
        val job = scope.launch {
            try {
                val cameraProvider = cameraManager.getCameraProvider()
                provider = cameraProvider
                val preview = cameraManager.createPreview().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val barcodeAnalyzer = BarcodeAnalyzer(onBarcodeDetected = onBarcode, onError = { })
                analyzer = barcodeAnalyzer
                val analysis = cameraManager.createImageAnalysis(barcodeAnalyzer)
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraManager.DEFAULT_CAMERA_SELECTOR,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                onError()
            }
        }
        onDispose {
            job.cancel()
            provider?.unbindAll()
            analyzer?.close()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        // Guía visual de escaneo
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(140.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary)
        )
    }
}
