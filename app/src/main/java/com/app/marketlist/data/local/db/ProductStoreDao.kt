package com.app.marketlist.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductStoreDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(productStore: ProductStoreEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(productStores: List<ProductStoreEntity>)

    @Query("DELETE FROM product_store WHERE product_id = :productId")
    suspend fun deleteByProduct(productId: Long)

    @Query("SELECT s.* FROM stores s INNER JOIN product_store ps ON s.id = ps.store_id WHERE ps.product_id = :productId")
    fun getStoresForProduct(productId: Long): Flow<List<StoreEntity>>

    @Query("SELECT * FROM product_store WHERE product_id = :productId")
    suspend fun getByProduct(productId: Long): List<ProductStoreEntity>
}
