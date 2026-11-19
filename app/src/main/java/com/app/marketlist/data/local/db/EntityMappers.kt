package com.app.marketlist.data.local.db

import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.model.StoreType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ─── Product Mappers ────────────────────────────────────────────────────────────

fun ProductEntity.toDomain(stores: List<Store> = emptyList(), initialPrices: Map<Long, Double> = emptyMap()): Product = Product(
    id = id,
    name = name,
    category = category,
    barcode = barcode,
    photoPath = photoPath,
    stores = stores,
    initialPrices = initialPrices
)

fun Product.toEntity(): ProductEntity {
    val now = System.currentTimeMillis()
    return ProductEntity(
        id = id,
        name = name,
        category = category,
        barcode = barcode,
        photoPath = photoPath,
        createdAt = if (id == 0L) now else now,
        updatedAt = now,
        syncStatus = "PENDING"
    )
}

// ─── Store Mappers ──────────────────────────────────────────────────────────────

fun StoreEntity.toDomain(): Store = Store(
    id = id,
    name = name,
    type = StoreType.valueOf(type),
    isPredefined = isPredefined
)

fun Store.toEntity(): StoreEntity {
    val now = System.currentTimeMillis()
    return StoreEntity(
        id = id,
        name = name,
        type = type.name,
        isPredefined = isPredefined,
        createdAt = if (id == 0L) now else now,
        updatedAt = now,
        syncStatus = "PENDING"
    )
}

// ─── MonthlyList Mappers ────────────────────────────────────────────────────────

fun MonthlyListEntity.toDomain(products: List<Product> = emptyList()): MonthlyList = MonthlyList(
    id = id,
    month = month,
    year = year,
    label = label,
    products = products
)

// ─── PriceRecord Mappers ────────────────────────────────────────────────────────

fun PriceRecordEntity.toDomain(storeName: String? = null): PriceRecord = PriceRecord(
    id = id,
    productId = productId,
    storeId = storeId,
    monthlyListId = monthlyListId,
    price = price,
    recordDate = Instant.ofEpochMilli(recordDate)
        .atZone(ZoneId.systemDefault())
        .toLocalDate(),
    storeName = storeName
)

fun PriceRecord.toEntity(): PriceRecordEntity {
    val now = System.currentTimeMillis()
    return PriceRecordEntity(
        id = id,
        productId = productId,
        storeId = storeId,
        monthlyListId = monthlyListId,
        price = price,
        recordDate = recordDate.atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        updatedAt = now,
        syncStatus = "PENDING"
    )
}
