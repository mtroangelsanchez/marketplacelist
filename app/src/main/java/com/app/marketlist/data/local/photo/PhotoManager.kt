package com.app.marketlist.data.local.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages product photo capture, compression, and storage.
 * Photos are stored in the app's private directory (context.filesDir/product_photos/)
 * in JPEG format with a maximum size of 2 MB.
 * No external storage permissions are required.
 */
@Singleton
class PhotoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAX_PHOTO_SIZE_BYTES = 2 * 1024 * 1024 // 2 MB
        private const val PHOTO_DIR = "product_photos"
        private const val INITIAL_QUALITY = 90
        private const val MIN_QUALITY = 20
        private const val QUALITY_STEP = 10
    }

    /**
     * Save a bitmap as JPEG, compressing if needed to stay under 2 MB.
     * @param bitmap The bitmap to save.
     * @param productId The product ID to associate the photo with.
     * @return Result containing the relative photo path on success.
     */
    suspend fun savePhoto(bitmap: Bitmap, productId: Long): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val photoDir = getOrCreatePhotoDir()
                val fileName = generateFileName(productId)
                val file = File(photoDir, fileName)

                val compressedBytes = compressBitmap(bitmap)
                FileOutputStream(file).use { fos ->
                    fos.write(compressedBytes)
                }

                val relativePath = "$PHOTO_DIR/$fileName"
                Result.success(relativePath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Save a photo from a file URI (e.g., from camera capture).
     * Decodes the URI into a bitmap, then compresses and saves it.
     * @param uri The content URI of the captured image.
     * @param productId The product ID to associate the photo with.
     * @return Result containing the relative photo path on success.
     */
    suspend fun savePhotoFromUri(uri: Uri, productId: Long): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Cannot open input stream for URI: $uri")
                    )

                val bitmap = inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: return@withContext Result.failure(
                    IllegalArgumentException("Cannot decode bitmap from URI: $uri")
                )

                savePhoto(bitmap, productId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Delete a photo file.
     * @param photoPath The relative photo path (e.g., "product_photos/photo_123_1700000000.jpg").
     * @return Result indicating success or failure.
     */
    suspend fun deletePhoto(photoPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, photoPath)
                if (file.exists()) {
                    if (file.delete()) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("Failed to delete photo: $photoPath"))
                    }
                } else {
                    // File doesn't exist, consider deletion successful
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Check if a photo file exists.
     * @param photoPath The relative photo path.
     * @return true if the file exists, false otherwise.
     */
    fun photoExists(photoPath: String): Boolean {
        if (photoPath.isBlank()) return false
        val file = File(context.filesDir, photoPath)
        return file.exists() && file.isFile
    }

    /**
     * Get the File for a photo path. Returns null if the file doesn't exist.
     * Use this to display the photo or as a fallback check before showing a placeholder.
     * @param photoPath The relative photo path.
     * @return The File object if it exists, null otherwise.
     */
    fun getPhotoFile(photoPath: String): File? {
        if (photoPath.isBlank()) return null
        val file = File(context.filesDir, photoPath)
        return if (file.exists() && file.isFile) file else null
    }

    /**
     * Compress a bitmap to JPEG format, progressively reducing quality
     * until the output is under MAX_PHOTO_SIZE_BYTES or MIN_QUALITY is reached.
     * Starts at INITIAL_QUALITY (90) and decreases by QUALITY_STEP (10) each iteration.
     */
    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        var quality = INITIAL_QUALITY
        var outputBytes: ByteArray

        do {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            outputBytes = baos.toByteArray()

            if (outputBytes.size <= MAX_PHOTO_SIZE_BYTES) {
                break
            }

            quality -= QUALITY_STEP
        } while (quality >= MIN_QUALITY)

        // If still over size at MIN_QUALITY, return what we have (best effort)
        return outputBytes
    }

    /**
     * Generate a unique filename for a product photo using productId and current timestamp.
     */
    private fun generateFileName(productId: Long): String {
        val timestamp = System.currentTimeMillis()
        return "photo_${productId}_$timestamp.jpg"
    }

    /**
     * Get or create the photo storage directory under the app's private files directory.
     */
    private fun getOrCreatePhotoDir(): File {
        val dir = File(context.filesDir, PHOTO_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
