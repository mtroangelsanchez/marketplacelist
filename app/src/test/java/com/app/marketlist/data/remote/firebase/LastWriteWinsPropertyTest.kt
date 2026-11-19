package com.app.marketlist.data.remote.firebase

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * **Validates: Requirements 7.3**
 *
 * Property 17: Resolución de conflictos last-write-wins
 *
 * For any pair of conflicting versions of the same record (same ID),
 * the version with the more recent `updatedAt` timestamp should prevail
 * and the older version should be discarded.
 */
class LastWriteWinsPropertyTest : FunSpec({

    val resolver = ConflictResolver()

    test("last-write-wins: local newer than remote → local wins") {
        checkAll(PropTestConfig(iterations = 100), arbRecordPairLocalNewer()) { (local, remote) ->
            val result = resolver.resolve(local, remote)
            result shouldBe local
            result.updatedAt shouldBe local.updatedAt
        }
    }

    test("last-write-wins: remote newer than local → remote wins") {
        checkAll(PropTestConfig(iterations = 100), arbRecordPairRemoteNewer()) { (local, remote) ->
            val result = resolver.resolve(local, remote)
            result shouldBe remote
            result.updatedAt shouldBe remote.updatedAt
        }
    }

    test("last-write-wins: equal timestamps → local wins (tie-breaking)") {
        checkAll(PropTestConfig(iterations = 100), arbRecordPairEqualTimestamp()) { (local, remote) ->
            val result = resolver.resolve(local, remote)
            result shouldBe local
        }
    }

    test("last-write-wins: winner always has the most recent updatedAt") {
        checkAll(PropTestConfig(iterations = 100), arbRecordPairDistinctTimestamps()) { (local, remote) ->
            val result = resolver.resolve(local, remote)

            // The winner must be the one with the larger (more recent) updatedAt
            val expectedWinner = if (local.updatedAt >= remote.updatedAt) local else remote
            result shouldBe expectedWinner
        }
    }
})

// ─── Generators ─────────────────────────────────────────────────────────────────

private data class RecordPair(
    val local: ConflictResolver.VersionedRecord,
    val remote: ConflictResolver.VersionedRecord
)

/**
 * Generates an ISO timestamp string like "2024-01-15T10:30:00"
 */
private fun arbIsoTimestamp(): Arb<String> = arbitrary {
    val year = Arb.int(2020..2030).bind()
    val month = Arb.int(1..12).bind()
    val day = Arb.int(1..28).bind()
    val hour = Arb.int(0..23).bind()
    val minute = Arb.int(0..59).bind()
    val second = Arb.int(0..59).bind()
    "%04d-%02d-%02dT%02d:%02d:%02d".format(year, month, day, hour, minute, second)
}

/**
 * Generates a pair where local is strictly newer than remote.
 */
private fun arbRecordPairLocalNewer(): Arb<RecordPair> = arbitrary {
    val id = Arb.long(1L..10000L).bind()
    val localData = Arb.string(1..50).bind()
    val remoteData = Arb.string(1..50).bind()

    // Generate two distinct timestamps and assign newer to local
    val ts1 = arbIsoTimestamp().bind()
    val ts2 = arbIsoTimestamp().bind()
    val (newer, older) = if (ts1 > ts2) ts1 to ts2 else if (ts2 > ts1) ts2 to ts1 else {
        // Ensure they're different by incrementing second
        ts1 to ts1.replaceRange(17, 19, "00")
    }
    // Make sure they're actually different
    val finalNewer = if (newer == older) newer.replaceRange(17, 19, "59") else newer
    val finalOlder = if (finalNewer == older) older.replaceRange(17, 19, "00") else older

    val local = ConflictResolver.VersionedRecord(id, localData, finalNewer)
    val remote = ConflictResolver.VersionedRecord(id, remoteData, finalOlder)
    RecordPair(local, remote)
}

/**
 * Generates a pair where remote is strictly newer than local.
 */
private fun arbRecordPairRemoteNewer(): Arb<RecordPair> = arbitrary {
    val id = Arb.long(1L..10000L).bind()
    val localData = Arb.string(1..50).bind()
    val remoteData = Arb.string(1..50).bind()

    // Generate two distinct timestamps and assign newer to remote
    val ts1 = arbIsoTimestamp().bind()
    val ts2 = arbIsoTimestamp().bind()
    val (newer, older) = if (ts1 > ts2) ts1 to ts2 else if (ts2 > ts1) ts2 to ts1 else {
        ts1 to ts1.replaceRange(17, 19, "00")
    }
    val finalNewer = if (newer == older) newer.replaceRange(17, 19, "59") else newer
    val finalOlder = if (finalNewer == older) older.replaceRange(17, 19, "00") else older

    val local = ConflictResolver.VersionedRecord(id, localData, finalOlder)
    val remote = ConflictResolver.VersionedRecord(id, remoteData, finalNewer)
    RecordPair(local, remote)
}

/**
 * Generates a pair where both have the same timestamp (tests tie-breaking).
 */
private fun arbRecordPairEqualTimestamp(): Arb<RecordPair> = arbitrary {
    val id = Arb.long(1L..10000L).bind()
    val localData = Arb.string(1..50).bind()
    val remoteData = Arb.string(1..50).bind()
    val timestamp = arbIsoTimestamp().bind()

    val local = ConflictResolver.VersionedRecord(id, localData, timestamp)
    val remote = ConflictResolver.VersionedRecord(id, remoteData, timestamp)
    RecordPair(local, remote)
}

/**
 * Generates a pair with distinct timestamps (either order).
 * Used to verify the general property that the most recent always wins.
 */
private fun arbRecordPairDistinctTimestamps(): Arb<RecordPair> = arbitrary {
    val id = Arb.long(1L..10000L).bind()
    val localData = Arb.string(1..50).bind()
    val remoteData = Arb.string(1..50).bind()

    // Generate two timestamps that are guaranteed different
    val year1 = Arb.int(2020..2025).bind()
    val year2 = Arb.int(2026..2030).bind()
    val month = Arb.int(1..12).bind()
    val day = Arb.int(1..28).bind()
    val hour = Arb.int(0..23).bind()
    val minute = Arb.int(0..59).bind()
    val second = Arb.int(0..59).bind()

    val olderTs = "%04d-%02d-%02dT%02d:%02d:%02d".format(year1, month, day, hour, minute, second)
    val newerTs = "%04d-%02d-%02dT%02d:%02d:%02d".format(year2, month, day, hour, minute, second)

    // Randomly assign which is local vs remote
    val localIsNewer = Arb.int(0..1).bind() == 0
    val local = if (localIsNewer) {
        ConflictResolver.VersionedRecord(id, localData, newerTs)
    } else {
        ConflictResolver.VersionedRecord(id, localData, olderTs)
    }
    val remote = if (localIsNewer) {
        ConflictResolver.VersionedRecord(id, remoteData, olderTs)
    } else {
        ConflictResolver.VersionedRecord(id, remoteData, newerTs)
    }
    RecordPair(local, remote)
}
