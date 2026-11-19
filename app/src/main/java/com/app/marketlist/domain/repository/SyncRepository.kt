package com.app.marketlist.domain.repository

import com.app.marketlist.domain.model.SyncResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface SyncRepository {
    suspend fun syncToCloud(): Result<SyncResult>
    fun getLastSyncTime(): Flow<LocalDateTime?>
    fun getPendingChangesCount(): Flow<Int>
}
