package com.app.marketlist.data.ocr

import android.content.Context
import android.graphics.Point
import android.net.Uri
import com.app.marketlist.domain.usecase.TicketParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** OCR en el dispositivo (ML Kit, modelo latino empaquetado). */
@Singleton
class TicketTextRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Línea de OCR proyectada al marco del texto: x = posición a lo largo del sentido de lectura,
     * y = posición perpendicular (crece hacia abajo en el texto). [slope] es la inclinación propia de
     * la línea respecto al marco (el papel del ticket suele estar curvo o en perspectiva).
     */
    internal class Piece(
        val text: String,
        val x0: Double,
        val x1: Double,
        val cx: Double,
        val cy: Double,
        val h: Double,
        val slope: Double
    ) {
        /** Y de la línea base de esta pieza prolongada hasta la posición x. */
        fun baselineAt(x: Double) = cy + slope * (x - cx)
    }

    suspend fun recognizeRows(uri: Uri): Result<List<String>> = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(context, uri)
            val text = recognizer.process(image).await()
            val lines = text.textBlocks.flatMap { it.lines }
            Result.success(buildRows(lines.map { LineGeometry(it.text, quad(it.cornerPoints, it.boundingBox)) }))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("No se pudo leer la imagen del ticket", e))
        } finally {
            recognizer.close()
        }
    }

    /** Cuatro esquinas (arriba-izq, arriba-der, abajo-der, abajo-izq) en el sentido de lectura del texto. */
    private fun quad(corners: Array<Point>?, box: android.graphics.Rect?): List<Pair<Double, Double>>? {
        if (corners != null && corners.size == 4) return corners.map { it.x.toDouble() to it.y.toDouble() }
        if (box != null) {
            return listOf(
                box.left.toDouble() to box.top.toDouble(),
                box.right.toDouble() to box.top.toDouble(),
                box.right.toDouble() to box.bottom.toDouble(),
                box.left.toDouble() to box.bottom.toDouble()
            )
        }
        return null
    }

    private class LineGeometry(val text: String, val quad: List<Pair<Double, Double>>?)

    /**
     * Reconstruye los renglones del ticket aunque la foto esté girada (p. ej. 90°) o inclinada:
     * orienta todas las líneas según el sentido de lectura dominante, separa tickets que aparezcan
     * lado a lado en una misma foto y une las líneas que quedan en el mismo renglón.
     */
    private fun buildRows(lines: List<LineGeometry>): List<String> {
        val placed = lines.filter { it.quad != null && it.text.isNotBlank() }
        val unplaced = lines.filter { it.quad == null && it.text.isNotBlank() }.map { it.text }
        if (placed.isEmpty()) return unplaced

        // Ángulo de lectura dominante: promedio vectorial de p0->p1 ponderado por la longitud del texto.
        var sx = 0.0
        var sy = 0.0
        placed.forEach { line ->
            val q = line.quad!!
            val dx = q[1].first - q[0].first
            val dy = q[1].second - q[0].second
            val len = hypot(dx, dy)
            if (len > 0) {
                val w = line.text.length.toDouble()
                sx += w * dx / len
                sy += w * dy / len
            }
        }
        val theta = if (sx == 0.0 && sy == 0.0) 0.0 else atan2(sy, sx)
        val ux = cos(theta)
        val uy = sin(theta)
        val vx = -uy
        val vy = ux

        val pieces = placed.map { line ->
            val q = line.quad!!
            val px = q.map { it.first * ux + it.second * uy }
            val py = q.map { it.first * vx + it.second * vy }
            val height = hypot(q[3].first - q[0].first, q[3].second - q[0].second)
            val dx = px[1] - px[0]
            val dy = py[1] - py[0]
            // Con textos cortos el ángulo no es fiable: se asume alineado con el marco.
            val slope = if (line.text.length < 8 || dx <= 0.0) 0.0 else (dy / dx).coerceIn(-0.5, 0.5)
            Piece(line.text, px.min(), px.max(), px.average(), py.average(), max(height, 1.0), slope)
        }
        val medianHeight = pieces.map { it.h }.sorted()[pieces.size / 2]

        val rows = mutableListOf<String>()
        splitTickets(pieces, medianHeight).forEachIndexed { index, ticket ->
            if (index > 0) rows.add(TicketParser.TICKET_SEPARATOR)
            rows.addAll(groupRows(ticket, medianHeight))
        }
        return rows + unplaced
    }

    /** Separa tickets colocados lado a lado: bandas verticales sin ninguna línea de texto. */
    private fun splitTickets(pieces: List<Piece>, medianHeight: Double): List<List<Piece>> {
        if (pieces.size < 12) return listOf(pieces)
        val sorted = pieces.sortedBy { it.x0 }
        val totalWidth = sorted.maxOf { it.x1 } - sorted.first().x0
        val minGap = max(3 * medianHeight, 0.08 * totalWidth)
        val groups = mutableListOf<MutableList<Piece>>()
        var reach = Double.NEGATIVE_INFINITY
        sorted.forEach { piece ->
            if (groups.isEmpty() || piece.x0 - reach >= minGap) {
                groups.add(mutableListOf(piece))
                reach = piece.x1
            } else {
                groups.last().add(piece)
                reach = max(reach, piece.x1)
            }
        }
        // Un "ticket" con muy pocas líneas es ruido (sello, logotipo): se une al anterior.
        val merged = mutableListOf<MutableList<Piece>>()
        groups.forEach { group ->
            if (merged.isNotEmpty() && group.size < 6) merged.last().addAll(group) else merged.add(group)
        }
        if (merged.size > 1 && merged.first().size < 6) {
            val first = merged.removeAt(0)
            merged.first().addAll(first)
        }
        return merged
    }

    /**
     * Une líneas del mismo renglón visual (nombre a la izquierda, precio a la derecha). Las líneas más
     * largas (más fiables en su inclinación) fijan la línea base de cada renglón y las cortas
     * (precios, pesos) se unen al renglón cuya línea base pasa por su centro.
     */
    private companion object {
        /** Longitud (caracteres) desde la cual la inclinación de una línea de OCR se considera fiable. */
        const val RELIABLE_LENGTH = 12
    }

    private fun groupRows(pieces: List<Piece>, medianHeight: Double): List<String> {
        if (pieces.isEmpty()) return emptyList()
        val tolerance = 0.5 * medianHeight
        class Row(val anchor: Piece, val slope: Double) {
            val members = mutableListOf(anchor)
            fun baselineAt(x: Double) = anchor.cy + slope * (x - anchor.cx)
        }
        val rows = mutableListOf<Row>()

        fun attachOrNull(piece: Piece): Boolean {
            val best = rows
                .map { it to abs(piece.cy - it.baselineAt(piece.cx)) }
                .filter { it.second < tolerance }
                .minByOrNull { it.second }
            best?.first?.members?.add(piece)
            return best != null
        }

        // 1) Las líneas largas fijan los renglones: su inclinación es fiable.
        val (long, short) = pieces.sortedByDescending { it.text.length }.partition { it.text.length >= RELIABLE_LENGTH }
        long.forEach { piece -> if (!attachOrNull(piece)) rows.add(Row(piece, piece.slope)) }
        val longRows = rows.toList()

        // 2) Las cortas (precios, pesos, encabezados) se unen al renglón cuya línea base pasa por su
        //    centro; si no hay ninguno forman un renglón propio con la inclinación de los vecinos.
        short.forEach { piece ->
            if (!attachOrNull(piece)) {
                val neighbours = longRows.sortedBy { abs(it.anchor.cy - piece.cy) }.take(5).map { it.slope }.sorted()
                rows.add(Row(piece, neighbours.getOrNull(neighbours.size / 2) ?: 0.0))
            }
        }
        val refX = pieces.map { it.cx }.sorted()[pieces.size / 2]
        return rows
            .sortedBy { it.baselineAt(refX) }
            .map { row -> row.members.sortedBy { min(it.x0, it.x1) }.joinToString(" ") { it.text } }
    }

    /** Borra un archivo temporal propio (URI de FileProvider). */
    fun deleteTemp(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }
}
