package com.app.marketlist.data.repository

import androidx.room.withTransaction
import com.app.marketlist.data.local.db.AppDatabase
import com.app.marketlist.data.local.db.MonthlyListDao
import com.app.marketlist.data.local.db.MonthlyListEntity
import com.app.marketlist.data.local.db.MonthlyListProductDao
import com.app.marketlist.data.local.db.MonthlyListProductEntity
import com.app.marketlist.data.local.db.PriceRecordDao
import com.app.marketlist.data.local.db.PriceRecordEntity
import com.app.marketlist.data.local.db.ProductDao
import com.app.marketlist.data.local.db.ProductEntity
import com.app.marketlist.data.local.db.ProductStoreDao
import com.app.marketlist.data.local.db.ProductStoreEntity
import com.app.marketlist.domain.model.TicketImportRequest
import com.app.marketlist.domain.model.TicketImportResult
import com.app.marketlist.domain.repository.TicketImportRepository
import com.app.marketlist.domain.usecase.ListNameSuggester
import com.app.marketlist.domain.usecase.ProductMatcher
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketImportRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val monthlyListDao: MonthlyListDao,
    private val productDao: ProductDao,
    private val productStoreDao: ProductStoreDao,
    private val monthlyListProductDao: MonthlyListProductDao,
    private val priceRecordDao: PriceRecordDao
) : TicketImportRepository {

    private companion object {
        const val MAX_PRODUCTS_COPY = 500
        const val DEFAULT_CATEGORY = "Otros"
    }

    override suspend fun import(request: TicketImportRequest): Result<TicketImportResult> {
        return try {
            appDatabase.withTransaction {
                if (monthlyListDao.getByMonthAndYear(request.month, request.year) != null) {
                    return@withTransaction Result.failure(
                        IllegalStateException(
                            "Ya existe una lista para ${ListNameSuggester.periodLabel(request.month, request.year)}"
                        )
                    )
                }
                val now = System.currentTimeMillis()
                val listId = monthlyListDao.insert(
                    MonthlyListEntity(
                        month = request.month,
                        year = request.year,
                        label = request.label.trim().take(50),
                        createdAt = now,
                        updatedAt = now,
                        syncStatus = "PENDING"
                    )
                )
                request.copyFromListId?.let { sourceId ->
                    monthlyListProductDao.getAllForList(sourceId).take(MAX_PRODUCTS_COPY).forEach {
                        monthlyListProductDao.insert(MonthlyListProductEntity(listId, it.productId))
                    }
                }

                // Deduplicar: gana la última línea por producto existente o por nombre nuevo normalizado.
                val unique = LinkedHashMap<String, com.app.marketlist.domain.model.TicketImportLine>()
                request.lines.forEach { line ->
                    val key = line.productId?.let { "p:$it" }
                        ?: "n:${ProductMatcher.normalize(line.newProductName.orEmpty())}"
                    unique.remove(key)
                    unique[key] = line
                }

                val recordDate = request.recordDate.atStartOfDay(ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                var created = 0
                unique.values.forEach { line ->
                    val productId = line.productId ?: run {
                        val id = productDao.insert(
                            ProductEntity(
                                name = line.newProductName.orEmpty().trim(),
                                category = line.category?.trim()?.take(50)?.ifBlank { null } ?: DEFAULT_CATEGORY,
                                createdAt = now,
                                updatedAt = now,
                                syncStatus = "PENDING"
                            )
                        )
                        productStoreDao.insert(ProductStoreEntity(id, request.storeId, null))
                        created++
                        id
                    }
                    monthlyListProductDao.insert(MonthlyListProductEntity(listId, productId))
                    priceRecordDao.insertOrReplace(
                        PriceRecordEntity(
                            productId = productId,
                            storeId = request.storeId,
                            monthlyListId = listId,
                            price = line.price,
                            recordDate = recordDate,
                            updatedAt = now,
                            syncStatus = "PENDING"
                        )
                    )
                }
                Result.success(
                    TicketImportResult(
                        listId = listId,
                        productsCreated = created,
                        pricesRegistered = unique.size,
                        duplicatesMerged = request.lines.size - unique.size
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
