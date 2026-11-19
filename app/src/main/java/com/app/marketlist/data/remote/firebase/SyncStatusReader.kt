package com.app.marketlist.data.remote.firebase

import com.app.marketlist.data.local.db.SyncMetadataDao
import com.app.marketlist.data.repository.SyncRepositoryImpl
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Lee el estado de sincronización guardado localmente (Room es la fuente de verdad). */
@Singleton
class SyncStatusReader @Inject constructor(
    private val syncMetadataDao: SyncMetadataDao
) {
    suspend fun lastSyncTime(): LocalDateTime? {
        val value = syncMetadataDao.get(SyncRepositoryImpl.KEY_LAST_SYNC_TIME) ?: return null
        return try {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun isStorageLimitReached(): Boolean =
        syncMetadataDao.get(KEY_STORAGE_LIMIT) == "true"

    companion object {
        const val KEY_STORAGE_LIMIT = "storage_limit_reached"
    }
}
