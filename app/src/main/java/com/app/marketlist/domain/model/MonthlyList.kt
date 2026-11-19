package com.app.marketlist.domain.model

data class MonthlyList(
    val id: Long = 0,
    val month: Int,
    val year: Int,
    val label: String,
    val products: List<Product> = emptyList()
)
