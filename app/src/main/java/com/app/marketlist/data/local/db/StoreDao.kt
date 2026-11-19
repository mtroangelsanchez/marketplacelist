package com.app.marketlist.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {

    @Insert
    suspend fun insert(store: StoreEntity): Long

    @Update
    suspend fun update(store: StoreEntity)

    @Query("DELETE FROM stores WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM stores ORDER BY name")
    fun getAll(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores")
    suspend fun getAllOnce(): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun getById(id: Long): StoreEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM stores WHERE name = :name AND type = :type)")
    suspend fun existsByNameAndType(name: String, type: String): Boolean

    @Query("SELECT * FROM stores WHERE sync_status = :status")
    suspend fun getBySyncStatus(status: String): List<StoreEntity>
}
