package com.app.marketlist.data.remote.firebase

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.marketlist.data.repository.SyncRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that performs data synchronization to Firebase Firestore.
 * Uses WorkManager with NetworkType.CONNECTED constraint to ensure network availability.
 *
 * Retry strategy: Exponential backoff (configured in WorkRequest).
 * - On success: Returns Result.success()
 * - On transient failure: Returns Result.retry() to trigger backoff
 * - On permanent failure (max retries exceeded): Returns Result.failure()
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepositoryImpl
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val MAX_RETRY_COUNT = 5
    }

    override suspend fun doWork(): Result {
        // Check if max retries exceeded
        if (runAttemptCount >= MAX_RETRY_COUNT) {
            return Result.failure()
        }

        val syncResult = syncRepository.syncToCloud()

        return when {
            syncResult.isSuccess -> {
                val result = syncResult.getOrNull()
                if (result != null && result.success) {
                    Result.success()
                } else if (result != null && result.failedCount > 0 && result.syncedCount > 0) {
                    // Partial success — retry for remaining items
                    Result.retry()
                } else {
                    // All failed — retry with backoff
                    Result.retry()
                }
            }
            else -> {
                // Exception occurred — retry with exponential backoff
                Result.retry()
            }
        }
    }
}
