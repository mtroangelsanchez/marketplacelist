package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.repository.PriceRepository
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

class RegisterPriceUseCase @Inject constructor(
    private val priceRepo: PriceRepository
) {
    suspend operator fun invoke(priceRecord: PriceRecord): Result<Unit> {
        val price = priceRecord.price

        if (!isValidPrice(price)) {
            return Result.failure(
                AppErrorException(
                    AppError.ValidationError(listOf("precio"))
                )
            )
        }

        return priceRepo.registerPrice(priceRecord)
    }

    private fun isValidPrice(price: Double): Boolean {
        if (price < 0.01 || price > 999999.99) {
            return false
        }
        // Check max 2 decimal places: multiply by 100 and verify it's an integer
        val scaled = (price * 100).roundToInt()
        val reconstructed = scaled / 100.0
        return (price - reconstructed) == 0.0 || abs(price - reconstructed) < 0.000001
    }
}
