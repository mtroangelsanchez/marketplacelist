package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError
import com.app.marketlist.domain.model.PriceRecord
import com.app.marketlist.domain.repository.PriceRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **Validates: Requirements 4.1, 4.2**
 *
 * Propiedad 11: Validación de rango de precio
 *
 * Para cualquier valor numérico, si está en el rango [0.01, 999999.99] con máximo 2 decimales,
 * deberá ser aceptado como precio válido; si está fuera de ese rango o no tiene formato numérico
 * válido, deberá ser rechazado.
 */
class RegisterPriceValidationPropertyTest : FunSpec({

    val priceRepo = mockk<PriceRepository>()
    val useCase = RegisterPriceUseCase(priceRepo)

    // Mock the repository to always succeed when called
    coEvery { priceRepo.registerPrice(any()) } returns Result.success(Unit)

    // Helper to create a PriceRecord with a given price
    fun priceRecord(price: Double) = PriceRecord(
        id = 0,
        productId = 1L,
        storeId = 1L,
        monthlyListId = 1L,
        price = price,
        recordDate = LocalDate.now()
    )

    // Generator for valid prices: [0.01, 999999.99] with max 2 decimal places
    // Strategy: generate cents as integer [1, 99999999] and divide by 100
    val validPriceArb: Arb<Double> = Arb.int(1, 99999999).map { cents ->
        cents / 100.0
    }

    // Generator for negative prices (invalid)
    val negativePriceArb: Arb<Double> = Arb.int(1, 1000000).map { value ->
        -(value / 100.0)
    }

    // Generator for zero price (invalid)
    val zeroPriceArb: Arb<Double> = Arb.of(0.0, 0.00, -0.0)

    // Generator for prices above max range (invalid)
    val aboveMaxPriceArb: Arb<Double> = Arb.int(100000000, 999999999).map { cents ->
        cents / 100.0
    }

    // Generator for prices with more than 2 decimal places (within valid range, invalid)
    // Strategy: generate thousandths [1, 999999999] where last digit (thousandths) is non-zero
    val moreThan2DecimalsArb: Arb<Double> = Arb.int(10, 999999999).map { thousandths ->
        thousandths / 1000.0
    }.filter { price ->
        // Ensure it actually has more than 2 effective decimal places
        val scaled = (price * 100).roundToInt()
        val reconstructed = scaled / 100.0
        abs(price - reconstructed) > 0.000001 && price in 0.01..999999.99
    }

    test("valid prices in range [0.01, 999999.99] with max 2 decimals should be accepted") {
        checkAll(PropTestConfig(iterations = 100), validPriceArb) { price ->
            val result = useCase(priceRecord(price))
            result.isSuccess shouldBe true
        }
    }

    test("negative prices should be rejected with ValidationError") {
        checkAll(PropTestConfig(iterations = 100), negativePriceArb) { price ->
            val result = useCase(priceRecord(price))
            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<AppErrorException>()
            (exception as AppErrorException).appError.shouldBeInstanceOf<AppError.ValidationError>()
        }
    }

    test("zero price should be rejected with ValidationError") {
        checkAll(PropTestConfig(iterations = 100), zeroPriceArb) { price ->
            val result = useCase(priceRecord(price))
            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<AppErrorException>()
            (exception as AppErrorException).appError.shouldBeInstanceOf<AppError.ValidationError>()
        }
    }

    test("prices above 999999.99 should be rejected with ValidationError") {
        checkAll(PropTestConfig(iterations = 100), aboveMaxPriceArb) { price ->
            val result = useCase(priceRecord(price))
            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<AppErrorException>()
            (exception as AppErrorException).appError.shouldBeInstanceOf<AppError.ValidationError>()
        }
    }

    test("prices with more than 2 decimal places should be rejected with ValidationError") {
        checkAll(PropTestConfig(iterations = 100), moreThan2DecimalsArb) { price ->
            val result = useCase(priceRecord(price))
            result.isFailure shouldBe true
            val exception = result.exceptionOrNull()
            exception.shouldBeInstanceOf<AppErrorException>()
            (exception as AppErrorException).appError.shouldBeInstanceOf<AppError.ValidationError>()
        }
    }

    test("boundary values: 0.01 (min) and 999999.99 (max) should be accepted") {
        val minResult = useCase(priceRecord(0.01))
        minResult.isSuccess shouldBe true

        val maxResult = useCase(priceRecord(999999.99))
        maxResult.isSuccess shouldBe true
    }
})
