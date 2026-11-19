package com.app.marketlist.ui.screens.pricehistory

import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.model.Store

data class PriceRow(val storeName: String, val price: Double, val date: String, val isLowest: Boolean)

data class PriceGroup(val listLabel: String, val rows: List<PriceRow>)

/** Agrupa el historial por lista mensual (más reciente primero) y marca el precio más bajo de cada lista. */
object PriceHistoryBuilder {
    fun build(records: List<PriceRecord>, lists: List<MonthlyList>, stores: List<Store>): List<PriceGroup> {
        val orderedLists = lists.sortedWith(compareByDescending<MonthlyList> { it.year }.thenByDescending { it.month })
        return orderedLists.mapNotNull { list ->
            val listRecords = records.filter { it.monthlyListId == list.id }
            if (listRecords.isEmpty()) {
                null
            } else {
                val lowest = listRecords.minOf { it.price }
                PriceGroup(
                    listLabel = list.label,
                    rows = listRecords.sortedBy { it.price }.map { record ->
                        PriceRow(
                            storeName = record.storeName
                                ?: stores.firstOrNull { it.id == record.storeId }?.name
                                ?: "Tienda",
                            price = record.price,
                            date = "%02d/%02d/%d".format(
                                record.recordDate.dayOfMonth,
                                record.recordDate.monthValue,
                                record.recordDate.year
                            ),
                            isLowest = record.price == lowest
                        )
                    }
                )
            }
        }
    }
}
