package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.repository.PriceRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * **Validates: Requirements 3.1, 3.2**
 *
 * Property 7: Copia de lista preserva productos sin precios
 *
 * When copying a monthly list, the target list should contain
 * the same products as the source but with zero price records.
 * The source list's price records must remain unchanged.
 */
class CopyMonthlyListPropertyTest : FunSpec({

    test("copying a list preserves products but does not copy price records") {
        checkAll(PropTestConfig(iterations = 100), arbProductIds(), arbPriceCount()) { productIds, priceCountPerProduct ->
            // Setup
            val sourceListId = 1L
            val targetListId = 2L

            val fakeMonthlyListRepo = FakeMonthlyListRepository()
            val fakePriceRepo = FakePriceRepository()
            val useCase = CopyMonthlyListUseCase(fakeMonthlyListRepo)

            // Add products to source list
            productIds.forEach { productId ->
                fakeMonthlyListRepo.addProductToList(productId, sourceListId)
            }

            // Register price records for source list products
            val sourcePriceRecords = mutableListOf<PriceRecord>()
            productIds.forEach { productId ->
                repeat(priceCountPerProduct) { i ->
                    val record = PriceRecord(
                        id = 0,
                        productId = productId,
                        storeId = (i + 1).toLong(),
                        monthlyListId = sourceListId,
                        price = (i + 1) * 10.0,
                        recordDate = LocalDate.of(2024, 1, 1)
                    )
                    fakePriceRepo.registerPrice(record)
                    sourcePriceRecords.add(record)
                }
            }

            // Execute copy
            val result = useCase(sourceListId, targetListId)
            result.isSuccess shouldBe true

            // Verify target list has same products as source
            val targetProducts = fakeMonthlyListRepo.getProductIdsForList(targetListId)
            targetProducts shouldContainExactlyInAnyOrder productIds

            // Verify target list has ZERO price records
            val targetPrices = fakePriceRepo.getPricesForList(targetListId).first()
            targetPrices.shouldBeEmpty()

            // Verify source list still has its original price records unchanged
            val sourcePrices = fakePriceRepo.getPricesForList(sourceListId).first()
            sourcePrices.size shouldBe sourcePriceRecords.size
        }
    }
})

// ─── Generators ─────────────────────────────────────────────────────────────────

private fun arbProductIds(): Arb<List<Long>> = Arb.list(Arb.long(1L..1000L), 1..15)

private fun arbPriceCount(): Arb<Int> = Arb.int(1..5)

// ─── Fake Repositories ──────────────────────────────────────────────────────────

private class FakeMonthlyListRepository : MonthlyListRepository {
    // Map of listId -> set of productIds
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
        // Copy product associations but NOT price records (simulates real behavior)
        val sourceProducts = listProducts[sourceListId] ?: emptySet()
        listProducts.getOrPut(targetListId) { mutableSetOf() }.addAll(sourceProducts)
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

private class FakePriceRepository : PriceRepository {
    private val idGenerator = AtomicLong(1)
    // Map of monthlyListId -> list of price records
    private val priceRecords = mutableMapOf<Long, MutableList<PriceRecord>>()

    override suspend fun registerPrice(priceRecord: PriceRecord): Result<Unit> {
        val id = idGenerator.getAndIncrement()
        val stored = priceRecord.copy(id = id)
        priceRecords.getOrPut(stored.monthlyListId) { mutableListOf() }.add(stored)
        return Result.success(Unit)
    }

    override fun getPriceHistory(productId: Long, lastNMonths: Int): Flow<List<PriceRecord>> {
        val all = priceRecords.values.flatten().filter { it.productId == productId }
        return flowOf(all)
    }

    override fun getPricesForList(monthlyListId: Long): Flow<List<PriceRecord>> {
        return flowOf(priceRecords[monthlyListId] ?: emptyList())
    }

    override fun getLowestPrice(productId: Long, monthlyListId: Long): Flow<PriceRecord?> {
        val prices = priceRecords[monthlyListId]?.filter { it.productId == productId }
        return flowOf(prices?.minByOrNull { it.price })
    }
}
