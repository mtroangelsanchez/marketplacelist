package com.app.marketlist.domain.model

import java.time.LocalDate

data class PriceRecord(
    val id: Long = 0,
    val productId: Long,
    val storeId: Long,
    val monthlyListId: Long,
    val price: Double,
    val recordDate: LocalDate,
    val storeName: String? = null
)
