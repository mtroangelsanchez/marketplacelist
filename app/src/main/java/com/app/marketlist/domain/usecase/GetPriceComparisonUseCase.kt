package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.repository.PriceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPriceComparisonUseCase @Inject constructor(
    private val priceRepo: PriceRepository
) {
    operator fun invoke(productId: Long): Flow<List<PriceRecord>> {
        return priceRepo.getPriceHistory(productId, lastNMonths = 6)
    }
}
