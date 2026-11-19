package com.app.marketlist.domain.repository

import com.app.marketlist.domain.model.Store
import kotlinx.coroutines.flow.Flow

interface StoreRepository {
    fun getAllStores(): Flow<List<Store>>
    suspend fun createStore(store: Store): Result<Long>
    suspend fun updateStore(store: Store): Result<Unit>
    suspend fun deleteStore(storeId: Long): Result<Unit>
    fun isStorePredefined(storeId: Long): Boolean
}
