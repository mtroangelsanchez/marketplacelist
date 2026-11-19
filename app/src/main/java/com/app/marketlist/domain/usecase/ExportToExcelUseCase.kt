package com.app.marketlist.domain.usecase

import android.net.Uri
import com.app.marketlist.domain.repository.ExcelExportRepository
import javax.inject.Inject

class ExportToExcelUseCase @Inject constructor(
    private val excelExportRepo: ExcelExportRepository
) {
    suspend operator fun invoke(monthlyListId: Long): Result<Uri> {
        return excelExportRepo.exportMonthlyList(monthlyListId)
    }
}
