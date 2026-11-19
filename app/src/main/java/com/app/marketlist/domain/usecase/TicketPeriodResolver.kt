package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.MonthlyList
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Período (mes, año) de la lista creada desde un ticket. Se respeta el índice único (month, year):
 * se usa el mes del ticket si está libre; si no, el mes libre más cercano hacia atrás (así la
 * lista activa, que es la más reciente, no cambia).
 */
object TicketPeriodResolver {
    private const val MAX_LABEL = 50
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private fun index(month: Int, year: Int) = year * 12 + (month - 1)

    fun resolve(ticketDate: LocalDate?, today: LocalDate, existing: List<MonthlyList>): Pair<Int, Int> {
        val base = ticketDate ?: today
        val used = existing.map { index(it.month, it.year) }.toSet()
        var idx = index(base.monthValue, base.year)
        while (idx in used) idx--
        return (idx % 12 + 1) to (idx / 12)
    }

    fun defaultLabel(storeName: String, date: LocalDate): String =
        "Ticket $storeName ${date.format(DATE_FORMAT)}".take(MAX_LABEL)

    /** La lista más reciente actual, o null si no hay listas. */
    fun mostRecent(existing: List<MonthlyList>): MonthlyList? =
        existing.maxByOrNull { index(it.month, it.year) }

    /** True si el período queda más reciente que todas las listas (pasará a ser la activa). */
    fun needsCopyFromPrevious(period: Pair<Int, Int>, existing: List<MonthlyList>): Boolean {
        val latest = mostRecent(existing) ?: return false
        return index(period.first, period.second) > index(latest.month, latest.year)
    }
}
