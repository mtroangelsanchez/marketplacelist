package com.app.marketlist.domain.repository

import com.app.marketlist.domain.model.MonthlyList
import kotlinx.coroutines.flow.Flow

interface MonthlyListRepository {
    fun getMonthlyLists(): Flow<List<MonthlyList>>
    fun getActiveMonthlyList(): Flow<MonthlyList?>
    fun getListsForStore(storeId: Long): Flow<List<MonthlyList>>
    suspend fun createMonthlyList(month: Int, year: Int, label: String? = null): Result<MonthlyList>
    suspend fun copyFromPrevious(sourceListId: Long, targetListId: Long): Result<Unit>
    suspend fun addProductToList(productId: Long, listId: Long): Result<Unit>
    suspend fun removeProductFromList(productId: Long, listId: Long): Result<Unit>
}
