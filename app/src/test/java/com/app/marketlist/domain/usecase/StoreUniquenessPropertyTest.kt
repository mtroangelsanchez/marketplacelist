package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.model.StoreType
import com.app.marketlist.domain.repository.StoreRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/**
 * **Validates: Requirements 2.3**
 *
 * Property 5: Unicidad de tiendas por nombre y tipo
 *
 * For any valid store name (2-50 chars) and type, creating a store and then
 * attempting to create a duplicate (same name case-insensitive + same type)
 * must be rejected with DuplicateError, preserving only the original store.
 */
class StoreUniquenessPropertyTest : FunSpec({

    test("duplicate store with same name (case-insensitive) and same type is rejected") {
        val fakeRepo = FakeStoreRepository()
        val createUseCase = CreateStoreUseCase(fakeRepo)

        checkAll(PropTestConfig(iterations = 100), arbValidStoreName(), Arb.enum<StoreType>()) { name, type ->
            // Reset repo for each iteration
            fakeRepo.clear()

            val originalStore = Store(id = 0, name = name, type = type)

            // Create first store successfully
            val firstResult = createUseCase(originalStore)
            firstResult.isSuccess shouldBe true

            // Attempt to create duplicate with same name (case-insensitive) and same type
            val duplicateName = name.uppercase()
            val duplicateStore = Store(id = 0, name = duplicateName, type = type)
            val secondResult = createUseCase(duplicateStore)

            // Second creation must fail
            secondResult.isFailure shouldBe true

            // Failure must be a DuplicateError
            val exception = secondResult.exceptionOrNull()
            exception.shouldBeInstanceOf<AppErrorException>()
            (exception as AppErrorException).appError.shouldBeInstanceOf<AppError.DuplicateError>()

            // Only 1 store exists in the repo (the original)
            fakeRepo.storeCount() shouldBe 1
        }
    }
})

// ─── Generators ─────────────────────────────────────────────────────────────────

private val ALPHANUMERIC_CHARS = (('a'..'z') + ('A'..'Z') + ('0'..'9')).toList()

private fun arbValidStoreName(): Arb<String> = arbitrary {
    val length = Arb.int(2..50).bind()
    buildString {
        repeat(length) {
            append(Arb.element(ALPHANUMERIC_CHARS).bind())
        }
    }
}

// ─── Fake Repository ────────────────────────────────────────────────────────────

private class FakeStoreRepository : StoreRepository {
    private val idGenerator = AtomicLong(1)
    private val storesMap = mutableMapOf<Long, Store>()
    private val storesFlow = MutableStateFlow<Map<Long, Store>>(emptyMap())

    fun clear() {
        storesMap.clear()
        storesFlow.value = emptyMap()
        idGenerator.set(1)
    }

    fun storeCount(): Int = storesMap.size

    override fun getAllStores(): Flow<List<Store>> {
        return storesFlow.map { it.values.toList() }
    }

    override suspend fun createStore(store: Store): Result<Long> {
        val id = idGenerator.getAndIncrement()
        val stored = store.copy(id = id)
        storesMap[id] = stored
        storesFlow.value = storesMap.toMap()
        return Result.success(id)
    }

    override suspend fun updateStore(store: Store): Result<Unit> {
        storesMap[store.id] = store
        storesFlow.value = storesMap.toMap()
        return Result.success(Unit)
    }

    override suspend fun deleteStore(storeId: Long): Result<Unit> {
        storesMap.remove(storeId)
        storesFlow.value = storesMap.toMap()
        return Result.success(Unit)
    }

    override fun isStorePredefined(storeId: Long): Boolean {
        return storesMap[storeId]?.isPredefined ?: false
    }
}
