package com.app.marketlist.domain.usecase

import java.time.LocalDate

data class TicketLineDraft(
    val description: String,
    val price: Double?,
    val quantity: Int = 1,
    /** Categoría sugerida a partir del encabezado de departamento bajo el que aparece el artículo. */
    val category: String? = null
)

data class ParsedTicket(
    val lines: List<TicketLineDraft>,
    val storeName: String?,
    val date: LocalDate?
)

/**
 * Parseo heurístico y tolerante del texto OCR de un ticket. Nunca lanza excepciones: si no
 * reconoce nada devuelve una lista vacía. El usuario revisa y corrige todo el resultado.
 *
 * Formatos soportados:
 *  - Descripción y precio en el mismo renglón ("LECHE 1L   24.50 A").
 *  - Descripción en un renglón y precio en los siguientes, con o sin renglones vacíos entre ellos
 *    ("LECHE 1L" / "" / "24.50"); los precios se asignan a las descripciones en orden.
 *  - Artículos por peso ("MELON" y luego "1.285 KGS X 35.00/KG 44.98T") y por cantidad
 *    ("2 X $68.54 137.08T" o "RAM CARBON 20.00 x 2 40.00A").
 *  - Encabezados de departamento ("LACTEOS", "FRUTAS Y VERDURAS---"): no son artículos y su
 *    nombre se sugiere como categoría de los artículos que le siguen.
 *  - Varios tickets en una misma lectura, separados por [TICKET_SEPARATOR].
 */
object TicketParser {

    /** Renglón especial que el reconocedor inserta entre dos tickets distintos de una misma foto. */
    const val TICKET_SEPARATOR = "\u0001"

    private const val MIN_PRICE = 0.01
    private const val MAX_PRICE = 999999.99
    private const val STORE_HEADER_ROWS = 12
    private const val MAX_PENDING = 8

    private val PRICE = Regex("(?<![\\d.,])(?:\\d{1,3}(?:[.,]\\d{3})+|\\d{1,6})[.,]\\s?\\d{2}(?!\\d)")
    private val TRAILING_TAX = Regex("^\\s*[A-Za-z]?\\s*$")
    private val QUANTITY_ROW = Regex("^\\s*(\\d{1,3})\\s*[xX@]\\s*\\$?\\s*(\\d{1,6}[.,]\\d{2})(?!\\d)")
    private val INLINE_QUANTITY = Regex("(?<![\\d.,])(\\d{1,3})\\s*[xX@]\\s*\\$?\\s*(\\d{1,6}[.,]\\d{2})(?!\\d)")
    private val UNIT_TIMES_QTY = Regex("(?<![\\d.,])(\\d{1,6}[.,]\\d{2})\\s*[xX@]\\s*(\\d{1,3})(?![\\d.,])")
    private val WEIGHT_ROW = Regex("(?i)\\d+[.,]\\d{2,3}\\s*k\\w{0,2}\\s*[xX×]\\s*\\$?\\s*\\d+[.,]\\d{2}\\s*/\\s*k\\w{0,2}")
    // Precio al final del renglón con ruido típico del OCR: "O"/"U" por "0" ("151.0UA") y la
    // letra de impuesto leída como "1"/"I"/"l" ("16.221").
    private val PRICE_NOISE = Regex("(\\d{1,6}[.,])\\s?([0-9OoUu]{2})([A-Za-z1Il|]?)\\s*$")
    private val DATE_YMD = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")
    private val DATE_DMY = Regex("(?<!\\d)(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4}|\\d{2})(?!\\d)")
    private val IGNORED = setOf(
        "total", "subtotal", "iva", "impuesto", "cambio", "efectivo", "tarjeta", "credito", "debito",
        "visa", "recibido", "descuento", "redondeo", "folio", "ticket",
        "rfc", "fecha", "hora", "cajero", "tel", "gracias", "articulos", "vuelva",
        "sucursal", "factura", "puntos", "saldo",
        "evento", "promocion", "romocion", "ajuste", "cant", "articulo", "articul0", "descripcion"
    )
    // Palabras que también aparecen en nombres de producto ("CAJA GALLETAS"): el renglón solo se
    // ignora si no trae otras palabras de 3+ letras.
    private val WEAK_IGNORED = setOf("master", "pago", "ahorro", "caja")

    private class Pending(val description: String, val category: String?)

    private fun isIgnoredRow(tokens: List<String>): Boolean {
        if (tokens.any { it in IGNORED }) return true
        if (tokens.none { it in WEAK_IGNORED }) return false
        return tokens.none { it.length >= 3 && it.any { c -> c.isLetter() } && it !in WEAK_IGNORED }
    }

    fun parse(rows: List<String>, knownStoreNames: List<String>): ParsedTicket {
        return try {
            val textRows = rows.filter { it != TICKET_SEPARATOR }
            ParsedTicket(
                lines = parseLines(rows),
                storeName = detectStore(textRows, knownStoreNames),
                date = detectDate(textRows)
            )
        } catch (e: Exception) {
            ParsedTicket(emptyList(), null, null)
        }
    }

    private fun parseLines(rows: List<String>): List<TicketLineDraft> {
        val result = mutableListOf<TicketLineDraft>()
        val pending = ArrayDeque<Pending>()
        var category: String? = null
        var ended = false
        // Precio que el OCR leyó ANTES de su descripción (renglón de solo precio sin descripción pendiente).
        var orphan: Pair<Double, Int>? = null

        fun addPending(price: Double, quantity: Int = 1): Boolean {
            val desc = pending.removeFirstOrNull() ?: return false
            result.add(TicketLineDraft(desc.description, price, quantity, desc.category))
            return true
        }

        for (rawRow in rows) {
            if (rawRow == TICKET_SEPARATOR) {
                pending.clear()
                orphan = null
                category = null
                ended = false
                continue
            }
            val row = normalizePriceNoise(rawRow.trim())
            if (row.isBlank() || ended) continue

            val tokens = ProductMatcher.normalize(row).split(' ')
            // Con artículos ya leídos, "TOTAL"/"SUBTOTAL" cierra la lista: lo que sigue es pie de ticket.
            if (result.isNotEmpty() && tokens.any { it == "total" || it == "subtotal" }) {
                ended = true
                pending.clear()
                orphan = null
                continue
            }
            if (isIgnoredRow(tokens) || DATE_DMY.containsMatchIn(row) || DATE_YMD.containsMatchIn(row) ||
                row.contains("**")
            ) {
                pending.clear()
                orphan = null
                continue
            }

            val header = DepartmentCategory.headerText(row)
            if (header != null) {
                category = DepartmentCategory.categoryFor(header)
                pending.clear()
                orphan = null
                continue
            }

            // Artículo por peso: la descripción va en el renglón anterior y el importe al final de este.
            val weight = WEIGHT_ROW.find(row)
            if (weight != null) {
                val price = PRICE.findAll(row.substring(weight.range.last + 1)).lastOrNull()?.let { toPrice(it.value) }
                if (price != null) addPending(price)
                continue
            }

            val quantity = QUANTITY_ROW.find(row)
            if (quantity != null) {
                val unit = toPrice(quantity.groupValues[2])
                val qty = (quantity.groupValues[1].toIntOrNull() ?: 1).coerceAtLeast(1)
                if (unit != null) addPending(unit, qty)
                continue
            }

            val inline = INLINE_QUANTITY.find(row)
            if (inline != null) {
                val unit = toPrice(inline.groupValues[2])
                val desc = cleanDescription(row.substring(0, inline.range.first))
                if (unit != null && letters(desc) >= 2) {
                    result.add(
                        TicketLineDraft(
                            desc, unit, (inline.groupValues[1].toIntOrNull() ?: 1).coerceAtLeast(1), category
                        )
                    )
                    pending.clear()
                    continue
                }
            }

            val match = PRICE.findAll(row).lastOrNull()
            if (match != null && TRAILING_TAX.matches(row.substring(match.range.last + 1))) {
                // Importes negativos son descuentos ("Evento $ -55.24"): no son artículos.
                if (match.range.first > 0 && row[match.range.first - 1] == '-') continue
                var price = toPrice(match.value)
                var qty = 1
                var before = row.substring(0, match.range.first)
                UNIT_TIMES_QTY.findAll(before).lastOrNull()?.let { unitQty ->
                    toPrice(unitQty.groupValues[1])?.let { price = it }
                    qty = (unitQty.groupValues[2].toIntOrNull() ?: 1).coerceAtLeast(1)
                    before = before.removeRange(unitQty.range)
                }
                val desc = cleanDescription(before)
                val finalPrice = price
                if (finalPrice != null && letters(desc) >= 2) {
                    result.add(TicketLineDraft(desc, finalPrice, qty, category))
                    pending.clear()
                } else if (finalPrice != null && letters(desc) == 0) {
                    // Renglón solo con precio: pertenece a la descripción pendiente más antigua.
                    if (!addPending(finalPrice, qty)) orphan = finalPrice to qty
                }
            } else {
                val desc = cleanDescription(row)
                if (letters(desc) >= 2) {
                    val early = orphan
                    if (early != null && pending.isEmpty()) {
                        result.add(TicketLineDraft(desc, early.first, early.second, category))
                        orphan = null
                    } else {
                        if (pending.size >= MAX_PENDING) pending.removeFirst()
                        pending.addLast(Pending(desc, category))
                    }
                }
            }
        }
        return result
    }

    private fun letters(text: String) = text.count { it.isLetter() }

    private fun toPrice(text: String): Double? {
        val separator = text.lastIndexOfAny(charArrayOf('.', ','))
        if (separator < 0) return null
        val whole = text.substring(0, separator).filter { it.isDigit() }
        val decimals = text.substring(separator + 1).filter { it.isDigit() }
        val value = "$whole.$decimals".toDoubleOrNull() ?: return null
        return if (value in MIN_PRICE..MAX_PRICE) value else null
    }

    /** "151.0UA" -> "151.00 A"; "16.221" -> "16.22 T". Solo toca el precio al final del renglón. */
    private fun normalizePriceNoise(row: String): String {
        val m = PRICE_NOISE.find(row) ?: return row
        val decimals = m.groupValues[2].map { if (it in "OoUu") '0' else it }.joinToString("")
        val flag = m.groupValues[3].let { if (it.isEmpty()) "" else if (it[0].isLetter() && it[0] != 'I' && it[0] != 'l') it else "T" }
        return row.substring(0, m.range.first) + m.groupValues[1] + decimals + (if (flag.isEmpty()) "" else " $flag")
    }

    /** Un token es "código" (EAN, PLU, SKU) si tiene 4+ dígitos y casi todo son dígitos. */
    private fun isCodeToken(token: String): Boolean {
        val digits = token.count { it.isDigit() }
        return digits >= 4 && digits * 10 >= token.length * 7
    }

    private fun cleanDescription(text: String): String {
        val tokens = text.replace("$", " ").replace("*", " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val start = tokens.indexOfFirst { !isCodeToken(it) }.let { if (it < 0) tokens.size else it }
        return tokens.drop(start).joinToString(" ").trim()
    }
    fun detectDate(rows: List<String>): LocalDate? {
        for (row in rows) {
            DATE_YMD.find(row)?.let { m ->
                safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
            }
            DATE_DMY.find(row)?.let { m ->
                val rawYear = m.groupValues[3]
                val year = if (rawYear.length == 2) 2000 + rawYear.toInt() else rawYear.toInt()
                safeDate(year, m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { return it }
            }
        }
        return null
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        try { LocalDate.of(year, month, day) } catch (e: Exception) { null }

    fun detectStore(rows: List<String>, knownStoreNames: List<String>): String? {
        val known = knownStoreNames
            .map { it to ProductMatcher.normalize(it) }
            .filter { it.second.length >= 2 }
            .sortedByDescending { it.second.length }
        if (known.isEmpty()) return null
        val normalizedRows = rows.map { ProductMatcher.normalize(it) }
        for (scope in listOf(normalizedRows.take(STORE_HEADER_ROWS), normalizedRows)) {
            val joined = " " + scope.joinToString(" ") + " "
            known.firstOrNull { joined.contains(" " + it.second + " ") }?.let { return it.first }
        }
        return null
    }
}
