package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.repository.MonthlyListRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong

/**
 * **Validates: Requirements 3.4**
 *
 * Property 9: Historial de listas ordenado descendente por fecha
 *
 * For any set of monthly lists with distinct (month, year) combinations,
 * getMonthlyLists() must return them strictly ordered descending by date
 * (year DESC, then month DESC).
 */
class MonthlyListOrderPropertyTest : FunSpec({

    test("ordering: monthly lists history is strictly ordered descending by date") {
        val fakeRepo = FakeMonthlyListRepository()
        val createUseCase = CreateMonthlyListUseCase(fakeRepo)

        checkAll(
            PropTestConfig(iterations = 100),
            arbDistinctMonthYearPairs()
        ) { monthYearPairs ->
            // Reset repo for each iteration
            fakeRepo.clear()

            // Create all monthly lists
            monthYearPairs.forEach { (month, year) ->
                val result = createUseCase(month, year)
                result.isSuccess shouldBe true
            }

            // Collect the list from the Flow
            val lists = fakeRepo.getMonthlyLists().first()

            // Verify we have all created lists
            lists.size shouldBe monthYearPairs.size

            // Verify strict descending order: for each consecutive pair (i, i+1),
            // list[i] must be more recent than list[i+1]
            for (i in 0 until lists.size - 1) {
                val current = lists[i]
                val next = lists[i + 1]

                val currentDate = current.year * 12 + current.month
                val nextDate = next.year * 12 + next.month

                (currentDate > nextDate) shouldBe true
            }
        }
    }
})

// ─── Generators ─────────────────────────────────────────────────────────────────

private data class MonthYear(val month: Int, val year: Int)

private fun arbDistinctMonthYearPairs(): Arb<List<MonthYear>> = arbitrary {
    val count = Arb.int(3..12).bind()
    val pairs = mutableSetOf<MonthYear>()

    while (pairs.size < count) {
        val month = Arb.int(1..12).bind()
        val year = Arb.int(2020..2030).bind()
        pairs.add(MonthYear(month, year))
    }

    pairs.toList()
}

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

    override fun getMonthlyLists(): Flow<List<MonthlyList>> {
        // Simulates what Room does: ORDER BY year DESC, month DESC
        return listsFlow.map { map ->
            map.values.sortedWith(compareByDescending<MonthlyList> { it.year }.thenByDescending { it.month })
        }
    }

    override fun getActiveMonthlyList(): Flow<MonthlyList?> {
        return listsFlow.map { map ->
            map.values.maxByOrNull { it.year * 12 + it.month }
        }
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
