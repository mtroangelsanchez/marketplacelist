package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.repository.ProductRepository
import javax.inject.Inject

class DeleteProductUseCase @Inject constructor(
    private val productRepo: ProductRepository
) {
    suspend operator fun invoke(productId: Long): Result<Unit> {
        return productRepo.deleteProduct(productId)
    }
}
