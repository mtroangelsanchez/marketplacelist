package com.app.marketlist.domain.model

data class Store(
    val id: Long = 0,
    val name: String,
    val type: StoreType,
    val isPredefined: Boolean = false
)
