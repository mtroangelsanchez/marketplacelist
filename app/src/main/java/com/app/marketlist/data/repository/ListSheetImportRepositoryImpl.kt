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
import com.app.marketlist.domain.model.ListSheetImportRequest
import com.app.marketlist.domain.model.ListSheetImportResult
import com.app.marketlist.domain.model.ListSheetLine
import com.app.marketlist.domain.repository.ListSheetImportRepository
import com.app.marketlist.domain.usecase.ListNameSuggester
import com.app.marketlist.domain.usecase.ProductMatcher
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListSheetImportRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val monthlyListDao: MonthlyListDao,
    private val productDao: ProductDao,
    private val productStoreDao: ProductStoreDao,
    private val monthlyListProductDao: MonthlyListProductDao,
    private val priceRecordDao: PriceRecordDao
) : ListSheetImportRepository {

    private companion object {
        const val DEFAULT_CATEGORY = "Otros"
    }

    override suspend fun import(request: ListSheetImportRequest): Result<ListSheetImportResult> {
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

                // Deduplicar: gana la última línea por producto existente o por nombre nuevo normalizado.
                val unique = LinkedHashMap<String, ListSheetLine>()
                request.lines.forEach { line ->
                    val key = line.productId?.let { "p:$it" }
                        ?: "n:${ProductMatcher.normalize(line.newProductName.orEmpty())}"
                    unique.remove(key)
                    unique[key] = line
                }

                val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                var created = 0
                var prices = 0
                unique.values.forEach { line ->
                    val productId = line.productId ?: run {
                        val id = productDao.insert(
                            ProductEntity(
                                name = line.newProductName.orEmpty().trim(),
                                category = DEFAULT_CATEGORY,
                                createdAt = now,
                                updatedAt = now,
                                syncStatus = "PENDING"
                            )
                        )
                        request.referenceStoreId?.let { productStoreDao.insert(ProductStoreEntity(id, it, null)) }
                        created++
                        id
                    }
                    monthlyListProductDao.insert(MonthlyListProductEntity(listId, productId))
                    val storeId = line.priceStoreId ?: request.referenceStoreId
                    if (line.price != null && storeId != null) {
                        priceRecordDao.insertOrReplace(
                            PriceRecordEntity(
                                productId = productId,
                                storeId = storeId,
                                monthlyListId = listId,
                                price = line.price,
                                recordDate = today,
                                updatedAt = now,
                                syncStatus = "PENDING"
                            )
                        )
                        prices++
                    }
                }
                Result.success(ListSheetImportResult(listId, created, prices))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
