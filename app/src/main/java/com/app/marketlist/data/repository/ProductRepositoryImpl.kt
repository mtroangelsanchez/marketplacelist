package com.app.marketlist.data.repository

import androidx.room.withTransaction
import com.app.marketlist.data.local.db.AppDatabase
import com.app.marketlist.data.local.db.ProductDao
import com.app.marketlist.data.local.db.ProductEntity
import com.app.marketlist.data.local.db.ProductStoreDao
import com.app.marketlist.data.local.db.ProductStoreEntity
import com.app.marketlist.data.local.db.toDomain
import com.app.marketlist.data.local.db.toEntity
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val productStoreDao: ProductStoreDao,
    private val appDatabase: AppDatabase
) : ProductRepository {

    private suspend fun withStores(entity: ProductEntity): Product {
        val stores = productStoreDao.getStoresForProduct(entity.id).first().map { it.toDomain() }
        val prices = productStoreDao.getByProduct(entity.id)
            .mapNotNull { ps -> ps.initialPrice?.let { ps.storeId to it } }
            .toMap()
        return entity.toDomain(stores, prices)
    }

    override fun getProducts(monthlyListId: Long): Flow<List<Product>> {
        return productDao.getByMonthlyList(monthlyListId).map { entities ->
            entities.map { entity -> withStores(entity) }
        }
    }

    override fun searchProducts(query: String, monthlyListId: Long): Flow<List<Product>> {
        return productDao.searchByNameOrCategory(query).map { entities ->
            entities.map { entity -> withStores(entity) }
        }
    }

    override fun getProductByBarcode(barcode: String): Flow<Product?> {
        return productDao.getByBarcode(barcode).map { entity ->
            entity?.let { withStores(it) }
        }
    }

    override suspend fun createProduct(product: Product): Result<Long> {
        return try {
            val productId = appDatabase.withTransaction {
                val entity = product.toEntity()
                val id = productDao.insert(entity)
                val productStoreEntities = product.stores.map { store ->
                    ProductStoreEntity(productId = id, storeId = store.id, initialPrice = product.initialPrices[store.id])
                }
                productStoreDao.insertAll(productStoreEntities)
                id
            }
            Result.success(productId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProduct(product: Product): Result<Unit> {
        return try {
            appDatabase.withTransaction {
                val entity = product.toEntity()
                productDao.update(entity)
                productStoreDao.deleteByProduct(product.id)
                val productStoreEntities = product.stores.map { store ->
                    ProductStoreEntity(productId = product.id, storeId = store.id, initialPrice = product.initialPrices[store.id])
                }
                productStoreDao.insertAll(productStoreEntities)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteProduct(productId: Long): Result<Unit> {
        return try {
            productDao.deleteById(productId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
