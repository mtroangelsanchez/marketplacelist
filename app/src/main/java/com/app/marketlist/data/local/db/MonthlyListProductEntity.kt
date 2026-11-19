package com.app.marketlist.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "monthly_list_products",
    primaryKeys = ["monthly_list_id", "product_id"],
    foreignKeys = [
        ForeignKey(
            entity = MonthlyListEntity::class,
            parentColumns = ["id"],
            childColumns = ["monthly_list_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["monthly_list_id"]),
        Index(value = ["product_id"])
    ]
)
data class MonthlyListProductEntity(
    @ColumnInfo(name = "monthly_list_id") val monthlyListId: Long,
    @ColumnInfo(name = "product_id") val productId: Long
)
