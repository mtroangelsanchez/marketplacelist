package com.app.marketlist.domain.repository

import com.app.marketlist.domain.model.PriceRecord
import kotlinx.coroutines.flow.Flow

interface PriceRepository {
    suspend fun registerPrice(priceRecord: PriceRecord): Result<Unit>
    fun getPriceHistory(productId: Long, lastNMonths: Int = 6): Flow<List<PriceRecord>>
    fun getAllPrices(): Flow<List<PriceRecord>>
    fun getPricesForList(monthlyListId: Long): Flow<List<PriceRecord>>
    /** Último precio registrado del producto (mayor fecha, desempate por id) con el nombre de su tienda. */
    suspend fun getLatestPrice(productId: Long): PriceRecord?

    fun getLowestPrice(productId: Long, monthlyListId: Long): Flow<PriceRecord?>
}
