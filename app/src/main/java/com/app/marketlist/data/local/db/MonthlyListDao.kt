package com.app.marketlist.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyListDao {

    @Insert
    suspend fun insert(monthlyList: MonthlyListEntity): Long

    @Update
    suspend fun update(monthlyList: MonthlyListEntity)

    @Query("SELECT * FROM monthly_lists ORDER BY year DESC, month DESC")
    fun getAll(): Flow<List<MonthlyListEntity>>

    @Query(
        "SELECT * FROM monthly_lists WHERE id IN (" +
            "SELECT mlp.monthly_list_id FROM monthly_list_products mlp " +
            "INNER JOIN product_store ps ON ps.product_id = mlp.product_id WHERE ps.store_id = :storeId) " +
            "OR id IN (SELECT monthly_list_id FROM price_records WHERE store_id = :storeId) " +
            "ORDER BY year DESC, month DESC"
    )
    fun getForStore(storeId: Long): Flow<List<MonthlyListEntity>>

    @Query("SELECT * FROM monthly_lists WHERE month = :month AND year = :year LIMIT 1")
    suspend fun getByMonthAndYear(month: Int, year: Int): MonthlyListEntity?

    @Query("SELECT * FROM monthly_lists ORDER BY year DESC, month DESC LIMIT 1")
    suspend fun getMostRecent(): MonthlyListEntity?

    @Query("SELECT * FROM monthly_lists ORDER BY year DESC, month DESC LIMIT 1")
    fun getActive(): Flow<MonthlyListEntity?>

    @Query("SELECT * FROM monthly_lists WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MonthlyListEntity?

    @Query("SELECT * FROM monthly_lists WHERE sync_status = :status")
    suspend fun getBySyncStatus(status: String): List<MonthlyListEntity>
}
