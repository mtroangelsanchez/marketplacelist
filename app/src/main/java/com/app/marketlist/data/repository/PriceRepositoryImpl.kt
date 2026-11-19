package com.app.marketlist.data.repository

import com.app.marketlist.data.local.db.PriceRecordDao
import com.app.marketlist.data.local.db.StoreDao
import com.app.marketlist.data.local.db.toDomain
import com.app.marketlist.data.local.db.toEntity
import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.repository.PriceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceRepositoryImpl @Inject constructor(
    private val priceRecordDao: PriceRecordDao,
    private val storeDao: StoreDao
) : PriceRepository {

    override suspend fun registerPrice(priceRecord: PriceRecord): Result<Unit> {
        return try {
            val entity = priceRecord.toEntity()
            priceRecordDao.insertOrReplace(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getPriceHistory(productId: Long, lastNMonths: Int): Flow<List<PriceRecord>> {
        // Approximate limit: each month could have prices in multiple stores
        // Using lastNMonths * 10 as a reasonable upper bound
        val limit = lastNMonths * 10
        return priceRecordDao.getHistoryLastNMonths(productId, limit).map { entities ->
            entities.map { entity ->
                val storeName = storeDao.getById(entity.storeId)?.name
                entity.toDomain(storeName)
            }
        }
    }

    override fun getAllPrices(): Flow<List<PriceRecord>> {
        return priceRecordDao.getAll().map { entities ->
            entities.map { entity ->
                val storeName = storeDao.getById(entity.storeId)?.name
                entity.toDomain(storeName)
            }
        }
    }

    override fun getPricesForList(monthlyListId: Long): Flow<List<PriceRecord>> {
        return priceRecordDao.getForList(monthlyListId).map { entities ->
            entities.map { entity ->
                val storeName = storeDao.getById(entity.storeId)?.name
                entity.toDomain(storeName)
            }
        }
    }

    override suspend fun getLatestPrice(productId: Long): PriceRecord? {
        val entity = priceRecordDao.getLatestForProduct(productId) ?: return null
        return entity.toDomain(storeDao.getById(entity.storeId)?.name)
    }

    override fun getLowestPrice(productId: Long, monthlyListId: Long): Flow<PriceRecord?> {
        return priceRecordDao.getLowestForProductInList(productId, monthlyListId).map { entity ->
            entity?.let {
                val storeName = storeDao.getById(it.storeId)?.name
                it.toDomain(storeName)
            }
        }
    }
}
