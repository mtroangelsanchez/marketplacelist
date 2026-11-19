package com.app.marketlist.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.app.marketlist.data.local.db.MonthlyListDao
import com.app.marketlist.data.local.db.MonthlyListEntity
import com.app.marketlist.data.local.db.PriceRecordDao
import com.app.marketlist.data.local.db.PriceRecordEntity
import com.app.marketlist.data.local.db.ProductDao
import com.app.marketlist.data.local.db.ProductEntity
import com.app.marketlist.data.local.db.StoreDao
import com.app.marketlist.data.local.db.StoreEntity
import com.app.marketlist.data.local.db.SyncMetadataDao
import com.app.marketlist.data.local.db.SyncMetadataEntity
import com.app.marketlist.data.remote.firebase.FirestoreDataSource
import com.app.marketlist.data.remote.firebase.SyncWorker
import com.app.marketlist.domain.model.SyncResult
import com.app.marketlist.domain.repository.SyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SyncRepository that manages synchronization of local Room data
 * to Firebase Firestore using WorkManager for background scheduling.
 *
 * Strategy:
 * - Reads all entities with syncStatus="PENDING"
 * - Converts them to Map<String, Any> for Firestore batch writes
 * - Marks entities as "SYNCED" on success
 * - Stores lastSyncTime in SyncMetadata
 * - Uses WorkManager with NetworkType.CONNECTED constraint
 * - Implements exponential backoff retry on failure
 * - Detects Firestore free tier storage limit and notifies user
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val firestoreDataSource: FirestoreDataSource,
    private val productDao: ProductDao,
    private val storeDao: StoreDao,
    private val monthlyListDao: MonthlyListDao,
    private val priceRecordDao: PriceRecordDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context
) : SyncRepository {

    companion object {
        const val SYNC_WORK_NAME = "market_list_sync"
        const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val FIRESTORE_FREE_TIER_LIMIT_BYTES = 1_073_741_824L // 1 GiB
    }

    override suspend fun syncToCloud(): Result<SyncResult> {
        return try {
            // Ensure user is authenticated
            val authResult = firestoreDataSource.ensureAuthenticated()
            if (authResult.isFailure) {
                return Result.failure(
                    authResult.exceptionOrNull()
                        ?: IllegalStateException("Authentication failed")
                )
            }

            // Read all pending entities
            val pendingProducts = productDao.getBySyncStatus("PENDING")
            val pendingStores = storeDao.getBySyncStatus("PENDING")
            val pendingMonthlyLists = monthlyListDao.getBySyncStatus("PENDING")
            val pendingPriceRecords = priceRecordDao.getBySyncStatus("PENDING")

            val totalPending = pendingProducts.size + pendingStores.size +
                    pendingMonthlyLists.size + pendingPriceRecords.size

            if (totalPending == 0) {
                val now = LocalDateTime.now()
                storeSyncTime(now)
                return Result.success(
                    SyncResult(
                        success = true,
                        syncedCount = 0,
                        failedCount = 0,
                        lastSyncTime = now
                    )
                )
            }

            var syncedCount = 0
            var failedCount = 0

            // Sync products
            if (pendingProducts.isNotEmpty()) {
                val productMaps = pendingProducts.map { it.toFirestoreMap() }
                val result = firestoreDataSource.syncProducts(productMaps)
                if (result.isSuccess) {
                    markProductsAsSynced(pendingProducts)
                    syncedCount += pendingProducts.size
                } else {
                    failedCount += pendingProducts.size
                    checkStorageLimitError(result.exceptionOrNull())
                }
            }

            // Sync stores
            if (pendingStores.isNotEmpty()) {
                val storeMaps = pendingStores.map { it.toFirestoreMap() }
                val result = firestoreDataSource.syncStores(storeMaps)
                if (result.isSuccess) {
                    markStoresAsSynced(pendingStores)
                    syncedCount += pendingStores.size
                } else {
                    failedCount += pendingStores.size
                    checkStorageLimitError(result.exceptionOrNull())
                }
            }

            // Sync monthly lists
            if (pendingMonthlyLists.isNotEmpty()) {
                val listMaps = pendingMonthlyLists.map { it.toFirestoreMap() }
                val result = firestoreDataSource.syncMonthlyLists(listMaps)
                if (result.isSuccess) {
                    markMonthlyListsAsSynced(pendingMonthlyLists)
                    syncedCount += pendingMonthlyLists.size
                } else {
                    failedCount += pendingMonthlyLists.size
                    checkStorageLimitError(result.exceptionOrNull())
                }
            }

            // Sync price records
            if (pendingPriceRecords.isNotEmpty()) {
                val recordMaps = pendingPriceRecords.map { it.toFirestoreMap() }
                val result = firestoreDataSource.syncPriceRecords(recordMaps)
                if (result.isSuccess) {
                    markPriceRecordsAsSynced(pendingPriceRecords)
                    syncedCount += pendingPriceRecords.size
                } else {
                    failedCount += pendingPriceRecords.size
                    checkStorageLimitError(result.exceptionOrNull())
                }
            }

            // Store last sync time
            val now = LocalDateTime.now()
            if (syncedCount > 0) {
                storeSyncTime(now)
            }

            val success = failedCount == 0
            Result.success(
                SyncResult(
                    success = success,
                    syncedCount = syncedCount,
                    failedCount = failedCount,
                    lastSyncTime = now
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getLastSyncTime(): Flow<LocalDateTime?> = flow {
        val value = syncMetadataDao.get(KEY_LAST_SYNC_TIME)
        if (value != null) {
            try {
                val dateTime = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                emit(dateTime)
            } catch (e: Exception) {
                emit(null)
            }
        } else {
            emit(null)
        }
    }

    override fun getPendingChangesCount(): Flow<Int> {
        // Combine counts from all tables by re-querying pending status
        // Using a flow that queries each table
        val productsFlow = flow { emit(productDao.getBySyncStatus("PENDING").size) }
        val storesFlow = flow { emit(storeDao.getBySyncStatus("PENDING").size) }
        val monthlyListsFlow = flow { emit(monthlyListDao.getBySyncStatus("PENDING").size) }
        val priceRecordsFlow = flow { emit(priceRecordDao.getBySyncStatus("PENDING").size) }

        return combine(
            productsFlow,
            storesFlow,
            monthlyListsFlow,
            priceRecordsFlow
        ) { products, stores, lists, records ->
            products + stores + lists + records
        }
    }

    /**
     * Schedule a one-time sync work request with network connectivity constraint.
     * Uses exponential backoff for retry.
     */
    fun scheduleSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    // --- Private helpers ---

    private suspend fun storeSyncTime(time: LocalDateTime) {
        val formatted = time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        syncMetadataDao.set(SyncMetadataEntity(KEY_LAST_SYNC_TIME, formatted))
    }

    private suspend fun markProductsAsSynced(products: List<ProductEntity>) {
        products.forEach { product ->
            productDao.update(product.copy(syncStatus = "SYNCED"))
        }
    }

    private suspend fun markStoresAsSynced(stores: List<StoreEntity>) {
        stores.forEach { store ->
            storeDao.update(store.copy(syncStatus = "SYNCED"))
        }
    }

    private suspend fun markMonthlyListsAsSynced(lists: List<MonthlyListEntity>) {
        lists.forEach { list ->
            monthlyListDao.update(list.copy(syncStatus = "SYNCED"))
        }
    }

    private suspend fun markPriceRecordsAsSynced(records: List<PriceRecordEntity>) {
        records.forEach { record ->
            priceRecordDao.insertOrReplace(record.copy(syncStatus = "SYNCED"))
        }
    }

    /**
     * Checks if a sync failure is due to Firestore free tier storage limit.
     * If detected, stores a flag for the UI to display notification.
     */
    private suspend fun checkStorageLimitError(error: Throwable?) {
        if (error != null) {
            val message = error.message ?: ""
            // Firestore returns RESOURCE_EXHAUSTED when quota is exceeded
            if (message.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                message.contains("quota", ignoreCase = true) ||
                message.contains("storage limit", ignoreCase = true)
            ) {
                syncMetadataDao.set(
                    SyncMetadataEntity("storage_limit_reached", "true")
                )
            }
        }
    }

    // --- Entity to Firestore Map converters ---

    private fun ProductEntity.toFirestoreMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to id.toString(),
            "name" to name,
            "category" to category,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
        barcode?.let { map["barcode"] = it }
        // Note: photoPath is not synced (only structural data per requirements)
        return map
    }

    private fun StoreEntity.toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id.toString(),
            "name" to name,
            "type" to type,
            "isPredefined" to isPredefined,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }

    private fun MonthlyListEntity.toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id.toString(),
            "month" to month,
            "year" to year,
            "label" to label,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }

    private fun PriceRecordEntity.toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id.toString(),
            "productId" to productId.toString(),
            "storeId" to storeId.toString(),
            "monthlyListId" to monthlyListId.toString(),
            "price" to price,
            "recordDate" to recordDate,
            "updatedAt" to updatedAt
        )
    }
}
