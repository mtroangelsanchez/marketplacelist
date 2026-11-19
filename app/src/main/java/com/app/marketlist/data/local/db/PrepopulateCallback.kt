package com.app.marketlist.data.local.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database callback that inserts predefined stores on first creation.
 * Predefined stores: Walmart, 3B, Sams, Gran Bodega (SUPERMARKET) and Mercado_Local (GENERAL_MARKET).
 */
class PrepopulateCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = System.currentTimeMillis()

        val predefinedStores = listOf(
            Triple("Walmart", "SUPERMARKET", now),
            Triple("3B", "SUPERMARKET", now),
            Triple("Sams", "SUPERMARKET", now),
            Triple("Gran Bodega", "SUPERMARKET", now),
            Triple("Mercado_Local", "GENERAL_MARKET", now)
        )

        predefinedStores.forEach { (name, type, timestamp) ->
            db.execSQL(
                """
                INSERT INTO stores (name, type, is_predefined, created_at, updated_at, sync_status)
                VALUES (?, ?, 1, ?, ?, 'SYNCED')
                """.trimIndent(),
                arrayOf(name, type, timestamp, timestamp)
            )
        }
    }
}
