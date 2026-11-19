package com.app.marketlist.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyListProductDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(monthlyListProduct: MonthlyListProductEntity)

    @Query("DELETE FROM monthly_list_products WHERE monthly_list_id = :listId AND product_id = :productId")
    suspend fun delete(listId: Long, productId: Long)

    @Query("SELECT p.* FROM products p INNER JOIN monthly_list_products mlp ON p.id = mlp.product_id WHERE mlp.monthly_list_id = :listId")
    fun getProductsForList(listId: Long): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM monthly_list_products WHERE monthly_list_id = :listId")
    suspend fun countProducts(listId: Long): Int

    @Query("SELECT * FROM monthly_list_products WHERE monthly_list_id = :listId")
    suspend fun getAllForList(listId: Long): List<MonthlyListProductEntity>
}
