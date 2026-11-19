package com.app.marketlist.di

import android.content.Context
import androidx.room.Room
import com.app.marketlist.data.local.db.AppDatabase
import com.app.marketlist.data.local.db.MIGRATION_1_2
import com.app.marketlist.data.local.db.MonthlyListDao
import com.app.marketlist.data.local.db.MonthlyListProductDao
import com.app.marketlist.data.local.db.PrepopulateCallback
import com.app.marketlist.data.local.db.PriceRecordDao
import com.app.marketlist.data.local.db.ProductDao
import com.app.marketlist.data.local.db.ProductStoreDao
import com.app.marketlist.data.local.db.StoreDao
import com.app.marketlist.data.local.db.SyncMetadataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides Room database and DAO instances.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "market_list_database"
        )
            .addMigrations(MIGRATION_1_2)
            .addCallback(PrepopulateCallback())
            .build()
    }

    @Provides
    fun provideProductDao(database: AppDatabase): ProductDao = database.productDao()

    @Provides
    fun provideStoreDao(database: AppDatabase): StoreDao = database.storeDao()

    @Provides
    fun provideMonthlyListDao(database: AppDatabase): MonthlyListDao = database.monthlyListDao()

    @Provides
    fun provideProductStoreDao(database: AppDatabase): ProductStoreDao = database.productStoreDao()

    @Provides
    fun provideMonthlyListProductDao(database: AppDatabase): MonthlyListProductDao = database.monthlyListProductDao()

    @Provides
    fun providePriceRecordDao(database: AppDatabase): PriceRecordDao = database.priceRecordDao()

    @Provides
    fun provideSyncMetadataDao(database: AppDatabase): SyncMetadataDao = database.syncMetadataDao()
}
