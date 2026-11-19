package com.app.marketlist.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.app.marketlist.data.export.ExcelImporter
import com.app.marketlist.data.export.ExcelRawRow
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
import com.app.marketlist.data.local.db.StoreDao
import com.app.marketlist.data.local.db.StoreEntity
import com.app.marketlist.domain.model.ExcelImportResult
import com.app.marketlist.domain.repository.ExcelImportRepository
import com.app.marketlist.domain.usecase.ListNameSuggester
import com.app.marketlist.domain.usecase.ProductMatcher
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcelImportRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val excelImporter: ExcelImporter,
    private val monthlyListDao: MonthlyListDao,
    private val productDao: ProductDao,
    private val storeDao: StoreDao,
    private val productStoreDao: ProductStoreDao,
    private val monthlyListProductDao: MonthlyListProductDao,
    private val priceRecordDao: PriceRecordDao
) : ExcelImportRepository {

    private companion object {
        const val DEFAULT_CATEGORY = "Otros"
        const val BATCH_SIZE = 200
        const val MIN_PRICE = 0.01
        const val MAX_PRICE = 999_999.99
    }

    override suspend fun importProducts(uri: Uri): Result<ExcelImportResult> {
        val rows = excelImporter.read(uri).getOrElse { return Result.failure(it) }
        return try {
            var created = 0
            var skipped = 0
            var storesCreated = 0
            val errors = ArrayList<String>()
            val knownProducts = productDao.getAllOnce().map { ProductMatcher.normalize(it.name) }.toMutableSet()
            val storesByName = storeDao.getAllOnce()
                .associateBy { ProductMatcher.normalize(it.name) }
                .toMutableMap()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            val recordDate = today.atStartOfDay(zone).toInstant().toEpochMilli()

            rows.chunked(BATCH_SIZE).forEach { batch ->
                appDatabase.withTransaction {
                    val now = System.currentTimeMillis()
                    var listId: Long? = null
                    batch.forEach { row ->
                        val error = validate(row)
                        if (error != null) {
                            errors += "Fila ${row.rowNumber}: $error"
                            return@forEach
                        }
                        val price = parsePrice(row.priceText)
                        if (row.priceText != null && price == null) {
                            errors += "Fila ${row.rowNumber}: precio inválido (${row.priceText})"
                            return@forEach
                        }
                        val key = ProductMatcher.normalize(row.product)
                        if (key in knownProducts) {
                            skipped++
                            return@forEach
                        }
                        val storeKey = ProductMatcher.normalize(row.store)
                        val store = storesByName[storeKey] ?: run {
                            val name = row.store.trim().take(100)
                            val id = storeDao.insert(
                                StoreEntity(
                                    name = name,
                                    type = "SUPERMARKET",
                                    isPredefined = false,
                                    createdAt = now,
                                    updatedAt = now,
                                    syncStatus = "PENDING"
                                )
                            )
                            storesCreated++
                            StoreEntity(
                                id = id, name = name, type = "SUPERMARKET", createdAt = now, updatedAt = now
                            ).also { storesByName[storeKey] = it }
                        }
                        val productId = productDao.insert(
                            ProductEntity(
                                name = row.product.trim(),
                                category = row.category.trim().take(50).ifBlank { DEFAULT_CATEGORY },
                                createdAt = now,
                                updatedAt = now,
                                syncStatus = "PENDING"
                            )
                        )
                        productStoreDao.insert(ProductStoreEntity(productId, store.id, null))
                        val activeListId = listId ?: ensureActiveListId(today, now).also { listId = it }
                        monthlyListProductDao.insert(MonthlyListProductEntity(activeListId, productId))
                        if (price != null) {
                            priceRecordDao.insertOrReplace(
                                PriceRecordEntity(
                                    productId = productId,
                                    storeId = store.id,
                                    monthlyListId = activeListId,
                                    price = price,
                                    recordDate = recordDate,
                                    updatedAt = now,
                                    syncStatus = "PENDING"
                                )
                            )
                        }
                        knownProducts += key
                        created++
                    }
                }
            }
            Result.success(ExcelImportResult(created, skipped, storesCreated, errors))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validate(row: ExcelRawRow): String? = when {
        row.product.isBlank() -> "falta el nombre del producto"
        row.store.isBlank() -> "falta la tienda"
        row.product.trim().length !in 2..100 -> "el nombre debe tener entre 2 y 100 caracteres"
        else -> null
    }

    /** Acepta "24.50" o "24,50"; devuelve null si no es válido (rango o más de 2 decimales). */
    private fun parsePrice(text: String?): Double? {
        if (text == null) return null
        var value = text.trim().replace("$", "").replace(" ", "")
        value = when {
            value.contains(',') && value.contains('.') -> value.replace(",", "")
            else -> value.replace(',', '.')
        }
        val decimal = value.toBigDecimalOrNull() ?: return null
        if (decimal.stripTrailingZeros().scale() > 2) return null
        val number = decimal.toDouble()
        return if (number < MIN_PRICE || number > MAX_PRICE) null else number
    }

    private suspend fun ensureActiveListId(today: LocalDate, now: Long): Long {
        monthlyListDao.getMostRecent()?.let { return it.id }
        return monthlyListDao.insert(
            MonthlyListEntity(
                month = today.monthValue,
                year = today.year,
                label = ListNameSuggester.periodLabel(today.monthValue, today.year),
                createdAt = now,
                updatedAt = now,
                syncStatus = "PENDING"
            )
        )
    }
}
