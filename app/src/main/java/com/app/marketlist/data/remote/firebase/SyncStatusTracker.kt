package com.app.marketlist.data.remote.firebase

/**
 * Pure logic component that tracks sync status for offline-first operations.
 *
 * In the offline-first architecture, any CRUD operation performed without
 * connectivity must mark the affected entity as "PENDING" for later synchronization.
 * This class encapsulates that logic in a testable, pure manner.
 */
class SyncStatusTracker {

    enum class Operation { CREATE, UPDATE, DELETE }

    data class TrackedEntity(
        val id: Long,
        val data: String,
        var syncStatus: String = "SYNCED"
    )

    /**
     * When any CRUD operation happens offline, the entity's syncStatus becomes "PENDING".
     * This ensures the SyncWorker will pick it up for later synchronization.
     */
    fun onOperation(entity: TrackedEntity, operation: Operation): TrackedEntity {
        return entity.copy(syncStatus = "PENDING")
    }
}
