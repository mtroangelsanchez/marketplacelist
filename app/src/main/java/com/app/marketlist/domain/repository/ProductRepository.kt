package com.app.marketlist.domain.repository

import com.app.marketlist.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun getProducts(monthlyListId: Long): Flow<List<Product>>
    fun searchProducts(query: String, monthlyListId: Long): Flow<List<Product>>
    fun getProductByBarcode(barcode: String): Flow<Product?>
    suspend fun createProduct(product: Product): Result<Long>
    suspend fun updateProduct(product: Product): Result<Unit>
    suspend fun deleteProduct(productId: Long): Result<Unit>
}
