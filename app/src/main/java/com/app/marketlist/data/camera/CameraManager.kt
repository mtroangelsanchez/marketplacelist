package com.app.marketlist.data.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages CameraX configuration and lifecycle for barcode scanning.
 *
 * Provides camera availability checks, CameraProvider access,
 * and factory methods for Preview and ImageAnalysis use cases.
 */
@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Checks whether the CAMERA permission is currently granted.
     */
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks whether the device has a back-facing camera available.
     */
    fun hasCamera(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    /**
     * Obtains the ProcessCameraProvider asynchronously.
     * Suspends until the provider is ready.
     */
    suspend fun getCameraProvider(): ProcessCameraProvider {
        return suspendCancellableCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    try {
                        val provider = cameraProviderFuture.get()
                        continuation.resume(provider)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    }

    /**
     * Creates an ImageAnalysis use case configured for barcode scanning.
     *
     * Uses STRATEGY_KEEP_ONLY_LATEST to drop frames when the analyzer is busy,
     * which avoids memory pressure and keeps scanning responsive.
     */
    fun createImageAnalysis(analyzer: ImageAnalysis.Analyzer): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer) }
    }

    /**
     * Creates a Preview use case for displaying the camera feed.
     */
    fun createPreview(): Preview {
        return Preview.Builder().build()
    }

    companion object {
        /**
         * Default camera selector — back-facing camera for barcode scanning.
         */
        val DEFAULT_CAMERA_SELECTOR: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }
}
