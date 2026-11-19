package com.app.marketlist.data.export

import android.content.Context
import android.net.Uri
import com.app.marketlist.domain.usecase.ProductMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** Fila cruda leída del Excel. [rowNumber] es el número de fila visible en Excel (base 1). */
data class ExcelRawRow(
    val rowNumber: Int,
    val product: String,
    val category: String,
    val store: String,
    val priceText: String?
)

/**
 * Lee un .xlsx con el mismo formato que [ExcelExporter] (hoja "Lista", columnas
 * Producto, Categoría, Tienda, Precio, Fecha_Registro). La fecha se ignora.
 */
@Singleton
class ExcelImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun read(uri: Uri): Result<List<ExcelRawRow>> = withContext(Dispatchers.IO) {
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
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("No se pudo abrir el archivo seleccionado")
            val workbook = try {
                input.use { XSSFWorkbook(it) }
            } catch (e: Exception) {
                throw IllegalArgumentException("El archivo no es un Excel (.xlsx) válido")
            }
            workbook.use { wb ->
                if (wb.numberOfSheets == 0) throw IllegalArgumentException("El archivo no tiene hojas")
                val sheet = wb.getSheet("Lista") ?: wb.getSheetAt(0)
                val header = sheet.getRow(sheet.firstRowNum)
                    ?: throw IllegalArgumentException("La hoja está vacía")
                val formatter = DataFormatter()
                val columns = HashMap<String, Int>()
                header.forEach { cell ->
                    columns.putIfAbsent(ProductMatcher.normalize(formatter.formatCellValue(cell)), cell.columnIndex)
                }
                val productCol = columns["producto"]
                val storeCol = columns["tienda"]
                if (productCol == null || storeCol == null) {
                    throw IllegalArgumentException("El archivo debe tener las columnas Producto y Tienda")
                }
                val categoryCol = columns["categoria"]
                val priceCol = columns["precio"]

                val rows = ArrayList<ExcelRawRow>()
                for (index in (sheet.firstRowNum + 1)..sheet.lastRowNum) {
                    val row = sheet.getRow(index) ?: continue
                    val product = text(row, productCol, formatter)
                    val store = text(row, storeCol, formatter)
                    val category = categoryCol?.let { text(row, it, formatter) }.orEmpty()
                    val price = priceCol?.let { priceText(row.getCell(it), formatter) }
                    if (product.isEmpty() && store.isEmpty() && category.isEmpty() && price == null) continue
                    rows += ExcelRawRow(index + 1, product, category, store, price)
                }
                rows
            }
        }
    }

    private fun text(row: Row, column: Int, formatter: DataFormatter): String =
        row.getCell(column)?.let { formatter.formatCellValue(it).trim() }.orEmpty()

    private fun priceText(cell: Cell?, formatter: DataFormatter): String? {
        if (cell == null) return null
        val raw = if (cell.cellType == Cell.CELL_TYPE_NUMERIC) {
            BigDecimal.valueOf(cell.numericCellValue).toPlainString()
        } else {
            formatter.formatCellValue(cell).trim()
        }
        return raw.ifEmpty { null }
    }
}
