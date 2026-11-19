package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.repository.ProductRepository
import javax.inject.Inject

class CreateProductUseCase @Inject constructor(
    private val productRepo: ProductRepository
) {
    suspend operator fun invoke(product: Product): Result<Long> {
        val missingFields = mutableListOf<String>()

        if (product.name.length < 2 || product.name.length > 100) {
            missingFields.add("nombre")
        }
        if (product.category.isBlank()) {
            missingFields.add("categoría")
        }
        if (product.stores.isEmpty()) {
            missingFields.add("tienda")
        }

        if (missingFields.isNotEmpty()) {
            return Result.failure(
                AppErrorException(AppError.ValidationError(missingFields))
            )
        }

        return productRepo.createProduct(product)
    }
}
