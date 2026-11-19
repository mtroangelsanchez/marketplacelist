package com.app.marketlist.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_records",
    indices = [
        Index(value = ["product_id", "store_id", "monthly_list_id"], unique = true),
        Index(value = ["product_id"]),
        Index(value = ["store_id"]),
        Index(value = ["monthly_list_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["store_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MonthlyListEntity::class,
            parentColumns = ["id"],
            childColumns = ["monthly_list_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PriceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "store_id") val storeId: Long,
    @ColumnInfo(name = "monthly_list_id") val monthlyListId: Long,
    @ColumnInfo(name = "price") val price: Double,
    @ColumnInfo(name = "record_date") val recordDate: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_status") val syncStatus: String = "PENDING"
)
