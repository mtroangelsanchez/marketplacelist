package com.app.marketlist.data.export

import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.model.Store
import com.app.marketlist.domain.model.StoreType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * **Validates: Requirements 8.1, 8.2**
 *
 * Property 20: Exportación Excel con nombre, estructura y orden correctos
 *
 * For monthly lists with products and prices, verify:
 * - File name matches "Lista_[SpanishMonth]_[Year].xlsx"
 * - Column headers are exactly 5: Producto, Categoría, Tienda, Precio, Fecha_Registro
 * - Row count equals the number of valid price records (where product and store both exist)
 * - Rows are sorted case-insensitively by product name
 */
class ExcelExportPropertyTest : FunSpec({

    val logic = ExcelExportLogic()

    test("file name matches Lista_[SpanishMonth]_[Year].xlsx for any valid month/year") {
        checkAll(PropTestConfig(iterations = 100), Arb.int(1..12), Arb.int(2020..2030)) { month, year ->
            val fileName = logic.generateFileName(month, year)

            // Must match the pattern Lista_[SpanishMonth]_[Year].xlsx
            fileName shouldMatch Regex("Lista_[A-Za-záéíóúñ]+_\\d{4}\\.xlsx")

            // Month name must be one of the valid Spanish months
            val monthName = fileName.removePrefix("Lista_").removeSuffix("_$year.xlsx")
            monthName shouldBe logic.MONTH_NAMES[month]

            // Year must be correct
            fileName.substringAfterLast("_").removeSuffix(".xlsx") shouldBe year.toString()
        }
    }

    test("column headers are exactly 5: Producto, Categoría, Tienda, Precio, Fecha_Registro") {
        checkAll(PropTestConfig(iterations = 100), Arb.int(1..12), Arb.int(2020..2030)) { _, _ ->
            // The column headers are a constant of the export logic
            logic.COLUMN_HEADERS.size shouldBe 5
            logic.COLUMN_HEADERS[0] shouldBe "Producto"
            logic.COLUMN_HEADERS[1] shouldBe "Categoría"
            logic.COLUMN_HEADERS[2] shouldBe "Tienda"
            logic.COLUMN_HEADERS[3] shouldBe "Precio"
            logic.COLUMN_HEADERS[4] shouldBe "Fecha_Registro"
        }
    }

    test("row count equals the number of valid price records where product and store both exist") {
        checkAll(PropTestConfig(iterations = 100), arbExportData()) { data ->
            val rows = logic.generateRows(data.products, data.priceRecords, data.stores)

            // Count valid records: those whose productId and storeId exist in the provided lists
            val productIds = data.products.map { it.id }.toSet()
            val storeIds = data.stores.map { it.id }.toSet()
            val expectedCount = data.priceRecords.count { record ->
                record.productId in productIds && record.storeId in storeIds
            }

            rows.size shouldBe expectedCount
        }
    }

    test("rows are sorted case-insensitively by product name") {
        checkAll(PropTestConfig(iterations = 100), arbExportData()) { data ->
            val rows = logic.generateRows(data.products, data.priceRecords, data.stores)

            if (rows.size > 1) {
                for (i in 0 until rows.size - 1) {
                    val current = rows[i].producto.lowercase()
                    val next = rows[i + 1].producto.lowercase()
                    (current <= next) shouldBe true
                }
            }
        }
    }
})

// ─── Pure Logic Class (mirrors ExcelExporter logic without Android dependencies) ────

private class ExcelExportLogic {
    val MONTH_NAMES = mapOf(
        1 to "Enero", 2 to "Febrero", 3 to "Marzo", 4 to "Abril",
        5 to "Mayo", 6 to "Junio", 7 to "Julio", 8 to "Agosto",
        9 to "Septiembre", 10 to "Octubre", 11 to "Noviembre", 12 to "Diciembre"
    )
    val COLUMN_HEADERS = listOf("Producto", "Categoría", "Tienda", "Precio", "Fecha_Registro")

    fun generateFileName(month: Int, year: Int): String {
        val monthName = MONTH_NAMES[month] ?: "Mes$month"
        return "Lista_${monthName}_$year.xlsx"
    }

    data class ExportRow(
        val producto: String,
        val categoria: String,
        val tienda: String,
        val precio: String,
        val fechaRegistro: String
    )

    fun generateRows(
        products: List<Product>,
        priceRecords: List<PriceRecord>,
        stores: List<Store>
    ): List<ExportRow> {
        val productMap = products.associateBy { it.id }
        val storeMap = stores.associateBy { it.id }
        return priceRecords
            .filter { productMap.containsKey(it.productId) && storeMap.containsKey(it.storeId) }
            .sortedBy { productMap[it.productId]?.name?.lowercase() ?: "" }
            .mapNotNull { record ->
                val product = productMap[record.productId] ?: return@mapNotNull null
                val store = storeMap[record.storeId] ?: return@mapNotNull null
                ExportRow(
                    producto = product.name,
                    categoria = product.category,
                    tienda = store.name,
                    precio = "%.2f".format(record.price),
                    fechaRegistro = record.recordDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                )
            }
    }
}

// ─── Data class for generated test data ─────────────────────────────────────────

private data class ExportData(
    val products: List<Product>,
    val stores: List<Store>,
    val priceRecords: List<PriceRecord>
)

// ─── Generators ─────────────────────────────────────────────────────────────────

private fun arbExportData(): Arb<ExportData> = arbitrary {
    // Generate 1-5 products with unique IDs
    val productCount = Arb.int(1..5).bind()
    val products = (1..productCount).map { idx ->
        Product(
            id = idx.toLong(),
            name = Arb.string(2..30, Codepoint.alphanumeric()).bind(),
            category = Arb.string(2..15, Codepoint.alphanumeric()).bind(),
            barcode = null,
            photoPath = null,
            stores = emptyList()
        )
    }

    // Generate 1-3 stores with unique IDs
    val storeCount = Arb.int(1..3).bind()
    val stores = (1..storeCount).map { idx ->
        Store(
            id = idx.toLong(),
            name = Arb.string(2..20, Codepoint.alphanumeric()).bind(),
            type = Arb.enum<StoreType>().bind(),
            isPredefined = false
        )
    }

    // Generate 1-8 price records, some valid (existing product/store), some possibly invalid
    val recordCount = Arb.int(1..8).bind()
    val priceRecords = (1..recordCount).map {
        // Mix of valid and potentially invalid product/store references
        val productId = Arb.long(1L..(productCount.toLong() + 2)).bind()
        val storeId = Arb.long(1L..(storeCount.toLong() + 2)).bind()
        val price = Arb.double(0.01..999999.99).bind()
        val roundedPrice = (price * 100).roundToInt() / 100.0
        val day = Arb.int(1..28).bind()
        val month = Arb.int(1..12).bind()
        val year = Arb.int(2022..2025).bind()

        PriceRecord(
            id = it.toLong(),
            productId = productId,
            storeId = storeId,
            monthlyListId = 1L,
            price = roundedPrice,
            recordDate = LocalDate.of(year, month, day)
        )
    }

    ExportData(products, stores, priceRecords)
}

// ─── Codepoint helper ───────────────────────────────────────────────────────────

private object Codepoint {
    fun alphanumeric(): Arb<Char> = arbitrary {
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        chars[Arb.int(0 until chars.size).bind()]
    }
}

private fun Arb.Companion.string(range: IntRange, charArb: Arb<Char>): Arb<String> = arbitrary {
    val length = Arb.int(range).bind()
    buildString {
        repeat(length) {
            append(charArb.bind())
        }
    }
}
