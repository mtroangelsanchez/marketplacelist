package com.app.marketlist.domain.model

import java.time.LocalDateTime

data class SyncResult(
    val success: Boolean,
    val syncedCount: Int,
    val failedCount: Int,
    val lastSyncTime: LocalDateTime?
)
