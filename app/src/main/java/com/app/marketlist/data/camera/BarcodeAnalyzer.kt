package com.app.marketlist.data.camera

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * ImageAnalysis.Analyzer implementation that uses ML Kit to decode barcodes
 * in formats EAN-13, UPC-A, and Code 128.
 *
 * Works entirely offline using the on-device ML Kit barcode scanning model.
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit,
    private val onError: (Exception) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_CODE_128
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    @Volatile
    private var isProcessing = false

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.firstOrNull()
                val barcodeValue = barcode?.rawValue
                if (barcodeValue != null) {
                    onBarcodeDetected(barcodeValue)
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Barcode scanning failed", exception)
                onError(exception)
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    fun close() {
        scanner.close()
    }

    companion object {
        private const val TAG = "BarcodeAnalyzer"
    }
}
