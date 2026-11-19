package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.repository.MonthlyListRepository
import javax.inject.Inject

class CreateMonthlyListUseCase @Inject constructor(
    private val monthlyListRepo: MonthlyListRepository
) {
    suspend operator fun invoke(month: Int, year: Int, label: String? = null): Result<MonthlyList> {
        return monthlyListRepo.createMonthlyList(month, year, label)
    }
}
