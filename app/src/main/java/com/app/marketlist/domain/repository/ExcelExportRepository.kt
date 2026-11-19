package com.app.marketlist.domain.repository

import android.net.Uri

interface ExcelExportRepository {
    suspend fun exportMonthlyList(monthlyListId: Long): Result<Uri>
}
