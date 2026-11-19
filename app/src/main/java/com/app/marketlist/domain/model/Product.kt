package com.app.marketlist.domain.model

data class Product(
    val id: Long = 0,
    val name: String,
    val category: String,
    val barcode: String? = null,
    val photoPath: String? = null,
    val stores: List<Store> = emptyList(),
    /** Precio inicial opcional por tienda (storeId -> precio). */
    val initialPrices: Map<Long, Double> = emptyMap()
)
