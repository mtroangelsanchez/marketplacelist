package com.app.marketlist.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 -> v2: precio inicial opcional por tienda (columna nullable; conserva todos los datos). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE product_store ADD COLUMN initial_price REAL")
    }
}
