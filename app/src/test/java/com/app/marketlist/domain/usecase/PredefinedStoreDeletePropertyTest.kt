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
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * **Validates: Requirements 2.7**
 *
 * Property 6: Tiendas predefinidas son indestructibles
 *
 * Para toda tienda predefinida, verificar que eliminar falla y la tienda permanece intacta.
 */
class PredefinedStoreDeletePropertyTest : FunSpec({

    test("predefined stores cannot be deleted - deletion always fails with PredefinedEntityError") {
        val fakeRepo = FakeStoreRepository()
        val deleteUseCase = DeleteStoreUseCase(fakeRepo)

        val predefinedStoreIds = listOf(1L, 2L, 3L, 4L, 5L)

        checkAll(PropTestConfig(iterations = 100), Arb.element(predefinedStoreIds)) { storeId ->
            // Capture store state before deletion attempt
            val storeBefore = fakeRepo.getStoreById(storeId)

            // Attempt to delete the predefined store
            val result = deleteUseCase(storeId)

            // Verify result is failure with PredefinedEntityError
            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<AppErrorException>()
            (exception as AppErrorException).appError.shouldBeInstanceOf<AppError.PredefinedEntityError>()

            // Verify the store still exists unchanged
            val storeAfter = fakeRepo.getStoreById(storeId)
            storeAfter shouldBe storeBefore
        }
    }
})

// ─── Fake Repository ────────────────────────────────────────────────────────────

private class FakeStoreRepository : StoreRepository {

    private val predefinedStores = mutableMapOf(
        1L to Store(id = 1, name = "Walmart", type = StoreType.SUPERMARKET, isPredefined = true),
        2L to Store(id = 2, name = "3B", type = StoreType.SUPERMARKET, isPredefined = true),
        3L to Store(id = 3, name = "Sams", type = StoreType.SUPERMARKET, isPredefined = true),
        4L to Store(id = 4, name = "Gran Bodega", type = StoreType.SUPERMARKET, isPredefined = true),
        5L to Store(id = 5, name = "Mercado_Local", type = StoreType.GENERAL_MARKET, isPredefined = true)
    )

    fun getStoreById(id: Long): Store? = predefinedStores[id]

    override fun isStorePredefined(storeId: Long): Boolean {
        return storeId in 1L..5L
    }

    override fun getAllStores(): Flow<List<Store>> {
        return flowOf(predefinedStores.values.toList())
    }

    override suspend fun createStore(store: Store): Result<Long> {
        val id = (predefinedStores.keys.maxOrNull() ?: 0) + 1
        predefinedStores[id] = store.copy(id = id)
        return Result.success(id)
    }

    override suspend fun updateStore(store: Store): Result<Unit> {
        predefinedStores[store.id] = store
        return Result.success(Unit)
    }

    override suspend fun deleteStore(storeId: Long): Result<Unit> {
        predefinedStores.remove(storeId)
        return Result.success(Unit)
    }
}
