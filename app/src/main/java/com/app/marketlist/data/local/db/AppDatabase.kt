package com.app.marketlist.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProductEntity::class,
        StoreEntity::class,
        MonthlyListEntity::class,
        ProductStoreEntity::class,
        MonthlyListProductEntity::class,
        PriceRecordEntity::class,
        SyncMetadataEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun storeDao(): StoreDao
    abstract fun monthlyListDao(): MonthlyListDao
    abstract fun productStoreDao(): ProductStoreDao
    abstract fun monthlyListProductDao(): MonthlyListProductDao
    abstract fun priceRecordDao(): PriceRecordDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
