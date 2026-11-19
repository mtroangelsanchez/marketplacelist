package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchProductsUseCase @Inject constructor(
    private val productRepo: ProductRepository
) {
    operator fun invoke(query: String, monthlyListId: Long): Flow<List<Product>> {
        return productRepo.searchProducts(query, monthlyListId)
    }
}
