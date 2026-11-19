package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.model.ListSheetImportRequest
import com.app.marketlist.domain.model.ListSheetImportResult
import com.app.marketlist.domain.repository.ListSheetImportRepository
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

class ImportListSheetUseCase @Inject constructor(
    private val repository: ListSheetImportRepository
) {
    suspend operator fun invoke(request: ListSheetImportRequest): Result<ListSheetImportResult> {
        val invalid = mutableListOf<String>()
        if (request.lines.isEmpty()) invalid.add("artículos")
        if (request.lines.any { it.price != null && !isValidPrice(it.price) }) invalid.add("precio")
        if (request.lines.any { it.productId == null && (it.newProductName?.trim()?.length ?: 0) !in 2..100 }) {
            invalid.add("nombre")
        }
        if (request.lines.any { it.price != null && (it.priceStoreId ?: request.referenceStoreId) == null }) {
            invalid.add("tienda")
        }
        if (invalid.isNotEmpty()) {
            return Result.failure(AppErrorException(AppError.ValidationError(invalid)))
        }
        val label = request.label.trim().ifBlank {
            ListNameSuggester.periodLabel(request.month, request.year)
        }
        return repository.import(request.copy(label = label))
    }

    // Mismas reglas que RegisterPriceUseCase.
    private fun isValidPrice(price: Double): Boolean {
        if (price < 0.01 || price > 999999.99) return false
        val reconstructed = (price * 100).roundToInt() / 100.0
        return abs(price - reconstructed) < 0.000001
    }
}
