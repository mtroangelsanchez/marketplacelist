package com.app.marketlist.data.remote.firebase

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * **Validates: Requirements 7.4**
 *
 * Property 18: Operaciones offline generan sync pendiente
 *
 * For any CRUD operation performed without connectivity, the affected record
 * must have its syncStatus set to "PENDING" after the operation.
 *
 * This validates the offline-first strategy: all modifications are tracked
 * for later synchronization with Firebase Firestore.
 */
class OfflineSyncPendingPropertyTest : FunSpec({

    val config = PropTestConfig(iterations = 100)
    val tracker = SyncStatusTracker()

    test("any CRUD operation on a SYNCED entity results in PENDING syncStatus") {
        checkAll(config, arbTrackedEntity(), Arb.enum<SyncStatusTracker.Operation>()) { entity, operation ->
            val result = tracker.onOperation(entity, operation)
            result.syncStatus shouldBe "PENDING"
        }
    }

    test("CREATE operation always produces PENDING syncStatus") {
        checkAll(config, arbTrackedEntity()) { entity ->
            val result = tracker.onOperation(entity, SyncStatusTracker.Operation.CREATE)
            result.syncStatus shouldBe "PENDING"
        }
    }

    test("UPDATE operation always produces PENDING syncStatus") {
        checkAll(config, arbTrackedEntity()) { entity ->
            val result = tracker.onOperation(entity, SyncStatusTracker.Operation.UPDATE)
            result.syncStatus shouldBe "PENDING"
        }
    }

    test("DELETE operation always produces PENDING syncStatus") {
        checkAll(config, arbTrackedEntity()) { entity ->
            val result = tracker.onOperation(entity, SyncStatusTracker.Operation.DELETE)
            result.syncStatus shouldBe "PENDING"
        }
    }

    test("multiple sequential operations all result in PENDING syncStatus") {
        checkAll(
            config,
            arbTrackedEntity(),
            Arb.enum<SyncStatusTracker.Operation>(),
            Arb.enum<SyncStatusTracker.Operation>()
        ) { entity, op1, op2 ->
            val afterFirst = tracker.onOperation(entity, op1)
            afterFirst.syncStatus shouldBe "PENDING"

            val afterSecond = tracker.onOperation(afterFirst, op2)
            afterSecond.syncStatus shouldBe "PENDING"
        }
    }
})

// ─── Generators ─────────────────────────────────────────────────────────────────

private fun arbTrackedEntity(): Arb<SyncStatusTracker.TrackedEntity> = arbitrary {
    SyncStatusTracker.TrackedEntity(
        id = Arb.long(1L..10000L).bind(),
        data = Arb.string(1..100).bind(),
        syncStatus = "SYNCED"
    )
}
