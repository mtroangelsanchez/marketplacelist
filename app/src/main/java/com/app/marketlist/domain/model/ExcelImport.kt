package com.app.marketlist.domain.model

/** Resultado de importar productos desde un Excel. Cada error tiene la forma "Fila N: motivo". */
data class ExcelImportResult(
    val created: Int,
    val skipped: Int,
    val storesCreated: Int,
    val rowErrors: List<String>
)
