package com.app.marketlist.data.remote.firebase

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Formats LocalDateTime for display as the "last sync time" indicator.
 * Output pattern: "dd/MM/yyyy HH:mm"
 */
class SyncDateFormatter {
    private val displayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun format(dateTime: LocalDateTime): String {
        return dateTime.format(displayFormat)
    }
}
