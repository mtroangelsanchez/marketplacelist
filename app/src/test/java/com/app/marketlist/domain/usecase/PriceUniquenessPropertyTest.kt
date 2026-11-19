package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.repository.PriceRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * **Validates: Requirements 4.3**
 *
 * Property 12: Unicidad de precio por combinación Producto-Tienda-Lista
 *
 * For any existing (productId, storeId, monthlyListId) combination,
 * attempting to register a second price without confirmation should fail
 * with DuplicateError, and only one record should exist for that combination.
 */
class PriceUniquenessPropertyTest : FunSpec({

    test("duplicate price for same product-store-list combination is rejected") {
        val fakeRepo = FakePriceRepository()
        val useCase = RegisterPriceUseCase(fakeRepo)

        checkAll(PropTestConfig(iterations = 100), arbValidPriceRecord()) { priceRecord ->
            // Reset repo for each iteration
            fakeRepo.clear()

            // First registration should succeed
            val firstResult = useCase(priceRecord)
            firstResult.isSuccess shouldBe true

            // Second registration with same (productId, storeId, monthlyListId) should fail
            val secondRecord = priceRecord.copy(
                price = arbValidPrice()
            )
            val secondResult = useCase(secondRecord)
            secondResult.isFailure shouldBe true

            val exception = secondResult.exceptionOrNull()
            exception.shouldBeInstanceOf<AppErrorException>()
            (exception as AppErrorException).appError.shouldBeInstanceOf<AppError.DuplicateError>()

            // Verify only one record exists for this combination
            fakeRepo.countRecordsForCombination(
                priceRecord.productId,
                priceRecord.storeId,
                priceRecord.monthlyListId
            ) shouldBe 1
        }
    }
})

// ─── Generators ─────────────────────────────────────────────────────────────────

private fun arbValidPrice(): Double {
    val cents = (100..99999).random()
    return cents / 100.0
}

private fun arbValidPriceArb(): Arb<Double> = arbitrary {
    val cents = Arb.long(100L..99999L).bind()
    cents / 100.0
}

private fun arbValidPriceRecord(): Arb<PriceRecord> = arbitrary {
    val productId = Arb.long(1L..1000L).bind()
    val storeId = Arb.long(1L..100L).bind()
    val monthlyListId = Arb.long(1L..100L).bind()
    val price = arbValidPriceArb().bind()

    PriceRecord(
        id = 0,
        productId = productId,
        storeId = storeId,
        monthlyListId = monthlyListId,
        price = price,
        recordDate = LocalDate.now()
    )
}

// ─── Fake Repository ────────────────────────────────────────────────────────────

private class FakePriceRepository : PriceRepository {
    private data class CombinationKey(
        val productId: Long,
        val storeId: Long,
        val monthlyListId: Long
    )

    private val records = mutableListOf<PriceRecord>()

    fun clear() {
        records.clear()
    }

    fun countRecordsForCombination(productId: Long, storeId: Long, monthlyListId: Long): Int {
        return records.count {
            it.productId == productId && it.storeId == storeId && it.monthlyListId == monthlyListId
        }
    }

    override suspend fun registerPrice(priceRecord: PriceRecord): Result<Unit> {
        val key = CombinationKey(
            priceRecord.productId,
            priceRecord.storeId,
            priceRecord.monthlyListId
        )
        val exists = records.any {
            it.productId == key.productId &&
                it.storeId == key.storeId &&
                it.monthlyListId == key.monthlyListId
        }
        if (exists) {
            return Result.failure(
                AppErrorException(
                    AppError.DuplicateError(
                        entity = "PriceRecord",
                        name = "productId=${key.productId}, storeId=${key.storeId}, monthlyListId=${key.monthlyListId}"
                    )
                )
            )
        }
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
