package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanBarcodeUseCase @Inject constructor(
    private val productRepo: ProductRepository
) {
    operator fun invoke(barcode: String): Flow<Product?> {
        return productRepo.getProductByBarcode(barcode)
    }
}
