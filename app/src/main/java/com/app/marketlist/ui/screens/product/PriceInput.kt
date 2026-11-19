package com.app.marketlist.ui.screens.product

import com.app.marketlist.data.remote.firebase.SyncScheduler
import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.repository.PriceRepository
import com.app.marketlist.domain.usecase.RegisterPriceUseCase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

sealed interface PriceParseResult {
    data class Valid(val value: Double) : PriceParseResult
    data class Invalid(val message: String) : PriceParseResult
}

/** Validación de texto de precio: numérico, hasta 2 decimales, rango [0.01, 999999.99]. */
object PriceInputValidator {
    const val ERROR_MESSAGE = "Precio inválido. Usa hasta 2 decimales entre 0.01 y 999999.99"

    private val FORMAT = Regex("^\\d{1,6}([.]\\d{1,2})?$")

    fun parse(text: String): PriceParseResult {
        val normalized = text.trim().replace(',', '.')
        if (!FORMAT.matches(normalized)) return PriceParseResult.Invalid(ERROR_MESSAGE)
        val value = normalized.toDoubleOrNull() ?: return PriceParseResult.Invalid(ERROR_MESSAGE)
        if (value < 0.01 || value > 999999.99) return PriceParseResult.Invalid(ERROR_MESSAGE)
        return PriceParseResult.Valid(value)
    }
}

/** Estado del formulario de precio (tienda seleccionada, texto y error). */
data class PriceFormState(
    val storeId: Long? = null,
    val text: String = "",
    val error: String? = null
)

/** Precio existente que se reemplazaría; requiere confirmación del usuario. */
data class PendingReplace(
    val storeId: Long,
    val storeName: String,
    val currentPrice: Double,
    val newPrice: Double
)

/** Lógica compartida de registro de precio (usada por detalle y formulario de producto). */
class PriceRegistrar @Inject constructor(
    private val registerPrice: RegisterPriceUseCase,
    private val priceRepo: PriceRepository,
    private val syncScheduler: SyncScheduler
) {
    suspend fun findExisting(productId: Long, storeId: Long, listId: Long): PriceRecord? =
        priceRepo.getPricesForList(listId).first()
            .firstOrNull { it.productId == productId && it.storeId == storeId }

    suspend fun register(productId: Long, storeId: Long, listId: Long, price: Double): Result<Unit> {
        val result = registerPrice(
            PriceRecord(
                productId = productId,
                storeId = storeId,
                monthlyListId = listId,
                price = price,
                recordDate = LocalDate.now()
            )
        )
        if (result.isSuccess) syncScheduler.requestSync()
        return result
    }
}
