package com.app.marketlist.domain.usecase

import android.net.Uri
import com.app.marketlist.domain.model.ExcelImportResult
import com.app.marketlist.domain.repository.ExcelImportRepository
import javax.inject.Inject

class ImportProductsFromExcelUseCase @Inject constructor(
    private val repository: ExcelImportRepository
) {
    suspend operator fun invoke(uri: Uri): Result<ExcelImportResult> = repository.importProducts(uri)
}
