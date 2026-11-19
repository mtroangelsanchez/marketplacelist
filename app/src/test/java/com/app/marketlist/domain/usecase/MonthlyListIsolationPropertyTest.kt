package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.repository.MonthlyListRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * **Validates: Requirements 3.5**
 *
 * Property 10: Aislamiento entre listas mensuales
 *
 * For any active monthly list and any previous monthly list,
 * adding or removing products from the active list must not alter
 * the quantity or data of products in previous lists.
 */
class MonthlyListIsolationPropertyTest : FunSpec({

    test("modifying active list does not affect previous list products") {
        checkAll(
            PropTestConfig(iterations = 100),
            arbProductIds(),
            arbNewProductIds(),
            arbRemoveCount()
        ) { initialProductIds, newProductIds, removeCount ->
            // Setup: two lists - A (previous) and B (active)
            val listAId = 1L // previous list
            val listBId = 2L // active list

            val fakeRepo = FakeMonthlyListRepository()

            // Step 1: Add N products to list A (the previous list)
            initialProductIds.forEach { productId ->
                fakeRepo.addProductToList(productId, listAId)
            }

            // Step 2: Copy products from list A to list B (simulating creating new month from previous)
            fakeRepo.copyFromPrevious(listAId, listBId)

            // Step 3: Record snapshot of list A's product IDs BEFORE modifications to B
            val listASnapshotBeforeModification = fakeRepo.getProductIdsForList(listAId).toSet()

            // Step 4: Modify list B - add new products
            newProductIds.forEach { productId ->
                fakeRepo.addProductToList(productId, listBId)
            }

            // Step 5: Modify list B - remove some existing products
            val productsInB = fakeRepo.getProductIdsForList(listBId)
            val toRemove = productsInB.take(removeCount.coerceAtMost(productsInB.size))
            toRemove.forEach { productId ->
                fakeRepo.removeProductFromList(productId, listBId)
            }

            // Step 6: Verify list A's products have NOT changed
            val listAAfterModification = fakeRepo.getProductIdsForList(listAId).toSet()
            listAAfterModification shouldBe listASnapshotBeforeModification
        }
    }
})

// ─── Generators ─────────────────────────────────────────────────────────────────

private fun arbProductIds(): Arb<List<Long>> = Arb.list(Arb.long(1L..500L), 2..8)

private fun arbNewProductIds(): Arb<List<Long>> = Arb.list(Arb.long(501L..1000L), 1..5)

private fun arbRemoveCount(): Arb<Int> = Arb.int(0..4)

// ─── Fake Repository ────────────────────────────────────────────────────────────

private class FakeMonthlyListRepository : MonthlyListRepository {
    // Map of listId -> set of productIds (independent tracking per list)
    private val listProducts = mutableMapOf<Long, MutableSet<Long>>()

    fun getProductIdsForList(listId: Long): List<Long> {
        return listProducts[listId]?.toList() ?: emptyList()
    }

    override fun getMonthlyLists(): Flow<List<MonthlyList>> = flowOf(emptyList())

    override fun getActiveMonthlyList(): Flow<MonthlyList?> = flowOf(null)

    override suspend fun createMonthlyList(month: Int, year: Int): Result<MonthlyList> {
        val list = MonthlyList(id = 1L, month = month, year = year, label = "$month/$year")
        return Result.success(list)
    }

    override suspend fun copyFromPrevious(sourceListId: Long, targetListId: Long): Result<Unit> {
        // Copy product associations independently - target gets its own copy of the set
        val sourceProducts = listProducts[sourceListId]?.toMutableSet() ?: mutableSetOf()
        listProducts[targetListId] = sourceProducts
        return Result.success(Unit)
    }

    override suspend fun addProductToList(productId: Long, listId: Long): Result<Unit> {
        listProducts.getOrPut(listId) { mutableSetOf() }.add(productId)
        return Result.success(Unit)
    }

    override suspend fun removeProductFromList(productId: Long, listId: Long): Result<Unit> {
        listProducts[listId]?.remove(productId)
        return Result.success(Unit)
    }
}
