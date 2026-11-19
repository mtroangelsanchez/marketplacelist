package com.app.marketlist.data.local.photo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper for creating temporary file URIs for camera capture using FileProvider.
 * Provides content URIs that can be used with CameraX ImageCapture or other
 * camera intents without requiring external storage permissions.
 */
@Singleton
class PhotoFileProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TEMP_PHOTO_DIR = "product_photos"
        private const val TEMP_PHOTO_PREFIX = "temp_capture_"
        private const val PHOTO_EXTENSION = ".jpg"
    }

    /**
     * Create a temporary file URI for CameraX ImageCapture.
     * The file is created in the app's private filesDir/product_photos/ directory
     * and exposed via FileProvider for camera use.
     * @return A content URI pointing to a new temporary file.
     */
    fun createTempPhotoUri(): Uri {
        val photoDir = getOrCreatePhotoDir()
        val tempFile = File.createTempFile(
            TEMP_PHOTO_PREFIX,
            PHOTO_EXTENSION,
            photoDir
        )
        return getContentUri(tempFile)
    }

    /**
     * Get the content URI for a file to share via FileProvider.
     * This is needed to grant temporary read/write permissions to
     * other apps (e.g., the camera app) via content:// URIs.
     * @param file The file to get a content URI for.
     * @return The content URI for the given file.
     */
    fun getContentUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Get or create the photo storage directory.
     */
    private fun getOrCreatePhotoDir(): File {
        val dir = File(context.filesDir, TEMP_PHOTO_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
