package com.app.marketlist.domain.repository

import com.app.marketlist.domain.model.TicketImportRequest
import com.app.marketlist.domain.model.TicketImportResult

interface TicketImportRepository {
    /** Crea la lista, los productos nuevos, sus vínculos y los precios en una sola transacción. */
    suspend fun import(request: TicketImportRequest): Result<TicketImportResult>
}
