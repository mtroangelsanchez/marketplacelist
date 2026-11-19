package com.app.marketlist.data.export

import android.content.Context
import com.app.marketlist.domain.model.MonthlyList
import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.model.Product
import com.app.marketlist.domain.model.Store
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates Excel (.xlsx) files from monthly list data using Apache POI.
 *
 * The exported file follows this format:
 * - File name: "Lista_[Mes]_[Año].xlsx"
 * - Columns: Producto, Categoría, Tienda, Precio, Fecha_Registro
 * - One row per Product-Store combination that has a PriceRecord
 * - Rows sorted alphabetically by product name (case-insensitive)
 * - Generated in the app's cache directory under "exports/"
 */
@Singleton
class ExcelExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val EXPORT_DIR = "exports"
        private const val COLUMN_WIDTH = 20 * 256
        private val MONTH_NAMES = mapOf(
            1 to "Enero", 2 to "Febrero", 3 to "Marzo", 4 to "Abril",
            5 to "Mayo", 6 to "Junio", 7 to "Julio", 8 to "Agosto",
            9 to "Septiembre", 10 to "Octubre", 11 to "Noviembre", 12 to "Diciembre"
        )
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val COLUMN_HEADERS = arrayOf("Producto", "Categoría", "Tienda", "Precio", "Fecha_Registro")
    }

    /**
     * Generates an Excel file (.xlsx) for a monthly list.
     *
     * File format:
     * - Name: "Lista_[Mes]_[Año].xlsx"
     * - Columns: Producto, Categoría, Tienda, Precio, Fecha_Registro
     * - One row per Product-Store combination that has a PriceRecord
     * - Sorted alphabetically by product name (case-insensitive)
     * - Generated in app cache directory
     *
     * @param monthlyList The monthly list to export
     * @param products List of products with their data
     * @param priceRecords Price records for the list
     * @param stores List of stores for name resolution
     * @return Result with the File on success
     */
    suspend fun export(
        monthlyList: MonthlyList,
        products: List<Product>,
        priceRecords: List<PriceRecord>,
        stores: List<Store>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // poi-android necesita un proveedor StAX explícito (aalto-xml) en Android
            System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLInputFactory",
                "com.fasterxml.aalto.stax.InputFactoryImpl"
            )
            System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLOutputFactory",
                "com.fasterxml.aalto.stax.OutputFactoryImpl"
            )
            System.setProperty(
                "org.apache.poi.javax.xml.stream.XMLEventFactory",
                "com.fasterxml.aalto.stax.EventFactoryImpl"
            )
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Lista")

            // Create header style with bold font
            val headerStyle = createHeaderStyle(workbook)

            // Create header row
            val headerRow = sheet.createRow(0)
            COLUMN_HEADERS.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.setCellStyle(headerStyle)
            }

            // Build lookup maps for efficient resolution
            val productMap = products.associateBy { it.id }
            val storeMap = stores.associateBy { it.id }

            // Build rows: one per Product-Store combination with a PriceRecord
            // Sorted alphabetically by product name (case-insensitive)
            val sortedRecords = priceRecords
                .filter { record ->
                    productMap.containsKey(record.productId) && storeMap.containsKey(record.storeId)
                }
                .sortedBy { record ->
                    productMap[record.productId]?.name?.lowercase() ?: ""
                }

            // Create data rows
            sortedRecords.forEachIndexed { index, record ->
                val product = productMap[record.productId] ?: return@forEachIndexed
                val store = storeMap[record.storeId] ?: return@forEachIndexed
                val row = sheet.createRow(index + 1)

                row.createCell(0).setCellValue(product.name)
                row.createCell(1).setCellValue(product.category)
                row.createCell(2).setCellValue(store.name)
                row.createCell(3).setCellValue(String.format("%.2f", record.price))
                row.createCell(4).setCellValue(record.recordDate.format(DATE_FORMATTER))
            }

            // Fixed widths: autoSizeColumn depends on java.awt, which is not available on Android
            for (i in COLUMN_HEADERS.indices) {
                sheet.setColumnWidth(i, COLUMN_WIDTH)
            }

            // Generate file name: "Lista_[Mes]_[Año].xlsx"
            val monthName = MONTH_NAMES[monthlyList.month] ?: "Mes${monthlyList.month}"
            val fileName = "Lista_${monthName}_${monthlyList.year}.xlsx"

            // Create export directory in cache
            val exportDir = File(context.cacheDir, EXPORT_DIR)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            // Write workbook to file
            val outputFile = File(exportDir, fileName)
            FileOutputStream(outputFile).use { outputStream ->
                workbook.write(outputStream)
            }
            workbook.close()

            outputFile
        }
    }

    private fun createHeaderStyle(workbook: XSSFWorkbook): CellStyle {
        val font: Font = workbook.createFont()
        font.setBold(true)
        val style = workbook.createCellStyle()
        style.setFont(font)
        style.setAlignment(HorizontalAlignment.CENTER)
        return style
    }
}
