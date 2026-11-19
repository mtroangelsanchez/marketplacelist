package com.app.marketlist.domain.usecase

/** Artículo leído de una hoja de lista; el precio es opcional. */
data class SheetItemDraft(val name: String, val price: Double?)

/**
 * Convierte los renglones reconocidos por OCR de una hoja (impresa o manuscrita) en artículos.
 * Puro y sin excepciones. No usa TicketParser (pensado para tickets); solo copia su patrón de precio.
 */
object SheetListParser {

    private const val MIN_PRICE = 0.01
    private const val MAX_PRICE = 999999.99

    // Mismo patrón de precio que TicketParser (con decimales).
    private val PRICE = Regex("(?<![\\d.,])(?:\\d{1,3}(?:[.,]\\d{3})+|\\d{1,6})[.,]\\s?\\d{2}(?!\\d)")
    private val TRAILING_PRICE = Regex("\\s*\\$?\\s*(" + PRICE.pattern + ")\\s*\\$?\\s*$")
    private val BULLET = Regex("^\\s*(?:[-*•·●○▪▫–—>]+|\\[\\s*[xX]?\\s*\\]|\\(\\s*[xX]?\\s*\\)|\\d{1,3}\\s*[.)\\-]\\s+)\\s*")
    private val QUANTITY = Regex("^\\d{1,3}\\s*(?:[xX]\\s+|\\s+)(?=\\p{L})")

    fun parse(rows: List<String>): List<SheetItemDraft> {
        return try {
            val seen = HashSet<String>()
            val result = mutableListOf<SheetItemDraft>()
            rows.forEach { raw ->
                val item = parseRow(raw) ?: return@forEach
                if (seen.add(ProductMatcher.normalize(item.name))) result.add(item)
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseRow(raw: String): SheetItemDraft? {
        var row = raw.trim()
        if (row.isEmpty()) return null
        row = row.replace(BULLET, "").trim()
        row = row.replace(QUANTITY, "").trim()
        var price: Double? = null
        val match = TRAILING_PRICE.find(row)
        if (match != null) {
            price = toPrice(match.groupValues[1])
            row = row.substring(0, match.range.first).trim()
        }
        row = row.replace(Regex("\\s{2,}"), " ").trim(' ', '-', ':', '.', ',', ';')
        if (row.count { it.isLetter() } < 2) return null
        if (row.length !in 2..100) return null
        return SheetItemDraft(row, price)
    }

    private fun toPrice(text: String): Double? {
        val separator = text.lastIndexOfAny(charArrayOf('.', ','))
        if (separator < 0) return null
        val whole = text.substring(0, separator).filter { it.isDigit() }
        val decimals = text.substring(separator + 1).filter { it.isDigit() }
        val value = "$whole.$decimals".toDoubleOrNull() ?: return null
        return if (value in MIN_PRICE..MAX_PRICE) value else null
    }
}
