package com.app.marketlist.domain.model

import java.time.LocalDate

/** Línea confirmada: producto existente (productId) o producto nuevo (newProductName). */
data class TicketImportLine(
    val productId: Long?,
    val newProductName: String?,
    val price: Double,
    /** Categoría del producto nuevo (departamento del ticket); null usa "Otros". */
    val category: String? = null
)

data class TicketImportRequest(
    val storeId: Long,
    val label: String,
    val month: Int,
    val year: Int,
    val recordDate: LocalDate,
    val copyFromListId: Long?,
    val lines: List<TicketImportLine>
)

data class TicketImportResult(
    val listId: Long,
    val productsCreated: Int,
    val pricesRegistered: Int,
    val duplicatesMerged: Int
)
