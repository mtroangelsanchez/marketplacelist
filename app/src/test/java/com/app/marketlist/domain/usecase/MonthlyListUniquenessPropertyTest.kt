package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.repository.MonthlyListRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/**
 * **Validates: Requirements 3.3, 3.7**
 *
 * Property 8: Unicidad de lista mensual por mes/año
 *
 * For any (month, year) pair, creating a monthly list should succeed the first time,
 * but attempting to create a duplicate with the same month/year must be rejected
 * with a DuplicateError, and only one list should exist for that month/year.
 */
class MonthlyListUniquenessPropertyTest : FunSpec({

    test("uniqueness: creating a duplicate monthly list for same month/year is rejected") {
        val fakeRepo = FakeMonthlyListRepository()
        val createUseCase = CreateMonthlyListUseCase(fakeRepo)

        checkAll(
            PropTestConfig(iterations = 100),
            Arb.int(1..12),
            Arb.int(2020..2030)
        ) { month, year ->
            // Reset repo for each iteration
            fakeRepo.clear()

            // First creation should succeed
            val firstResult = createUseCase(month, year)
            firstResult.isSuccess shouldBe true

            val createdList = firstResult.getOrThrow()
            createdList.month shouldBe month
            createdList.year shouldBe year

            // Second creation with same month/year should fail
            val secondResult = createUseCase(month, year)
            secondResult.isFailure shouldBe true

            // Verify failure is a DuplicateError
            val exception = secondResult.exceptionOrNull()
            exception.shouldBeInstanceOf<AppErrorException>()
            (exception as AppErrorException).appError.shouldBeInstanceOf<AppError.DuplicateError>()

            // Verify only one list exists for that month/year
            val listsForMonthYear = fakeRepo.getListsByMonthYear(month, year)
            listsForMonthYear.size shouldBe 1
        }
    }
})

// ─── Fake Repository ────────────────────────────────────────────────────────────

private class FakeMonthlyListRepository : MonthlyListRepository {
    private val idGenerator = AtomicLong(1)
    private val listsMap = mutableMapOf<Long, MonthlyList>()
    private val listsFlow = MutableStateFlow<Map<Long, MonthlyList>>(emptyMap())

    fun clear() {
        listsMap.clear()
        listsFlow.value = emptyMap()
        idGenerator.set(1)
    }

    fun getListsByMonthYear(month: Int, year: Int): List<MonthlyList> {
        return listsMap.values.filter { it.month == month && it.year == year }
    }

    override fun getMonthlyLists(): Flow<List<MonthlyList>> {
        return listsFlow.map { it.values.toList() }
    }

    override fun getActiveMonthlyList(): Flow<MonthlyList?> {
        return listsFlow.map { it.values.lastOrNull() }
    }

    override suspend fun createMonthlyList(month: Int, year: Int): Result<MonthlyList> {
        // Check uniqueness constraint
        val existing = listsMap.values.find { it.month == month && it.year == year }
        if (existing != null) {
            val label = "${month.toString().padStart(2, '0')}/$year"
            return Result.failure(
                AppErrorException(AppError.DuplicateError("lista mensual", label))
            )
        }

        val id = idGenerator.getAndIncrement()
        val label = "${month.toString().padStart(2, '0')}/$year"
        val list = MonthlyList(
            id = id,
            month = month,
            year = year,
            label = label,
            products = emptyList()
        )
        listsMap[id] = list
        listsFlow.value = listsMap.toMap()
        return Result.success(list)
    }

    override suspend fun copyFromPrevious(sourceListId: Long, targetListId: Long): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun addProductToList(productId: Long, listId: Long): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun removeProductFromList(productId: Long, listId: Long): Result<Unit> {
        return Result.success(Unit)
    }
}
