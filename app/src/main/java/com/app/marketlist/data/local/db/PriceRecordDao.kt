package com.app.marketlist.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(priceRecord: PriceRecordEntity): Long

    @Query("SELECT * FROM price_records WHERE product_id = :productId AND monthly_list_id = :monthlyListId")
    fun getByProductAndList(productId: Long, monthlyListId: Long): Flow<List<PriceRecordEntity>>

    @Query("SELECT pr.* FROM price_records pr INNER JOIN monthly_lists ml ON pr.monthly_list_id = ml.id WHERE pr.product_id = :productId ORDER BY ml.year DESC, ml.month DESC LIMIT :limit")
    fun getHistoryLastNMonths(productId: Long, limit: Int): Flow<List<PriceRecordEntity>>

    @Query("SELECT * FROM price_records WHERE product_id = :productId AND monthly_list_id = :monthlyListId ORDER BY price ASC LIMIT 1")
    fun getLowestForProductInList(productId: Long, monthlyListId: Long): Flow<PriceRecordEntity?>

    @Query("SELECT * FROM price_records")
    fun getAll(): Flow<List<PriceRecordEntity>>

    @Query("SELECT * FROM price_records WHERE monthly_list_id = :monthlyListId")
    fun getForList(monthlyListId: Long): Flow<List<PriceRecordEntity>>

    @Query("SELECT * FROM price_records WHERE product_id = :productId AND store_id = :storeId AND monthly_list_id = :monthlyListId LIMIT 1")
    suspend fun getExisting(productId: Long, storeId: Long, monthlyListId: Long): PriceRecordEntity?

    @Query("SELECT * FROM price_records WHERE product_id = :productId ORDER BY record_date DESC, id DESC LIMIT 1")
    suspend fun getLatestForProduct(productId: Long): PriceRecordEntity?

    @Query("SELECT * FROM price_records WHERE sync_status = :status")
    suspend fun getBySyncStatus(status: String): List<PriceRecordEntity>
}
