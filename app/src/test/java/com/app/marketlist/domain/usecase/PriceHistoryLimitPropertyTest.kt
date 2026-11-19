package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.repository.PriceRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveAtMostSize
import io.kotest.matchers.shouldBe
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
 * **Validates: Requirements 4.5**
 *
 * Property 13: Historial de precios limitado a ultimas 6 listas
 *
 * For any product with price records in N > 6 monthly lists,
 * the price comparison use case should return at most 6 records
 * corresponding to the 6 most recent ones (by recordDate).
 */
class PriceHistoryLimitPropertyTest : FunSpec({

    test("price history is limited to at most 6 records from the most recent lists") {
        checkAll(PropTestConfig(iterations = 100), arbPriceHistoryScenario()) { scenario ->
            val fakeRepo = FakePriceRepository(scenario.allRecords)
            val useCase = GetPriceComparisonUseCase(fakeRepo)

            val result = useCase(scenario.productId).first()

            // Property: result has at most 6 records
            result shouldHaveAtMostSize 6

            // Property: returned records are the 6 most recent by recordDate
            val expectedRecords = scenario.allRecords
                .filter { it.productId == scenario.productId }
                .sortedByDescending { it.recordDate }
                .take(6)

            result.size shouldBe expectedRecords.size
            result.map { it.monthlyListId }.toSet() shouldBe expectedRecords.map { it.monthlyListId }.toSet()
        }
    }
})

// ─── Scenario Data ──────────────────────────────────────────────────────────────

private data class PriceHistoryScenario(
    val productId: Long,
    val allRecords: List<PriceRecord>
)

// ─── Generators ─────────────────────────────────────────────────────────────────

private fun arbPriceHistoryScenario(): Arb<PriceHistoryScenario> = arbitrary {
    val productId = Arb.long(1L..1000L).bind()
    val recordCount = Arb.int(7..15).bind()
    val storeId = Arb.long(1L..100L).bind()
    val baseDate = LocalDate.of(2024, 1, 1)

    val records = (1..recordCount).map { index ->
        PriceRecord(
            id = index.toLong(),
            productId = productId,
            storeId = storeId,
            monthlyListId = index.toLong(),
            price = Arb.double(0.5..500.0).bind(),
            recordDate = baseDate.plusMonths(index.toLong()),
            storeName = "Store $storeId"
        )
    }

    PriceHistoryScenario(
        productId = productId,
        allRecords = records
    )
}

// ─── Fake Repository ────────────────────────────────────────────────────────────

private class FakePriceRepository(
    private val records: List<PriceRecord>
) : PriceRepository {

    override suspend fun registerPrice(priceRecord: PriceRecord): Result<Unit> {
        return Result.success(Unit)
    }

    override fun getPriceHistory(productId: Long, lastNMonths: Int): Flow<List<PriceRecord>> {
        val filtered = records
            .filter { it.productId == productId }
            .sortedByDescending { it.recordDate }
            .take(lastNMonths)
        return flowOf(filtered)
    }

    override fun getPricesForList(monthlyListId: Long): Flow<List<PriceRecord>> {
        return flowOf(records.filter { it.monthlyListId == monthlyListId })
    }

    override fun getLowestPrice(productId: Long, monthlyListId: Long): Flow<PriceRecord?> {
        return flowOf(
            records
                .filter { it.productId == productId && it.monthlyListId == monthlyListId }
                .minByOrNull { it.price }
        )
    }
}
