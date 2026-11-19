package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.MonthlyList
import java.time.LocalDate

data class ListSuggestion(val month: Int, val year: Int, val label: String)

/**
 * Sugiere el período (mes/año) y el nombre de la nueva lista. El período siempre queda libre
 * (el mes siguiente a la lista más reciente, o el mes actual si esa lista es anterior), de modo
 * que se respeta la unicidad (month, year). El nombre es libre y solo se guarda en `label`.
 */
object ListNameSuggester {
    private val MONTH_NAMES = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    fun monthName(month: Int): String = MONTH_NAMES[month - 1]

    fun periodLabel(month: Int, year: Int): String = "${monthName(month)} $year"

    fun suggestNext(lists: List<MonthlyList>, today: LocalDate): ListSuggestion {
        val currentIndex = today.year * 12 + (today.monthValue - 1)
        val latestIndex = lists.maxOfOrNull { it.year * 12 + (it.month - 1) }
        val index = when {
            latestIndex == null -> currentIndex
            latestIndex < currentIndex -> currentIndex
            else -> latestIndex + 1
        }
        val year = index / 12
        val month = index % 12 + 1
        return ListSuggestion(month, year, periodLabel(month, year))
    }
}
