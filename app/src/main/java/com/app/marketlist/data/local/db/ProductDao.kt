package com.app.marketlist.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM products WHERE id = :id")
    fun getById(id: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    fun getByBarcode(barcode: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' COLLATE NOCASE")
    fun searchByNameOrCategory(query: String): Flow<List<ProductEntity>>

    @Query("SELECT p.* FROM products p INNER JOIN monthly_list_products mlp ON p.id = mlp.product_id WHERE mlp.monthly_list_id = :monthlyListId")
    fun getByMonthlyList(monthlyListId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products")
    suspend fun getAllOnce(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE sync_status = :status")
    suspend fun getBySyncStatus(status: String): List<ProductEntity>
}
