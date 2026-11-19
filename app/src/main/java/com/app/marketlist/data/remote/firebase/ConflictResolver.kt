package com.app.marketlist.data.remote.firebase

/**
 * Encapsulates the last-write-wins conflict resolution strategy
 * used by the sync system when two versions of the same entity
 * exist with different updatedAt timestamps.
 *
 * The version with the more recent (lexicographically larger) updatedAt
 * timestamp wins the conflict.
 */
class ConflictResolver {

    data class VersionedRecord(
        val id: Long,
        val data: String,
        val updatedAt: String
    )

    /**
     * Resolves a conflict between a local and remote version of the same record.
     * Uses last-write-wins strategy: the record with the more recent updatedAt prevails.
     *
     * @param local The local version of the record
     * @param remote The remote version of the record
     * @return The winning version (most recent updatedAt)
     */
    fun resolve(local: VersionedRecord, remote: VersionedRecord): VersionedRecord {
        return if (local.updatedAt >= remote.updatedAt) local else remote
    }
}
