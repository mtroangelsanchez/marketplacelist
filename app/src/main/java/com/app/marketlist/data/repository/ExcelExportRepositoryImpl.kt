package com.app.marketlist.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.app.marketlist.data.export.ExcelExporter
import com.app.marketlist.data.local.db.MonthlyListDao
import com.app.marketlist.data.local.db.MonthlyListProductDao
import com.app.marketlist.data.local.db.PriceRecordDao
import com.app.marketlist.data.local.db.ProductStoreDao
import com.app.marketlist.data.local.db.StoreDao
import com.app.marketlist.data.local.db.toDomain
import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.repository.ExcelExportRepository
import com.app.marketlist.domain.usecase.AppErrorException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ExcelExportRepository] that generates an Excel file from
 * a monthly list's data and returns a content URI suitable for sharing via
 * Android Sharesheet (Intent.ACTION_SEND).
 */
@Singleton
class ExcelExportRepositoryImpl @Inject constructor(
    private val excelExporter: ExcelExporter,
    private val monthlyListDao: MonthlyListDao,
    private val monthlyListProductDao: MonthlyListProductDao,
    private val productStoreDao: ProductStoreDao,
    private val storeDao: StoreDao,
    private val priceRecordDao: PriceRecordDao,
    @ApplicationContext private val context: Context
) : ExcelExportRepository {

    companion object {
        private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    }

    override suspend fun exportMonthlyList(monthlyListId: Long): Result<Uri> {
        return try {
            // 1. Get the monthly list entity
            val monthlyListEntity = monthlyListDao.getById(monthlyListId)
                ?: return Result.failure(
                    IllegalArgumentException("Monthly list with id $monthlyListId not found")
                )

            // 2. Get all products for this list
            val productEntities = monthlyListProductDao.getProductsForList(monthlyListId).first()

            // 3. Get all stores
            val storeEntities = storeDao.getAll().first()

            // 4. Get all price records for this list
            val priceRecordEntities = priceRecordDao.getForList(monthlyListId).first()

            // 5. Validate: if no price records, return ExportEmptyListError
            if (priceRecordEntities.isEmpty()) {
                return Result.failure(
                    AppErrorException(AppError.ExportEmptyListError(monthlyListEntity.label))
                )
            }

            // 6. Map entities to domain models
            val monthlyList = monthlyListEntity.toDomain()
            val stores = storeEntities.map { it.toDomain() }

            // 7. For products, get stores for each product from ProductStoreDao
            val products = productEntities.map { productEntity ->
                val productStores = productStoreDao.getStoresForProduct(productEntity.id)
                    .first()
                    .map { it.toDomain() }
                productEntity.toDomain(productStores)
            }

            val priceRecords = priceRecordEntities.map { priceRecordEntity ->
                val storeName = storeEntities.find { it.id == priceRecordEntity.storeId }?.name
                priceRecordEntity.toDomain(storeName)
            }

            // 8. Call excelExporter.export()
            val exportResult = excelExporter.export(monthlyList, products, priceRecords, stores)

            val file = exportResult.getOrElse { throwable ->
                android.util.Log.e("ExcelExport", "Fallo al generar el Excel", throwable)
                return when (throwable) {
                    is SecurityException -> Result.failure(
                        AppErrorException(
                            AppError.ExportPermissionError("Permisos insuficientes para crear el archivo de exportación")
                        )
                    )
                    is IOException -> Result.failure(
                        AppErrorException(AppError.ExportWriteError(throwable))
                    )
                    else -> Result.failure(
                        AppErrorException(AppError.ExportWriteError(throwable))
                    )
                }
            }

            // 9. Get content URI via FileProvider for sharing
            val authority = "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX"
            val contentUri = FileProvider.getUriForFile(context, authority, file)

            // 10. Return the content URI
            Result.success(contentUri)
        } catch (e: SecurityException) {
            Result.failure(
                AppErrorException(
                    AppError.ExportPermissionError("Permisos insuficientes")
                )
            )
        } catch (e: IOException) {
            Result.failure(
                AppErrorException(AppError.ExportWriteError(e))
            )
        } catch (e: Exception) {
            Result.failure(
                AppErrorException(AppError.ExportWriteError(e))
            )
        }
    }
}
