package com.app.marketlist.data.repository

import com.app.marketlist.data.local.db.MonthlyListDao
import com.app.marketlist.data.local.db.MonthlyListEntity
import com.app.marketlist.data.local.db.MonthlyListProductDao
import com.app.marketlist.data.local.db.MonthlyListProductEntity
import com.app.marketlist.data.local.db.toDomain
import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.repository.MonthlyListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonthlyListRepositoryImpl @Inject constructor(
    private val monthlyListDao: MonthlyListDao,
    private val monthlyListProductDao: MonthlyListProductDao
) : MonthlyListRepository {

    companion object {
        private const val MAX_PRODUCTS_COPY = 500

        private val MONTH_NAMES = mapOf(
            1 to "Enero",
            2 to "Febrero",
            3 to "Marzo",
            4 to "Abril",
            5 to "Mayo",
            6 to "Junio",
            7 to "Julio",
            8 to "Agosto",
            9 to "Septiembre",
            10 to "Octubre",
            11 to "Noviembre",
            12 to "Diciembre"
        )
    }

    override fun getMonthlyLists(): Flow<List<MonthlyList>> {
        return monthlyListDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getActiveMonthlyList(): Flow<MonthlyList?> {
        return monthlyListDao.getActive().map { entity ->
            entity?.toDomain()
        }
    }

    override fun getListsForStore(storeId: Long): Flow<List<MonthlyList>> {
        return monthlyListDao.getForStore(storeId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createMonthlyList(month: Int, year: Int, label: String?): Result<MonthlyList> {
        return try {
            val existing = monthlyListDao.getByMonthAndYear(month, year)
            if (existing != null) {
                Result.failure(
                    IllegalStateException("Ya existe una lista para ${MONTH_NAMES[month]} $year")
                )
            } else {
                val label = label?.trim()?.take(50)?.ifBlank { null } ?: "${MONTH_NAMES[month]} $year"
                val now = System.currentTimeMillis()
                val entity = MonthlyListEntity(
                    month = month,
                    year = year,
                    label = label,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = "PENDING"
                )
                val id = monthlyListDao.insert(entity)
                val createdList = MonthlyList(
                    id = id,
                    month = month,
                    year = year,
                    label = label
                )
                Result.success(createdList)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun copyFromPrevious(sourceListId: Long, targetListId: Long): Result<Unit> {
        return try {
            val sourceProducts = monthlyListProductDao.getAllForList(sourceListId)
            val productsToCopy = sourceProducts.take(MAX_PRODUCTS_COPY)
            for (product in productsToCopy) {
                monthlyListProductDao.insert(
                    MonthlyListProductEntity(
                        monthlyListId = targetListId,
                        productId = product.productId
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addProductToList(productId: Long, listId: Long): Result<Unit> {
        return try {
            monthlyListProductDao.insert(
                MonthlyListProductEntity(
                    monthlyListId = listId,
                    productId = productId
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeProductFromList(productId: Long, listId: Long): Result<Unit> {
        return try {
            monthlyListProductDao.delete(listId, productId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
