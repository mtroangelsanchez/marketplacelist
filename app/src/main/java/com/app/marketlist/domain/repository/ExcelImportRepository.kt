package com.app.marketlist.domain.repository

import android.net.Uri
import com.app.marketlist.domain.model.ExcelImportResult

interface ExcelImportRepository {
    suspend fun importProducts(uri: Uri): Result<ExcelImportResult>
}
