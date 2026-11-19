package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.repository.MonthlyListRepository
import javax.inject.Inject

class CopyMonthlyListUseCase @Inject constructor(
    private val monthlyListRepo: MonthlyListRepository
) {
    suspend operator fun invoke(sourceListId: Long, targetListId: Long): Result<Unit> {
        return monthlyListRepo.copyFromPrevious(sourceListId, targetListId)
    }
}
