package com.app.marketlist.domain.repository

import com.app.marketlist.domain.model.ListSheetImportRequest
import com.app.marketlist.domain.model.ListSheetImportResult

interface ListSheetImportRepository {
    /** Crea la lista, los productos nuevos, sus vínculos y solo los precios confirmados, en una transacción. */
    suspend fun import(request: ListSheetImportRequest): Result<ListSheetImportResult>
}
