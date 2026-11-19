package com.app.marketlist.ui.screens.product

import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.repository.MonthlyListRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/** Obtiene la lista mensual activa; si no existe ninguna crea la del mes actual. */
class ActiveListManager @Inject constructor(
    private val monthlyListRepo: MonthlyListRepository
) {
    suspend fun ensureActive(): MonthlyList? {
        monthlyListRepo.getActiveMonthlyList().first()?.let { return it }
        val today = LocalDate.now()
        monthlyListRepo.createMonthlyList(today.monthValue, today.year)
        return monthlyListRepo.getActiveMonthlyList().first()
    }
}
