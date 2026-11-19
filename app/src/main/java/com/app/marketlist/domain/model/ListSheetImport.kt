package com.app.marketlist.domain.model

/** Línea confirmada de una hoja: producto existente (productId) o nuevo (newProductName); precio opcional. */
data class ListSheetLine(
    val productId: Long?,
    val newProductName: String?,
    val price: Double?,
    /** Tienda del precio (la del último registro si se usó la sugerencia); null usa la tienda de referencia. */
    val priceStoreId: Long?
)

data class ListSheetImportRequest(
    val label: String,
    val month: Int,
    val year: Int,
    val referenceStoreId: Long?,
    val lines: List<ListSheetLine>
)

data class ListSheetImportResult(
    val listId: Long,
    val productsCreated: Int,
    val pricesRegistered: Int
)
