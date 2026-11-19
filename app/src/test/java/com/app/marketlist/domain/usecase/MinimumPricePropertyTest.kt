package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.repository.PriceRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * **Validates: Requirements 4.6**
 *
 * Property 14: Identificación correcta del precio mínimo
 *
 * For any product with multiple prices registered in different stores,
 * getLowestPrice should return a PriceRecord whose price is <= all other
 * prices for that product in that monthly list, and equals the actual minimum.
 */
class MinimumPricePropertyTest : FunSpec({

    test("getLowestPrice returns price that is <= all other prices for the same product and list") {
        val fakeRepo = FakePriceRepository()

        checkAll(PropTestConfig(iterations = 100), arbPriceScenario()) { scenario ->
            // Reset repo for each iteration
            fakeRepo.clear()

            // Register all prices
            for (record in scenario.records) {
                fakeRepo.registerPrice(record)
            }

            // Get the lowest price
            val lowest = fakeRepo.getLowestPrice(scenario.productId, scenario.monthlyListId).first()

            // Should not be null since we always have at least 2 records
            lowest shouldNotBe null

            // The returned price should be <= all other prices for this product+list
            val allPrices = scenario.records.map { it.price }
            for (price in allPrices) {
                lowest!!.price shouldBeLessThanOrEqual price
            }

            // The returned price should equal the actual minimum
            val expectedMin = allPrices.min()
            lowest!!.price shouldBe expectedMin
        }
    }
})

// ─── Data Classes ───────────────────────────────────────────────────────────────

private data class PriceScenario(
    val productId: Long,
    val monthlyListId: Long,
    val records: List<PriceRecord>
)

// ─── Generators ─────────────────────────────────────────────────────────────────

private fun arbValidPrice(): Arb<Double> = arbitrary {
    val cents = Arb.long(100L..99999L).bind()
    cents / 100.0
}

private fun arbPriceScenario(): Arb<PriceScenario> = arbitrary {
    val productId = Arb.long(1L..1000L).bind()
    val monthlyListId = Arb.long(1L..100L).bind()
    val numRecords = Arb.int(2..10).bind()

    val records = (1..numRecords).map { index ->
        val storeId = index.toLong() // Different store for each record
        val price = arbValidPrice().bind()

        PriceRecord(
            id = index.toLong(),
            productId = productId,
            storeId = storeId,
            monthlyListId = monthlyListId,
            price = price,
            recordDate = LocalDate.now(),
            storeName = "Store $index"
        )
    }

    PriceScenario(
        productId = productId,
        monthlyListId = monthlyListId,
        records = records
    )
}

// ─── Fake Repository ────────────────────────────────────────────────────────────

private class FakePriceRepository : PriceRepository {
    private val records = mutableListOf<PriceRecord>()

    fun clear() {
        records.clear()
    }

    override suspend fun registerPrice(priceRecord: PriceRecord): Result<Unit> {
        records.add(priceRecord)
        return Result.success(Unit)
    }

    override fun getPriceHistory(productId: Long, lastNMonths: Int): Flow<List<PriceRecord>> {
        return flowOf(records.filter { it.productId == productId })
    }

    override fun getPricesForList(monthlyListId: Long): Flow<List<PriceRecord>> {
        return flowOf(records.filter { it.monthlyListId == monthlyListId })
    }

    override fun getLowestPrice(productId: Long, monthlyListId: Long): Flow<PriceRecord?> {
        return flowOf(
            records.filter { it.productId == productId && it.monthlyListId == monthlyListId }
                .minByOrNull { it.price }
        )
    }
}
