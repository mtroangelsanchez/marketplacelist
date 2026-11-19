package com.app.marketlist.di

import com.app.marketlist.data.repository.ExcelExportRepositoryImpl
import com.app.marketlist.data.repository.ExcelImportRepositoryImpl
import com.app.marketlist.domain.repository.ExcelImportRepository
import com.app.marketlist.data.repository.ListSheetImportRepositoryImpl
import com.app.marketlist.domain.repository.ListSheetImportRepository
import com.app.marketlist.data.repository.MonthlyListRepositoryImpl
import com.app.marketlist.data.repository.PriceRepositoryImpl
import com.app.marketlist.data.repository.ProductRepositoryImpl
import com.app.marketlist.data.repository.StoreRepositoryImpl
import com.app.marketlist.data.repository.SyncRepositoryImpl
import com.app.marketlist.data.repository.TicketImportRepositoryImpl
import com.app.marketlist.domain.repository.TicketImportRepository
import com.app.marketlist.domain.repository.ExcelExportRepository
import com.app.marketlist.domain.repository.MonthlyListRepository
import com.app.marketlist.domain.repository.PriceRepository
import com.app.marketlist.domain.repository.ProductRepository
import com.app.marketlist.domain.repository.StoreRepository
import com.app.marketlist.domain.repository.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds repository implementations to their domain interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds
    @Singleton
    abstract fun bindStoreRepository(impl: StoreRepositoryImpl): StoreRepository

    @Binds
    @Singleton
    abstract fun bindMonthlyListRepository(impl: MonthlyListRepositoryImpl): MonthlyListRepository

    @Binds
    @Singleton
    abstract fun bindPriceRepository(impl: PriceRepositoryImpl): PriceRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindExcelExportRepository(impl: ExcelExportRepositoryImpl): ExcelExportRepository

    @Binds
    @Singleton
    abstract fun bindTicketImportRepository(impl: TicketImportRepositoryImpl): TicketImportRepository

    @Binds
    @Singleton
    abstract fun bindExcelImportRepository(impl: ExcelImportRepositoryImpl): ExcelImportRepository

    @Binds
    @Singleton
    abstract fun bindListSheetImportRepository(impl: ListSheetImportRepositoryImpl): ListSheetImportRepository
}
